package dev.scriptkit.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/** Packet entry points for the menu. Permission and rate limits are enforced here, the work in {@link MenuService}. */
public final class MenuHandler {
	private static final int MAX_PACKETS_PER_WINDOW = 10;
	private static final long WINDOW_NANOS = 1_000_000_000L;
	private static final Map<UUID, long[]> RATE = new HashMap<>();

	private MenuHandler() {}

	public static boolean canEdit(ServerPlayer player) {
		return Commands.LEVEL_GAMEMASTERS.check(player.permissions());
	}

	public static void sendState(ServerPlayer player, MenuService.Result last) {
		ServerPlayNetworking.send(player, new MenuPayloads.State(
			MenuService.snapshot(player.level().getServer(), canEdit(player), last)));
	}

	public static void handleRequest(ServerPlayer player) {
		if (allow(player.getUUID(), System.nanoTime())) {
			sendState(player, null);
		}
	}

	public static void handleEdit(ServerPlayer player, MenuPayloads.Edit payload) {
		if (!allow(player.getUUID(), System.nanoTime())) {
			return;
		}
		if (!canEdit(player)) {
			sendState(player, new MenuService.Result(false, "You don't have permission to change ScriptKit settings."));
			return;
		}
		MenuService.Result result = MenuService.edit(player.level().getServer(), payload.op(), payload.json());
		sendState(player, result);
	}

	/** Opens the menu on a player's screen (client must have ScriptKit). Returns false if it can't. */
	public static boolean open(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, MenuPayloads.Open.TYPE)) {
			return false;
		}
		ServerPlayNetworking.send(player, new MenuPayloads.Open());
		return true;
	}

	private static boolean allow(UUID id, long now) {
		long[] s = RATE.computeIfAbsent(id, k -> new long[] {now, 0});
		if (now - s[0] >= WINDOW_NANOS) {
			s[0] = now;
			s[1] = 0;
		}
		return ++s[1] <= MAX_PACKETS_PER_WINDOW;
	}

	public static void forget(UUID id) {
		RATE.remove(id);
	}
}
