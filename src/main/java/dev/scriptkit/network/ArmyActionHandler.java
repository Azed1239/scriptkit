package dev.scriptkit.network;

import dev.scriptkit.ScriptKitMod;
import dev.scriptkit.npc.ArmyManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of {@link ArmyActionPayload}. Nothing from the client is trusted: the sender is
 * rate-limited and permission-checked, the action and team are validated, and the work is done by
 * the same {@link ArmyManager} methods the /army commands use.
 */
public final class ArmyActionHandler {
	/** More than this many packets inside {@link #WINDOW_NANOS} from one player are dropped. */
	public static final int MAX_PACKETS_PER_WINDOW = 4;
	public static final long WINDOW_NANOS = 1_000_000_000L;

	/** Outcome of one packet. {@code message} is null when the packet was dropped silently. */
	public record Feedback(Component message, boolean applied) {}

	private static final Map<UUID, long[]> RATE = new HashMap<>(); // {windowStartNanos, count}
	private static final Set<String> WARNED_TEAMS = new HashSet<>();

	private ArmyActionHandler() {}

	/** Entry point for a real packet, on the server thread. */
	public static void handle(ServerPlayer player, ArmyActionPayload payload) {
		boolean permitted = Commands.LEVEL_GAMEMASTERS.check(player.permissions());
		Feedback fb = process(player.level().getServer(), player.getUUID(), player.getGameProfile().name(),
			permitted, payload.action(), payload.team(), System.nanoTime());
		if (fb.message() != null) {
			player.displayClientMessage(fb.message(), true); // action bar
		}
	}

	/**
	 * All the logic, free of packet and player objects so it can be exercised from tests.
	 * {@code now} is a nanoTime-style clock.
	 */
	public static Feedback process(MinecraftServer server, UUID sender, String senderName, boolean permitted,
		String actionId, String team, long now) {
		if (!allow(sender, now)) {
			return new Feedback(null, false);
		}
		if (!permitted) {
			return new Feedback(Component.literal("You don't have permission to control armies.").withStyle(ChatFormatting.RED), false);
		}
		ArmyAction action = ArmyAction.parse(actionId);
		if (action == null) {
			ScriptKitMod.LOGGER.warn("{} sent an unknown army action '{}'", senderName, abbreviate(actionId));
			return new Feedback(Component.literal("Unknown army action.").withStyle(ChatFormatting.RED), false);
		}

		String target = ArmyManager.ALL;
		if (action.needsTeam()) {
			if (!ArmyAction.isValidTeamName(team)) {
				ScriptKitMod.LOGGER.warn("{} sent an invalid team name '{}' for {}", senderName, abbreviate(team), action.id());
				return new Feedback(Component.literal("Invalid team name.").withStyle(ChatFormatting.RED), false);
			}
			if (server.getScoreboard().getPlayerTeam(team) == null) {
				if (WARNED_TEAMS.add(senderName + "/" + team)) {
					ScriptKitMod.LOGGER.warn("{} asked to {} team '{}', but no such scoreboard team exists (check keybinds.json)",
						senderName, action.id(), team);
				}
				return new Feedback(Component.literal("Unknown team '" + team + "'.").withStyle(ChatFormatting.RED), false);
			}
			target = team;
		}

		ArmyManager.AiResult result = ArmyManager.setAi(server, target, action.state());
		if (result.count() == 0) {
			String who = action.needsTeam() ? "team " + team : "any team";
			return new Feedback(Component.literal("No NPCs on " + who + ".").withStyle(ChatFormatting.YELLOW), false);
		}
		String label = action.needsTeam() ? capitalize(team) + " army" : "All armies";
		MutableComponent msg = Component.literal(label + ": ").withStyle(ChatFormatting.WHITE)
			.append(Component.literal(result.enabled() ? "ON" : "OFF")
				.withStyle(result.enabled() ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD))
			.append(Component.literal(" (" + result.count() + " NPC" + (result.count() == 1 ? "" : "s") + ")").withStyle(ChatFormatting.GRAY));
		return new Feedback(msg, true);
	}

	/** Fixed one-second window: at most {@link #MAX_PACKETS_PER_WINDOW} packets per second per player. */
	private static boolean allow(UUID sender, long now) {
		long[] state = RATE.computeIfAbsent(sender, k -> new long[] {now, 0});
		if (now - state[0] >= WINDOW_NANOS) {
			state[0] = now;
			state[1] = 0;
		}
		return ++state[1] <= MAX_PACKETS_PER_WINDOW;
	}

	public static void forget(UUID player) {
		RATE.remove(player);
	}

	public static void clear() {
		RATE.clear();
		WARNED_TEAMS.clear();
	}

	private static String capitalize(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	private static String abbreviate(String s) {
		if (s == null) {
			return "null";
		}
		return s.length() > 40 ? s.substring(0, 40) + "..." : s;
	}
}
