package dev.scriptkit.npc;

import dev.scriptkit.ai.CombatConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;

/** Player-style chat messages for NPCs: death messages and "left the game". */
public final class NpcMessages {
	private NpcMessages() {}

	public static void death(ServerLevel level, ScriptNpc npc, Component deathMessage) {
		CombatConfig cfg = CombatConfig.get();
		MinecraftServer server = level.getServer();
		if (cfg.deathMessages && deathMessage != null && Boolean.TRUE.equals(level.getGameRules().get(GameRules.SHOW_DEATH_MESSAGES))) {
			server.getPlayerList().broadcastSystemMessage(deathMessage, false);
		}
		if (cfg.leaveMessages) {
			left(server, npc);
		}
	}

	/** Called when an NPC is removed by command. */
	public static void removed(ScriptNpc npc) {
		if (CombatConfig.get().leaveMessageOnRemove && npc.level() instanceof ServerLevel level) {
			left(level.getServer(), npc);
		}
	}

	private static void left(MinecraftServer server, ScriptNpc npc) {
		server.getPlayerList().broadcastSystemMessage(
			Component.translatable("multiplayer.player.left", npc.getDisplayName()).withStyle(ChatFormatting.YELLOW), false);
	}
}
