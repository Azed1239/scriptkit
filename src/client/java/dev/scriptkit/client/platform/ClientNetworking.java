package dev.scriptkit.client.platform;

import dev.scriptkit.network.ArmyActionPayload;
import dev.scriptkit.network.ReloadKeybindsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Thin wrapper over Fabric's client networking API (version-specific code lives here). */
public final class ClientNetworking {
	private ClientNetworking() {}

	/** Registers the handler for "reload keybinds" from the server; the callback runs on the client thread. */
	public static void registerReloadReceiver(Runnable onReload) {
		ClientPlayNetworking.registerGlobalReceiver(ReloadKeybindsPayload.TYPE,
			(payload, context) -> context.client().execute(onReload));
	}

	/** True if the connected server has ScriptKit (and so understands army packets). */
	public static boolean serverHasScriptKit() {
		return ClientPlayNetworking.canSend(ArmyActionPayload.TYPE);
	}

	public static void send(ArmyActionPayload payload) {
		ClientPlayNetworking.send(payload);
	}
}
