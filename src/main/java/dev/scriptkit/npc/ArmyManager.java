package dev.scriptkit.npc;

import dev.scriptkit.config.ScriptKitConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Central army/team operations. Commands and (later) network packets should call this class
 * rather than touching NPCs directly, e.g. {@code ArmyManager.toggle(server, "red", null)}.
 */
public final class ArmyManager {
	public static final String ALL = "all";
	private static final double SPACING = 1.5;
	private static final ChatFormatting[] PALETTE = {
		ChatFormatting.RED, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.YELLOW,
		ChatFormatting.AQUA, ChatFormatting.LIGHT_PURPLE, ChatFormatting.GOLD, ChatFormatting.DARK_GREEN,
		ChatFormatting.DARK_AQUA, ChatFormatting.DARK_PURPLE, ChatFormatting.DARK_RED, ChatFormatting.DARK_BLUE
	};

	public enum Formation {
		CIRCLE, GRID;

		public static Formation parse(String s) {
			try {
				return valueOf(s.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException | NullPointerException e) {
				return null;
			}
		}
	}

	private ArmyManager() {}

	/** Gets the scoreboard team, creating it (with a stable color and the configured friendly-fire flag) if missing. */
	public static PlayerTeam ensureTeam(Scoreboard scoreboard, String name) {
		PlayerTeam team = scoreboard.getPlayerTeam(name);
		if (team == null) {
			team = scoreboard.addPlayerTeam(name);
			team.setColor(PALETTE[Math.floorMod(name.hashCode(), PALETTE.length)]);
			team.setAllowFriendlyFire(ScriptKitConfig.get().friendlyFire);
		}
		return team;
	}

	/** Spawns {@code count} NPCs named team-N in a formation centered on {@code origin}. Returns how many were added. */
	public static int spawn(ServerLevel level, Vec3 origin, float yaw, int count, String team, String kit, String skin, Formation formation, String difficulty) {
		MinecraftServer server = level.getServer();
		int next = nextIndex(server, team);
		int spawned = 0;
		ScriptKitConfig cfg = ScriptKitConfig.get();
		boolean generated = cfg.nameStyle.equals("generated");
		Set<String> taken = new HashSet<>();
		if (generated) {
			NpcManager.all(server).forEach(n -> taken.add(n.getNpcName().toLowerCase()));
		}
		Random rnd = new Random();
		for (int i = 0; i < count; i++) {
			Vec3 offset = offset(formation, i, count);
			double x = origin.x + offset.x;
			double z = origin.z + offset.z;
			double y = groundY(level, x, origin.y, z);
			String name = generated
				? NameGenerator.generate(rnd, n -> taken.contains(n.toLowerCase()) || n.equalsIgnoreCase(ALL))
				: team + "-" + (next + i);
			taken.add(name.toLowerCase());

			ScriptNpc npc = ScriptNpc.create(level);
			npc.snapTo(x, y, z, yaw, 0.0F);
			npc.setYHeadRot(yaw);
			npc.setNpcName(name);
			npc.setSkinName(skin != null ? skin : randomSkin(cfg, rnd));
			npc.setKit(kit);
			npc.setTeamName(team);
			npc.setHome(new Vec3(x, y, z));
			if (difficulty != null) {
				npc.setDifficulty(difficulty);
			} else {
				npc.applyDifficulty(true);
			}
			if (level.addFreshEntity(npc)) {
				spawned++;
			}
		}
		return spawned;
	}

	/** A skin from the configured pool, or "" for a random built-in skin chosen by the client from the NPC's UUID. */
	private static String randomSkin(ScriptKitConfig cfg, Random rnd) {
		return cfg.skinPool.isEmpty() ? "" : cfg.skinPool.get(rnd.nextInt(cfg.skinPool.size()));
	}

	/** Result of {@link #setAi}: how many NPCs were affected and the AI state they now have. */
	public record AiResult(int count, boolean enabled) {}

	/**
	 * Sets aiEnabled for NPCs on {@code team} (or all NPCs for {@link #ALL}). {@code state == null}
	 * toggles the group: if any NPC has AI on they all go off, otherwise they all go on, so repeated
	 * toggles always alternate cleanly even when the group is mixed. This is the one method both the
	 * /army commands and keybind packets use.
	 */
	public static AiResult setAi(MinecraftServer server, String team, Boolean state) {
		List<ScriptNpc> npcs = select(server, team);
		if (npcs.isEmpty()) {
			return new AiResult(0, false);
		}
		boolean enable = state != null ? state : npcs.stream().noneMatch(ScriptNpc::isAiEnabled);
		for (ScriptNpc npc : npcs) {
			npc.setAiEnabled(enable);
		}
		if (enable) {
			ArmySelection.armiesStarted(server); // started armies are no longer selected: drop their outline and ghosts
		}
		return new AiResult(npcs.size(), enable);
	}

