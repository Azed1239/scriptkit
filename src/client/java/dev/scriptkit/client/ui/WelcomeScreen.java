package dev.scriptkit.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;

/**
 * The one-time "new here?" pop-up shown after joining a world. It never pauses the game (so it is safe
 * on servers) and closes with Escape, "Not now" or either button.
 */
public class WelcomeScreen extends Screen {
	private static final int BOX_W = 260;
	private static final int BOX_H = 118;

	private final Screen parent;
	private List<FormattedCharSequence> body = List.of();

	public WelcomeScreen(Screen parent) {
		super(Component.literal("Welcome to ScriptKit"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		body = font.split(Component.literal(
			"New here? The tutorial walks you through spawning two armies, arranging them with the Command Stick, and starting a fight. "
				+ "You can open the menu any time with K. This is only shown once."), BOX_W - 24);
		int left = (width - BOX_W) / 2;
		int top = (height - BOX_H) / 2;
		int y = top + BOX_H - 30;
		addRenderableWidget(Button.builder(Component.literal("Start tutorial"), b -> {
			minecraft.setScreen(parent);
			ScriptKitScreen.open("guide");
		}).bounds(left + 12, y, 120, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Not now"), b -> onClose())
			.bounds(left + BOX_W - 12 - 100, y, 100, 20).build());
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		int left = (width - BOX_W) / 2;
		int top = (height - BOX_H) / 2;
		g.fill(left - 2, top - 2, left + BOX_W + 2, top + BOX_H + 2, 0xFFE0B040);
		g.fill(left, top, left + BOX_W, top + BOX_H, 0xFF1C202C);
		g.drawString(font, title, left + 12, top + 10, 0xFFFFD75A);
		int y = top + 28;
		for (FormattedCharSequence line : body) {
			g.drawString(font, line, left + 12, y, 0xFFDDE3F0);
			y += font.lineHeight + 2;
		}
		super.render(g, mouseX, mouseY, partialTick);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		// No dimming or blur: the world stays visible behind the pop-up.
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
