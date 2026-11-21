package dev.scriptkit.npc;

import dev.scriptkit.ScriptKitMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Remembers every block NPCs mine or place so {@code /army cleanup} can put the world back.
 * Only the first change to a position keeps its original state; later changes update what the NPC
 * left there. Saved to {@code <world>/data/scriptkit_block_changes.dat} so it survives restarts.
 */
public final class BlockChangeLog {
	private record Key(ResourceKey<Level> dimension, long pos) {}

	private record Change(BlockState original, BlockState left) {}

	private static final Map<Key, Change> CHANGES = new LinkedHashMap<>();
	private static boolean dirty;

	private BlockChangeLog() {}

	public static synchronized void record(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
		Key key = new Key(level.dimension(), pos.asLong());
		Change existing = CHANGES.get(key);
		CHANGES.put(key, new Change(existing == null ? before : existing.original(), after));
		dirty = true;
	}

	public static synchronized int size() {
		return CHANGES.size();
	}

	/**
	 * Restores every logged position whose current block is still what an NPC left there. Positions
	 * changed since (by a player, say) are skipped. Returns {restored, skipped}.
	 */
	public static synchronized int[] cleanup(MinecraftServer server) {
		int restored = 0;
		int skipped = 0;
		for (Map.Entry<Key, Change> e : CHANGES.entrySet()) {
			ServerLevel level = server.getLevel(e.getKey().dimension());
			if (level == null) {
				skipped++;
				continue;
			}
			BlockPos pos = BlockPos.of(e.getKey().pos());
			if (level.getBlockState(pos) == e.getValue().left()) {
				level.setBlock(pos, e.getValue().original(), Block.UPDATE_ALL);
				restored++;
			} else {
				skipped++;
			}
		}
		CHANGES.clear();
		dirty = true;
		return new int[] {restored, skipped};
	}

	private static Path file(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("scriptkit_block_changes.dat");
	}

	public static synchronized void load(MinecraftServer server) {
		CHANGES.clear();
		dirty = false;
		Path path = file(server);
		if (!Files.exists(path)) {
			return;
		}
		try {
			CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
			HolderGetter<Block> blocks = server.registryAccess().lookupOrThrow(Registries.BLOCK);
			root.getListOrEmpty("changes").compoundStream().forEach(tag -> {
				Identifier dim = Identifier.tryParse(tag.getStringOr("dim", ""));
				if (dim == null) {
					return;
				}
				CHANGES.put(new Key(ResourceKey.create(Registries.DIMENSION, dim), tag.getLongOr("pos", 0L)),
					new Change(NbtUtils.readBlockState(blocks, tag.getCompoundOrEmpty("original")),
						NbtUtils.readBlockState(blocks, tag.getCompoundOrEmpty("left"))));
			});
		} catch (IOException | RuntimeException e) {
			ScriptKitMod.LOGGER.error("Failed to read {}", path, e);
		}
	}

	public static synchronized void save(MinecraftServer server) {
		if (!dirty) {
			return;
		}
		Path path = file(server);
		try {
			ListTag list = new ListTag();
			for (Map.Entry<Key, Change> e : CHANGES.entrySet()) {
				CompoundTag tag = new CompoundTag();
				tag.putString("dim", e.getKey().dimension().identifier().toString());
				tag.putLong("pos", e.getKey().pos());
				tag.put("original", NbtUtils.writeBlockState(e.getValue().original()));
				tag.put("left", NbtUtils.writeBlockState(e.getValue().left()));
				list.add(tag);
			}
			CompoundTag root = new CompoundTag();
			root.put("changes", list);
			Files.createDirectories(path.getParent());
			NbtIo.writeCompressed(root, path);
			dirty = false;
		} catch (IOException | RuntimeException e) {
			ScriptKitMod.LOGGER.error("Failed to write {}", path, e);
		}
	}

	/** Called when the server stops so a different world doesn't see this one's log. */
	public static synchronized void clear() {
		CHANGES.clear();
		dirty = false;
	}

	/** Air, for callers that record a mined block. */
	public static BlockState air() {
		return Blocks.AIR.defaultBlockState();
	}
}
