package dev.scriptkit.network;

import dev.scriptkit.ScriptKitMod;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> client: the formation of the army selected with the Command Stick, so the client can draw
 * ghost copies of it where you are aiming. An empty list means "no selection: remove the ghosts".
 * Each slot is one NPC: its skin, the UUID that picks its default skin, and its offset from the army's centre.
 */
public record GhostPayload(List<Slot> slots) implements CustomPacketPayload {
	public record Slot(String skin, long idMost, long idLeast, float dx, float dz) {
		public UUID id() {
			return new UUID(idMost, idLeast);
		}
	}

	/** Armies larger than this are cut off (the stick is for scenes, not for hundreds of NPCs). */
	public static final int MAX_SLOTS = 256;

	public static final Type<GhostPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ScriptKitMod.MOD_ID, "ghost"));
	public static final StreamCodec<RegistryFriendlyByteBuf, GhostPayload> CODEC = StreamCodec.of(GhostPayload::write, GhostPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, GhostPayload payload) {
		int n = Math.min(MAX_SLOTS, payload.slots().size());
		buf.writeVarInt(n);
		for (int i = 0; i < n; i++) {
			Slot s = payload.slots().get(i);
			buf.writeUtf(s.skin(), 16);
			buf.writeLong(s.idMost());
			buf.writeLong(s.idLeast());
			buf.writeFloat(s.dx());
			buf.writeFloat(s.dz());
		}
	}

	private static GhostPayload read(RegistryFriendlyByteBuf buf) {
		int n = Math.min(MAX_SLOTS, buf.readVarInt());
		List<Slot> slots = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			slots.add(new Slot(buf.readUtf(16), buf.readLong(), buf.readLong(), buf.readFloat(), buf.readFloat()));
		}
		return new GhostPayload(slots);
	}

	@Override
	public Type<GhostPayload> type() {
		return TYPE;
	}
}
