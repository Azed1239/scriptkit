package dev.scriptkit.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.scriptkit.ai.CombatConfig;
import dev.scriptkit.config.ScriptKitConfig;
import dev.scriptkit.network.ScriptKitNetworking;
import dev.scriptkit.npc.BlockChangeLog;
import dev.scriptkit.npc.ArmyManager;
import dev.scriptkit.npc.ArmyManager.Formation;
import dev.scriptkit.npc.KitManager;
import dev.scriptkit.npc.ScriptNpc;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.Entity;

/** /army spawn | toggle | remove | list | team  (op / gamemaster). */
public final class ArmyCommand {
	private static final SuggestionProvider<CommandSourceStack> TEAMS =
		(ctx, b) -> SharedSuggestionProvider.suggest(ArmyManager.teamNames(ctx.getSource().getServer()), b);
	private static final SuggestionProvider<CommandSourceStack> KITS =
		(ctx, b) -> SharedSuggestionProvider.suggest(KitManager.names(), b);
	private static final SuggestionProvider<CommandSourceStack> FORMATIONS =
		(ctx, b) -> SharedSuggestionProvider.suggest(List.of("circle", "grid"), b);

	private ArmyCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("army")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

		root.then(Commands.literal("spawn")
			.then(Commands.argument("count", IntegerArgumentType.integer(1))
				.then(Commands.argument("team", StringArgumentType.word())
					.suggests(TEAMS)
					.executes(c -> spawn(c, null, null, null, null))
					.then(Commands.argument("kit", StringArgumentType.word())
						.suggests(KITS)
						.executes(c -> spawn(c, str(c, "kit"), null, null, null))
						.then(Commands.argument("skin", StringArgumentType.word())
							.executes(c -> spawn(c, str(c, "kit"), str(c, "skin"), null, null))
							.then(Commands.argument("formation", StringArgumentType.word())
								.suggests(FORMATIONS)
								.executes(c -> spawn(c, str(c, "kit"), str(c, "skin"), str(c, "formation"), null))
								.then(Commands.argument("difficulty", StringArgumentType.word())
									.suggests(DIFFICULTIES)
									.executes(c -> spawn(c, str(c, "kit"), str(c, "skin"), str(c, "formation"), str(c, "difficulty"))))))))));

		root.then(Commands.literal("kit")
			.then(Commands.argument("team", StringArgumentType.word())
				.suggests(TEAMS)
				.then(Commands.argument("kit", StringArgumentType.word())
					.suggests(KITS)
					.executes(c -> {
						String kit = str(c, "kit");
						if (!KitManager.exists(kit)) {
							c.getSource().sendFailure(Component.literal("Unknown kit '" + kit + "'."));
							return 0;
						}
						int n = ArmyManager.setKit(c.getSource().getServer(), str(c, "team"), kit);
						if (n == 0) {
							c.getSource().sendFailure(Component.literal("No NPCs on that team."));
							return 0;
						}
						c.getSource().sendSuccess(() -> Component.literal("Gave kit " + kit + " to " + n + " NPC(s)."), true);
						return n;
					}))));

		root.then(Commands.literal("difficulty")
			.executes(ArmyCommand::difficultyHelp)
			.then(Commands.argument("team", StringArgumentType.word())
				.suggests(TEAMS)
				.then(Commands.argument("level", StringArgumentType.word())
					.suggests(DIFFICULTIES)
					.executes(ArmyCommand::difficulty))));

		root.then(Commands.literal("toggle")
			.then(Commands.argument("team", StringArgumentType.word())
				.suggests(TEAMS)
				.executes(c -> toggle(c, null))
				.then(Commands.literal("on").executes(c -> toggle(c, true)))
				.then(Commands.literal("off").executes(c -> toggle(c, false)))));

		root.then(Commands.literal("remove")
			.then(Commands.argument("team", StringArgumentType.word())
				.suggests(TEAMS)
				.executes(ArmyCommand::remove)));

		root.then(Commands.literal("list").executes(ArmyCommand::list));

		root.then(Commands.literal("blocks")
			.executes(c -> blocksStatus(c.getSource()))
			.then(Commands.literal("on").executes(c -> setBlocks(c.getSource(), true)))
			.then(Commands.literal("off").executes(c -> setBlocks(c.getSource(), false))));

		root.then(Commands.literal("cleanup").executes(ArmyCommand::cleanup));

