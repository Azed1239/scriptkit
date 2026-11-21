package dev.scriptkit.platform;

import dev.scriptkit.ScriptKitMod;
import dev.scriptkit.npc.ScriptNpc;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The only place that registers the NPC entity type. Registry/attribute calls are version-specific,
 * so a later Minecraft port should only need to touch this class.
 */
public final class NpcRegistry {
	public static final Identifier NPC_ID = Identifier.fromNamespaceAndPath(ScriptKitMod.MOD_ID, "npc");

	public static EntityType<ScriptNpc> NPC_TYPE;

	private NpcRegistry() {}

	public static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, NPC_ID);
		NPC_TYPE = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			key,
			EntityType.Builder.of(ScriptNpc::new, MobCategory.MISC)
				.sized(0.6F, 1.8F)
				.eyeHeight(1.62F)
				.clientTrackingRange(10)
				.build(key)
		);
		FabricDefaultAttributeRegistry.register(NPC_TYPE, ScriptNpc.createAttributes());
	}
}
