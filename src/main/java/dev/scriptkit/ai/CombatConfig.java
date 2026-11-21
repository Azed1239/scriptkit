package dev.scriptkit.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.scriptkit.ScriptKitMod;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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
	/** Multiplies all NPC damage (melee and arrows). Lower makes fights last longer. */
	public double damageMultiplier = 1.0;
	/** If false, NPCs never target players. */
	public boolean npcsCanHurtPlayers = true;
	/** Difficulty given to NPCs that have none set (new spawns use this). */
	public String defaultDifficulty = Difficulty.NORMAL;
	/** Named difficulty presets. Edit them or add your own; commands accept any name here. */
	public Map<String, Difficulty> difficulties = Difficulty.defaults();
	/** Hostile mobs (zombies, skeletons, creepers, spiders...) attack NPCs the way they attack players. */
	public boolean hostileMobsAttackNpcs = true;
	/** NPCs with AI on treat hostile mobs as enemies and fight back, whatever team they are on. */
	public boolean npcsFightHostileMobs = true;
	/** Also target players in creative mode (they can't be hurt, but NPCs will chase and swing at them). */
	public boolean targetCreativePlayers = false;
	/** Players can damage NPCs on their own team (accidental friendly fire). */
	public boolean playerFriendlyFire = true;
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

	/** The preset with this name (case-insensitive), or the default preset if it no longer exists. */
	public Difficulty difficulty(String name) {
		Difficulty d = name == null ? null : difficulties.get(name.toLowerCase(Locale.ROOT));
		return d != null ? d : difficulties.getOrDefault(defaultDifficulty, new Difficulty());
	}

	public boolean hasDifficulty(String name) {
		return name != null && difficulties.containsKey(name.toLowerCase(Locale.ROOT));
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
			Map<String, Difficulty> presets = new LinkedHashMap<>();
			if (loaded.difficulties != null) {
				loaded.difficulties.forEach((k, v) -> {
					if (k != null && v != null) {
						presets.put(k.toLowerCase(Locale.ROOT), v);
					}
				});
			}
			Difficulty.defaults().forEach(presets::putIfAbsent);
			presets.values().forEach(Difficulty::sanitize);
			loaded.difficulties = presets;
			if (loaded.defaultDifficulty == null || !presets.containsKey(loaded.defaultDifficulty.toLowerCase(Locale.ROOT))) {
				loaded.defaultDifficulty = Difficulty.NORMAL;
			}
			loaded.defaultDifficulty = loaded.defaultDifficulty.toLowerCase(Locale.ROOT);
			loaded.damageMultiplier = Math.max(0.0, loaded.damageMultiplier);
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
