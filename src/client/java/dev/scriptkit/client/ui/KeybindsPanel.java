package dev.scriptkit.client.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.scriptkit.ScriptKitMod;
import dev.scriptkit.client.keybinds.ClientKeybinds;
import dev.scriptkit.client.keybinds.KeyNames;
import dev.scriptkit.client.keybinds.KeybindsConfig;
import dev.scriptkit.network.ArmyAction;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

/**
 * Keybinds tab: edits the combos in your own keybinds.json (this never touches the server). Pick an
 * action and team, click the key box and press the combo, then Save & reload. Entries the editor
 * doesn't understand are kept untouched, so nothing you wrote by hand is lost. The vanilla keys
 * (toggle all, team slots, open menu) are rebound in the normal Controls screen.
 */
public class KeybindsPanel implements MenuPanel {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** One binding row. {@code raw} is set for entries we can't edit; they are saved back unchanged. */
	private static final class Entry {
		ArmyAction action = ArmyAction.TOGGLE_ALL;
		String team = "";
		List<String> keys = new ArrayList<>();
		String comment;
		JsonObject raw;

		String label() {
			if (raw != null) {
				return "(unreadable entry - kept as is)";
			}
			String k = keys.isEmpty() ? "(no keys yet)" : prettyKeys(keys, "+");
			return k + ": " + actionText(action) + (action.needsTeam() ? " " + (team.isEmpty() ? "?" : team) : "");
		}
	}

	/** "LEFT_CONTROL","R" -> "Ctrl+R". */
	static String prettyKeys(List<String> keys, String separator) {
		List<String> out = new ArrayList<>();
		for (String k : keys) {
			out.add(switch (k) {
				case "LEFT_CONTROL", "CTRL", "CONTROL" -> "Ctrl";
				case "RIGHT_CONTROL" -> "RCtrl";
				case "LEFT_SHIFT", "SHIFT" -> "Shift";
				case "RIGHT_SHIFT" -> "RShift";
				case "LEFT_ALT", "ALT" -> "Alt";
				case "RIGHT_ALT" -> "RAlt";
				default -> k;
			});
		}
		return String.join(separator, out);
	}

	private static String actionText(ArmyAction a) {
		return switch (a) {
			case TOGGLE_ALL -> "toggle all";
			case TOGGLE_TEAM -> "toggle";
			case ENABLE_ALL -> "enable all";
			case DISABLE_ALL -> "disable all";
			case ENABLE_TEAM -> "enable";
			case DISABLE_TEAM -> "disable";
		};
	}

	private final List<Entry> entries = new ArrayList<>();
	private final String[] slots = {"", "", ""};
	private String fileComment;
	private int selected = -1;
	private boolean loaded;
	private boolean capturing;
	private String loadError;

	private int panelX;
	private int panelY;
	private int rightX;

	@Override
	public void onState(JsonObject state) {
		// The keybinds file is local; server state doesn't matter here.
	}

	// ------------------------------------------------------------------ file

	private void load() {
		entries.clear();
		fileComment = null;
		loadError = null;
		java.util.Arrays.fill(slots, "");
		try {
			JsonElement parsed = JsonParser.parseString(ClientKeybinds.readFileText());
			if (!parsed.isJsonObject()) {
				loadError = "keybinds.json is not a JSON object; saving will replace it.";
				return;
			}
			JsonObject root = parsed.getAsJsonObject();
			if (root.has("_comment") && root.get("_comment").isJsonPrimitive()) {
				fileComment = root.get("_comment").getAsString();
			}
			if (root.has("teamSlots") && root.get("teamSlots").isJsonArray()) {
				JsonArray arr = root.getAsJsonArray("teamSlots");
				for (int i = 0; i < Math.min(3, arr.size()); i++) {
					if (arr.get(i).isJsonPrimitive()) {
						slots[i] = arr.get(i).getAsString();
					}
				}
			}
			if (root.has("bindings") && root.get("bindings").isJsonArray()) {
				for (JsonElement el : root.getAsJsonArray("bindings")) {
					entries.add(parseEntry(el));
				}
			}
		} catch (IOException | RuntimeException e) {
			loadError = "Could not read keybinds.json: " + e.getMessage();
			ScriptKitMod.LOGGER.warn("Keybinds tab could not read keybinds.json", e);
		}
	}

