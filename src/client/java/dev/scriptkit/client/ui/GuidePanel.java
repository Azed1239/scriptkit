package dev.scriptkit.client.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Tutorial tab: a short course. The left side lists the lessons; the right side teaches the selected
 * one in plain words, shows the exact commands to type (with Copy, and Run where it is safe to do for
 * you), and explains what each part means. A lesson ticks itself off when the server data shows you
 * did it, or when you run its example or press Next.
 */
public class GuidePanel implements MenuPanel {
	/** One piece of a lesson page. */
	private sealed interface Block permits Text, Cmd, Open {}

	/** A paragraph. */
	private record Text(String text) implements Block {}

	/** A command to type: {@code shown} is what the player types, {@code run} (nullable) is what the Run button sends. */
	private record Cmd(String shown, String run, String note) implements Block {}

	/** A button that jumps to another menu tab. */
	private record Open(String label, ScriptKitScreen.Tab tab) implements Block {}

	private record Lesson(String id, String title, List<Block> blocks, Predicate<JsonObject> detect) {}

	private static final List<Lesson> LESSONS = List.of(
		new Lesson("spawn", "Spawn an army", List.of(
			new Text("An army is a group of NPCs on one team. You create it with a command, typed in chat. Press Run to do it from here."),
			new Cmd("/army spawn 10 red", "army spawn 10 red", "10 NPCs on team red, standing around you."),
			new Cmd("/army spawn 10 blue archer steve wedge", "execute positioned ~20 ~ ~ run army spawn 10 blue archer steve wedge",
				"Count, team, then optional kit, skin, formation, difficulty."),
			new Text("Kits: knight, archer, god, none. Formations: circle, grid, line, column, wedge, scatter. Rival armies start fighting as soon as they see each other.")),
			s -> teamsWithNpcs(s) >= 2),
		new Lesson("toggle", "Start the fight", List.of(
			new Text("Every NPC has an AI switch. Off means it stands still. On means it fights. G toggles all armies at once, or use the command:"),
			new Cmd("/army toggle all", "army toggle all", "Flips every army: any running means all stop, otherwise all start."),
			new Cmd("/army toggle red off", "army toggle red off", "One team only. Use on or off, or leave it out to flip."),
			new Text("Press G in the game for the same thing without typing. You can change the key in the Keybinds tab.")),
			s -> false),
		new Lesson("difficulty", "Change a difficulty", List.of(
			new Text("Difficulty sets how fast, accurate and tough an army is. Levels: easy, normal, hard, nightmare. It changes instantly, even mid-fight."),
			new Cmd("/army difficulty red nightmare", "army difficulty red nightmare", "Makes team red as hard as it gets."),
			new Cmd("/army difficulty blue easy", "army difficulty blue easy", "And team blue gentle."),
			new Text("To edit the levels themselves (health, speed, damage, reaction time), use the Difficulty tab."),
			new Open("Open Difficulty tab", ScriptKitScreen.Tab.DIFFICULTY)),
			GuidePanel::difficultyChanged),
		new Lesson("kit", "Give gear with kits", List.of(
			new Text("A kit is a set of armor and items. Give one to a whole army, to one NPC, or to yourself."),
			new Cmd("/army kit red god", "army kit red god", "Gives every red NPC the god kit."),
			new Cmd("/scriptkit kit knight", "scriptkit kit knight", "Gives YOU the knight kit."),
			new Text("Build your own kits, with any items, in the Kits tab. NPCs drop everything they carry when they die."),
			new Open("Open Kits tab", ScriptKitScreen.Tab.KITS)),
			GuidePanel::hasCustomKit),
		new Lesson("formation", "Re-arrange an army", List.of(
			new Text("Armies can be re-arranged in place, facing the way you look. Shapes: circle, grid, line, column, wedge, scatter."),
			new Cmd("/army formation red wedge", "army formation red wedge", "Moves team red into a wedge."),
			new Cmd("/army formation all line 2", "army formation all line 2", "Everyone in a line, 2 blocks apart (0.8 to 8)."),
			new Text("The Command Stick, next, does this with clicks and a live preview.")),
			s -> false),
		new Lesson("stick", "Command Stick", List.of(
			new Text("The stick lets you pose an army from far away. Get it with:"),
			new Cmd("/scriptkit stick", "scriptkit stick", "Puts a Command Stick in your inventory."),
			new Text("Hold it. Right-click an NPC to select its army. Left-click to change the formation (Shift+left-click: spacing). "
				+ "Aim at the ground and you will see ghosts; right-click to place the army there. Shift+right-click deselects. Then press G.")),
			s -> false),
		new Lesson("team", "Teams and neutrals", List.of(
			new Text("Armies fight every other team and every player who has no team. Join a team and that army leaves you alone. Stay neutral and everyone hunts you."),
			new Text("Teams are normal Minecraft scoreboard teams. Choose or leave yours in the Team tab."),
			new Open("Open Team tab", ScriptKitScreen.Tab.TEAM)),
			s -> s.has("myTeam") && !s.get("myTeam").getAsString().isEmpty()),
		new Lesson("keys", "Keybinds", List.of(
			new Text("G toggles all armies. You can bind more keys, or combos like Ctrl+R, to toggle one team. Open Options > Controls > ScriptKit, or use the Keybinds tab."),
			new Text("Combos live in config/scriptkit/keybinds.json if you prefer editing the file, then run /scriptkit reload."),
			new Open("Open Keybinds tab", ScriptKitScreen.Tab.KEYBINDS)),
			s -> false),
		new Lesson("cleanup", "Clean up and reset", List.of(
			new Text("NPCs can mine and build to reach enemies. These commands keep your world and your scene tidy:"),
			new Cmd("/army list", "army list", "Every team, its size, AI state and difficulty."),
			new Cmd("/army cleanup", "army cleanup", "Puts back every block NPCs mined or placed."),
			new Cmd("/army remove red", "army remove red", "Removes a whole team (or use all)."),
			new Text("Not fighting? /npc info <name> tells you why. /army blocks off stops mining and building.")),
			s -> false));

