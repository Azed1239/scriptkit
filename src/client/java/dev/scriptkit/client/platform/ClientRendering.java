package dev.scriptkit.client.platform;

import dev.scriptkit.platform.NpcRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Registers client renderers. Version-specific rendering registration lives here. */
public final class ClientRendering {
	private ClientRendering() {}

	public static void register() {
		EntityRendererRegistry.register(NpcRegistry.NPC_TYPE, NpcRenderer::new);
	}
}
