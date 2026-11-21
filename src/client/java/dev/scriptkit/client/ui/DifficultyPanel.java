package dev.scriptkit.client.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Difficulty tab: pick a preset, tune every value with sliders, save it (or save a copy under a new
 * name), reset or delete it, make it the default, and apply it to an army.
 */
public class DifficultyPanel implements MenuPanel {
	/** key in the preset JSON, label, min, max, step, how to show it. */
	private record Field(String key, String label, double min, double max, double step, boolean percent) {}

	private static final List<Field> FIELDS = List.of(
		new Field("damage", "Damage", 0.0, 10.0, 0.05, false),
		new Field("health", "Health", 0.1, 5.0, 0.05, false),
		new Field("meleeAccuracy", "Melee accuracy", 0.0, 1.0, 0.01, true),
		new Field("archerAccuracy", "Archer accuracy", 0.0, 1.0, 0.01, true),
		new Field("critChance", "Crit chance", 0.0, 1.0, 0.01, true),
		new Field("attackSpeed", "Swing rate", 0.25, 3.0, 0.05, false),
		new Field("moveSpeed", "Speed", 0.5, 1.5, 0.01, false),
		new Field("blockSkill", "Shield skill", 0.0, 1.0, 0.01, true),
		new Field("reaction", "Reaction", 0.25, 5.0, 0.05, false),
		new Field("range", "Detection range", 0.25, 3.0, 0.05, false));

	private final Map<String, Map<String, Double>> presets = new LinkedHashMap<>();
	private final Map<String, Double> working = new LinkedHashMap<>();
	private Set<String> builtIn = Set.of();
	private String defaultName = "normal";
	private String selected = "normal";
	private final List<JsonObject> teams = new ArrayList<>();
	private String applyTarget = "all";
	private String newName = "";
	private boolean editable;
	private int panelX;
	private int panelY;
	private int panelH;
	private int armiesY = -1;

	@Override
	public void onState(JsonObject state) {
		if (state == null || !state.has("difficulties")) {
			return;
		}
		presets.clear();
		for (Map.Entry<String, JsonElement> e : state.getAsJsonObject("difficulties").entrySet()) {
			Map<String, Double> values = new LinkedHashMap<>();
			for (Field f : FIELDS) {
				JsonElement v = e.getValue().getAsJsonObject().get(f.key());
				values.put(f.key(), v == null ? 1.0 : v.getAsDouble());
			}
			presets.put(e.getKey(), values);
		}
		builtIn = new java.util.HashSet<>();
		state.getAsJsonArray("builtInDifficulties").forEach(n -> builtIn.add(n.getAsString()));
		defaultName = state.get("defaultDifficulty").getAsString();
		teams.clear();
		state.getAsJsonArray("teams").forEach(t -> teams.add(t.getAsJsonObject()));
		if (!presets.containsKey(selected)) {
			selected = presets.containsKey(defaultName) ? defaultName : presets.keySet().iterator().next();
		}
		loadWorking();
		if (!applyTarget.equals("all") && teams.stream().noneMatch(t -> t.get("name").getAsString().equals(applyTarget))) {
			applyTarget = "all";
		}
	}

	private void loadWorking() {
		working.clear();
		Map<String, Double> values = presets.get(selected);
		if (values != null) {
			working.putAll(values);
		}
	}