		root.then(Commands.literal("team")
			.then(Commands.argument("targets", EntityArgument.entities())
				.then(Commands.argument("team", StringArgumentType.word())
					.suggests(TEAMS)
					.executes(ArmyCommand::moveTeam))));

		dispatcher.register(root);
	}

	private static String str(CommandContext<CommandSourceStack> c, String arg) {
		return StringArgumentType.getString(c, arg);
	}

	private static final SuggestionProvider<CommandSourceStack> DIFFICULTIES =
		(ctx, b) -> SharedSuggestionProvider.suggest(CombatConfig.get().difficulties.keySet(), b);

	private static int spawn(CommandContext<CommandSourceStack> c, String kit, String skin, String formationName, String difficulty) {
		CommandSourceStack src = c.getSource();
		int count = IntegerArgumentType.getInteger(c, "count");
		String team = str(c, "team");
		int max = ScriptKitConfig.get().maxArmySize;
		if (count > max) {
			src.sendFailure(Component.literal("Count " + count + " is over the configured maximum of " + max + "."));
			return 0;
		}
		if (team.equalsIgnoreCase(ArmyManager.ALL)) {
			src.sendFailure(Component.literal("'all' is reserved and cannot be a team name."));
			return 0;
		}
		String kitName = kit == null ? "none" : kit;
		if (!KitManager.exists(kitName)) {
			src.sendFailure(Component.literal("Unknown kit '" + kitName + "'."));
			return 0;
		}
		Formation formation = Formation.parse(formationName == null ? ScriptKitConfig.get().defaultFormation : formationName);
		if (formation == null) {
			src.sendFailure(Component.literal("Unknown formation (use circle or grid)."));
			return 0;
		}
		if (difficulty != null && !CombatConfig.get().hasDifficulty(difficulty)) {
			src.sendFailure(Component.literal("Unknown difficulty '" + difficulty + "'. Available: "
				+ String.join(", ", CombatConfig.get().difficulties.keySet())));
			return 0;
		}
		int spawned = ArmyManager.spawn(src.getLevel(), src.getPosition(), src.getRotation().y, count, team, kitName, skin, formation, difficulty);
		src.sendSuccess(() -> Component.literal("Spawned " + spawned + " NPC(s) on team '" + team + "'."), true);
		src.sendSystemMessage(blocksHint());
		return spawned;
	}

	/** Chat hint shown after /army spawn: whether NPCs can mine/build, with a click to toggle, and how to undo. */
	private static Component blocksHint() {
		boolean on = CombatConfig.get().allowBlockBreaking || CombatConfig.get().allowBlockPlacing;
		Component toggle = Component.literal(on ? "[Turn off]" : "[Turn on]").withStyle(style -> style
			.withColor(on ? ChatFormatting.RED : ChatFormatting.GREEN)
			.withClickEvent(new ClickEvent.RunCommand("/army blocks " + (on ? "off" : "on")))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal("/army blocks " + (on ? "off" : "on")))));
		Component cleanup = Component.literal("/army cleanup").withStyle(style -> style
			.withColor(ChatFormatting.AQUA)
			.withClickEvent(new ClickEvent.SuggestCommand("/army cleanup"))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal("Put back every block NPCs mined or placed"))));
		return on
			? Component.literal("NPCs can mine and build to reach enemies. ").withStyle(ChatFormatting.GRAY)
				.append(toggle).append(Component.literal(" Undo their block changes with ").withStyle(ChatFormatting.GRAY))
				.append(cleanup).append(Component.literal(".").withStyle(ChatFormatting.GRAY))
			: Component.literal("NPC mining and building is off. ").withStyle(ChatFormatting.GRAY).append(toggle);
	}

	private static int blocksStatus(CommandSourceStack src) {
		src.sendSystemMessage(blocksHint());
		return 1;
	}

	private static int setBlocks(CommandSourceStack src, boolean on) {
		CombatConfig cfg = CombatConfig.get();
		cfg.allowBlockBreaking = on;
		cfg.allowBlockPlacing = on;
		CombatConfig.save();
		src.sendSuccess(() -> Component.literal("NPC mining and building turned " + (on ? "on" : "off") + "."), true);
		return 1;
	}

	private static int cleanup(CommandContext<CommandSourceStack> c) {
		int logged = BlockChangeLog.size();
		if (logged == 0) {
			c.getSource().sendSuccess(() -> Component.literal("No NPC block changes to undo."), false);
			return 0;
		}
		int[] result = BlockChangeLog.cleanup(c.getSource().getServer());
		String msg = "Restored " + result[0] + " block(s)"
			+ (result[1] > 0 ? "; skipped " + result[1] + " that were changed since by someone else." : ".");
		c.getSource().sendSuccess(() -> Component.literal(msg), true);
		return result[0];
	}

	private static int toggle(CommandContext<CommandSourceStack> c, Boolean state) {
		ArmyManager.AiResult r = ArmyManager.setAi(c.getSource().getServer(), str(c, "team"), state);
		if (r.count() == 0) {
			c.getSource().sendFailure(Component.literal("No NPCs on that team."));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("AI " + (r.enabled() ? "on" : "off") + " for " + r.count() + " NPC(s)."), true);
		if (c.getSource().getPlayer() != null) {
			ScriptKitNetworking.sendKeybindHint(c.getSource().getPlayer());
		}
		return r.count();
	}

	private static int remove(CommandContext<CommandSourceStack> c) {
		int n = ArmyManager.remove(c.getSource().getServer(), str(c, "team"));
		if (n == 0) {
			c.getSource().sendFailure(Component.literal("No NPCs on that team."));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Removed " + n + " NPC(s)."), true);
		return n;
	}

	private static int difficulty(CommandContext<CommandSourceStack> c) {
		String level = str(c, "level").toLowerCase();
		if (!CombatConfig.get().hasDifficulty(level)) {
			c.getSource().sendFailure(Component.literal("Unknown difficulty '" + level + "'. Available: "
				+ String.join(", ", CombatConfig.get().difficulties.keySet())));
			return 0;
		}
		int n = ArmyManager.setDifficulty(c.getSource().getServer(), str(c, "team"), level);
		if (n == 0) {
			c.getSource().sendFailure(Component.literal("No NPCs on that team."));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Set difficulty " + level + " on " + n + " NPC(s)."), true);
		return n;
	}

	/** /army difficulty with no arguments: list the presets and what they do. */
	private static int difficultyHelp(CommandContext<CommandSourceStack> c) {
		CombatConfig cfg = CombatConfig.get();
		c.getSource().sendSuccess(() -> Component.literal("Difficulties (default: " + cfg.defaultDifficulty
			+ "). Use /army difficulty <team|all> <level>, or add one to /army spawn."), false);
		cfg.difficulties.forEach((name, d) ->
			c.getSource().sendSuccess(() -> Component.literal("- " + name + ": " + d.summary()), false));
		return cfg.difficulties.size();
	}

	private static int list(CommandContext<CommandSourceStack> c) {
		Map<String, int[]> counts = ArmyManager.counts(c.getSource().getServer());
		if (counts.isEmpty()) {
			c.getSource().sendSuccess(() -> Component.literal("No NPCs."), false);
			return 0;
		}
		int total = 0;
		Map<String, String> diffs = ArmyManager.difficulties(c.getSource().getServer());
		for (Map.Entry<String, int[]> e : counts.entrySet()) {
			total += e.getValue()[0];
			String line = String.format("- %s: %d NPC(s), AI on for %d, difficulty %s", e.getKey(), e.getValue()[0], e.getValue()[1],
				diffs.getOrDefault(e.getKey(), "?"));
			c.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		int t = total;
		c.getSource().sendSuccess(() -> Component.literal("Total: " + t), false);
		return total;
	}

	private static int moveTeam(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		String team = str(c, "team");
		if (team.equalsIgnoreCase(ArmyManager.ALL)) {
			c.getSource().sendFailure(Component.literal("'all' is reserved and cannot be a team name."));
			return 0;
		}
		List<ScriptNpc> npcs = EntityArgument.getEntities(c, "targets").stream()
			.filter(ScriptNpc.class::isInstance).map(ScriptNpc.class::cast).toList();
		if (npcs.isEmpty()) {
			c.getSource().sendFailure(Component.literal("Selector matched no NPCs."));
			return 0;
		}
		int n = ArmyManager.moveToTeam(npcs, team);
		c.getSource().sendSuccess(() -> Component.literal("Moved " + n + " NPC(s) to team '" + team + "'."), true);
		return n;
	}
}
