package dev.scriptkit.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.scriptkit.config.ScriptKitConfig;
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
import net.minecraft.network.chat.Component;
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
					.executes(c -> spawn(c, null, null, null))
					.then(Commands.argument("kit", StringArgumentType.word())
						.suggests(KITS)
						.executes(c -> spawn(c, str(c, "kit"), null, null))
						.then(Commands.argument("skin", StringArgumentType.word())
							.executes(c -> spawn(c, str(c, "kit"), str(c, "skin"), null))
							.then(Commands.argument("formation", StringArgumentType.word())
								.suggests(FORMATIONS)
								.executes(c -> spawn(c, str(c, "kit"), str(c, "skin"), str(c, "formation")))))))));

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

	private static int spawn(CommandContext<CommandSourceStack> c, String kit, String skin, String formationName) {
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
		int spawned = ArmyManager.spawn(src.getLevel(), src.getPosition(), src.getRotation().y, count, team, kitName, skin, formation);
		src.sendSuccess(() -> Component.literal("Spawned " + spawned + " NPC(s) on team '" + team + "'."), true);
		return spawned;
	}

	private static int toggle(CommandContext<CommandSourceStack> c, Boolean state) {
		int n = ArmyManager.toggle(c.getSource().getServer(), str(c, "team"), state);
		if (n == 0) {
			c.getSource().sendFailure(Component.literal("No NPCs on that team."));
			return 0;
		}
		String what = state == null ? "Toggled AI on" : "Set AI " + (state ? "on" : "off") + " for";
		c.getSource().sendSuccess(() -> Component.literal(what + " " + n + " NPC(s)."), true);
		return n;
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

	private static int list(CommandContext<CommandSourceStack> c) {
		Map<String, int[]> counts = ArmyManager.counts(c.getSource().getServer());
		if (counts.isEmpty()) {
			c.getSource().sendSuccess(() -> Component.literal("No NPCs."), false);
			return 0;
		}
		int total = 0;
		for (Map.Entry<String, int[]> e : counts.entrySet()) {
			total += e.getValue()[0];
			String line = String.format("- %s: %d NPC(s), AI on for %d", e.getKey(), e.getValue()[0], e.getValue()[1]);
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