	private static Entry parseEntry(JsonElement el) {
		Entry e = new Entry();
		if (!el.isJsonObject()) {
			e.raw = new JsonObject();
			e.raw.add("_unreadable", el);
			return e;
		}
		JsonObject o = el.getAsJsonObject();
		// A binding is editable only if the real parser accepts it.
		JsonObject wrapper = new JsonObject();
		JsonArray one = new JsonArray();
		one.add(o);
		wrapper.add("bindings", one);
		KeybindsConfig.Parsed parsed = KeybindsConfig.parse(wrapper.toString());
		if (parsed.bindings().size() != 1) {
			e.raw = o;
			return e;
		}
		e.action = parsed.bindings().get(0).action();
		e.team = parsed.bindings().get(0).team();
		o.getAsJsonArray("keys").forEach(k -> e.keys.add(k.getAsString().trim().toUpperCase()));
		if (o.has("_comment") && o.get("_comment").isJsonPrimitive()) {
			e.comment = o.get("_comment").getAsString();
		}
		return e;
	}

	/** Writes the file and reloads the live keybinds. Returns a status line and whether it went well. */
	private String save(boolean[] ok) {
		JsonObject root = new JsonObject();
		if (fileComment != null) {
			root.addProperty("_comment", fileComment);
		}
		JsonArray slotArr = new JsonArray();
		for (String s : slots) {
			slotArr.add(s);
		}
		root.add("teamSlots", slotArr);
		JsonArray bindings = new JsonArray();
		for (Entry e : entries) {
			if (e.raw != null) {
				bindings.add(e.raw.has("_unreadable") ? e.raw.get("_unreadable") : e.raw);
				continue;
			}
			if (e.keys.isEmpty()) {
				continue; // an entry with no keys can't do anything
			}
			JsonObject o = new JsonObject();
			if (e.comment != null) {
				o.addProperty("_comment", e.comment);
			}
			o.addProperty("action", e.action.id());
			if (e.action.needsTeam()) {
				o.addProperty("team", e.team);
			}
			JsonArray keys = new JsonArray();
			e.keys.forEach(keys::add);
			o.add("keys", keys);
			bindings.add(o);
		}
		root.add("bindings", bindings);
		try {
			ClientKeybinds.writeFileText(GSON.toJson(root));
		} catch (IOException ex) {
			ok[0] = false;
			return "Could not write keybinds.json: " + ex.getMessage();
		}
		int warnings = ClientKeybinds.reload();
		if (!entries.isEmpty()) {
			TutorialProgress.markDone("keys");
		}
		ok[0] = warnings == 0;
		return warnings == 0 ? "Saved and reloaded keybinds.json." : "Saved, but " + warnings + " entry(ies) have problems - see the game log.";
	}

	// ------------------------------------------------------------------ layout

