package dev.scriptkit.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.scriptkit.ScriptKitMod;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** config/scriptkit/combat.json. Written with defaults on first run; re-read on server start. */
public final class CombatConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static volatile CombatConfig instance = new CombatConfig();

	/** Blocks within which NPCs notice enemies. */
	public double detectionRange = 32.0;
	/** Ticks between target re-evaluations per NPC (staggered across NPCs). */
	public int retargetIntervalTicks = 20;
	/** Circle-strafe and occasional retreat in melee range. */
	public boolean strafe = true;
	/** Raise an offhand shield when threatened. */
	public boolean block = true;
	/** Archer accuracy from 0.0 (wild) to 1.0 (perfect). */
	public double archerAccuracy = 0.8;
	/** Multiplies all NPC damage (melee and arrows). Lower makes fights last longer. */
	public double damageMultiplier = 1.0;
	/** If false, NPCs never target players. */
	public boolean npcsCanHurtPlayers = true;
	/** Players can damage NPCs on their own team (accidental friendly fire). */
	public boolean playerFriendlyFire = true;
	/** Chance (0-1) that a melee NPC jumps before a swing and lands a critical hit on the way down. */
	public double critChance = 0.35;
	/** Drop the equipped kit on death, like a player. */
	public boolean dropKitOnDeath = true;
	/** Broadcast a vanilla-style death message (also respects the showDeathMessages game rule). */
	public boolean deathMessages = true;
	/** Broadcast "<name> left the game" after a death message. */
	public boolean leaveMessages = true;
	/** Also broadcast "left the game" when NPCs are removed by command. */
	public boolean leaveMessageOnRemove = false;
	/** Let stuck NPCs mine through blocks toward their target. */
	public boolean allowBlockBreaking = true;
	/** Let stuck NPCs pillar up by placing blocks under themselves. */
	public boolean allowBlockPlacing = true;
	/** Hardest block NPCs may break (stone 1.5, iron block 5, obsidian 50). Blocks with storage/data are never broken. */
	public double maxBreakHardness = 5.0;
	/** Block NPCs place when pillaring (they have an unlimited supply). */
	public String pillarBlock = "minecraft:cobblestone";
	/** Whether blocks mined by NPCs drop as items. */
	public boolean dropMinedBlocks = false;

	public static CombatConfig get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("scriptkit").resolve("combat.json");
	}

	/** Writes the current settings back to combat.json (used by in-game toggles). */
	public static void save() {
		try {
			Files.createDirectories(path().getParent());
			try (Writer w = Files.newBufferedWriter(path())) {
				GSON.toJson(instance, w);
			}
		} catch (IOException e) {
			ScriptKitMod.LOGGER.error("Failed to save {}", path(), e);
		}
	}

	public static void load() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			CombatConfig loaded = new CombatConfig();
			if (Files.exists(path)) {
				try (Reader r = Files.newBufferedReader(path)) {
					CombatConfig read = GSON.fromJson(r, CombatConfig.class);
					if (read != null) {
						loaded = read;
					}
				}
			}
			loaded.detectionRange = Math.max(1.0, Math.min(128.0, loaded.detectionRange));
			loaded.retargetIntervalTicks = Math.max(2, loaded.retargetIntervalTicks);
			loaded.archerAccuracy = Math.max(0.0, Math.min(1.0, loaded.archerAccuracy));
			loaded.damageMultiplier = Math.max(0.0, loaded.damageMultiplier);
			loaded.critChance = Math.max(0.0, Math.min(1.0, loaded.critChance));
			loaded.maxBreakHardness = Math.max(0.0, loaded.maxBreakHardness);
			if (loaded.pillarBlock == null || loaded.pillarBlock.isBlank()) {
				loaded.pillarBlock = "minecraft:cobblestone";
			}
			// Rewrite so new options appear in older files.
			try (Writer w = Files.newBufferedWriter(path)) {
				GSON.toJson(loaded, w);
			}
			instance = loaded;
		} catch (IOException | RuntimeException e) {
			ScriptKitMod.LOGGER.error("Failed to load {}; using defaults", path, e);
			instance = new CombatConfig();
		}
	}
}