	@Override
	public void build(ScriptKitScreen screen, int x, int y, int w, int h) {
		editable = screen.canEdit();
		panelX = x;
		panelY = y;
		panelH = h;
		armiesY = -1;
		// Compact spacing on small windows so everything fits down to a 240-pixel-tall GUI.
		int rowStep = h >= 230 ? 24 : 21;
		int gap = h >= 230 ? 8 : 4;
		if (presets.isEmpty()) {
			return; // waiting for the server
		}
		// Preset list on the left.
		int listW = 130;
		SimpleList<String> list = new SimpleList<>(x, y + 12, listW, h - 12, 16,
			(g, name, rx, ry, rw, rh, hovered, sel) -> {
				String label = name + (name.equals(defaultName) ? "  (default)" : builtIn.contains(name) ? "" : "  *");
				g.drawString(screen.fontRenderer(), label, rx, ry + 4, sel ? 0xFFFFFFFF : 0xFFCCCCCC);
			},
			name -> {
				selected = name;
				loadWorking();
				screen.rebuild();
			});
		list.setItems(new ArrayList<>(presets.keySet()));
		list.select(selected);
		screen.add(list);

		// Sliders in two columns.
		int ex = x + listW + 12;
		int colW = Math.min(210, (w - listW - 12 - 8) / 2);
		int sliderY = y + 12;
		for (int i = 0; i < FIELDS.size(); i++) {
			Field f = FIELDS.get(i);
			int sx = ex + (i % 2) * (colW + 8);
			int sy = sliderY + (i / 2) * rowStep;
			ValueSlider slider = new ValueSlider(sx, sy, colW, 20, f.label(), f.min(), f.max(), f.step(),
				working.getOrDefault(f.key(), 1.0), v -> format(f, v), v -> working.put(f.key(), v));
			slider.active = editable;
			screen.add(slider);
		}

		// Actions.
		int by = sliderY + 5 * rowStep + gap;
		int bw = (colW * 2 + 8 - 8) / 3;
		boolean isBuiltIn = builtIn.contains(selected);
		button(screen, Component.literal("Save " + selected), ex, by, bw, "Saves these values to the '" + selected + "' preset.", () -> {
			JsonObject args = new JsonObject();
			args.addProperty("name", selected);
			args.add("values", valuesJson());
			screen.sendEdit("saveDifficulty", args);
		});
		button(screen, Component.literal(isBuiltIn ? "Reset to default" : "Delete"), ex + bw + 4, by, bw,
			isBuiltIn ? "Restores the built-in values." : "Deletes this custom preset.", () -> {
				JsonObject args = new JsonObject();
				args.addProperty("name", selected);
				screen.sendEdit("deleteDifficulty", args);
			});
		button(screen, Component.literal("Make default"), ex + (bw + 4) * 2, by, bw, "New NPCs will use this preset.", () -> {
			JsonObject args = new JsonObject();
			args.addProperty("name", selected);
			screen.sendEdit("setDefaultDifficulty", args);
		});

		int ny = by + rowStep + 2;
		EditBox nameBox = new EditBox(screen.fontRenderer(), ex, ny, colW, 20, Component.literal("New preset name"));
		nameBox.setHint(Component.literal("New preset name"));
		nameBox.setMaxLength(24);
		nameBox.setFilter(s -> s.matches("[A-Za-z0-9_\\-]*"));
		nameBox.setValue(newName);
		nameBox.setResponder(s -> newName = s);
		nameBox.active = editable;
		screen.add(nameBox);
		button(screen, Component.literal("Save as new preset"), ex + colW + 8, ny, colW, "Saves these values under the name on the left.", () -> {
			String name = newName.trim().toLowerCase(Locale.ROOT);
			if (name.isEmpty()) {
				screen.setStatus("Type a name for the new preset first.", false);
				return;
			}
			JsonObject args = new JsonObject();
			args.addProperty("name", name);
			args.add("values", valuesJson());
			selected = name;
			screen.sendEdit("saveDifficulty", args);
		});

		int ay = ny + rowStep + 2;
		armiesY = ay + 28;
		List<String> targets = new ArrayList<>();
		targets.add("all");
		teams.forEach(t -> targets.add(t.get("name").getAsString()));
		CycleButton<String> cycle = CycleButton.<String>builder(this::targetLabel, applyTarget)
			.withValues(targets)
			.create(ex, ay, colW, 20, Component.literal("Army"), (btn, value) -> applyTarget = value);
		cycle.active = editable;
		screen.add(cycle);
		button(screen, Component.literal("Apply " + selected + " to army"), ex + colW + 8, ay, colW,
			"Sets every NPC on that team to this preset (saved first if you changed it).", () -> {
				JsonObject args = new JsonObject();
				args.addProperty("target", applyTarget);
				args.addProperty("name", selected);
				screen.sendEdit("applyDifficulty", args);
			});
	}

	private Component targetLabel(String target) {
		if (target.equals("all")) {
			return Component.literal("all armies");
		}
		for (JsonObject t : teams) {
			if (t.get("name").getAsString().equals(target)) {
				return Component.literal(target + " (" + t.get("count").getAsInt() + " NPCs, "
					+ t.get("difficulty").getAsString() + ")");
			}
		}
		return Component.literal(target);
	}

	private void button(ScriptKitScreen screen, Component label, int x, int y, int w, String tip, Runnable action) {
		Button b = Button.builder(label, btn -> action.run()).bounds(x, y, w, 20)
			.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(tip))).build();
		b.active = editable;
		screen.add(b);
	}

	private JsonObject valuesJson() {
		JsonObject o = new JsonObject();
		working.forEach(o::addProperty);
		return o;
	}

	private static String format(Field f, double v) {
		return f.percent() ? String.format(Locale.ROOT, "%.0f%%", v * 100) : String.format(Locale.ROOT, "x%.2f", v);
	}

	@Override
	public void renderExtra(GuiGraphics g, int mouseX, int mouseY) {
		var font = net.minecraft.client.Minecraft.getInstance().font;
		if (presets.isEmpty()) {
			return;
		}
		g.drawString(font, "Presets  (* = custom)", panelX + 2, panelY + 1, 0xFFAAAAAA);
		if (!editable) {
			g.drawString(font, "Read-only: you need operator permission to change difficulties.", panelX + 142, panelY + 1, 0xFFFFAA55);
		} else {
			g.drawString(font, "Editing: " + selected + (selected.equals(defaultName) ? " (default for new NPCs)" : ""), panelX + 142, panelY + 1, 0xFFAAAAAA);
		}
		// Armies overview below the buttons, if there is room.
		int y = armiesY;
		TreeMap<String, String> rows = new TreeMap<>();
		for (JsonObject t : teams) {
			rows.put(t.get("name").getAsString(), t.get("count").getAsInt() + " NPCs, difficulty " + t.get("difficulty").getAsString()
				+ ", kit " + t.get("kit").getAsString());
		}
		int limit = panelY + panelH;
		if (!rows.isEmpty() && y > 0 && y + 12 <= limit) {
			g.drawString(font, "Armies:", panelX + 142, y, 0xFFAAAAAA);
			int line = 1;
			for (Map.Entry<String, String> e : rows.entrySet()) {
				if (y + line * 11 + 9 > limit) {
					break;
				}
				g.drawString(font, e.getKey() + ": " + e.getValue(), panelX + 150, y + line * 11, 0xFFCCCCCC);
				line++;
			}
		}
	}
}
