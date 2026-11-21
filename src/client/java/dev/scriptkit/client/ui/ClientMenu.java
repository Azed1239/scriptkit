package dev.scriptkit.client.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.scriptkit.client.platform.ClientNetworking;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

/**
 * Client-side glue for the menu: receives server state, shows the one-time "new here?" pop-up
 * ({@link WelcomeScreen}) after joining a world, and provides the client command {@code /sktutorial}.
 * The pop-up never pauses the game and waits until no other screen is open.
 */
public final class ClientMenu {
	private static final int ASK_DELAY_TICKS = 40;

	private static boolean awaitingNudge;
	private static int ticksSinceJoin;
	private static boolean popupPending;

	private ClientMenu() {}

	public static void init() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			awaitingNudge = !TutorialProgress.nudged();
			ticksSinceJoin = 0;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			awaitingNudge = false;
			popupPending = false;
		});
		ClientTickEvents.END_CLIENT_TICK.register(ClientMenu::tick);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
			dispatcher.register(ClientCommandManager.literal("sktutorial").executes(c -> {
				Minecraft.getInstance().execute(() -> ScriptKitScreen.open("guide"));
				return 1;
			})));
	}

	/** A few seconds after joining, quietly ask the server who we are (operator or not). */
	private static void tick(Minecraft mc) {
		if (popupPending && mc.player != null && mc.screen == null) {
			popupPending = false; // wait for chat or another screen to close, then show once
			mc.setScreen(new WelcomeScreen(null));
		}
		if (awaitingNudge && mc.player != null && ++ticksSinceJoin == ASK_DELAY_TICKS) {
			if (ClientNetworking.canSendMenu()) {
				ClientNetworking.sendMenuRequest();
			} else {
				awaitingNudge = false; // the server doesn't have ScriptKit
			}
		}
	}

	/** Every state packet from the server comes through here. */
	public static void onStatePacket(String json) {
		ScriptKitScreen.onStatePacket(json);
		if (!awaitingNudge) {
			return;
		}
		try {
			JsonObject state = JsonParser.parseString(json).getAsJsonObject();
			awaitingNudge = false;
			if (state.has("canEdit") && state.get("canEdit").getAsBoolean() && !TutorialProgress.nudged()) {
				TutorialProgress.setNudged();
				showNudge();
			}
		} catch (RuntimeException ignored) {
			awaitingNudge = false;
		}
	}

	private static void showNudge() {
		popupPending = true;
	}
}
