package dev.scriptkit.client.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.scriptkit.ScriptKitMod;
import dev.scriptkit.client.platform.ClientNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * The ScriptKit menu: tabs for Difficulty, Kits and Keybinds. The server tabs show data the server
 * sends (and read-only unless the player is an operator); the Keybinds tab only touches the local
 * keybinds.json. Armies and NPCs are still created with commands.
 */
public class ScriptKitScreen extends Screen {
	public enum Tab { DIFFICULTY, KITS, KEYBINDS }

	/** The open menu, so state packets can reach it. */
	private static ScriptKitScreen current;

	private final DifficultyPanel difficulty = new DifficultyPanel();
	private final KitsPanel kits = new KitsPanel();
	private final KeybindsPanel keybinds = new KeybindsPanel();
	private Tab tab = Tab.DIFFICULTY;
	private JsonObject state;
	private String status = "";
	private boolean statusOk = true;
	private final Screen parent;

	public ScriptKitScreen(Screen parent) {
		super(Component.literal("ScriptKit"));
		this.parent = parent;
	}

	// ------------------------------------------------------------------ opening and state

	/** Opens the menu and asks the server for its data. */
	public static void open() {
		Minecraft mc = Minecraft.getInstance();
		ScriptKitScreen screen = new ScriptKitScreen(mc.screen);
		mc.setScreen(screen);
		if (ClientNetworking.canSendMenu()) {
			ClientNetworking.sendMenuRequest();
		} else {
			screen.status = "This server doesn't have ScriptKit: showing the Keybinds tab only.";
			screen.statusOk = false;
			screen.tab = Tab.KEYBINDS;
			screen.rebuildWidgets();
		}
	}

	/** A state packet arrived. */
	public static void onStatePacket(String json) {
		if (current != null) {
			current.applyState(json);
		}
	}

	void applyState(String json) {
		try {
			state = JsonParser.parseString(json).getAsJsonObject();
		} catch (RuntimeException e) {
			ScriptKitMod.LOGGER.warn("Menu state from the server was not valid JSON", e);
			return;
		}
		String message = state.has("message") ? state.get("message").getAsString() : "";
		if (!message.isEmpty()) {
			status = message;
			statusOk = !state.has("ok") || state.get("ok").getAsBoolean();
		}
		difficulty.onState(state);
		kits.onState(state);
		keybinds.onState(state);
		rebuildWidgets();
	}

	// ------------------------------------------------------------------ screen

	@Override
	protected void init() {
		current = this;
		int tabWidth = 90;
		int x = 10;
		for (Tab t : Tab.values()) {
			Button button = Button.builder(Component.literal(label(t)), b -> {
				tab = t;
				rebuildWidgets();
			}).bounds(x, 8, tabWidth, 20).build();
			button.active = t != tab;
			addRenderableWidget(button);
			x += tabWidth + 4;
		}
		addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
			.bounds(width - 70, 8, 60, 20).build());

		int top = 32;
		int bottom = height - 18;
		panel().build(this, 10, top, width - 20, bottom - top);
	}

	private MenuPanel panel() {
		return switch (tab) {
			case DIFFICULTY -> difficulty;
			case KITS -> kits;
			case KEYBINDS -> keybinds;
		};
	}

	private static String label(Tab t) {
		return switch (t) {
			case DIFFICULTY -> "Difficulty";
			case KITS -> "Kits";
			case KEYBINDS -> "Keybinds";
		};
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		panel().renderExtra(g, mouseX, mouseY);
		if (!status.isEmpty()) {
			g.drawString(font, status, 10, height - 12, statusOk ? 0xFF80FF80 : 0xFFFF7070);
		} else if (state == null && tab != Tab.KEYBINDS) {
			g.drawString(font, "Loading from the server...", 10, height - 12, 0xFFAAAAAA);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (panel().keyPressed(event.key(), event.modifiers())) {
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		if (current == this) {
			current = null;
		}
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------ helpers for panels

	public <T extends AbstractWidget> T add(T widget) {
		return addRenderableWidget(widget);
	}

	public net.minecraft.client.gui.Font fontRenderer() {
		return font;
	}

	public boolean canEdit() {
		return state != null && state.has("canEdit") && state.get("canEdit").getAsBoolean();
	}

	public JsonObject state() {
		return state;
	}

	public void setStatus(String message, boolean ok) {
		status = message;
		statusOk = ok;
	}

	/** Sends an edit to the server; the answer arrives as a new state packet. */
	public void sendEdit(String op, JsonObject args) {
		if (!ClientNetworking.canSendMenu()) {
			setStatus("This server doesn't have ScriptKit.", false);
			return;
		}
		ClientNetworking.sendMenuEdit(op, args.toString());
	}

	public Tab currentTab() {
		return tab;
	}

	public void showTab(Tab t) {
		tab = t;
		rebuildWidgets();
	}

	public void rebuild() {
		rebuildWidgets();
	}
}
