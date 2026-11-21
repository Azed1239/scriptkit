package dev.scriptkit.network;

import dev.scriptkit.ScriptKitMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Payloads for the in-game menu. Plain records so a later Minecraft port only touches the registration code. */
public final class MenuPayloads {
	private MenuPayloads() {}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(ScriptKitMod.MOD_ID, path);
	}

	/** Server -> client: open the menu, optionally on a tab (sent by /scriptkit menu and /scriptkit tutorial). */
	public record Open(String tab) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(id("menu_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(16), Open::tab,
			Open::new);

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/** Client -> server: send me the current presets, kits and teams. */
	public record Request() implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(id("menu_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.unit(new Request());

		@Override
		public Type<Request> type() {
			return TYPE;
		}
	}

	/** Client -> server: one edit ("saveDifficulty", "applyKit", ...) with a small JSON argument. Untrusted. */
	public record Edit(String op, String json) implements CustomPacketPayload {
		public static final Type<Edit> TYPE = new Type<>(id("menu_edit"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Edit> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(32), Edit::op,
			ByteBufCodecs.stringUtf8(16000), Edit::json,
			Edit::new);

		@Override
		public Type<Edit> type() {
			return TYPE;
		}
	}

	/** Server -> client: snapshot of everything the menu shows, plus the result message of the last edit. */
	public record State(String json) implements CustomPacketPayload {
		public static final Type<State> TYPE = new Type<>(id("menu_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(500_000), State::json,
			State::new);

		@Override
		public Type<State> type() {
			return TYPE;
		}
	}
}
