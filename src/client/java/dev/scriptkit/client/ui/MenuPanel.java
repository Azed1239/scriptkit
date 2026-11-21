package dev.scriptkit.client.ui;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;

/** One tab of the menu. The screen asks it to build its widgets whenever the layout changes. */
public interface MenuPanel {
	/** Adds this panel's widgets inside the given rectangle. */
	void build(ScriptKitScreen screen, int x, int y, int width, int height);

	/** New server data arrived (called before {@link #build} runs again). {@code null} before the first snapshot. */
	void onState(JsonObject state);

	/** Extra drawing on top of the widgets (labels, previews). */
	default void renderExtra(GuiGraphics graphics, int mouseX, int mouseY) {
	}

	/** A key was pressed while this panel is showing; return true if it was used. */
	default boolean keyPressed(int key, int modifiers) {
		return false;
	}
}
