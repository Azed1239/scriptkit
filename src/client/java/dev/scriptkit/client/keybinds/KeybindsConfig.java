package dev.scriptkit.client.keybinds;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.scriptkit.network.ArmyAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * config/scriptkit/keybinds.json, parsed leniently: a bad binding is skipped with a clear warning
 * and never stops the others (or the game) from loading. Pure Java so it can be unit-tested.
 */
public final class KeybindsConfig {
	/** One combo binding. {@code groups} has one entry per required key; each is a set of acceptable codes. */
	public record Binding(ArmyAction action, String team, List<Set<Integer>> groups, String description) {}

	public record Parsed(List<Binding> bindings, List<String> teamSlots, List<String> warnings) {}

	public static final int TEAM_SLOTS = 3;

	public static final String SAMPLE = """
		{
		  "_comment": "EXAMPLES - these two combos and the team slots are samples, edit or delete them freely. Actions: toggleAll, toggleTeam, enableAll, disableAll, enableTeam, disableTeam. Key names: A-Z, 0-9, F1-F12, SPACE, TAB, ... and CTRL, SHIFT, ALT (either side) or LEFT_CONTROL, RIGHT_SHIFT, etc. Combos match exactly: Ctrl+R does not fire plain R. After editing run /scriptkit reload.",
		  "teamSlots": ["red", "blue", "green"],
		  "bindings": [
		    { "_comment": "Example: Ctrl+R toggles the red team", "action": "toggleTeam", "team": "red", "keys": ["LEFT_CONTROL", "R"] },
		    { "_comment": "Example: Ctrl+B toggles the blue team", "action": "toggleTeam", "team": "blue", "keys": ["LEFT_CONTROL", "B"] }
		  ]
		}
		""";

	private KeybindsConfig() {}

	public static Parsed parse(String json) {
		List<Binding> bindings = new ArrayList<>();
		List<String> slots = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		for (int i = 0; i < TEAM_SLOTS; i++) {
			slots.add("");
		}
		JsonObject root;
		try {
			JsonElement parsed = JsonParser.parseString(json);
			if (!parsed.isJsonObject()) {
				warnings.add("keybinds.json must contain a JSON object; ignoring the file");
				return new Parsed(bindings, slots, warnings);
			}
			root = parsed.getAsJsonObject();
		} catch (RuntimeException e) {
			warnings.add("keybinds.json is not valid JSON (" + e.getMessage() + "); ignoring the file");
			return new Parsed(bindings, slots, warnings);
		}

		if (root.has("teamSlots") && root.get("teamSlots").isJsonArray()) {
			JsonArray arr = root.getAsJsonArray("teamSlots");
			for (int i = 0; i < Math.min(arr.size(), TEAM_SLOTS); i++) {
				String team = asString(arr.get(i));
				if (team == null || team.isEmpty()) {
					continue;
				}
				if (!ArmyAction.isValidTeamName(team)) {
					warnings.add("teamSlots[" + i + "]: '" + team + "' is not a valid team name (letters, digits and _ . + - only, not 'all'); slot left empty");
					continue;
				}
				slots.set(i, team);
			}
		}

		if (root.has("bindings") && root.get("bindings").isJsonArray()) {
			JsonArray arr = root.getAsJsonArray("bindings");
			for (int i = 0; i < arr.size(); i++) {
				String where = "bindings[" + i + "]";
				if (!arr.get(i).isJsonObject()) {
					warnings.add(where + ": not an object, skipped");
					continue;
				}
				Binding b = parseBinding(arr.get(i).getAsJsonObject(), where, warnings);
				if (b != null) {
					for (Binding other : bindings) {
						if (sameKeys(other, b)) {
							warnings.add(where + ": same keys as '" + other.description() + "'; both will fire");
						}
					}
					bindings.add(b);
				}
			}
		}
		return new Parsed(bindings, slots, warnings);
	}

	private static Binding parseBinding(JsonObject o, String where, List<String> warnings) {
		String actionName = asString(o.get("action"));
		ArmyAction action = ArmyAction.parse(actionName);
		if (action == null) {
			warnings.add(where + ": unknown action '" + actionName + "' (valid: " + ArmyAction.validIds() + "); skipped");
			return null;
		}
		String team = "";
		if (action.needsTeam()) {
			team = asString(o.get("team"));
			if (team == null || team.isEmpty()) {
				warnings.add(where + ": action '" + action.id() + "' needs a \"team\"; skipped");
				return null;
			}
			if (!ArmyAction.isValidTeamName(team)) {
				warnings.add(where + ": '" + team + "' is not a valid team name (letters, digits and _ . + - only, not 'all'); skipped");
				return null;
			}
		}
		if (!o.has("keys") || !o.get("keys").isJsonArray() || o.getAsJsonArray("keys").isEmpty()) {
			warnings.add(where + ": \"keys\" must be a non-empty list like [\"LEFT_CONTROL\", \"R\"]; skipped");
			return null;
		}
		List<Set<Integer>> groups = new ArrayList<>();
		StringBuilder desc = new StringBuilder();
		for (JsonElement k : o.getAsJsonArray("keys")) {
			String name = asString(k);
			Set<Integer> codes = KeyNames.lookup(name);
			if (codes == null) {
				warnings.add(where + ": unknown key '" + name + "'; skipped");
				return null;
			}
			if (groups.contains(codes)) {
				continue; // key listed twice
			}
			groups.add(codes);
			if (desc.length() > 0) {
				desc.append('+');
			}
			desc.append(name.trim().toUpperCase());
		}
		if (groups.stream().allMatch(KeyNames::isModifierGroup)) {
			warnings.add(where + ": a combo needs at least one non-modifier key (not just Ctrl/Shift/Alt); skipped");
			return null;
		}
		String label = desc + " -> " + action.id() + (team.isEmpty() ? "" : "(" + team + ")");
		return new Binding(action, team, List.copyOf(groups), label);
	}

	private static boolean sameKeys(Binding a, Binding b) {
		return Set.copyOf(a.groups()).equals(Set.copyOf(b.groups()));
	}

	private static String asString(JsonElement e) {
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}
}