	/** Sets the difficulty preset of every NPC on {@code team} (or all). Returns how many NPCs changed. */
	public static int setDifficulty(MinecraftServer server, String team, String difficulty) {
		List<ScriptNpc> npcs = select(server, team);
		for (ScriptNpc npc : npcs) {
			npc.setDifficulty(difficulty);
		}
		return npcs.size();
	}

	/** Gives every NPC on {@code team} (or all) a kit. Returns how many NPCs changed. */
	public static int setKit(MinecraftServer server, String team, String kit) {
		List<ScriptNpc> npcs = select(server, team);
		for (ScriptNpc npc : npcs) {
			npc.setKit(kit);
		}
		return npcs.size();
	}

	/** team name ("-" for none) -> difficulty name, or "mixed" if the team has several. */
	public static Map<String, String> difficulties(MinecraftServer server) {
		Map<String, String> result = new TreeMap<>();
		for (ScriptNpc npc : NpcManager.all(server)) {
			String team = npc.getTeamName().isEmpty() ? "-" : npc.getTeamName();
			result.merge(team, npc.getDifficultyName(), (a, b) -> a.equals(b) ? a : "mixed");
		}
		return result;
	}

	public static int remove(MinecraftServer server, String team) {
		List<ScriptNpc> list = select(server, team);
		for (ScriptNpc npc : list) {
			NpcMessages.removed(npc);
			npc.leaveTeam();
			npc.discard();
		}
		return list.size();
	}

	public static int moveToTeam(Collection<ScriptNpc> npcs, String team) {
		for (ScriptNpc npc : npcs) {
			npc.leaveTeam();
			npc.setTeamName(team);
		}
		return npcs.size();
	}

	/** team name ("-" for none) -> {total, aiOn}. */
	public static Map<String, int[]> counts(MinecraftServer server) {
		Map<String, int[]> result = new TreeMap<>();
		for (ScriptNpc npc : NpcManager.all(server)) {
			int[] c = result.computeIfAbsent(npc.getTeamName().isEmpty() ? "-" : npc.getTeamName(), k -> new int[2]);
			c[0]++;
			if (npc.isAiEnabled()) {
				c[1]++;
			}
		}
		return result;
	}

	public static List<ScriptNpc> select(MinecraftServer server, String team) {
		if (team.equalsIgnoreCase(ALL)) {
			return NpcManager.all(server);
		}
		return NpcManager.all(server).stream().filter(n -> n.getTeamName().equalsIgnoreCase(team)).toList();
	}

	private static int nextIndex(MinecraftServer server, String team) {
		Pattern p = Pattern.compile(Pattern.quote(team) + "-(\\d+)", Pattern.CASE_INSENSITIVE);
		int max = 0;
		for (ScriptNpc npc : NpcManager.all(server)) {
			Matcher m = p.matcher(npc.getNpcName());
			if (m.matches()) {
				try {
					max = Math.max(max, Integer.parseInt(m.group(1)));
				} catch (NumberFormatException ignored) {
					// absurdly long number; ignore
				}
			}
		}
		return max + 1;
	}

	private static Vec3 offset(Formation formation, int i, int count) {
		if (formation == Formation.GRID) {
			int cols = (int) Math.ceil(Math.sqrt(count));
			int rows = (int) Math.ceil(count / (double) cols);
			double x = (i % cols - (cols - 1) / 2.0) * SPACING;
			double z = (i / cols - (rows - 1) / 2.0) * SPACING;
			return new Vec3(x, 0, z);
		}
		if (count == 1) {
			return Vec3.ZERO;
		}
		double radius = Math.max(2.0, count * SPACING / (2 * Math.PI));
		double angle = 2 * Math.PI * i / count;
		return new Vec3(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
	}

	/** Finds a standing height near {@code originY}; only inspects already-loaded chunks. */
	public static double groundY(ServerLevel level, double x, double originY, double z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Math.floor(x), Math.floor(originY), Math.floor(z));
		if (!level.isLoaded(pos)) {
			return originY;
		}
		// If the spot is inside solid blocks, climb until there is room for a 2-tall NPC.
		for (int i = 0; i < 24 && !(isFree(level, pos) && isFree(level, pos.above())); i++) {
			pos.move(0, 1, 0);
		}
		if (!isFree(level, pos) || !isFree(level, pos.above())) {
			return originY;
		}
		// Then drop down onto the first solid floor.
		for (int i = 0; i < 12; i++) {
			if (!isFree(level, pos.below())) {
				return pos.getY();
			}
			pos.move(0, -1, 0);
		}
		return originY;
	}

	private static boolean isFree(ServerLevel level, BlockPos p) {
		BlockState state = level.getBlockState(p);
		return state.getCollisionShape(level, p).isEmpty();
	}

	/** Names for tab completion. */
	public static List<String> teamNames(MinecraftServer server) {
		List<String> names = new ArrayList<>(server.getScoreboard().getTeamNames());
		names.add(ALL);
		return names;
	}
}
