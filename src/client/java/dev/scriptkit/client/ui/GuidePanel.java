package dev.scriptkit.client.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.scriptkit.client.platform.ClientNetworking;
import dev.scriptkit.network.ArmyAction;
import dev.scriptkit.network.ArmyActionPayload;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Tutorial tab: a short checklist. Each step has a button that does (or opens) the thing, and ticks
 * itself off when the server data shows it happened, or when the action was taken here.
 */
public class GuidePanel implements MenuPanel {
	private record Step(String id, String title, String description, String button, Predicate<JsonObject> detect) {}

	private static final List<Step> STEPS = List.of(
		new Step("spawn", "1. Spawn two armies", "10 red knights, 10 blue archers nearby.", "Spawn red + blue",
			s -> teamsWithNpcs(s) >= 2),
		new Step("toggle", "2. Freeze and resume the fight", "G freezes and resumes every army.", "Toggle all (G)",
			s -> false),
		new Step("difficulty", "3. Change a difficulty", "Make an army tougher or easier.", "Open Difficulty",
			GuidePanel::difficultyChanged),
		new Step("kit", "4. Make a kit", "Build gear, give it to yourself.", "Open Kits",
			GuidePanel::hasCustomKit),
		new Step("keys", "5. Set a key combo", "Bind e.g. Ctrl+R to toggle a team.", "Open Keybinds",
			s -> false),
		new Step("team", "6. Join a team or stay neutral", "Join a team, or fight everyone.", "Open Team",
			s -> s.has("myTeam") && !s.get("myTeam").getAsString().isEmpty()),
		new Step("stick", "7. Arrange a battlefield", "Command Stick: click army, left-click formation, click ground, G.", "Get Command Stick",
			s -> false));

	private JsonObject state;
	private boolean editable;
	private int panelX;
	private int panelY;
	private int panelW;
	private int rowH = 26;

	@Override
	public void onState(JsonObject state) {
		this.state = state;
		if (state != null) {
			for (Step step : STEPS) {
				if (step.detect().test(state)) {
					TutorialProgress.markDone(step.id());
				}
			}
		}
	}

	@Override
	public void build(ScriptKitScreen screen, int x, int y, int w, int h) {
		editable = screen.canEdit();
		panelX = x;
		panelY = y;
		panelW = w;
		rowH = h >= 215 ? 28 : 24;
		int btnW = Math.min(130, w * 32 / 100);
		for (int i = 0; i < STEPS.size(); i++) {
			Step step = STEPS.get(i);
			Button b = Button.builder(Component.literal(step.button()), btn -> run(screen, step))
				.bounds(x + w - btnW, y + 12 + i * rowH + 1, btnW, 20).build();
			b.active = needsOp(step.id()) ? editable : true;
			screen.add(b);
		}
	}

	private static boolean needsOp(String id) {
		return id.equals("spawn") || id.equals("toggle") || id.equals("stick");
	}

	private void run(ScriptKitScreen screen, Step step) {
		Minecraft mc = Minecraft.getInstance();
		switch (step.id()) {
			case "spawn" -> {
				if (mc.player == null) {
					return;
				}
				mc.player.connection.sendCommand("army spawn 10 red knight");
				mc.player.connection.sendCommand("execute positioned ~20 ~ ~ run army spawn 10 blue archer");
				screen.setStatus("Spawned two armies. Close this menu and watch them fight!", true);
			}
			case "toggle" -> {
				if (ClientNetworking.serverHasScriptKit()) {
					ClientNetworking.send(ArmyActionPayload.of(ArmyAction.TOGGLE_ALL, ""));
					TutorialProgress.markDone("toggle");
					screen.setStatus("Toggled all armies. Press G in the game to do this any time.", true);
				}
			}
			case "difficulty" -> screen.showTab(ScriptKitScreen.Tab.DIFFICULTY);
			case "kit" -> screen.showTab(ScriptKitScreen.Tab.KITS);
			case "keys" -> screen.showTab(ScriptKitScreen.Tab.KEYBINDS);
			case "team" -> screen.showTab(ScriptKitScreen.Tab.TEAM);
			case "stick" -> {
				screen.sendEdit("giveStick", new JsonObject());
				TutorialProgress.markDone("stick");
			}
			default -> {
			}
		}
	}

	@Override
	public void renderExtra(GuiGraphics g, int mouseX, int mouseY) {
		var font = Minecraft.getInstance().font;
		int done = 0;
		for (Step s : STEPS) {
			if (TutorialProgress.isDone(s.id())) {
				done++;
			}
		}
		g.drawString(font, "Getting started  (" + done + "/" + STEPS.size() + " done)", panelX + 2, panelY + 1, 0xFFAAAAAA);
		if (!editable) {
			g.drawString(font, "Most steps need operator permission.", panelX + panelW - 190, panelY + 1, 0xFFFFAA55);
		}
		for (int i = 0; i < STEPS.size(); i++) {
			Step s = STEPS.get(i);
			boolean ok = TutorialProgress.isDone(s.id());
			int y = panelY + 12 + i * rowH;
			g.drawString(font, ok ? "[x]" : "[ ]", panelX + 2, y + 6, ok ? 0xFF55FF55 : 0xFF888888);
			int textW = panelW - 20 - Math.min(130, panelW * 32 / 100) - 8;
			g.drawString(font, font.plainSubstrByWidth(s.title(), textW), panelX + 22, y + 1, ok ? 0xFF88CC88 : 0xFFFFFFFF);
			g.drawString(font, font.plainSubstrByWidth(s.description(), textW), panelX + 22, y + 12, 0xFF999999);
		}
	}

	// ------------------------------------------------------------------ detection helpers

	private static int teamsWithNpcs(JsonObject s) {
		int n = 0;
		if (s.has("teams")) {
			for (JsonElement t : s.getAsJsonArray("teams")) {
				if (t.getAsJsonObject().get("count").getAsInt() > 0) {
					n++;
				}
			}
		}
		return n;
	}

	private static boolean difficultyChanged(JsonObject s) {
		if (!s.has("teams") || !s.has("defaultDifficulty")) {
			return false;
		}
		String def = s.get("defaultDifficulty").getAsString();
		for (JsonElement t : s.getAsJsonArray("teams")) {
			JsonObject o = t.getAsJsonObject();
			String d = o.get("difficulty").getAsString();
			if (o.get("count").getAsInt() > 0 && !d.equals("-") && !d.equals(def)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasCustomKit(JsonObject s) {
		if (!s.has("kits") || !s.has("builtInKits")) {
			return false;
		}
		java.util.Set<String> builtIn = new java.util.HashSet<>();
		s.getAsJsonArray("builtInKits").forEach(n -> builtIn.add(n.getAsString()));
		return s.getAsJsonObject("kits").keySet().stream().anyMatch(k -> !builtIn.contains(k));
	}
}
