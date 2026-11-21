package dev.scriptkit.npc;

import dev.scriptkit.npc.ArmyManager.Formation;
import java.util.ArrayList;
import java.util.Comparator;
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
	/**
	 * A selection: a team name, or one NPC's UUID if the clicked NPC has no team, plus the formation the
	 * stick will arrange it in. A null formation means "keep the army's current shape".
	 */
	private static final class Selection {
		final String team;
		final UUID single;
		Formation formation;
		int spacingIndex = Formations.spacingIndex(Formations.DEFAULT_SPACING);

		Selection(String team, UUID single) {
			this.team = team;
			this.single = single;
		}

		String team() {
			return team;
		}

		UUID single() {
			return single;
		}

		double spacing() {
			return Formations.SPACINGS[spacingIndex];
		}
	}

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
		sendGhost(server, player, now, members);
		String what = now.team().isEmpty() ? clicked.getNpcName() : "army " + now.team() + " (" + members.size() + " NPCs)";
		return new Result(true, "Selected " + what + ". Left-click: change formation (Shift: spacing). Right-click the ground to place it, Shift+right-click to deselect, G to start the fight.");
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
		place(level, members, sel.formation, sel.spacing(), target, yaw);
		sendGhost(level.getServer(), player, sel, members);
		String what = sel.team().isEmpty() ? members.get(0).getNpcName() : "army " + sel.team() + " (" + members.size() + " NPCs)";
		return new Result(true, "Placed " + what + (sel.formation == null ? "" : " in " + describe(sel)) + ". Press G to start the fight.");
	}

	/** Members in a stable order so every NPC keeps the same slot while you cycle shapes. */
	private static List<ScriptNpc> ordered(List<ScriptNpc> members) {
		List<ScriptNpc> sorted = new ArrayList<>(members);
		sorted.sort(Comparator.comparing(ScriptNpc::getUUID));
		return sorted;
	}

	private static String describe(Selection sel) {
		return sel.formation.name().toLowerCase() + ", spacing " + sel.spacing();
	}

	/**
	 * Puts {@code members} on the ground around {@code target}. With a formation, each NPC goes to its slot
	 * rotated to face {@code yaw}; with none (null) the army shifts as it is, keeping its current shape.
	 */
	public static void place(ServerLevel level, List<ScriptNpc> members, Formation formation, double spacing, Vec3 target, float yaw) {
		double cx = 0;
		double cz = 0;
		for (ScriptNpc npc : members) {
			cx += npc.getX();
			cz += npc.getZ();
		}
		cx /= members.size();
		cz /= members.size();
		List<ScriptNpc> sorted = ordered(members);
		List<double[]> layout = formation == null ? null : Formations.layout(formation, sorted.size(), spacing);
		for (ScriptNpc npc : members) {
			double nx;
			double nz;
			if (layout == null) {
				nx = target.x + (npc.getX() - cx);
				nz = target.z + (npc.getZ() - cz);
			} else {
				double[] slot = layout.get(sorted.indexOf(npc));
				double[] off = Formations.toWorld(slot[0], slot[1], yaw);
				nx = target.x + off[0];
				nz = target.z + off[1];
			}
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
	}

	/** Stick left-click: next formation (or, if {@code spacing}, next spacing). The ghost preview updates at once. */
	public static synchronized Result cycle(MinecraftServer server, UUID player, boolean spacing) {
		Selection sel = SELECTED.get(player);
		if (sel == null) {
			return new Result(false, "Nothing selected. Right-click an NPC to select its army first.");
		}
		if (spacing) {
			if (sel.formation == null) {
				return new Result(false, "Pick a formation first (left-click), then Shift+left-click changes the spacing.");
			}
			sel.spacingIndex = Formations.nextSpacingIndex(sel.spacingIndex);
		} else {
			sel.formation = sel.formation == null ? Formation.LINE : Formations.next(sel.formation);
		}
		sendGhost(server, player, sel, members(server, sel));
		return new Result(true, "Formation: " + describe(sel) + " (left-click: shape, Shift+left-click: spacing)");
	}

	/** Re-sends every selection's ghost preview (after something moved an army without the stick). */
	public static synchronized void refreshGhosts(MinecraftServer server) {
		for (Map.Entry<UUID, Selection> e : SELECTED.entrySet()) {
			sendGhost(server, e.getKey(), e.getValue(), members(server, e.getValue()));
		}
	}

	/** For tests: the formation currently chosen by {@code player}, or null. */
	public static synchronized Formation formationOf(UUID player) {
		Selection sel = SELECTED.get(player);
		return sel == null ? null : sel.formation;
	}

	public static synchronized Result deselect(MinecraftServer server, UUID player) {
		Selection sel = SELECTED.remove(player);
		if (sel == null) {
			return new Result(true, "Nothing was selected.");
		}
		outline(server, sel, false);
		sendGhost(server, player, null, List.of());
		return new Result(true, "Deselected.");
	}

	/** Called when armies are started (AI switched on): selections that are now running are dropped, outline and ghosts included. */
	public static synchronized void armiesStarted(MinecraftServer server) {
		for (Map.Entry<UUID, Selection> e : new ArrayList<>(SELECTED.entrySet())) {
			if (members(server, e.getValue()).stream().anyMatch(ScriptNpc::isAiEnabled)) {
				SELECTED.remove(e.getKey());
				outline(server, e.getValue(), false);
				sendGhost(server, e.getKey(), null, List.of());
			}
		}
	}

	/**
	 * Builds the preview formation and sends it to the player so the client can draw ghosts. With a chosen
	 * formation the slots are local (right, ahead) offsets that the client rotates to the player's aim;
	 * otherwise they are the army's current world-aligned offsets from its centre.
	 */
	private static void sendGhost(MinecraftServer server, UUID player, Selection sel, List<ScriptNpc> members) {
		List<dev.scriptkit.network.GhostPayload.Slot> slots = new ArrayList<>();
		boolean rotates = sel != null && sel.formation != null;
		if (!members.isEmpty()) {
			List<ScriptNpc> sorted = rotates ? ordered(members) : members;
			List<double[]> layout = rotates ? Formations.layout(sel.formation, sorted.size(), sel.spacing()) : null;
			double cx = 0;
			double cz = 0;
			for (ScriptNpc npc : members) {
				cx += npc.getX();
				cz += npc.getZ();
			}
			cx /= members.size();
			cz /= members.size();
			for (int i = 0; i < sorted.size() && slots.size() < dev.scriptkit.network.GhostPayload.MAX_SLOTS; i++) {
				ScriptNpc npc = sorted.get(i);
				float dx = rotates ? (float) layout.get(i)[0] : (float) (npc.getX() - cx);
				float dz = rotates ? (float) layout.get(i)[1] : (float) (npc.getZ() - cz);
				slots.add(new dev.scriptkit.network.GhostPayload.Slot(npc.getSkinName(),
					npc.getUUID().getMostSignificantBits(), npc.getUUID().getLeastSignificantBits(), dx, dz));
			}
		}
		dev.scriptkit.network.ScriptKitNetworking.sendGhost(server, player, slots, rotates);
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
