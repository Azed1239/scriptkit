package dev.scriptkit.ai;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One difficulty preset. Every field is a multiplier or probability on top of the global
 * combat.json settings, so presets can be edited or added in the "difficulties" section of
 * combat.json. NPCs store the *name* of their preset (saved in NBT), so different armies can have
 * different difficulties and a preset can be tuned without respawning anyone.
 */
public final class Difficulty {
	public static final String EASY = "easy";
	public static final String NORMAL = "normal";
	public static final String HARD = "hard";
	public static final String NIGHTMARE = "nightmare";

	/** Multiplies melee and arrow damage (on top of the global damageMultiplier). */
	public double damage = 1.0;
	/** Multiplies maximum health. */
	public double health = 1.0;
	/** Chance (0-1) a melee swing connects. Overrides the global meleeAccuracy. */
	public double meleeAccuracy = 0.75;
	/** Archer accuracy 0 (wild) to 1 (perfect). Overrides the global archerAccuracy. */
	public double archerAccuracy = 0.8;
	/** Chance (0-1) of a jump critical hit before a swing. Overrides the global critChance. */
	public double critChance = 0.35;
	/** Multiplies swing rate (1.2 = swings 20% more often). */
	public double attackSpeed = 1.0;
	/** Multiplies walking and sprinting speed. Keep near 1.0 to stay close to player speed. */
	public double moveSpeed = 1.0;
	/** Chance (0-1) the NPC raises its shield when it should. 0 = never blocks. */
	public double blockSkill = 0.7;
	/** Multiplies how fast NPCs notice enemies (retarget interval is divided by this) . */
	public double reaction = 1.0;
	/** Multiplies the detection range. */
	public double range = 1.0;

	public static Map<String, Difficulty> defaults() {
		Map<String, Difficulty> m = new LinkedHashMap<>();
		m.put(EASY, make(0.6, 0.8, 0.45, 0.5, 0.10, 0.8, 1.0, 0.3, 0.5, 0.8));
		m.put(NORMAL, make(1.0, 1.0, 0.75, 0.8, 0.35, 1.0, 1.0, 0.7, 1.0, 1.0));
		m.put(HARD, make(1.6, 1.3, 0.9, 0.92, 0.5, 1.15, 1.05, 0.9, 1.5, 1.2));
		m.put(NIGHTMARE, make(3.0, 2.0, 1.0, 1.0, 0.7, 1.3, 1.1, 1.0, 2.5, 1.5));
		return m;
	}

	private static Difficulty make(double damage, double health, double melee, double archer, double crit,
		double attackSpeed, double moveSpeed, double block, double reaction, double range) {
		Difficulty d = new Difficulty();
		d.damage = damage;
		d.health = health;
		d.meleeAccuracy = melee;
		d.archerAccuracy = archer;
		d.critChance = crit;
		d.attackSpeed = attackSpeed;
		d.moveSpeed = moveSpeed;
		d.blockSkill = block;
		d.reaction = reaction;
		d.range = range;
		return d;
	}

	/** Clamps every value into a sane range so a typo in combat.json can't break fights. */
	public void sanitize() {
		damage = clamp(damage, 0.0, 20.0);
		health = clamp(health, 0.1, 20.0);
		meleeAccuracy = clamp(meleeAccuracy, 0.0, 1.0);
		archerAccuracy = clamp(archerAccuracy, 0.0, 1.0);
		critChance = clamp(critChance, 0.0, 1.0);
		attackSpeed = clamp(attackSpeed, 0.25, 4.0);
		moveSpeed = clamp(moveSpeed, 0.5, 1.5);
		blockSkill = clamp(blockSkill, 0.0, 1.0);
		reaction = clamp(reaction, 0.25, 8.0);
		range = clamp(range, 0.25, 4.0);
	}

	private static double clamp(double v, double lo, double hi) {
		return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
	}

	/** One-line summary for /army difficulty. */
	public String summary() {
		return String.format("damage x%.2f, health x%.2f, melee accuracy %.0f%%, archer accuracy %.0f%%, crits %.0f%%, swing rate x%.2f, speed x%.2f, blocks %.0f%%, reaction x%.2f, range x%.2f",
			damage, health, meleeAccuracy * 100, archerAccuracy * 100, critChance * 100, attackSpeed, moveSpeed, blockSkill * 100, reaction, range);
	}
}
