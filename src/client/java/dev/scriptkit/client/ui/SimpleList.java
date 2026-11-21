package dev.scriptkit.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A small scrolling list: rows of fixed height drawn by a callback, mouse-wheel scrolling, click to
 * select. Written by hand (rather than extending vanilla's selection lists) so it only depends on
 * the basic widget API.
 */
public class SimpleList<T> extends AbstractWidget {
	@FunctionalInterface
	public interface RowRenderer<T> {
		void render(GuiGraphics graphics, T item, int x, int y, int width, int height, boolean hovered, boolean selected);
	}

	private final int rowHeight;
	private final RowRenderer<T> renderer;
	private final Consumer<T> onSelect;
	private List<T> items = new ArrayList<>();
	private T selected;
	private int scroll;
	private java.util.function.BiConsumer<T, Integer> buttonHandler;

	public SimpleList(int x, int y, int width, int height, int rowHeight, RowRenderer<T> renderer, Consumer<T> onSelect) {
		super(x, y, width, height, Component.empty());
		this.rowHeight = rowHeight;
		this.renderer = renderer;
		this.onSelect = onSelect;
	}

	public void setItems(List<T> items) {
		this.items = new ArrayList<>(items);
		clampScroll();
	}

	/** Also called on every click with the mouse button (0 = left, 1 = right), e.g. to raise or lower a value. */
	public void setButtonHandler(java.util.function.BiConsumer<T, Integer> handler) {
		this.buttonHandler = handler;
	}

	public T selected() {
		return selected;
	}

	public void select(T item) {
		this.selected = item;
		int index = items.indexOf(item);
		if (index >= 0) {
			// Scroll just enough to keep the selection visible.
			int top = index * rowHeight;
			if (top < scroll) {
				scroll = top;
			} else if (top + rowHeight > scroll + height) {
				scroll = top + rowHeight - height;
			}
			clampScroll();
		}
	}

	private int maxScroll() {
		return Math.max(0, items.size() * rowHeight - height);
	}

	private void clampScroll() {
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
	}

	@Override
	protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		g.fill(getX(), getY(), getX() + width, getY() + height, 0x90000000);
		g.renderOutline(getX(), getY(), width, height, 0xFF555555);
		g.enableScissor(getX(), getY(), getX() + width, getY() + height);
		int first = scroll / rowHeight;
		for (int i = first; i < items.size(); i++) {
			int y = getY() + i * rowHeight - scroll;
			if (y >= getY() + height) {
				break;
			}
			T item = items.get(i);
			boolean hovered = mouseX >= getX() && mouseX < getX() + width - 6 && mouseY >= y && mouseY < y + rowHeight
				&& mouseY >= getY() && mouseY < getY() + height;
			boolean sel = item.equals(selected);
			if (sel) {
				g.fill(getX() + 1, y, getX() + width - 1, y + rowHeight, 0x80406080);
			} else if (hovered) {
				g.fill(getX() + 1, y, getX() + width - 1, y + rowHeight, 0x40FFFFFF);
			}
			renderer.render(g, item, getX() + 4, y, width - 10, rowHeight, hovered, sel);
		}
		g.disableScissor();
		if (maxScroll() > 0) {
			int barHeight = Math.max(12, height * height / (items.size() * rowHeight));
			int barY = getY() + (int) ((long) (height - barHeight) * scroll / maxScroll());
			g.fill(getX() + width - 4, barY, getX() + width - 1, barY + barHeight, 0xFFAAAAAA);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (!active || !visible || !isMouseOver(event.x(), event.y())) {
			return false;
		}
		int index = (int) (event.y() - getY() + scroll) / rowHeight;
		if (index >= 0 && index < items.size()) {
			selected = items.get(index);
			onSelect.accept(selected);
			if (buttonHandler != null) {
				buttonHandler.accept(selected, event.button());
			}
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!isMouseOver(mouseX, mouseY)) {
			return false;
		}
		scroll -= (int) (scrollY * rowHeight);
		clampScroll();
		return true;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
	}
}
