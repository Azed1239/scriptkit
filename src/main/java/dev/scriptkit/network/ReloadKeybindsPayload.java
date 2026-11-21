package dev.scriptkit.network;

import dev.scriptkit.ScriptKitMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: "reload your keybinds.json" (sent by /scriptkit reload to clients that have ScriptKit). */
public record ReloadKeybindsPayload() implements CustomPacketPayload {
	public static final Type<ReloadKeybindsPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath(ScriptKitMod.MOD_ID, "reload_keybinds"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ReloadKeybindsPayload> CODEC =
		StreamCodec.unit(new ReloadKeybindsPayload());

	@Override
	public Type<ReloadKeybindsPayload> type() {
		return TYPE;
	}
}