	private static int selected = -1;

	private JsonObject state;
	private boolean editable;
	private int panelX;
	private int panelY;
	private int panelW;
	private int paneX;
	private int paneW;

	private record DrawText(int x, int y, FormattedCharSequence line, int color) {}

	private record DrawCmd(int x, int y, int w, List<FormattedCharSequence> lines) {}

	private final List<DrawText> texts = new ArrayList<>();
	private final List<DrawCmd> cmds = new ArrayList<>();

	@Override
	public void onState(JsonObject state) {
		this.state = state;
		if (state != null) {
			for (Lesson l : LESSONS) {
				if (l.detect().test(state)) {
					TutorialProgress.markDone(l.id());
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
		if (selected < 0) {
			selected = Math.max(0, Math.min(LESSONS.size() - 1, Integer.getInteger("scriptkit.debug.lesson", 0)));
			for (int i = 0; i < LESSONS.size(); i++) {
				if (Integer.getInteger("scriptkit.debug.lesson") == null && !TutorialProgress.isDone(LESSONS.get(i).id())) {
					selected = i;
					break;
				}
			}
		}
		Font font = Minecraft.getInstance().font;
		texts.clear();
		cmds.clear();

		// Lesson list on the left.
		int listW = Math.min(150, w * 33 / 100);
		int rowH = Math.min(20, (h - 12) / LESSONS.size());
		for (int i = 0; i < LESSONS.size(); i++) {
			final int idx = i;
			Lesson l = LESSONS.get(i);
			boolean ok = TutorialProgress.isDone(l.id());
			String label = (ok ? "✓ " : "") + (i + 1) + ". " + l.title();
			Button b = Button.builder(Component.literal(font.plainSubstrByWidth(label, listW - 8))
				.withStyle(ok ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.WHITE), btn -> {
				selected = idx;
				screen.rebuild();
			}).bounds(x, y + 12 + i * rowH, listW, rowH - 2).build();
			b.active = i != selected;
			screen.add(b);
		}

		// The selected lesson on the right.
		paneX = x + listW + 10;
		paneW = w - listW - 10;
		Lesson lesson = LESSONS.get(selected);
		int cy = y + 12;
		for (Block block : lesson.blocks()) {
			if (block instanceof Text t) {
				for (FormattedCharSequence line : font.split(Component.literal(t.text()), paneW)) {
					texts.add(new DrawText(paneX, cy, line, 0xFFCCD2E0));
					cy += font.lineHeight + 2;
				}
				cy += 4;
			} else if (block instanceof Cmd c) {
				int btns = c.run() == null ? 44 : 44 + 4 + 34;
				int boxW = paneW - btns - 4;
				List<FormattedCharSequence> cmdLines = font.split(Component.literal(c.shown()), boxW - 8);
				cmds.add(new DrawCmd(paneX, cy, boxW, cmdLines));
				int boxH = cmdLines.size() * (font.lineHeight + 1) + 3;
				int bx = paneX + boxW + 4;
				final String shown = c.shown();
				screen.add(Button.builder(Component.literal("Copy"), b -> {
					Minecraft.getInstance().keyboardHandler.setClipboard(shown);
					screen.setStatus("Copied " + shown + ". Press T, paste with Ctrl+V and Enter.", true);
				}).bounds(bx, cy - 3, 44, 16).build());
				if (c.run() != null) {
					final String run = c.run();
					Button runBtn = Button.builder(Component.literal("Run"), b -> {
						Minecraft mc = Minecraft.getInstance();
						if (mc.player != null) {
							mc.player.connection.sendCommand(run);
							TutorialProgress.markDone(lesson.id());
							screen.setStatus("Ran " + shown, true);
						}
					}).bounds(bx + 48, cy - 3, 34, 16).build();
					runBtn.active = editable;
					screen.add(runBtn);
				}
				cy += boxH + 1;
				for (FormattedCharSequence line : font.split(Component.literal(c.note()), paneW)) {
					texts.add(new DrawText(paneX + 4, cy, line, 0xFF8E98AE));
					cy += font.lineHeight + 1;
				}
				cy += 6;
			} else if (block instanceof Open o) {
				screen.add(Button.builder(Component.literal(o.label()), b -> {
					TutorialProgress.markDone(lesson.id());
					screen.showTab(o.tab());
				}).bounds(paneX, cy, Math.min(150, paneW), 18).build());
				cy += 22;
			}
		}

		// Previous / Next along the bottom of the pane.
		int by = y + h - 18;
		Button prev = Button.builder(Component.literal("< Back"), b -> {
			selected = Math.max(0, selected - 1);
			screen.rebuild();
		}).bounds(paneX, by, 70, 18).build();
		prev.active = selected > 0;
		screen.add(prev);
		boolean last = selected == LESSONS.size() - 1;
		screen.add(Button.builder(Component.literal(last ? "Finish" : "Next >"), b -> {
			TutorialProgress.markDone(lesson.id());
			selected = last ? selected : selected + 1;
			screen.rebuild();
		}).bounds(paneX + paneW - 70, by, 70, 18).build());
	}

	@Override
	public void renderExtra(GuiGraphics g, int mouseX, int mouseY) {
		Font font = Minecraft.getInstance().font;
		int done = 0;
		for (Lesson l : LESSONS) {
			if (TutorialProgress.isDone(l.id())) {
				done++;
			}
		}
		g.drawString(font, "Tutorial  (" + done + "/" + LESSONS.size() + " done)", panelX + 2, panelY + 1, 0xFFAAAAAA);
		if (selected >= 0) {
			String title = "Lesson " + (selected + 1) + ": " + LESSONS.get(selected).title();
			g.drawString(font, title, paneX, panelY + 1, 0xFFFFD75A);
		}
		if (!editable) {
			String warn = "Run buttons need operator permission.";
			g.drawString(font, warn, panelX + panelW - font.width(warn), panelY + 1, 0xFFFFAA55);
		}
		for (DrawCmd c : cmds) {
			g.fill(c.x(), c.y() - 3, c.x() + c.w(), c.y() - 3 + c.lines().size() * (font.lineHeight + 1) + 5, 0xFF12151C);
			int ly = c.y();
			for (FormattedCharSequence line : c.lines()) {
				g.drawString(font, line, c.x() + 4, ly, 0xFFFFD75A);
				ly += font.lineHeight + 1;
			}
		}
		for (DrawText t : texts) {
			g.drawString(font, t.line(), t.x(), t.y(), t.color());
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
