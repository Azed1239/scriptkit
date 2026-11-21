package dev.scriptkit.client.ui;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.scriptkit.ScriptKitMod;
import dev.scriptkit.ai.Difficulty;
import dev.scriptkit.npc.KitManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Developer smoke test, enabled with {@code -Dscriptkit.debug.ui=true}: opens the menu on the title
 * screen with sample data, shows every tab for a couple of seconds (so layout and rendering code run
 * for real) and then quits the game. A crash in any panel shows up in the log and crash report.
 */
public final class DebugUi {
	private static final Gson GSON = new Gson();
	private static final int TICKS_PER_STEP = 50;

	private static boolean started;
	private static int ticks;
	private static int step;
	private static ScriptKitScreen screen;

	private DebugUi() {}

	public static void init() {
		if (Boolean.getBoolean("scriptkit.debug.ui")) {
			ClientTickEvents.END_CLIENT_TICK.register(DebugUi::tick);
			ScriptKitMod.LOGGER.info("UI smoke test enabled");
		}
	}

	private static void tick(Minecraft mc) {
		if (!started) {
			if (!(mc.screen instanceof TitleScreen)) {
				return;
			}
			started = true;
			screen = new ScriptKitScreen(mc.screen);
			mc.setScreen(screen);
			screen.applyState(sampleState());
			ScriptKitMod.LOGGER.info("UI smoke: menu opened on {}", ScriptKitScreen.Tab.DIFFICULTY);
			return;
		}
		if (++ticks % TICKS_PER_STEP != 0) {
			return;
		}
		// Save what is on screen before moving on (run/screenshots), so the layout can be inspected.
		if (step >= 0 && screen != null) {
			String name = "smoke-" + step + ".png";
			net.minecraft.client.Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), 1, m -> {});
		}
		step++;
		ScriptKitScreen.Tab[] tabs = ScriptKitScreen.Tab.values();
		if (step <= tabs.length) {
			screen.showTab(tabs[step - 1]);
			ScriptKitMod.LOGGER.info("UI smoke: showing tab {}", tabs[step - 1]);
		} else if (step == tabs.length + 1) {
			DebugUiScenarios.openPicker(screen);
		} else if (step == tabs.length + 2) {
			DebugUiScenarios.typeInPicker();
		} else if (step == tabs.length + 3) {
			DebugUiScenarios.closePicker(screen);
		} else {
			ScriptKitMod.LOGGER.info("UI smoke test finished with no errors");
			mc.stop();
		}
	}

	/** What a server would send, built from the real defaults. */
	static String sampleState() {
		JsonObject root = new JsonObject();
		root.addProperty("canEdit", true);
		root.addProperty("ok", true);
		root.addProperty("message", "");
		root.addProperty("defaultDifficulty", "normal");
		JsonObject diffs = new JsonObject();
		Difficulty.defaults().forEach((name, d) -> diffs.add(name, GSON.toJsonTree(d)));
		diffs.add("brutal", GSON.toJsonTree(new Difficulty()));
		root.add("difficulties", diffs);
		root.add("builtInDifficulties", GSON.toJsonTree(Difficulty.defaults().keySet()));
		JsonObject kits = new JsonObject();
		KitManager.definitions().forEach((name, slots) -> kits.add(name, KitManager.toJson(slots)));
		root.add("kits", kits);
		root.add("builtInKits", GSON.toJsonTree(KitManager.builtInNames()));
		JsonArray teams = new JsonArray();
		for (String[] t : new String[][] {{"red", "30", "hard", "god"}, {"blue", "25", "normal", "knight"}, {"green", "0", "-", "-"}}) {
			JsonObject o = new JsonObject();
			o.addProperty("name", t[0]);
			o.addProperty("count", Integer.parseInt(t[1]));
			o.addProperty("difficulty", t[2]);
			o.addProperty("kit", t[3]);
			teams.add(o);
		}
		root.add("teams", teams);
		return root.toString();
	}
}
