package dev.scriptkit.npc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * What the Command Stick does, free of item and packet code so it can be tested from the dev-only
 * simulate command. A player selects an army (everyone on the clicked NPC's team) and then moves it:
 * the whole army shifts to the clicked spot, keeping its formation, and its new position becomes
 * its home for regrouping. Selected armies are frozen (AI off) and outlined until they are started.
 */
public final class ArmySelection {
	/** A selection: a team name, or one NPC's UUID if the clicked NPC has no team. */
	private record Selection(String team, UUID single) {}

	public record Result(boolean ok, String message) {}

	private static final Map<UUID, Selection> SELECTED = new HashMap<>();

	private ArmySelection() {}

	public static synchronized Result select(MinecraftServer server, UUID player, ScriptNpc clicked) {
		Selection previous = SELECTED.get(player);
		if (previous != null) {
			outline(server, previous, false);
		}
		Selection now = clicked.getTeamName().isEmpty()
			? new Selection("", clicked.getUUID())
			: new Selection(clicked.getTeamName(), null);
		SELECTED.put(player, now);
		List<ScriptNpc> members = members(server, now);
		for (ScriptNpc npc : members) {
			npc.setAiEnabled(false); // frozen while it is arranged
			npc.setGlowingTag(true);
		}
		sendGhost(server, player, members);
		String what = now.team().isEmpty() ? clicked.getNpcName() : "army " + now.team() + " (" + members.size() + " NPCs)";
		return new Result(true, "Selected " + what + ". Right-click the ground to move it, Shift+right-click to deselect, G to start the fight.");
	}

	/** Moves the selected army so its centre lands on {@code target}, facing {@code yaw}. */
	public static synchronized Result move(ServerLevel level, UUID player, Vec3 target, float yaw) {
		Selection sel = SELECTED.get(player);
		if (sel == null) {
			return new Result(false, "Nothing selected. Right-click an NPC to select its army first.");
		}
		List<ScriptNpc> members = new ArrayList<>();
		for (ScriptNpc npc : members(level.getServer(), sel)) {
			if (npc.level() == level) {
				members.add(npc);
			}
		}
		if (members.isEmpty()) {
			SELECTED.remove(player);
			return new Result(false, "That army is gone or in another dimension.");
		}
		double cx = 0;
		double cz = 0;
		for (ScriptNpc npc : members) {
			cx += npc.getX();
			cz += npc.getZ();
		}
		cx /= members.size();
		cz /= members.size();
		for (ScriptNpc npc : members) {
			double nx = target.x + (npc.getX() - cx);
			double nz = target.z + (npc.getZ() - cz);
			double ny = ArmyManager.groundY(level, nx, target.y, nz);
			npc.getNavigation().stop();
			npc.snapTo(nx, ny, nz, yaw, 0.0F);
			// If the spot turned out to be inside a block (uneven ground, overhangs), step up out of it.
			for (int i = 0; i < 12 && !level.noCollision(npc); i++) {
				ny += 1.0;
				npc.snapTo(nx, ny, nz, yaw, 0.0F);
			}
			npc.setYHeadRot(yaw);
			npc.setDeltaMovement(Vec3.ZERO);
			npc.fallDistance = 0.0;
			npc.setHome(new Vec3(nx, ny, nz));
		}
		sendGhost(level.getServer(), player, members);
		String what = sel.team().isEmpty() ? members.get(0).getNpcName() : "army " + sel.team() + " (" + members.size() + " NPCs)";
		return new Result(true, "Moved " + what + ". Press G to start the fight.");
	}

	public static synchronized Result deselect(MinecraftServer server, UUID player) {
		Selection sel = SELECTED.remove(player);
		if (sel == null) {
			return new Result(true, "Nothing was selected.");
		}
		outline(server, sel, false);
		sendGhost(server, player, List.of());
		return new Result(true, "Deselected.");
	}

	/** Called when armies are started (AI switched on): selections that are now running are dropped, outline and ghosts included. */
	public static synchronized void armiesStarted(MinecraftServer server) {
		for (Map.Entry<UUID, Selection> e : new ArrayList<>(SELECTED.entrySet())) {
			if (members(server, e.getValue()).stream().anyMatch(ScriptNpc::isAiEnabled)) {
				SELECTED.remove(e.getKey());
				outline(server, e.getValue(), false);
				sendGhost(server, e.getKey(), List.of());
			}
		}
	}

	/** Builds the formation (offsets from the army's centre) and sends it to the player so the client can draw ghosts. */
	private static void sendGhost(MinecraftServer server, UUID player, List<ScriptNpc> members) {
		List<dev.scriptkit.network.GhostPayload.Slot> slots = new ArrayList<>();
		if (!members.isEmpty()) {
			double cx = 0;
			double cz = 0;
			for (ScriptNpc npc : members) {
				cx += npc.getX();
				cz += npc.getZ();
			}
			cx /= members.size();
			cz /= members.size();
			for (ScriptNpc npc : members) {
				if (slots.size() >= dev.scriptkit.network.GhostPayload.MAX_SLOTS) {
					break;
				}
				slots.add(new dev.scriptkit.network.GhostPayload.Slot(npc.getSkinName(),
					npc.getUUID().getMostSignificantBits(), npc.getUUID().getLeastSignificantBits(),
					(float) (npc.getX() - cx), (float) (npc.getZ() - cz)));
			}
		}
		dev.scriptkit.network.ScriptKitNetworking.sendGhost(server, player, slots);
	}

	/** Forget a player (disconnect) and remove the outline from whatever they had selected. */
	public static synchronized void forget(MinecraftServer server, UUID player) {
		Selection sel = SELECTED.remove(player);
		if (sel != null) {
			outline(server, sel, false);
		}
	}

	public static synchronized void clear() {
		SELECTED.clear();
	}

	private static List<ScriptNpc> members(MinecraftServer server, Selection sel) {
		if (!sel.team().isEmpty()) {
			return ArmyManager.select(server, sel.team());
		}
		List<ScriptNpc> one = new ArrayList<>();
		for (ScriptNpc npc : NpcManager.all(server)) {
			if (npc.getUUID().equals(sel.single())) {
				one.add(npc);
			}
		}
		return one;
	}

	private static void outline(MinecraftServer server, Selection sel, boolean on) {
		for (ScriptNpc npc : members(server, sel)) {
			npc.setGlowingTag(on);
		}
	}
}
