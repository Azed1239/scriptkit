package dev.scriptkit.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.scriptkit.ai.CombatConfig;
import dev.scriptkit.config.ScriptKitConfig;
import dev.scriptkit.network.ArmyActionHandler;
import dev.scriptkit.network.MenuHandler;
import dev.scriptkit.network.MenuService;
import dev.scriptkit.network.ScriptKitNetworking;
import net.minecraft.server.level.ServerPlayer;
import dev.scriptkit.npc.KitManager;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** /scriptkit reload (op / gamemaster). */
public final class ScriptKitCommand {
	private ScriptKitCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("scriptkit")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

		root.then(Commands.literal("reload").executes(ScriptKitCommand::reload));

		root.then(Commands.literal("kit")
			.then(Commands.argument("kit", StringArgumentType.word())
				.suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(KitManager.names(), b))
				.executes(c -> giveKit(c, java.util.List.of(c.getSource().getPlayerOrException())))
				.then(Commands.argument("players", net.minecraft.commands.arguments.EntityArgument.players())
					.executes(c -> giveKit(c, net.minecraft.commands.arguments.EntityArgument.getPlayers(c, "players"))))));

		root.then(Commands.literal("menu").executes(c -> {
			ServerPlayer player = c.getSource().getPlayer();
			if (player == null) {
				c.getSource().sendFailure(Component.literal("Only a player can open the menu."));
				return 0;
			}
			if (!MenuHandler.open(player)) {
				c.getSource().sendFailure(Component.literal("Your client doesn't have ScriptKit installed, so it can't show the menu."));
				return 0;
			}
			return 1;
		}));

		// Developer-only: feed the packet handlers without a client. Needs -Dscriptkit.debug=true.
		if (Boolean.getBoolean("scriptkit.debug")) {
			root.then(Commands.literal("simulate-menu")
				.then(Commands.argument("permitted", StringArgumentType.word())
					.then(Commands.argument("op", StringArgumentType.word())
						.then(Commands.argument("json", StringArgumentType.greedyString())
							.executes(ScriptKitCommand::simulateMenu)))));
			root.then(Commands.literal("snapshot").executes(c -> {
				c.getSource().sendSuccess(() -> Component.literal(MenuService.snapshot(c.getSource().getServer(), true, null)), false);
				return 1;
			}));
			root.then(Commands.literal("simulate")
				.then(Commands.argument("sender", StringArgumentType.word())
					.then(Commands.argument("permitted", StringArgumentType.word())
						.then(Commands.argument("action", StringArgumentType.word())
							.executes(c -> simulate(c, ""))
							.then(Commands.argument("team", StringArgumentType.word())
								.executes(c -> simulate(c, StringArgumentType.getString(c, "team"))))))));
		}
		dispatcher.register(root);
	}

	private static int reload(CommandContext<CommandSourceStack> c) {
		ScriptKitConfig.load();
		CombatConfig.load();
		KitManager.load();
		int clients = ScriptKitNetworking.sendReloadKeybinds(c.getSource().getServer());
		String clientsPart = clients == 0
			? "no connected client has ScriptKit keybinds"
			: "keybinds reloading on " + clients + " client(s)";
		c.getSource().sendSuccess(() -> Component.literal("ScriptKit reloaded: config, combat and kits; " + clientsPart + "."), true);
		return 1;
	}

	/** /scriptkit kit <kit> [players]: put a kit's items in inventories, like /give. */
	private static int giveKit(CommandContext<CommandSourceStack> c, java.util.Collection<ServerPlayer> players) {
		String kit = StringArgumentType.getString(c, "kit");
		if (!KitManager.exists(kit)) {
			c.getSource().sendFailure(Component.literal("Unknown kit '" + kit + "'."));
			return 0;
		}
		int total = 0;
		for (ServerPlayer p : players) {
			int n = KitManager.give(p, kit);
			total += Math.max(0, n);
		}
		if (total == 0) {
			c.getSource().sendFailure(Component.literal("That kit has no items to give."));
			return 0;
		}
		int given = total;
		c.getSource().sendSuccess(() -> Component.literal("Gave kit " + kit + " (" + given + " item(s)) to " + players.size() + " player(s)."), true);
		return given;
	}

	private static int simulateMenu(CommandContext<CommandSourceStack> c) {
		boolean permitted = StringArgumentType.getString(c, "permitted").equalsIgnoreCase("op");
		MenuService.Result r = permitted
			? MenuService.edit(c.getSource().getServer(), StringArgumentType.getString(c, "op"), StringArgumentType.getString(c, "json"))
			: new MenuService.Result(false, "You don't have permission to change ScriptKit settings.");
		c.getSource().sendSuccess(() -> Component.literal("[menu " + (r.ok() ? "ok" : "FAILED") + "] " + r.message()), false);
		return r.ok() ? 1 : 0;
	}

	private static int simulate(CommandContext<CommandSourceStack> c, String team) {
		String sender = StringArgumentType.getString(c, "sender");
		boolean permitted = StringArgumentType.getString(c, "permitted").equalsIgnoreCase("op");
		UUID id = UUID.nameUUIDFromBytes(sender.getBytes());
		ArmyActionHandler.Feedback fb = ArmyActionHandler.process(c.getSource().getServer(), id, sender, permitted,
			StringArgumentType.getString(c, "action"), team, System.nanoTime());
		String text = fb.message() == null ? "(dropped by rate limit)" : fb.message().getString();
		c.getSource().sendSuccess(() -> Component.literal("[sim " + sender + "] " + text).withStyle(ChatFormatting.GRAY), false);
		return fb.applied() ? 1 : 0;
	}
}
