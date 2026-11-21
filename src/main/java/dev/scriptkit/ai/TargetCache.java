package dev.scriptkit.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Per-world list of entities that can take part in fights (alive and on a scoreboard team).
 * Rebuilt lazily at most every {@link #REFRESH_TICKS} ticks in one pass over loaded entities, so
 * NPCs never scan the world themselves. When no NPC has AI on, nothing asks and nothing is built.
 */
public final class TargetCache {
	public static final int REFRESH_TICKS = 10;
	private static final Map<ServerLevel, Snapshot> CACHE = new WeakHashMap<>();

	private record Snapshot(long gameTime, List<LivingEntity> entities) {}

	private TargetCache() {}

	public static List<LivingEntity> candidates(ServerLevel level) {
		long now = level.getGameTime();
		Snapshot snap = CACHE.get(level);
		if (snap == null || now - snap.gameTime() >= REFRESH_TICKS || now < snap.gameTime()) {
			List<LivingEntity> list = new ArrayList<>();
			for (Entity e : level.getAllEntities()) {
				if (e instanceof LivingEntity living && living.isAlive() && living.getTeam() != null) {
					list.add(living);
				}
			}
			snap = new Snapshot(now, Collections.unmodifiableList(list));
			CACHE.put(level, snap);
		}
		return snap.entities();
	}
}
