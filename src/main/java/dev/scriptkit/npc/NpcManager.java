package dev.scriptkit.npc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.EntityTypeTest;

/** Lookup helpers over all currently loaded NPCs on a server. */
public final class NpcManager {
	private NpcManager() {}

	public static List<ScriptNpc> all(MinecraftServer server) {
		List<ScriptNpc> result = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			result.addAll(level.getEntities(EntityTypeTest.forClass(ScriptNpc.class), npc -> true));
		}
		return result;
	}

	public static List<ScriptNpc> named(MinecraftServer server, String name) {
		return all(server).stream().filter(npc -> npc.getNpcName().equalsIgnoreCase(name)).toList();
	}

	public static List<String> names(MinecraftServer server) {
		return all(server).stream().map(ScriptNpc::getNpcName).distinct().sorted().toList();
	}
}
