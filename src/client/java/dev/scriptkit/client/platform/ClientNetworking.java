package dev.scriptkit.client.platform;

import dev.scriptkit.network.ArmyActionPayload;
import dev.scriptkit.network.MenuPayloads;
import dev.scriptkit.network.ReloadKeybindsPayload;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Thin wrapper over Fabric's client networking API (version-specific code lives here). */
public final class ClientNetworking {
	private ClientNetworking() {}

	/** Registers the handler for "reload keybinds" from the server; the callback runs on the client thread. */
	public static void registerReloadReceiver(Runnable onReload) {
		ClientPlayNetworking.registerGlobalReceiver(ReloadKeybindsPayload.TYPE,
			(payload, context) -> context.client().execute(onReload));
	}

	/** Registers the menu handlers: "open the menu" and "here is the current state"; callbacks run on the client thread. */
	public static void registerMenuReceivers(Consumer<String> onOpen, Consumer<String> onState) {
		ClientPlayNetworking.registerGlobalReceiver(MenuPayloads.Open.TYPE,
			(payload, context) -> context.client().execute(() -> onOpen.accept(payload.tab())));
		ClientPlayNetworking.registerGlobalReceiver(MenuPayloads.State.TYPE,
			(payload, context) -> context.client().execute(() -> onState.accept(payload.json())));
	}

	/** True if the connected server has ScriptKit (and so understands army packets). */
	public static boolean serverHasScriptKit() {
		return ClientPlayNetworking.canSend(ArmyActionPayload.TYPE);
	}

	public static void send(ArmyActionPayload payload) {
		ClientPlayNetworking.send(payload);
	}

	/** Registers the handler for the Command Stick's ghost formation; the callback runs on the client thread. */
	public static void registerGhostReceiver(Consumer<java.util.List<dev.scriptkit.network.GhostPayload.Slot>> onSlots) {
		ClientPlayNetworking.registerGlobalReceiver(dev.scriptkit.network.GhostPayload.TYPE,
			(payload, context) -> context.client().execute(() -> onSlots.accept(payload.slots())));
	}

	public static boolean canSendMenu() {
		return ClientPlayNetworking.canSend(MenuPayloads.Request.TYPE);
	}

	public static void sendMenuRequest() {
		ClientPlayNetworking.send(new MenuPayloads.Request());
	}

	public static void sendMenuEdit(String op, String json) {
		ClientPlayNetworking.send(new MenuPayloads.Edit(op, json));
	}
}
