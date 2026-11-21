package dev.scriptkit.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.scriptkit.network.GhostPayload.Slot;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** The Command Stick sends the army's formation to the client; make sure it survives the wire. */
class GhostPayloadTest {
	private static RegistryFriendlyByteBuf buffer() {
		return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
	}

	@Test
	void formationRoundTrips() {
		List<Slot> slots = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			UUID id = UUID.nameUUIDFromBytes(("n" + i).getBytes());
			slots.add(new Slot(i % 2 == 0 ? "" : "Notch", id.getMostSignificantBits(), id.getLeastSignificantBits(), i * 1.5F - 20, -i * 0.5F));
		}
		RegistryFriendlyByteBuf buf = buffer();
		GhostPayload.CODEC.encode(buf, new GhostPayload(slots));
		GhostPayload decoded = GhostPayload.CODEC.decode(buf);
		assertEquals(slots, decoded.slots());
		assertEquals(0, buf.readableBytes());
	}

	@Test
	void emptyListMeansClear() {
		RegistryFriendlyByteBuf buf = buffer();
		GhostPayload.CODEC.encode(buf, new GhostPayload(List.of()));
		assertTrue(GhostPayload.CODEC.decode(buf).slots().isEmpty());
	}

	@Test
	void hugeArmiesAreCutOffNotCrashed() {
		List<Slot> slots = new ArrayList<>();
		for (int i = 0; i < GhostPayload.MAX_SLOTS + 50; i++) {
			slots.add(new Slot("", 1L, i, 0F, 0F));
		}
		RegistryFriendlyByteBuf buf = buffer();
		GhostPayload.CODEC.encode(buf, new GhostPayload(slots));
		assertEquals(GhostPayload.MAX_SLOTS, GhostPayload.CODEC.decode(buf).slots().size());
	}

	@Test
	void slotIdRebuildsTheUuid() {
		UUID id = UUID.randomUUID();
		assertEquals(id, new Slot("", id.getMostSignificantBits(), id.getLeastSignificantBits(), 0, 0).id());
	}
}
