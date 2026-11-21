package dev.scriptkit.client.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.scriptkit.npc.KitManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Kits tab: choose a kit, set each of the six slots with the item picker, edit enchantments, save
 * it (or save a copy), reset or delete it, and give it to an army. Edits stay local until saved.
 */
public class KitsPanel implements MenuPanel {
	private static final String[] SLOTS = KitManager.SLOT_KEYS;
	private static final String[] SLOT_LABELS = {"Head", "Chest", "Legs", "Feet", "Main hand", "Off hand"};

	/** One equipped item: its id and enchantments (id -> level). */
	private static final class SlotData {
		String id;
		final Map<String, Integer> enchantments = new LinkedHashMap<>();

		SlotData(String id) {
			this.id = id;
		}
	}

	private record Enchant(String id, Component name, int maxLevel) {}

	private final Map<String, Map<String, SlotData>> kits = new LinkedHashMap<>();
	private final Map<String, SlotData> working = new LinkedHashMap<>();
	private Set<String> builtIn = Set.of();
	private final List<JsonObject> teams = new ArrayList<>();
	private String selected;
	private String selectedSlot = "mainhand";
	private String newName = "";
	private static final String ONE_NPC = "@npc";
	private String applyTarget = "all";
	private String npcName = "";
	private boolean dirty;
	private boolean editable;
	private List<Enchant> enchantCache;

	// layout remembered for renderExtra
	private int panelX;
	private int panelY;
	private int slotX;
	private int slotWidth;
	private int rightX;
	private int rightW;

	@Override
	public void onState(JsonObject state) {
		if (state == null || !state.has("kits")) {
			return;
		}
		kits.clear();
		for (Map.Entry<String, JsonElement> e : state.getAsJsonObject("kits").entrySet()) {
			Map<String, SlotData> slots = new LinkedHashMap<>();
			JsonObject o = e.getValue().getAsJsonObject();
			for (String key : SLOTS) {
				JsonElement el = o.get(key);
				if (el == null || el.isJsonNull()) {
					continue;
				}
				if (el.isJsonPrimitive()) {
					slots.put(key, new SlotData(el.getAsString()));
				} else if (el.isJsonObject() && el.getAsJsonObject().has("id")) {
					SlotData d = new SlotData(el.getAsJsonObject().get("id").getAsString());
					if (el.getAsJsonObject().has("enchantments")) {
						el.getAsJsonObject().getAsJsonObject("enchantments").entrySet()
							.forEach(en -> d.enchantments.put(en.getKey(), en.getValue().getAsInt()));
					}
					slots.put(key, d);
				}
			}
			kits.put(e.getKey(), slots);
		}
		builtIn = new java.util.HashSet<>();
		state.getAsJsonArray("builtInKits").forEach(n -> builtIn.add(n.getAsString()));
		teams.clear();
		state.getAsJsonArray("teams").forEach(t -> teams.add(t.getAsJsonObject()));
		if (selected == null || !kits.containsKey(selected)) {
			selected = kits.containsKey("knight") ? "knight" : kits.keySet().iterator().next();
		}
		if (!applyTarget.equals("all") && !applyTarget.equals(ONE_NPC)
			&& teams.stream().noneMatch(t -> t.get("name").getAsString().equals(applyTarget))) {
			applyTarget = "all";
		}
		loadWorking();
	}

	private void loadWorking() {
		working.clear();
		Map<String, SlotData> slots = kits.get(selected);
		if (slots != null) {
			slots.forEach((k, v) -> {
				SlotData copy = new SlotData(v.id);
				copy.enchantments.putAll(v.enchantments);
				working.put(k, copy);
			});
		}
		dirty = false;
	}

	// ------------------------------------------------------------------ layout

