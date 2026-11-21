package dev.scriptkit.network;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The only place that touches Fabric's networking registration API, so a later Minecraft port only
 * has to change this class (payload classes are plain records).
 */
public final class ScriptKitNetworking {
	private static final Set<UUID> HINTED = new HashSet<>();

	private ScriptKitNetworking() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(ArmyActionPayload.TYPE, ArmyActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ReloadKeybindsPayload.TYPE, ReloadKeybindsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(MenuPayloads.Open.TYPE, MenuPayloads.Open.CODEC);
		PayloadTypeRegistry.playS2C().register(MenuPayloads.State.TYPE, MenuPayloads.State.CODEC);
		PayloadTypeRegistry.playC2S().register(MenuPayloads.Request.TYPE, MenuPayloads.Request.CODEC);
		PayloadTypeRegistry.playC2S().register(MenuPayloads.Edit.TYPE, MenuPayloads.Edit.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ArmyActionPayload.TYPE, (payload, context) ->
			context.server().execute(() -> ArmyActionHandler.handle(context.player(), payload)));

		ServerPlayNetworking.registerGlobalReceiver(MenuPayloads.Request.TYPE, (payload, context) ->
			context.server().execute(() -> MenuHandler.handleRequest(context.player())));
		ServerPlayNetworking.registerGlobalReceiver(MenuPayloads.Edit.TYPE, (payload, context) ->
			context.server().execute(() -> MenuHandler.handleEdit(context.player(), payload)));

		ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> {
			MenuHandler.forget(listener.getPlayer().getUUID());
			UUID id = listener.getPlayer().getUUID();
			ArmyActionHandler.forget(id);
			HINTED.remove(id);
		});
	}

	/** Tells clients that have ScriptKit to re-read their keybinds.json. Returns how many were told. */
	public static int sendReloadKeybinds(MinecraftServer server) {
		int sent = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, ReloadKeybindsPayload.TYPE)) {
				ServerPlayNetworking.send(player, new ReloadKeybindsPayload());
				sent++;
			}
		}
		return sent;
	}

	/** Once per player per session, after /army toggle: mention the keybind, if their client can use it. */
	public static void sendKeybindHint(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, ArmyActionPayload.TYPE) || !HINTED.add(player.getUUID())) {
			return;
		}
		player.sendSystemMessage(Component.literal("Tip: press ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal("G").withStyle(ChatFormatting.AQUA))
			.append(Component.literal(" to toggle all armies without typing. Change keys in Controls > ScriptKit, or add combos like Ctrl+R in config/scriptkit/keybinds.json.")
				.withStyle(ChatFormatting.GRAY)));
	}

	public static void clearSession() {
		HINTED.clear();
		ArmyActionHandler.clear();
	}
}
