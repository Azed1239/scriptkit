package dev.scriptkit.network;

import dev.scriptkit.ScriptKitMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> server: the player left-clicked with the Command Stick. Cycles the selected army's formation
 * shape, or its spacing if {@code spacing} (Shift held). The server re-checks permission and the held item.
 */
public record StickCyclePayload(boolean spacing) implements CustomPacketPayload {
	public static final Type<StickCyclePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ScriptKitMod.MOD_ID, "stick_cycle"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StickCyclePayload> CODEC = StreamCodec.of(
		(buf, p) -> buf.writeBoolean(p.spacing()), buf -> new StickCyclePayload(buf.readBoolean()));

	@Override
	public Type<StickCyclePayload> type() {
		return TYPE;
	}
}