	@Override
	public void build(ScriptKitScreen screen, int x, int y, int w, int h) {
		editable = screen.canEdit();
		panelX = x;
		panelY = y;
		if (kits.isEmpty()) {
			return;
		}
		int listW = 130;
		SimpleList<String> list = new SimpleList<>(x, y + 12, listW, h - 12, 16, (g, name, rx, ry, rw, rh, hovered, sel) -> {
			String label = name + (builtIn.contains(name) ? "" : "  *");
			g.drawString(screen.fontRenderer(), label, rx, ry + 4, sel ? 0xFFFFFFFF : 0xFFCCCCCC);
		}, name -> {
			selected = name;
			loadWorking();
			screen.rebuild();
		});
		list.setItems(new ArrayList<>(kits.keySet()));
		list.select(selected);
		screen.add(list);

		rightX = x + listW + 12;
		rightW = w - listW - 12;
		int slotW = Math.min(160, rightW * 55 / 100);
		slotX = rightX;
		slotWidth = slotW;
		int rowY = y + 12;

		for (int i = 0; i < SLOTS.length; i++) {
			String slot = SLOTS[i];
			SlotData d = working.get(slot);
			String itemName = d == null ? "(empty)" : itemName(d.id);
			// The slot text and item icon are drawn in renderExtra (left-aligned), so the button label is empty.
			Button select = Button.builder(Component.empty(), b -> {
				selectedSlot = slot;
				screen.rebuild();
			}).bounds(rightX, rowY + i * 20, slotW - 22, 18)
				.tooltip(Tooltip.create(Component.literal(SLOT_LABELS[i] + " - click to edit its enchantments"))).build();
			select.active = !slot.equals(selectedSlot);
			screen.add(select);
			Button pick = Button.builder(Component.literal("..."), b -> openPicker(screen, slot, SLOT_LABELS[indexOf(slot)]))
				.bounds(rightX + slotW - 20, rowY + i * 20, 20, 18)
				.tooltip(Tooltip.create(Component.literal("Choose the item for this slot"))).build();
			pick.active = editable;
			screen.add(pick);
		}

		// Enchantments for the selected slot.
		int enchX = rightX + slotW + 6;
		int enchW = rightW - slotW - 6;
		SlotData current = working.get(selectedSlot);
		SimpleList<Enchant> enchList = new SimpleList<>(enchX, rowY, enchW, SLOTS.length * 20 - 2, 14, (g, e, rx, ry, rw, rh, hovered, sel) -> {
			int level = current == null ? 0 : current.enchantments.getOrDefault(e.id(), 0);
			String text = e.name().getString() + (level > 0 ? "  " + level : "");
			g.drawString(screen.fontRenderer(), text, rx, ry + 3, level > 0 ? 0xFF55FF55 : 0xFFAAAAAA);
		}, e -> {
		});
		enchList.setItems(current == null ? List.of() : enchants());
		enchList.setButtonHandler((e, button) -> {
			if (!editable || current == null) {
				return;
			}
			int level = current.enchantments.getOrDefault(e.id(), 0);
			int max = Math.max(1, e.maxLevel());
			level = button == 1 ? Math.max(0, level - 1) : (level >= max ? 0 : level + 1);
			if (level == 0) {
				current.enchantments.remove(e.id());
			} else {
				current.enchantments.put(e.id(), level);
			}
			dirty = true;
		});
		screen.add(enchList);

		// Row A: save / reset-delete / new name / save as.
		int ay = rowY + SLOTS.length * 20 + 4;
		int gap = 3;
		int unit = (rightW - 3 * gap);
		int w1 = unit * 20 / 100;
		int w2 = unit * 27 / 100;
		int w3 = unit * 26 / 100;
		int w4 = unit - w1 - w2 - w3;
		boolean isBuiltIn = builtIn.contains(selected);
		action(screen, "Save", rightX, ay, w1, "Saves this kit.", () -> screen.sendEdit("saveKit", kitJson(selected)));
		action(screen, isBuiltIn ? "Reset" : "Delete", rightX + w1 + gap, ay, w2,
			isBuiltIn ? "Restores the built-in gear." : "Deletes this custom kit.", () -> {
				JsonObject args = new JsonObject();
				args.addProperty("name", selected);
				screen.sendEdit("deleteKit", args);
			});
		EditBox nameBox = new EditBox(screen.fontRenderer(), rightX + w1 + w2 + 2 * gap, ay, w3, 20, Component.literal("New kit name"));
		nameBox.setHint(Component.literal("New kit name"));
		nameBox.setMaxLength(24);
		nameBox.setFilter(s -> s.matches("[A-Za-z0-9_\\-]*"));
		nameBox.setValue(newName);
		nameBox.setResponder(s -> newName = s);
		nameBox.active = editable;
		screen.add(nameBox);
		action(screen, "Save as new", rightX + w1 + w2 + w3 + 3 * gap, ay, w4, "Saves this gear as a new kit.", () -> {
			String name = newName.trim().toLowerCase(Locale.ROOT);
			if (name.isEmpty()) {
				screen.setStatus("Type a name for the new kit first.", false);
				return;
			}
			selected = name;
			screen.sendEdit("saveKit", kitJson(name));
		});

		// Row B: give the kit to an army.
		int by = ay + 22;
		List<String> targets = new ArrayList<>();
		targets.add("all");
		teams.forEach(t -> targets.add(t.get("name").getAsString()));
		targets.add(ONE_NPC);
		boolean oneNpc = applyTarget.equals(ONE_NPC);
		int cw = rightW * (oneNpc ? 36 : 58) / 100;
		CycleButton<String> cycle = CycleButton.<String>builder(this::targetLabel, applyTarget)
			.withValues(targets)
			.create(rightX, by, cw, 20, Component.literal("Give to"), (btn, value) -> {
				applyTarget = value;
				screen.rebuild();
			});
		cycle.active = editable;
		screen.add(cycle);
		int giveX = rightX + cw + gap;
		if (oneNpc) {
			int nw = rightW * 26 / 100;
			EditBox npcBox = new EditBox(screen.fontRenderer(), giveX, by, nw, 20, Component.literal("NPC name"));
			npcBox.setHint(Component.literal("NPC name"));
			npcBox.setMaxLength(32);
			npcBox.setFilter(s -> s.matches("[A-Za-z0-9_.+\\-]*"));
			npcBox.setValue(npcName);
			npcBox.setResponder(s -> npcName = s);
			npcBox.active = editable;
			screen.add(npcBox);
			giveX += nw + gap;
		}
		action(screen, oneNpc ? "Give " + selected : "Give " + selected + " to army", giveX, by, rightX + rightW - giveX,
			oneNpc ? "Equips the NPC with that exact name (saved first if you changed the kit)."
				: "Equips every NPC on that team with this kit (saved first if you changed it).", () -> {
				String target = applyTarget;
				if (oneNpc) {
					if (npcName.isBlank()) {
						screen.setStatus("Type the NPC's name first.", false);
						return;
					}
					target = "npc:" + npcName.trim();
				}
				if (dirty) {
					screen.sendEdit("saveKit", kitJson(selected));
				}
				JsonObject args = new JsonObject();
				args.addProperty("target", target);
				args.addProperty("kit", selected);
				screen.sendEdit("applyKit", args);
			});
	}

