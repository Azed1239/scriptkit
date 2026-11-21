package dev.scriptkit.client.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.scriptkit.ScriptKitMod;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

/** What the player has done in the tutorial, kept in config/scriptkit/tutorial.json (client only). */
public final class TutorialProgress {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final class Data {
		boolean nudged;
		Set<String> done = new LinkedHashSet<>();
	}

	private static Data data;

	private TutorialProgress() {}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("scriptkit").resolve("tutorial.json");
	}

	private static synchronized Data data() {
		if (data == null) {
			data = new Data();
			Path p = path();
			if (Files.exists(p)) {
				try (Reader r = Files.newBufferedReader(p)) {
					Data loaded = GSON.fromJson(r, Data.class);
					if (loaded != null) {
						data = loaded;
						if (data.done == null) {
							data.done = new LinkedHashSet<>();
						}
					}
				} catch (IOException | RuntimeException e) {
					ScriptKitMod.LOGGER.warn("Could not read {}; starting the tutorial fresh", p, e);
				}
			}
		}
		return data;
	}

	private static void save() {
		Path p = path();
		try {
			Files.createDirectories(p.getParent());
			try (Writer w = Files.newBufferedWriter(p)) {
				GSON.toJson(data(), w);
			}
		} catch (IOException e) {
			ScriptKitMod.LOGGER.warn("Could not write {}", p, e);
		}
	}

	public static synchronized boolean isDone(String step) {
		return data().done.contains(step);
	}

	public static synchronized void markDone(String step) {
		if (data().done.add(step)) {
			save();
		}
	}

	/** Whether the "new here? start the tutorial" chat line has already been shown once. */
	public static synchronized boolean nudged() {
		return data().nudged;
	}

	public static synchronized void setNudged() {
		if (!data().nudged) {
			data().nudged = true;
			save();
		}
	}

	public static synchronized void reset() {
		data = new Data();
		save();
	}
}
