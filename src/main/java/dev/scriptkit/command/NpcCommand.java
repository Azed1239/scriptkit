package dev.scriptkit.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.scriptkit.npc.KitManager;
import dev.scriptkit.npc.NpcManager;
import dev.scriptkit.npc.NpcMessages;
import dev.scriptkit.npc.ScriptNpc;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** /npc spawn | remove | list | kit  (permission level: gamemaster / op). */
public final class NpcCommand {
	private static final SuggestionProvider<CommandSourceStack> NPC_NAMES = (ctx, builder) -> {
		List<String> names = new ArrayList<>(NpcManager.names(ctx.getSource().getServer()));
		names.add("all");
		return SharedSuggestionProvider.suggest(names, builder);
	};
	private static final SuggestionProvider<CommandSourceStack> KIT_NAMES =
		(ctx, builder) -> SharedSuggestionProvider.suggest(KitManager.names(), builder);

	private NpcCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("npc")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

		root.then(Commands.literal("spawn")
			.then(Commands.argument("name", StringArgumentType.word())
				.executes(c -> spawn(c, null, null, null))
				.then(Commands.argument("skin", StringArgumentType.word())
					.executes(c -> spawn(c, str(c, "skin"), null, null))
					.then(Commands.argument("team", StringArgumentType.word())
						.executes(c -> spawn(c, str(c, "skin"), str(c, "team"), null))
						.then(Commands.argument("kit", StringArgumentType.word())
							.suggests(KIT_NAMES)
							.executes(c -> spawn(c, str(c, "skin"), str(c, "team"), str(c, "kit"))))))));

		root.then(Commands.literal("remove")
			.then(Commands.argument("name", StringArgumentType.word())
				.suggests(NPC_NAMES)
				.executes(NpcCommand::remove)));

		root.then(Commands.literal("list").executes(NpcCommand::list));

		root.then(Commands.literal("kit")
			.then(Commands.argument("name", StringArgumentType.word())
				.suggests(NPC_NAMES)
				.then(Commands.argument("kit", StringArgumentType.word())
					.suggests(KIT_NAMES)
					.executes(NpcCommand::kit))));

		dispatcher.register(root);
	}

	private static String str(CommandContext<CommandSourceStack> c, String arg) {
		return StringArgumentType.getString(c, arg);
	}

	private static int spawn(CommandContext<CommandSourceStack> c, String skin, String team, String kit) {
		CommandSourceStack src = c.getSource();
		String name = str(c, "name");
		if (name.equalsIgnoreCase("all")) {
			src.sendFailure(Component.literal("'all' is reserved and cannot be an NPC name."));
			return 0;
		}
		if (!NpcManager.named(src.getServer(), name).isEmpty()) {
			src.sendFailure(Component.literal("An NPC named '" + name + "' already exists."));
			return 0;
		}
		String kitName = kit == null ? "none" : kit;
		if (!KitManager.exists(kitName)) {
			src.sendFailure(Component.literal("Unknown kit '" + kitName + "'."));
			return 0;
		}
		ScriptNpc npc = ScriptNpc.create(src.getLevel());
		Vec3 pos = src.getPosition();
		npc.snapTo(pos.x, pos.y, pos.z, src.getRotation().y, 0.0F);
		npc.setYHeadRot(src.getRotation().y);
		npc.setNpcName(name);
		npc.setSkinName(skin == null ? name : skin);
		npc.setKit(kitName);
		if (team != null) {
			npc.setTeamName(team);
		}
		if (!src.getLevel().addFreshEntity(npc)) {
			src.sendFailure(Component.literal("Could not add NPC to the world at " + pos + "."));
			return 0;
		}
		src.sendSuccess(() -> Component.literal("Spawned NPC '" + name + "'."), true);
		return 1;
	}

	private static List<ScriptNpc> targets(CommandContext<CommandSourceStack> c) {
		String name = str(c, "name");
		return name.equalsIgnoreCase("all")
			? NpcManager.all(c.getSource().getServer())
			: NpcManager.named(c.getSource().getServer(), name);
	}

	private static int remove(CommandContext<CommandSourceStack> c) {
		List<ScriptNpc> list = targets(c);
		if (list.isEmpty()) {
			c.getSource().sendFailure(Component.literal("No matching NPC."));
			return 0;
		}
		for (ScriptNpc npc : list) {
			NpcMessages.removed(npc);
			npc.leaveTeam();
			npc.discard();
		}
		c.getSource().sendSuccess(() -> Component.literal("Removed " + list.size() + " NPC(s)."), true);
		return list.size();
	}

	private static int kit(CommandContext<CommandSourceStack> c) {
		String kit = str(c, "kit");
		if (!KitManager.exists(kit)) {
			c.getSource().sendFailure(Component.literal("Unknown kit '" + kit + "'."));
			return 0;
		}
		List<ScriptNpc> list = targets(c);
		if (list.isEmpty()) {
			c.getSource().sendFailure(Component.literal("No matching NPC."));
			return 0;
		}
		list.forEach(npc -> npc.setKit(kit));
		c.getSource().sendSuccess(() -> Component.literal("Applied kit '" + kit + "' to " + list.size() + " NPC(s)."), true);
		return list.size();
	}

	private static int list(CommandContext<CommandSourceStack> c) {
		List<ScriptNpc> list = NpcManager.all(c.getSource().getServer());
		if (list.isEmpty()) {
			c.getSource().sendSuccess(() -> Component.literal("No NPCs."), false);
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal(list.size() + " NPC(s):"), false);
		for (ScriptNpc npc : list) {
			String line = String.format("- %s (skin=%s, team=%s, kit=%s) at %.1f %.1f %.1f",
				npc.getNpcName(), npc.getSkinName(),
				npc.getTeamName().isEmpty() ? "-" : npc.getTeamName(), npc.getKit(),
				npc.getX(), npc.getY(), npc.getZ());
			c.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		return list.size();
	}
}