	private void action(ScriptKitScreen screen, String label, int x, int y, int w, String tip, Runnable run) {
		Button b = Button.builder(Component.literal(label), btn -> run.run()).bounds(x, y, w, 20)
			.tooltip(Tooltip.create(Component.literal(tip))).build();
		b.active = editable;
		screen.add(b);
	}

	private void openPicker(ScriptKitScreen screen, String slot, String label) {
		Minecraft mc = Minecraft.getInstance();
		mc.setScreen(new ItemPickerScreen(screen, label, id -> {
			if (id.isEmpty()) {
				working.remove(slot);
			} else {
				SlotData d = working.get(slot);
				if (d == null) {
					working.put(slot, new SlotData(id));
				} else {
					d.id = id;
				}
			}
			selectedSlot = slot;
			dirty = true;
		}));
	}

	private Component targetLabel(String target) {
		if (target.equals("all")) {
			return Component.literal("all armies");
		}
		if (target.equals(ONE_NPC)) {
			return Component.literal("one NPC by name");
		}
		for (JsonObject t : teams) {
			if (t.get("name").getAsString().equals(target)) {
				return Component.literal(target + " (" + t.get("count").getAsInt() + " NPCs, kit " + t.get("kit").getAsString() + ")");
			}
		}
		return Component.literal(target);
	}

