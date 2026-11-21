package dev.scriptkit.client.ui;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * Team tab: join a team, create one, or go neutral. On a team you fight for it and armies on other
 * teams attack you; with no team you are neutral and every army attacks you.
 */
public class TeamPanel implements MenuPanel {
	private final List<JsonObject> teams = new ArrayList<>();
	private String myTeam = "";
	private String selected = "";
	private String newName = "";
	private boolean editable;
	private boolean hasState;
	private int panelX;
	private int panelY;
	private int rightX;

	@Override
	public void onState(JsonObject state) {
		if (state == null || !state.has("teams")) {
			return;
		}
		hasState = true;
		teams.clear();
		state.getAsJsonArray("teams").forEach(t -> teams.add(t.getAsJsonObject()));
		myTeam = state.has("myTeam") ? state.get("myTeam").getAsString() : "";
		if (selected.isEmpty() || teams.stream().noneMatch(t -> t.get("name").getAsString().equals(selected))) {
			selected = !myTeam.isEmpty() ? myTeam : teams.isEmpty() ? "" : teams.get(0).get("name").getAsString();
		}
	}

	@Override
	public void build(ScriptKitScreen screen, int x, int y, int w, int h) {
		editable = screen.canEdit();
		panelX = x;
		panelY = y;
		if (!hasState) {
			return;
		}
		int listW = 150;
		SimpleList<String> list = new SimpleList<>(x, y + 12, listW, h - 12, 16, (g, name, rx, ry, rw, rh, hovered, sel) -> {
			JsonObject info = teams.stream().filter(t -> t.get("name").getAsString().equals(name)).findFirst().orElse(null);
			String label = name + (info == null ? "" : "  (" + info.get("count").getAsInt() + " NPCs)")
				+ (name.equals(myTeam) ? "  < you" : "");
			g.drawString(screen.fontRenderer(), screen.fontRenderer().plainSubstrByWidth(label, rw), rx, ry + 4,
				name.equals(myTeam) ? 0xFF55FF55 : sel ? 0xFFFFFFFF : 0xFFCCCCCC);
		}, name -> {
			selected = name;
			screen.rebuild();
		});
		List<String> names = new ArrayList<>();
		teams.forEach(t -> names.add(t.get("name").getAsString()));
		list.setItems(names);
		if (!selected.isEmpty()) {
			list.select(selected);
		}
		screen.add(list);

		rightX = x + listW + 12;
		int rightW = w - listW - 12;
		int ry = y + 40;
		Button join = Button.builder(Component.literal(selected.isEmpty() ? "Pick a team on the left" : "Join " + selected), b -> {
			JsonObject args = new JsonObject();
			args.addProperty("team", selected);
			screen.sendEdit("joinTeam", args);
		}).bounds(rightX, ry, rightW, 20)
			.tooltip(Tooltip.create(Component.literal("You fight for this team; armies on other teams attack you."))).build();
		join.active = editable && !selected.isEmpty() && !selected.equals(myTeam);
		screen.add(join);

		Button neutral = Button.builder(Component.literal("Go neutral (no team)"), b -> {
			screen.sendEdit("leaveTeam", new JsonObject());
			TutorialProgress.markDone("team");
		}).bounds(rightX, ry + 24, rightW, 20)
			.tooltip(Tooltip.create(Component.literal("With no team, every army attacks you - and they fight each other too."))).build();
		neutral.active = editable && !myTeam.isEmpty();
		screen.add(neutral);

		int boxW = rightW * 55 / 100;
		EditBox box = new EditBox(screen.fontRenderer(), rightX, ry + 56, boxW, 20, Component.literal("New team name"));
		box.setHint(Component.literal("New team name"));
		box.setMaxLength(32);
		box.setFilter(s -> s.matches("[A-Za-z0-9_.+\\-]*"));
		box.setValue(newName);
		box.setResponder(s -> newName = s);
		box.active = editable;
		screen.add(box);
		Button create = Button.builder(Component.literal("Create & join"), b -> {
			String name = newName.trim();
			if (name.isEmpty()) {
				screen.setStatus("Type a name for the new team first.", false);
				return;
			}
			JsonObject args = new JsonObject();
			args.addProperty("team", name);
			screen.sendEdit("joinTeam", args);
			selected = name;
		}).bounds(rightX + boxW + 4, ry + 56, rightW - boxW - 4, 20).build();
		create.active = editable;
		screen.add(create);
	}

	@Override
	public void renderExtra(GuiGraphics g, int mouseX, int mouseY) {
		var font = Minecraft.getInstance().font;
		g.drawString(font, "Teams", panelX + 2, panelY + 1, 0xFFAAAAAA);
		if (!hasState) {
			return;
		}
		if (!editable) {
			g.drawString(font, "Read-only: you need operator permission to change teams.", rightX, panelY + 1, 0xFFFFAA55);
		}
		String you = myTeam.isEmpty() ? "You are NEUTRAL (no team)" : "You are on team " + myTeam;
		g.drawString(font, you, rightX, panelY + 14, myTeam.isEmpty() ? 0xFFFFAA55 : 0xFF55FF55);
		int y = panelY + 40 + 56 + 28;
		g.drawString(font, "Team: you fight for it.", rightX, y, 0xFF999999);
		g.drawString(font, "Neutral: every army attacks you.", rightX, y + 11, 0xFF999999);
	}
}
