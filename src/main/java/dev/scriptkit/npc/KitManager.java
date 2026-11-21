package dev.scriptkit.npc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.scriptkit.ScriptKitMod;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Loads gear kits from config/scriptkit/kits.json (writing defaults if missing) and applies them. */
public final class KitManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String[] SLOT_KEYS = {"head", "chest", "legs", "feet", "mainhand", "offhand"};
	private static final EquipmentSlot[] SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
		EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
	};

	/** kit name (lowercase) -> slot key -> item id. */
	private static volatile Map<String, Map<String, String>> kits = Collections.emptyMap();

	private KitManager() {}

	public static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("scriptkit").resolve("kits.json");
	}

	public static void load() {
		Path path = configPath();
		try {
			if (!Files.exists(path)) {
				Files.createDirectories(path.getParent());
				try (Writer w = Files.newBufferedWriter(path)) {
					GSON.toJson(defaults(), w);
				}
				ScriptKitMod.LOGGER.info("Wrote default kits to {}", path);
			}
			Map<String, Map<String, String>> loaded = new LinkedHashMap<>();
			try (Reader r = Files.newBufferedReader(path)) {
				JsonObject root = GSON.fromJson(r, JsonObject.class);
				if (root != null) {
					for (Map.Entry<String, JsonElement> e : root.entrySet()) {
						if (!e.getValue().isJsonObject()) {
							ScriptKitMod.LOGGER.warn("Kit '{}' is not an object, skipping", e.getKey());
							continue;
						}
						Map<String, String> slots = new LinkedHashMap<>();
						for (String key : SLOT_KEYS) {
							JsonElement v = e.getValue().getAsJsonObject().get(key);
							if (v != null && v.isJsonPrimitive() && !v.getAsString().isBlank()) {
								slots.put(key, v.getAsString());
							}
						}
						loaded.put(e.getKey().toLowerCase(), slots);
					}
				}
			}
			loaded.putIfAbsent("none", Map.of());
			kits = loaded;
			ScriptKitMod.LOGGER.info("Loaded {} kits: {}", loaded.size(), loaded.keySet());
		} catch (IOException | RuntimeException e) {
			ScriptKitMod.LOGGER.error("Failed to load kits from {}; using built-in defaults", path, e);
			kits = defaults();
		}
	}

	public static boolean exists(String kit) {
		return kits.containsKey(kit.toLowerCase());
	}

	public static Iterable<String> names() {
		return kits.keySet();
	}

	/** Equips every slot from the kit (slots the kit omits are cleared). Unknown items are skipped with a warning. */
	public static void apply(Mob mob, String kitName) {
		Map<String, String> kit = kits.get(kitName.toLowerCase());
		if (kit == null) {
			ScriptKitMod.LOGGER.warn("Unknown kit '{}'", kitName);
			return;
		}
		for (int i = 0; i < SLOTS.length; i++) {
			mob.setItemSlot(SLOTS[i], stack(kit.get(SLOT_KEYS[i])));
			// Vanilla default is ~8.5% per slot; NPCs drop everything on death, like a player.
			mob.setDropChance(SLOTS[i], 1.0F);
		}
	}

	private static ItemStack stack(String id) {
		if (id == null) {
			return ItemStack.EMPTY;
		}
		Identifier parsed = Identifier.tryParse(id);
		Item item = parsed == null ? null : BuiltInRegistries.ITEM.getOptional(parsed).orElse(null);
		if (item == null || item == Items.AIR) {
			ScriptKitMod.LOGGER.warn("Unknown item '{}' in kit, ignoring", id);
			return ItemStack.EMPTY;
		}
		return new ItemStack(item);
	}

	private static Map<String, Map<String, String>> defaults() {
		Map<String, Map<String, String>> d = new LinkedHashMap<>();
		Map<String, String> knight = new LinkedHashMap<>();
		knight.put("head", "minecraft:iron_helmet");
		knight.put("chest", "minecraft:iron_chestplate");
		knight.put("legs", "minecraft:iron_leggings");
		knight.put("feet", "minecraft:iron_boots");
		knight.put("mainhand", "minecraft:iron_sword");
		knight.put("offhand", "minecraft:shield");
		d.put("knight", knight);
		Map<String, String> archer = new LinkedHashMap<>();
		archer.put("head", "minecraft:leather_helmet");
		archer.put("chest", "minecraft:leather_chestplate");
		archer.put("legs", "minecraft:leather_leggings");
		archer.put("feet", "minecraft:leather_boots");
		archer.put("mainhand", "minecraft:bow");
		d.put("archer", archer);
		d.put("none", new LinkedHashMap<>());
		return d;
	}
}