	private JsonObject kitJson(String name) {
		JsonObject root = new JsonObject();
		root.addProperty("name", name);
		JsonObject slots = new JsonObject();
		working.forEach((slot, d) -> {
			JsonObject item = new JsonObject();
			item.addProperty("id", d.id);
			JsonObject en = new JsonObject();
			d.enchantments.forEach(en::addProperty);
			item.add("enchantments", en);
			slots.add(slot, item);
		});
		root.add("slots", slots);
		return root;
	}

	private static int indexOf(String slot) {
		for (int i = 0; i < SLOTS.length; i++) {
			if (SLOTS[i].equals(slot)) {
				return i;
			}
		}
		return 0;
	}

	// ------------------------------------------------------------------ data helpers

	private static String itemName(String id) {
		Identifier parsed = Identifier.tryParse(id);
		if (parsed != null) {
			var item = BuiltInRegistries.ITEM.getOptional(parsed);
			if (item.isPresent()) {
				return item.get().getDefaultInstance().getHoverName().getString();
			}
		}
		return id;
	}

	/** Every enchantment in the registry (from the world if joined, otherwise the built-in ones), sorted by name. */
	private List<Enchant> enchants() {
		if (enchantCache == null) {
			HolderLookup.Provider provider = Minecraft.getInstance().level != null
				? Minecraft.getInstance().level.registryAccess()
				: VanillaRegistries.createLookup();
			List<Enchant> list = new ArrayList<>();
			provider.lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(holder ->
				list.add(new Enchant(holder.key().identifier().toString(), holder.value().description(), holder.value().getMaxLevel())));
			list.sort(Comparator.comparing(e -> e.name().getString()));
			enchantCache = list;
		}
		return enchantCache;
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void renderExtra(GuiGraphics g, int mouseX, int mouseY) {
		var font = Minecraft.getInstance().font;
		if (kits.isEmpty()) {
			return;
		}
		g.drawString(font, "Kits  (* = custom)", panelX + 2, panelY + 1, 0xFFAAAAAA);
		String head = !editable ? "Read-only: you need operator permission to change kits."
			: "Editing: " + selected + " > " + SLOT_LABELS[indexOf(selectedSlot)] + (dirty ? "  (unsaved)" : "");
		g.drawString(font, head, rightX, panelY + 1, !editable ? 0xFFFFAA55 : dirty ? 0xFFFFFF55 : 0xFFAAAAAA);
		int textWidth = slotWidth - 22 - 22;
		for (int i = 0; i < SLOTS.length; i++) {
			SlotData d = working.get(SLOTS[i]);
			int rowTop = panelY + 12 + i * 20;
			boolean isSelected = SLOTS[i].equals(selectedSlot);
			if (d != null) {
				Identifier parsed = Identifier.tryParse(d.id);
				var item = parsed == null ? java.util.Optional.<net.minecraft.world.item.Item>empty() : BuiltInRegistries.ITEM.getOptional(parsed);
				if (item.isPresent()) {
					ItemStack stack = item.get().getDefaultInstance();
					g.renderFakeItem(stack, slotX + 2, rowTop + 1);
				}
			}
			String text = d == null ? "(" + SLOT_LABELS[i] + ")" : itemName(d.id);
			g.drawString(font, font.plainSubstrByWidth(text, textWidth), slotX + 22, rowTop + 5,
				isSelected ? 0xFFFFFF55 : d == null ? 0xFF999999 : 0xFFFFFFFF);
		}
		SlotData current = working.get(selectedSlot);
		String hint = current == null ? "Pick an item first" : "Enchants: L +  R -";
		g.drawString(font, hint, rightX + Math.min(160, rightW * 55 / 100) + 6, panelY + 1, 0xFF888888);
	}
}
