package dev.scriptkit;

import dev.scriptkit.ai.CombatConfig;
import dev.scriptkit.ai.HostileMobs;
import dev.scriptkit.command.ArmyCommand;
import dev.scriptkit.command.NpcCommand;
import dev.scriptkit.command.ScriptKitCommand;
import dev.scriptkit.network.ScriptKitNetworking;
import dev.scriptkit.config.ScriptKitConfig;
import dev.scriptkit.npc.BlockChangeLog;
import dev.scriptkit.npc.KitManager;
import dev.scriptkit.platform.NpcRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common (server + client) entrypoint. */
public class ScriptKitMod implements ModInitializer {
	public static final String MOD_ID = "scriptkit";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		NpcRegistry.register();
		HostileMobs.register();
		ScriptKitNetworking.register();
		ScriptKitConfig.load();
		CombatConfig.load();
		KitManager.load();
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			ScriptKitConfig.load();
			CombatConfig.load();
			KitManager.load();
		});
		ServerLifecycleEvents.SERVER_STARTED.register(BlockChangeLog::load);
		ServerLifecycleEvents.AFTER_SAVE.register((server, flush, force) -> BlockChangeLog.save(server));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			BlockChangeLog.save(server);
			BlockChangeLog.clear();
			ScriptKitNetworking.clearSession();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
			NpcCommand.register(dispatcher);
			ArmyCommand.register(dispatcher);
			ScriptKitCommand.register(dispatcher);
		});
		LOGGER.info("ScriptKit initialized (common)");
	}
}