	@Override
	public void build(ScriptKitScreen screen, int x, int y, int w, int h) {
		if (!loaded) {
			load();
			loaded = true;
			selected = entries.isEmpty() ? -1 : 0;
		}
		panelX = x;
		panelY = y;
		int listW = 150;
		SimpleList<Integer> list = new SimpleList<>(x, y + 12, listW, h - 12 - 24, 16, (g, index, rx, ry, rw, rh, hovered, sel) -> {
			Entry e = entries.get(index);
			g.drawString(screen.fontRenderer(), screen.fontRenderer().plainSubstrByWidth(e.label(), rw), rx, ry + 4,
				e.raw != null ? 0xFFAA7755 : sel ? 0xFFFFFFFF : 0xFFCCCCCC);
		}, index -> {
			selected = index;
			capturing = false;
			screen.rebuild();
		});
		List<Integer> indices = new ArrayList<>();
		for (int i = 0; i < entries.size(); i++) {
			indices.add(i);
		}
		list.setItems(indices);
		if (selected >= 0) {
			list.select(selected);
		}
		screen.add(list);

		screen.add(Button.builder(Component.literal("Add binding"), b -> {
			Entry e = new Entry();
			entries.add(e);
			selected = entries.size() - 1;
			capturing = true;
			screen.rebuild();
		}).bounds(x, y + h - 20, listW, 20).build());

		rightX = x + listW + 12;
		int rightW = w - listW - 12;
		int ry = y + 12;
		Entry e = selected >= 0 && selected < entries.size() ? entries.get(selected) : null;
		if (e != null && e.raw == null) {
			int actionW = Math.min(170, rightW * 55 / 100);
			screen.add(CycleButton.<ArmyAction>builder(a -> Component.literal(a.id()), e.action)
				.withValues(ArmyAction.values())
				.create(rightX, ry, actionW, 20, Component.literal("Action"), (btn, value) -> {
					e.action = value;
					screen.rebuild();
				}));
			EditBox teamBox = new EditBox(screen.fontRenderer(), rightX + actionW + 4, ry, rightW - actionW - 4, 20, Component.literal("Team"));
			teamBox.setHint(Component.literal(e.action.needsTeam() ? "Team name" : "(no team needed)"));
			teamBox.setMaxLength(32);
			teamBox.setFilter(s -> s.matches("[A-Za-z0-9_.+\\-]*"));
			teamBox.setValue(e.team);
			teamBox.setResponder(s -> e.team = s);
			teamBox.active = e.action.needsTeam();
			screen.add(teamBox);

			Button keyButton = Button.builder(Component.literal(capturing
				? "Press the keys now...  (Esc cancels)"
				: "Keys: " + (e.keys.isEmpty() ? "(none)" : prettyKeys(e.keys, " + ")) + "   [click to change]"), b -> {
				capturing = true;
				screen.rebuild();
			}).bounds(rightX, ry + 24, rightW, 20).build();
			screen.add(keyButton);

			screen.add(Button.builder(Component.literal("Delete binding"), b -> {
				entries.remove(selected);
				selected = Math.min(selected, entries.size() - 1);
				capturing = false;
				screen.rebuild();
			}).bounds(rightX, ry + 48, rightW / 2 - 2, 20).build());
		} else if (e != null) {
			screen.add(Button.builder(Component.literal("Delete this entry"), b -> {
				entries.remove(selected);
				selected = Math.min(selected, entries.size() - 1);
				screen.rebuild();
			}).bounds(rightX, ry, rightW, 20).build());
		}

		// Team slots.
		int sy = ry + 84;
		int boxW = (rightW - 8) / 3;
		for (int i = 0; i < 3; i++) {
			int slot = i;
			EditBox box = new EditBox(screen.fontRenderer(), rightX + i * (boxW + 4), sy, boxW, 20, Component.literal("Team slot " + (i + 1)));
			box.setHint(Component.literal("Team slot " + (i + 1)));
			box.setMaxLength(32);
			box.setFilter(s -> s.matches("[A-Za-z0-9_.+\\-]*"));
			box.setValue(slots[i]);
			box.setResponder(s -> slots[slot] = s);
			screen.add(box);
		}

		// Save / controls.
		int by = y + h - 20;
		screen.add(Button.builder(Component.literal("Save & reload"), b -> {
			boolean[] ok = new boolean[1];
			String message = save(ok);
			screen.setStatus(message, ok[0]);
			load();
		}).bounds(rightX, by, rightW / 2 - 2, 20).build());
		screen.add(Button.builder(Component.literal("Open vanilla Controls..."), b -> {
			Minecraft mc = Minecraft.getInstance();
			mc.setScreen(new KeyBindsScreen(screen, mc.options));
		}).bounds(rightX + rightW / 2 + 2, by, rightW - rightW / 2 - 2, 20).build());
	}

	@Override
	public boolean keyPressed(int key, int modifiers) {
		if (!capturing || selected < 0 || selected >= entries.size()) {
			return false;
		}
		Entry e = entries.get(selected);
		if (key == 256) { // Esc
			capturing = false;
			return true;
		}
		if (KeyNames.MODIFIERS.contains(key)) {
			return true; // wait for the real key; modifiers come from the event
		}
		String name = KeyNames.nameOf(key);
		if (name == null) {
			return true; // unsupported key: keep waiting
		}
		List<String> keys = new ArrayList<>();
		if ((modifiers & 2) != 0) {
			keys.add("CTRL");
		}
		if ((modifiers & 1) != 0) {
			keys.add("SHIFT");
		}
		if ((modifiers & 4) != 0) {
			keys.add("ALT");
		}
		keys.add(name);
		e.keys = keys;
		capturing = false;
		Minecraft.getInstance().execute(() -> {
			if (Minecraft.getInstance().screen instanceof ScriptKitScreen s) {
				s.rebuild();
			}
		});
		return true;
	}

	@Override
	public void renderExtra(GuiGraphics g, int mouseX, int mouseY) {
		var font = Minecraft.getInstance().font;
		g.drawString(font, "Your key combos", panelX + 2, panelY + 1, 0xFFAAAAAA);
		if (loadError != null) {
			g.drawString(font, loadError, rightX, panelY + 1, 0xFFFF7070);
		} else {
			g.drawString(font, "Edit binding", rightX, panelY + 1, 0xFFAAAAAA);
		}
		g.drawString(font, "Team slots (for the 3 'Toggle team' keys):", rightX, panelY + 12 + 76, 0xFFAAAAAA);
		if (entries.isEmpty()) {
			g.drawString(font, "No combos yet - click Add binding.", rightX, panelY + 14, 0xFF888888);
		}
	}
}
