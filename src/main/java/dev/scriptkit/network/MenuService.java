package dev.scriptkit.network;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.scriptkit.ai.CombatConfig;
import dev.scriptkit.ai.Difficulty;
import dev.scriptkit.npc.ArmyManager;
import dev.scriptkit.npc.KitManager;
import dev.scriptkit.npc.NpcManager;
import dev.scriptkit.npc.ScriptNpc;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;

/**
 * Server logic behind the menu: builds the snapshot the screen shows and applies edits. Everything
 * arriving from a client is untrusted and validated here; the caller has already checked permission.
 * Free of packet and player objects so it can be tested from the dev-only simulate command.
 */
public final class MenuService {
	private static final Gson GSON = new Gson();
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_\\-]{1,24}");
	private static final int MAX_DIFFICULTIES = 50;
	private static final int MAX_KITS = 100;

	public record Result(boolean ok, String message) {}

	private MenuService() {}

	// ------------------------------------------------------------------ snapshot

	public static String snapshot(MinecraftServer server, boolean canEdit, Result last) {
		CombatConfig cfg = CombatConfig.get();
		JsonObject root = new JsonObject();
		root.addProperty("canEdit", canEdit);
		root.addProperty("ok", last == null || last.ok());
		root.addProperty("message", last == null ? "" : last.message());
		root.addProperty("defaultDifficulty", cfg.defaultDifficulty);

		JsonObject diffs = new JsonObject();
		cfg.difficulties.forEach((name, d) -> diffs.add(name, GSON.toJsonTree(d)));
		root.add("difficulties", diffs);
		root.add("builtInDifficulties", GSON.toJsonTree(Difficulty.defaults().keySet()));

		JsonObject kits = new JsonObject();
		KitManager.definitions().forEach((name, slots) -> kits.add(name, KitManager.toJson(slots)));
		root.add("kits", kits);
		root.add("builtInKits", GSON.toJsonTree(KitManager.builtInNames()));

		Map<String, int[]> counts = ArmyManager.counts(server);
		Map<String, String> difficulties = ArmyManager.difficulties(server);
		Map<String, Set<String>> kitsByTeam = new LinkedHashMap<>();
		for (ScriptNpc npc : NpcManager.all(server)) {
			kitsByTeam.computeIfAbsent(npc.getTeamName().isEmpty() ? "-" : npc.getTeamName(), k -> new LinkedHashSet<>()).add(npc.getKit());
		}
		Set<String> teamNames = new TreeSet<>(server.getScoreboard().getTeamNames());
		teamNames.addAll(counts.keySet());
		teamNames.remove("-");
		var teams = new com.google.gson.JsonArray();
		for (String team : teamNames) {
			JsonObject t = new JsonObject();
			t.addProperty("name", team);
			int[] c = counts.get(team);
			t.addProperty("count", c == null ? 0 : c[0]);
			t.addProperty("difficulty", difficulties.getOrDefault(team, "-"));
			Set<String> k = kitsByTeam.get(team);
			t.addProperty("kit", k == null ? "-" : k.size() == 1 ? k.iterator().next() : "mixed");
			teams.add(t);
		}
		root.add("teams", teams);
		return root.toString();
	}

	// ------------------------------------------------------------------ edits

	/** Applies one edit. Never throws: bad input becomes a failed {@link Result}. */
	public static Result edit(MinecraftServer server, String op, String json) {
		try {
			JsonObject args = JsonParser.parseString(json == null || json.isBlank() ? "{}" : json).getAsJsonObject();
			return switch (op) {
				case "saveDifficulty" -> saveDifficulty(args);
				case "deleteDifficulty" -> deleteDifficulty(args);
				case "setDefaultDifficulty" -> setDefaultDifficulty(args);
				case "applyDifficulty" -> applyDifficulty(server, args);
				case "saveKit" -> saveKit(server, args);
				case "deleteKit" -> deleteKit(args);
				case "applyKit" -> applyKit(server, args);
				default -> fail("Unknown menu action.");
			};
		} catch (RuntimeException e) {
			return fail("That request was not valid.");
		}
	}

	private static Result saveDifficulty(JsonObject a) {
		String name = str(a, "name").toLowerCase(Locale.ROOT);
		if (!NAME.matcher(name).matches()) {
			return fail("Difficulty names use letters, digits, - and _ (1-24 characters).");
		}
		CombatConfig cfg = CombatConfig.get();
		if (!cfg.difficulties.containsKey(name) && cfg.difficulties.size() >= MAX_DIFFICULTIES) {
			return fail("Too many difficulties (max " + MAX_DIFFICULTIES + ").");
		}
		Difficulty d = GSON.fromJson(a.get("values"), Difficulty.class);
		if (d == null) {
			return fail("Missing values.");
		}
		d.sanitize();
		cfg.difficulties.put(name, d);
		CombatConfig.save();
		return ok("Saved difficulty " + name + ".");
	}

	private static Result deleteDifficulty(JsonObject a) {
		String name = str(a, "name").toLowerCase(Locale.ROOT);
		CombatConfig cfg = CombatConfig.get();
		if (!cfg.difficulties.containsKey(name)) {
			return fail("No such difficulty.");
		}
		Difficulty builtIn = Difficulty.defaults().get(name);
		if (builtIn != null) {
			cfg.difficulties.put(name, builtIn);
			CombatConfig.save();
			return ok("Reset " + name + " to its built-in values.");
		}
		cfg.difficulties.remove(name);
		if (cfg.defaultDifficulty.equals(name)) {
			cfg.defaultDifficulty = Difficulty.NORMAL;
		}
		CombatConfig.save();
		return ok("Deleted difficulty " + name + ". NPCs that used it now use the default.");
	}

	private static Result setDefaultDifficulty(JsonObject a) {
		String name = str(a, "name").toLowerCase(Locale.ROOT);
		CombatConfig cfg = CombatConfig.get();
		if (!cfg.difficulties.containsKey(name)) {
			return fail("No such difficulty.");
		}
		cfg.defaultDifficulty = name;
		CombatConfig.save();
		return ok("New NPCs will use " + name + ".");
	}

	private static Result applyDifficulty(MinecraftServer server, JsonObject a) {
		String name = str(a, "name").toLowerCase(Locale.ROOT);
		if (!CombatConfig.get().hasDifficulty(name)) {
			return fail("No such difficulty.");
		}
		String target = str(a, "target");
		if (!validTarget(server, target)) {
			return fail("Unknown team.");
		}
		int n = ArmyManager.setDifficulty(server, target, name);
		return n == 0 ? fail("No NPCs there.") : ok("Set " + name + " on " + n + " NPC(s).");
	}

	private static Result saveKit(MinecraftServer server, JsonObject a) {
		String name = str(a, "name").toLowerCase(Locale.ROOT);
		if (!KitManager.exists(name) && KitManager.definitions().size() >= MAX_KITS) {
			return fail("Too many kits (max " + MAX_KITS + ").");
		}
		if (!a.has("slots") || !a.get("slots").isJsonObject()) {
			return fail("Missing slots.");
		}
		for (String key : a.getAsJsonObject("slots").keySet()) {
			if (!java.util.Arrays.asList(KitManager.SLOT_KEYS).contains(key)) {
				return fail("Unknown slot '" + key + "'.");
			}
		}
		Map<String, KitManager.KitItem> slots = KitManager.slotsFromJson(a.getAsJsonObject("slots"));
		String error = KitManager.validate(slots, server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT));
		if (error == null) {
			error = KitManager.save(name, slots);
		}
		return error == null ? ok("Saved kit " + name + ".") : fail(error);
	}

	private static Result deleteKit(JsonObject a) {
		String name = str(a, "name").toLowerCase(Locale.ROOT);
		boolean builtIn = KitManager.builtInNames().contains(name);
		String error = KitManager.delete(name);
		if (error != null) {
			return fail(error);
		}
		return ok(builtIn ? "Reset kit " + name + " to its built-in gear." : "Deleted kit " + name + ".");
	}

	/** Target is "all", a team name, or "npc:<name>". */
	private static Result applyKit(MinecraftServer server, JsonObject a) {
		String kit = str(a, "kit").toLowerCase(Locale.ROOT);
		if (!KitManager.exists(kit)) {
			return fail("No such kit.");
		}
		String target = str(a, "target");
		List<ScriptNpc> npcs;
		if (target.startsWith("npc:")) {
			npcs = NpcManager.named(server, target.substring(4));
		} else if (validTarget(server, target)) {
			npcs = ArmyManager.select(server, target);
		} else {
			return fail("Unknown team.");
		}
		if (npcs.isEmpty()) {
			return fail("No NPCs there.");
		}
		npcs.forEach(n -> n.setKit(kit));
		return ok("Gave kit " + kit + " to " + npcs.size() + " NPC(s).");
	}

	/** "all", or the name of an existing scoreboard team. */
	private static boolean validTarget(MinecraftServer server, String target) {
		return ArmyManager.ALL.equalsIgnoreCase(target) || (target != null && server.getScoreboard().getPlayerTeam(target) != null);
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	private static Result ok(String message) {
		return new Result(true, message);
	}

	private static Result fail(String message) {
		return new Result(false, message);
	}
}
