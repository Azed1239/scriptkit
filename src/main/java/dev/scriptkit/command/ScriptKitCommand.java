package dev.scriptkit.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.scriptkit.ai.CombatConfig;
import dev.scriptkit.config.ScriptKitConfig;
import dev.scriptkit.network.ArmyActionHandler;
import dev.scriptkit.network.ScriptKitNetworking;
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

		// Developer-only: feed the keybind packet handler without a client. Needs -Dscriptkit.debug=true.
		if (Boolean.getBoolean("scriptkit.debug")) {
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
