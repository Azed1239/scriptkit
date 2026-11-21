package dev.scriptkit.network;

import dev.scriptkit.ScriptKitMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> server: "do this to an army". The server treats every field as untrusted input: the
 * action is parsed leniently, the team is validated, and permissions are checked per packet.
 */
public record ArmyActionPayload(String action, String team) implements CustomPacketPayload {
	public static final Type<ArmyActionPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath(ScriptKitMod.MOD_ID, "army_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ArmyActionPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(32), ArmyActionPayload::action,
		ByteBufCodecs.stringUtf8(64), ArmyActionPayload::team,
		ArmyActionPayload::new);

	public static ArmyActionPayload of(ArmyAction action, String team) {
		return new ArmyActionPayload(action.id(), team == null ? "" : team);
	}

	@Override
	public Type<ArmyActionPayload> type() {
		return TYPE;
	}
}
