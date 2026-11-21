package dev.scriptkit.client.platform;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * Thin wrapper over vanilla's skin pipeline. Vanilla's PlayerSkinRenderCache resolves the username
 * asynchronously, caches the result (including failures) and hands back a default skin until the
 * real one is ready, so this never blocks the render thread.
 */
public final class NpcSkins {
	private static final int MAX_CACHED_PROFILES = 1024;
	private static final Map<String, ResolvableProfile> PROFILES = new ConcurrentHashMap<>();

	private NpcSkins() {}

	public static PlayerSkin skinFor(String username) {
		if (username == null || username.isBlank()) {
			return DefaultPlayerSkin.getDefaultSkin();
		}
		try {
			if (PROFILES.size() > MAX_CACHED_PROFILES) {
				PROFILES.clear();
			}
			ResolvableProfile profile = PROFILES.computeIfAbsent(username, ResolvableProfile::createUnresolved);
			return Minecraft.getInstance().playerSkinRenderCache().getOrDefault(profile).playerSkin();
		} catch (RuntimeException e) {
			return DefaultPlayerSkin.getDefaultSkin();
		}
	}
}
