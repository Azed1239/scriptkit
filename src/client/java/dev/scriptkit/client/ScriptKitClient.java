package dev.scriptkit.client;

import dev.scriptkit.ScriptKitMod;
import dev.scriptkit.client.keybinds.ClientKeybinds;
import dev.scriptkit.client.platform.ClientRendering;
import net.fabricmc.api.ClientModInitializer;

/** Client-only entrypoint. */
public class ScriptKitClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientRendering.register();
		ClientKeybinds.init();
		ScriptKitMod.LOGGER.info("ScriptKit initialized (client)");
	}
}
