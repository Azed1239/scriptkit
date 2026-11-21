package dev.scriptkit.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.scriptkit.ScriptKitMod;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** config/scriptkit/config.json. Written with defaults on first run; re-read on server start. */
public final class ScriptKitConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static volatile ScriptKitConfig instance = new ScriptKitConfig();

	/** Maximum NPCs a single /army spawn may create. */
	public int maxArmySize = 100;
	/** Whether NPCs on the same team may hurt each other. Applied to teams when they are created. */
	public boolean friendlyFire = false;
	/** "circle" or "grid". */
	public String defaultFormation = "circle";
	/** "generated" gives army NPCs gamer-tag names (PingAbuse, BanThis...); "numbered" gives team-1, team-2... */
	public String nameStyle = "generated";
	/**
	 * Usernames whose skins army NPCs pick from at random when no skin is given. Empty (the default)
	 * means every NPC gets a random built-in skin (Steve, Alex, Ari, ...), with no account lookups.
	 */
	public java.util.List<String> skinPool = new java.util.ArrayList<>();

	public static ScriptKitConfig get() {
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("scriptkit").resolve("config.json");
		try {
			if (!Files.exists(path)) {
				Files.createDirectories(path.getParent());
				try (Writer w = Files.newBufferedWriter(path)) {
					GSON.toJson(new ScriptKitConfig(), w);
				}
			}
			try (Reader r = Files.newBufferedReader(path)) {
				ScriptKitConfig loaded = GSON.fromJson(r, ScriptKitConfig.class);
				if (loaded != null) {
					loaded.maxArmySize = Math.max(1, loaded.maxArmySize);
					if (loaded.defaultFormation == null) {
						loaded.defaultFormation = "circle";
					}
					if (loaded.nameStyle == null || !(loaded.nameStyle.equalsIgnoreCase("generated") || loaded.nameStyle.equalsIgnoreCase("numbered"))) {
						loaded.nameStyle = "generated";
					}
					loaded.nameStyle = loaded.nameStyle.toLowerCase();
					if (loaded.skinPool == null) {
						loaded.skinPool = new java.util.ArrayList<>();
					}
					loaded.skinPool.removeIf(s -> s == null || !s.matches("[A-Za-z0-9_]{1,16}"));
					instance = loaded;
				}
			}
		} catch (IOException | RuntimeException e) {
			ScriptKitMod.LOGGER.error("Failed to load {}; using defaults", path, e);
			instance = new ScriptKitConfig();
		}
	}
}
