package dev.scriptkit.client.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.scriptkit.client.platform.ClientNetworking;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/**
 * Client-side glue for the menu: receives server state, shows the one-time "new here? start the
 * tutorial" chat line after joining a world, and provides the client command {@code /sktutorial}
 * (which that chat line runs). Nothing here ever blocks the game or pops up on its own.
 */
public final class ClientMenu {
	private static final int ASK_DELAY_TICKS = 40;

	private static boolean awaitingNudge;
	private static int ticksSinceJoin;

	private ClientMenu() {}

	public static void init() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			awaitingNudge = !TutorialProgress.nudged();
			ticksSinceJoin = 0;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> awaitingNudge = false);
		ClientTickEvents.END_CLIENT_TICK.register(ClientMenu::tick);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
			dispatcher.register(ClientCommandManager.literal("sktutorial").executes(c -> {
				Minecraft.getInstance().execute(() -> ScriptKitScreen.open("guide"));
				return 1;
			})));
	}

	/** A few seconds after joining, quietly ask the server who we are (operator or not). */
	private static void tick(Minecraft mc) {
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
		Minecraft mc = Minecraft.getInstance();
		Component start = Component.literal("[Start tutorial]").withStyle(style -> style
			.withColor(ChatFormatting.AQUA)
			.withClickEvent(new ClickEvent.RunCommand("/sktutorial"))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal("Opens the ScriptKit getting-started guide"))));
		mc.gui.getChat().addMessage(Component.literal("ScriptKit: ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal("new here? ").withStyle(ChatFormatting.GRAY))
			.append(start)
			.append(Component.literal("  (press K any time for the menu; shown only once)").withStyle(ChatFormatting.GRAY)));
	}
}
