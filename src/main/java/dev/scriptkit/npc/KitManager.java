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
import java.util.Set;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Gear kits. Built-in kits (knight, archer, god, none) are always available; config/scriptkit/kits.json
 * can override them or add more, and the in-game menu edits that file. Each slot is either an item id
 * string or {@code {"id": "...", "enchantments": {"minecraft:sharpness": 5}}}.
 */
public final class KitManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final String[] SLOT_KEYS = {"head", "chest", "legs", "feet", "mainhand", "offhand"};
	private static final EquipmentSlot[] SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
		EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
	};
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_\\-]{1,24}");

	public record KitItem(String id, Map<String, Integer> enchantments) {}

	/** Built-in kits, parsed once. */
	private static Map<String, Map<String, KitItem>> builtIns;
	/** Kits as stored in kits.json (overrides and custom kits). */
	private static Map<String, Map<String, KitItem>> userKits = new LinkedHashMap<>();
	/** Everything in effect: built-ins overridden by the file. */
	private static volatile Map<String, Map<String, KitItem>> kits = Collections.emptyMap();

	private KitManager() {}

	public static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("scriptkit").resolve("kits.json");
	}

	private static synchronized Map<String, Map<String, KitItem>> builtIns() {
		if (builtIns == null) {
			builtIns = parseAll(defaults());
		}
		return builtIns;
	}

	public static synchronized void load() {
		Path path = configPath();
		Map<String, Map<String, KitItem>> fromFile = new LinkedHashMap<>();
		try {
			if (!Files.exists(path)) {
				Files.createDirectories(path.getParent());
				try (Writer w = Files.newBufferedWriter(path)) {
					GSON.toJson(defaults(), w);
				}
				ScriptKitMod.LOGGER.info("Wrote default kits to {}", path);
			}
			try (Reader r = Files.newBufferedReader(path)) {
				JsonObject root = GSON.fromJson(r, JsonObject.class);
				if (root != null) {
					fromFile.putAll(parseAll(root));
				}
			}
		} catch (IOException | RuntimeException e) {
			ScriptKitMod.LOGGER.error("Failed to load kits from {}; using built-in kits only", path, e);
			fromFile.clear();
		}
		userKits = fromFile;
		rebuild();
		ScriptKitMod.LOGGER.info("Loaded {} kits: {}", kits.size(), kits.keySet());
	}

	private static void rebuild() {
		Map<String, Map<String, KitItem>> merged = new LinkedHashMap<>(builtIns());
		merged.putAll(userKits);
		kits = merged;
	}

	// ------------------------------------------------------------------ editing (used by the menu)

	public static Map<String, Map<String, KitItem>> definitions() {
		return Collections.unmodifiableMap(kits);
	}

	public static Set<String> builtInNames() {
		return Collections.unmodifiableSet(builtIns().keySet());
	}

	public static boolean isValidName(String name) {
		return name != null && NAME.matcher(name).matches();
	}

	/**
	 * Checks a kit definition: known slots, existing items, existing enchantments, levels 1-255.
	 * Returns an error message, or null if it is fine.
	 */
	public static String validate(Map<String, KitItem> slots, Registry<Enchantment> enchantments) {
		for (Map.Entry<String, KitItem> e : slots.entrySet()) {
			if (!java.util.Arrays.asList(SLOT_KEYS).contains(e.getKey())) {
				return "Unknown slot '" + e.getKey() + "'.";
			}
			KitItem item = e.getValue();
			Identifier id = Identifier.tryParse(item.id());
			Item it = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
			if (it == null || it == Items.AIR) {
				return "Unknown item '" + item.id() + "'.";
			}
			for (Map.Entry<String, Integer> en : item.enchantments().entrySet()) {
				Identifier eid = Identifier.tryParse(en.getKey());
				if (eid == null || enchantments.get(ResourceKey.create(Registries.ENCHANTMENT, eid)).isEmpty()) {
					return "Unknown enchantment '" + en.getKey() + "'.";
				}
				if (en.getValue() < 1 || en.getValue() > 255) {
					return "Enchantment level for '" + en.getKey() + "' must be 1-255.";
				}
			}
		}
		return null;
	}

	/** Saves (or overrides) a kit and writes kits.json. Returns an error message or null. */
	public static synchronized String save(String name, Map<String, KitItem> slots) {
		if (!isValidName(name)) {
			return "Kit names use letters, digits, - and _ (1-24 characters).";
		}
		if (name.equalsIgnoreCase("none")) {
			return "The 'none' kit is always empty and can't be changed.";
		}
		userKits.put(name.toLowerCase(), new LinkedHashMap<>(slots));
		rebuild();
		return writeUserFile();
	}

	/** Deletes a custom kit, or reverts a built-in kit to its default. Returns an error message or null. */
	public static synchronized String delete(String name) {
		String key = name.toLowerCase();
		if (!userKits.containsKey(key) && !kits.containsKey(key)) {
			return "No such kit.";
		}
		if (key.equals("none")) {
			return "The 'none' kit can't be deleted.";
		}
		userKits.remove(key);
		rebuild();
		return writeUserFile();
	}

	/** Writes only kits that differ from the built-ins, so the file stays small and built-in updates still apply. */
	private static String writeUserFile() {
		JsonObject root = new JsonObject();
		for (Map.Entry<String, Map<String, KitItem>> e : userKits.entrySet()) {
			if (!e.getValue().equals(builtIns().get(e.getKey()))) {
				root.add(e.getKey(), toJson(e.getValue()));
			}
		}
		Path path = configPath();
		try {
			Files.createDirectories(path.getParent());
			try (Writer w = Files.newBufferedWriter(path)) {
				GSON.toJson(root, w);
			}
			return null;
		} catch (IOException e) {
			ScriptKitMod.LOGGER.error("Failed to write {}", path, e);
			return "Could not write kits.json (see the server log).";
		}
	}

	public static JsonObject toJson(Map<String, KitItem> slots) {
		JsonObject o = new JsonObject();
		for (String key : SLOT_KEYS) {
			KitItem item = slots.get(key);
			if (item == null) {
				continue;
			}
			if (item.enchantments().isEmpty()) {
				o.addProperty(key, item.id());
			} else {
				JsonObject io = new JsonObject();
				io.addProperty("id", item.id());
				JsonObject en = new JsonObject();
				item.enchantments().forEach(en::addProperty);
				io.add("enchantments", en);
				o.add(key, io);
			}
		}
		return o;
	}

	public static Map<String, KitItem> slotsFromJson(JsonObject o) {
		Map<String, KitItem> slots = new LinkedHashMap<>();
		for (String key : SLOT_KEYS) {
			KitItem item = parseItem(o.get(key));
			if (item != null) {
				slots.put(key, item);
			}
		}
		return slots;
	}

	// ------------------------------------------------------------------ parsing and applying

	private static Map<String, Map<String, KitItem>> parseAll(JsonObject root) {
		Map<String, Map<String, KitItem>> result = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : root.entrySet()) {
			if (!e.getValue().isJsonObject()) {
				ScriptKitMod.LOGGER.warn("Kit '{}' is not an object, skipping", e.getKey());
				continue;
			}
			result.put(e.getKey().toLowerCase(), slotsFromJson(e.getValue().getAsJsonObject()));
		}
		return result;
	}

	private static KitItem parseItem(JsonElement v) {
		if (v == null || v.isJsonNull()) {
			return null;
		}
		if (v.isJsonPrimitive()) {
			String id = v.getAsString();
			return id.isBlank() ? null : new KitItem(id, Map.of());
		}
		if (v.isJsonObject() && v.getAsJsonObject().has("id")) {
			JsonObject o = v.getAsJsonObject();
			Map<String, Integer> enchants = new LinkedHashMap<>();
			if (o.has("enchantments") && o.get("enchantments").isJsonObject()) {
				for (Map.Entry<String, JsonElement> en : o.getAsJsonObject("enchantments").entrySet()) {
					try {
						enchants.put(en.getKey(), en.getValue().getAsInt());
					} catch (RuntimeException ignored) {
						ScriptKitMod.LOGGER.warn("Bad enchantment level for '{}', ignoring", en.getKey());
					}
				}
			}
			return new KitItem(o.get("id").getAsString(), enchants);
		}
		return null;
	}

	public static boolean exists(String kit) {
		return kits.containsKey(kit.toLowerCase());
	}

	public static Iterable<String> names() {
		return kits.keySet();
	}

	/**
	 * Equips every slot from the kit (slots the kit omits are cleared). Unknown items or enchantments
	 * are skipped with a warning. Every slot drops on death (when kit drops are enabled), like a player.
	 */
	public static void apply(Mob mob, String kitName) {
		Map<String, KitItem> kit = kits.get(kitName.toLowerCase());
		if (kit == null) {
			ScriptKitMod.LOGGER.warn("Unknown kit '{}'", kitName);
			return;
		}
		Registry<Enchantment> enchantments = mob.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		for (int i = 0; i < SLOTS.length; i++) {
			mob.setItemSlot(SLOTS[i], stack(kit.get(SLOT_KEYS[i]), enchantments));
			mob.setDropChance(SLOTS[i], 1.0F);
		}
	}

	private static ItemStack stack(KitItem item, Registry<Enchantment> enchantments) {
		if (item == null) {
			return ItemStack.EMPTY;
		}
		Identifier parsed = Identifier.tryParse(item.id());
		Item it = parsed == null ? null : BuiltInRegistries.ITEM.getOptional(parsed).orElse(null);
		if (it == null || it == Items.AIR) {
			ScriptKitMod.LOGGER.warn("Unknown item '{}' in kit, ignoring", item.id());
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(it);
		for (Map.Entry<String, Integer> e : item.enchantments().entrySet()) {
			Identifier eid = Identifier.tryParse(e.getKey());
			Holder<Enchantment> holder = eid == null ? null
				: enchantments.get(ResourceKey.create(Registries.ENCHANTMENT, eid)).orElse(null);
			if (holder == null) {
				ScriptKitMod.LOGGER.warn("Unknown enchantment '{}' in kit, ignoring", e.getKey());
				continue;
			}
			stack.enchant(holder, Math.max(1, e.getValue()));
		}
		return stack;
	}

	// ------------------------------------------------------------------ built-in kits

	private static JsonObject defaults() {
		JsonObject d = new JsonObject();

		JsonObject knight = new JsonObject();
		knight.addProperty("head", "minecraft:iron_helmet");
		knight.addProperty("chest", "minecraft:iron_chestplate");
		knight.addProperty("legs", "minecraft:iron_leggings");
		knight.addProperty("feet", "minecraft:iron_boots");
		knight.addProperty("mainhand", "minecraft:iron_sword");
		knight.addProperty("offhand", "minecraft:shield");
		d.add("knight", knight);

		JsonObject archer = new JsonObject();
		archer.addProperty("head", "minecraft:leather_helmet");
		archer.addProperty("chest", "minecraft:leather_chestplate");
		archer.addProperty("legs", "minecraft:leather_leggings");
		archer.addProperty("feet", "minecraft:leather_boots");
		archer.addProperty("mainhand", "minecraft:bow");
		d.add("archer", archer);

		// Maxed-out netherite.
		JsonObject god = new JsonObject();
		god.add("head", item("minecraft:netherite_helmet",
			"protection", 4, "unbreaking", 3, "mending", 1, "respiration", 3, "aqua_affinity", 1));
		god.add("chest", item("minecraft:netherite_chestplate",
			"protection", 4, "unbreaking", 3, "mending", 1));
		god.add("legs", item("minecraft:netherite_leggings",
			"protection", 4, "unbreaking", 3, "mending", 1, "swift_sneak", 3));
		god.add("feet", item("minecraft:netherite_boots",
			"protection", 4, "feather_falling", 4, "depth_strider", 3, "soul_speed", 3, "unbreaking", 3, "mending", 1));
		god.add("mainhand", item("minecraft:netherite_sword",
			"sharpness", 5, "fire_aspect", 2, "looting", 3, "sweeping_edge", 3, "unbreaking", 3, "mending", 1));
		god.add("offhand", item("minecraft:shield", "unbreaking", 3, "mending", 1));
		d.add("god", god);

		d.add("none", new JsonObject());
		return d;
	}

	private static JsonObject item(String id, Object... enchantPairs) {
		JsonObject o = new JsonObject();
		o.addProperty("id", id);
		JsonObject en = new JsonObject();
		for (int i = 0; i + 1 < enchantPairs.length; i += 2) {
			en.addProperty("minecraft:" + enchantPairs[i], (Integer) enchantPairs[i + 1]);
		}
		o.add("enchantments", en);
		return o;
	}
}
