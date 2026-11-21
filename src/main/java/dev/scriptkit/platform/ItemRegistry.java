package dev.scriptkit.platform;

import dev.scriptkit.ScriptKitMod;
import dev.scriptkit.item.CommandStickItem;
import dev.scriptkit.npc.ScriptNpc;
import java.util.List;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;

/**
 * The only place that registers ScriptKit's items (registry calls are version-specific, so a later
 * Minecraft port should only need to touch this class).
 */
public final class ItemRegistry {
	public static final Identifier COMMAND_STICK_ID = Identifier.fromNamespaceAndPath(ScriptKitMod.MOD_ID, "command_stick");

	public static Item COMMAND_STICK;

	private ItemRegistry() {}

	public static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, COMMAND_STICK_ID);
		COMMAND_STICK = Registry.register(BuiltInRegistries.ITEM, key, new CommandStickItem(new Item.Properties()
			.setId(key)
			.stacksTo(1)
			.rarity(Rarity.EPIC)
			.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
			.component(DataComponents.LORE, new ItemLore(List.of(
				Component.literal("Right-click an NPC: select its army").withStyle(ChatFormatting.GRAY),
				Component.literal("Right-click the ground: move it there").withStyle(ChatFormatting.GRAY),
				Component.literal("Shift+right-click: deselect").withStyle(ChatFormatting.GRAY),
				Component.literal("Then press G to start the fight").withStyle(ChatFormatting.AQUA))))));

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.accept(COMMAND_STICK));

		// Left-clicking an NPC with the stick would hurt it; the stick only ever selects and moves.
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
			entity instanceof ScriptNpc && player.getItemInHand(hand).is(COMMAND_STICK) ? InteractionResult.FAIL : InteractionResult.PASS);
	}
}
