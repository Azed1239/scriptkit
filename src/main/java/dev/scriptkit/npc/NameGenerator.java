package dev.scriptkit.npc;

import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Procedural player-style names ("PingAbuse", "BanThis", "SwordPro", "xXDarkLordXx", "Frostbite99").
 * Always 3-16 characters of letters, digits and underscores, like real Minecraft usernames, so they
 * work as command arguments. Pure Java so it can be unit-tested.
 */
public final class NameGenerator {
	private static final int MAX_LENGTH = 16;

	/** First words: attitudes, gamer slang, themes. */
	private static final List<String> HEADS = List.of(
		"Ping", "Ban", "Sword", "Noob", "Pro", "Dragon", "Frost", "Shadow", "Lag", "Try", "Crit", "Loot",
		"Raid", "Creeper", "Zombie", "Pixel", "Block", "Diamond", "Nether", "Ender", "Redstone", "Slayer",
		"Dark", "Toxic", "Epic", "Mega", "Ultra", "Super", "Hyper", "Rage", "Salty", "Sweaty", "Clutch",
		"Lucky", "Sneaky", "Silent", "Savage", "Tiny", "Giant", "Cursed", "Blazing", "Frozen", "Golden",
		"Iron", "Obsidian", "Bedrock", "Wither", "Skeleton", "Piglin", "Phantom", "Warden", "Steve",
		"Alex", "Lava", "Water", "Fire", "Ice", "Storm", "Thunder", "Night", "Moon", "Sun", "Star",
		"Void", "Chaos", "Turbo", "Rusty", "Angry", "Happy", "Crazy", "Lazy", "Fast", "Slow", "Big",
		"Little", "Evil", "Holy", "Rogue", "Ninja", "Pirate", "Viking", "Knight", "Archer", "Wizard",
		"Spawn", "Camp", "Grind", "Farm", "Mine", "Craft", "Build", "Parkour", "Speed", "Hard", "Soft");

	/** Second words: things, roles, verbs. */
	private static final List<String> TAILS = List.of(
		"Abuse", "This", "Pro", "Slayer", "Hunter", "Master", "King", "Lord", "Gamer", "Lover", "Fan",
		"God", "Boss", "Noob", "Sweat", "Camper", "Killer", "Smasher", "Crusher", "Breaker", "Walker",
		"Runner", "Rider", "Dodger", "Blocker", "Spammer", "Farmer", "Miner", "Builder", "Crafter",
		"Warrior", "Wizard", "Ninja", "Pirate", "Knight", "Archer", "Sniper", "Tank", "Healer",
		"Bomber", "Trapper", "Looter", "Raider", "Diver", "Jumper", "Climber", "Stalker", "Ghost",
		"Demon", "Angel", "Reaper", "Beast", "Wolf", "Fox", "Bear", "Tiger", "Shark", "Eagle", "Snake",
		"Spider", "Creeper", "Zombie", "Golem", "Villager", "Sword", "Axe", "Bow", "Shield", "Blade",
		"Pickaxe", "Armor", "Potion", "Block", "Pixel", "Flame", "Frost", "Storm", "Shadow", "Light",
		"Dust", "Rage", "Fury", "Wrath", "Mode", "Time", "Life", "Zone", "Craft", "Cube", "Bro", "Guy");

	/** Standalone names. */
	private static final List<String> SOLO = List.of(
		"Swordpro", "BanThis", "PingAbuse", "NoobSlayer", "xXDarkXx", "LagSwitch", "TryHard", "Clutchgod",
		"Sweatlord", "CreeperAwMan", "DiamondDan", "ObsidianOwl", "NetherNerd", "EndermanFan", "BlockHead",
		"CraftyCat", "PixelPete", "RedstoneRay", "IronIvy", "GoldenGus", "LavaLarry", "WaterWalker",
		"SneakyPete", "SilentSam", "SavageSally", "CrazyCraft", "LuckyLuke", "TinyTim", "GiantGary",
		"Spawnkiller", "Raidbreaker", "Lootgoblin", "Critmaster", "Parkourpro", "Speedrunner", "Fallguy",
		"Griefer", "Pvpking", "Bedwarsbro", "Skywarsgod", "Minemaster", "Cobblegod", "Stonks", "Dirtlord");

	private static final List<String> SUFFIXES = List.of("99", "123", "69", "420", "007", "2012", "XD", "HD", "YT", "TTV", "_", "xx");

	private NameGenerator() {}

	/**
	 * A name for which {@code taken} is false. Falls back to adding digits if the lists run dry, so it
	 * always terminates with a free name.
	 */
	public static String generate(Random rnd, Predicate<String> taken) {
		for (int attempt = 0; attempt < 60; attempt++) {
			String name = candidate(rnd);
			if (valid(name) && !taken.test(name)) {
				return name;
			}
		}
		for (int n = 2; n < 100_000; n++) {
			String base = candidate(rnd);
			String name = base.length() > MAX_LENGTH - String.valueOf(n).length()
				? base.substring(0, MAX_LENGTH - String.valueOf(n).length()) + n
				: base + n;
			if (valid(name) && !taken.test(name)) {
				return name;
			}
		}
		return "Player" + rnd.nextInt(1_000_000);
	}

	static boolean valid(String name) {
		return name.length() >= 3 && name.length() <= MAX_LENGTH && name.matches("[A-Za-z0-9_]+");
	}

	private static String candidate(Random rnd) {
		int style = rnd.nextInt(100);
		String head = pick(rnd, HEADS);
		String tail = pick(rnd, TAILS);
		while (tail.equals(head)) {
			tail = pick(rnd, TAILS); // never "ArcherArcher"
		}
		if (style < 12) {
			return pick(rnd, SOLO);
		}
		if (style < 50) {
			return head + tail;
		}
		if (style < 66) {
			return head + tail + pick(rnd, SUFFIXES);
		}
		if (style < 74) {
			return head + "_" + tail;
		}
		if (style < 82) {
			return "xX" + head + tail + "Xx";
		}
		if (style < 90) {
			return leet(head + tail, rnd);
		}
		if (style < 95) {
			return head.toLowerCase() + tail.toLowerCase();
		}
		return head + tail + (1 + rnd.nextInt(99));
	}

	/** Swaps a couple of letters for lookalike digits (E->3, A->4, O->0, I->1). */
	private static String leet(String s, Random rnd) {
		StringBuilder sb = new StringBuilder(s);
		int swaps = 0;
		for (int i = 0; i < sb.length() && swaps < 2; i++) {
			char swapped = switch (Character.toLowerCase(sb.charAt(i))) {
				case 'e' -> '3';
				case 'a' -> '4';
				case 'o' -> '0';
				case 'i' -> '1';
				default -> 0;
			};
			if (swapped != 0 && rnd.nextInt(3) != 0) {
				sb.setCharAt(i, swapped);
				swaps++;
			}
		}
		return sb.toString();
	}

	private static String pick(Random rnd, List<String> list) {
		return list.get(rnd.nextInt(list.size()));
	}
}
