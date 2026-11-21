package dev.scriptkit.client.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Pick any item by searching its name or id. Returns the item id to the caller ("" clears the slot). */
public class ItemPickerScreen extends Screen {
	/** One row: the item, its display name and id, lowercased for searching. */
	private record Entry(Item item, ItemStack stack, String name, String id, String haystack) {}

	private static List<Entry> allEntries;

	private final Screen parent;
	private final String slotLabel;
	private final Consumer<String> onPick;
	private EditBox search;
	private SimpleList<Entry> list;
	private String query = "";

	public ItemPickerScreen(Screen parent, String slotLabel, Consumer<String> onPick) {
		super(Component.literal("Choose an item"));
		this.parent = parent;
		this.slotLabel = slotLabel;
		this.onPick = onPick;
	}

	private static List<Entry> entries() {
		if (allEntries == null) {
			List<Entry> list = new ArrayList<>();
			for (Item item : BuiltInRegistries.ITEM) {
				if (item == Items.AIR) {
					continue;
				}
				ItemStack stack = item.getDefaultInstance();
				String id = BuiltInRegistries.ITEM.getKey(item).toString();
				String name = stack.getHoverName().getString();
				list.add(new Entry(item, stack, name, id, (name + " " + id).toLowerCase(Locale.ROOT)));
			}
			list.sort(Comparator.comparing(Entry::id));
			allEntries = list;
		}
		return allEntries;
	}

	@Override
	protected void init() {
		int listW = Math.min(width - 20, 320);
		int x = (width - listW) / 2;
		search = new EditBox(font, x, 30, listW, 20, Component.literal("Search"));
		search.setHint(Component.literal("Search items (name or id)..."));
		search.setMaxLength(48);
		search.setValue(query);
		search.setResponder(s -> {
			query = s;
			refilter();
		});
		addRenderableWidget(search);
		setInitialFocus(search);

		int listY = 56;
		int listH = height - listY - 30;
		list = new SimpleList<>(x, listY, listW, listH, 20, (g, e, rx, ry, rw, rh, hovered, selected) -> {
			g.renderFakeItem(e.stack(), rx, ry + 2);
			g.drawString(font, e.name(), rx + 22, ry + 1, 0xFFFFFFFF);
			g.drawString(font, e.id(), rx + 22, ry + 11, 0xFF888888);
		}, e -> {
			onPick.accept(e.id());
			minecraft.setScreen(parent);
		});
		addRenderableWidget(list);
		refilter();

		int by = height - 24;
		addRenderableWidget(Button.builder(Component.literal("Empty slot"), b -> {
			onPick.accept("");
			minecraft.setScreen(parent);
		}).bounds(x, by, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
			.bounds(x + listW - 100, by, 100, 20).build());
	}

	private void refilter() {
		String q = query.trim().toLowerCase(Locale.ROOT);
		List<Entry> result = new ArrayList<>();
		for (Entry e : entries()) {
			if (q.isEmpty() || e.haystack().contains(q)) {
				result.add(e);
			}
		}
		list.setItems(result);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		g.drawCenteredString(font, "Choose the item for: " + slotLabel, width / 2, 12, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
