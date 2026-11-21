package dev.scriptkit.ai;

import dev.scriptkit.npc.ScriptNpc;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Picks the nearest valid enemy from {@link TargetCache}. Re-evaluates every
 * {@code retargetIntervalTicks}, with each NPC offset by its entity id so a large army spreads its
 * scans over the interval instead of all scanning on the same tick. A current target that dies,
 * leaves range or becomes invalid is dropped immediately.
 */
public class NpcTargetGoal extends Goal {
	/** A new target must be this much closer (blocks) to replace the current one, to avoid flip-flopping. */
	private static final double SWITCH_MARGIN = 2.0;

	private final ScriptNpc npc;
	private int countdown;

	public NpcTargetGoal(ScriptNpc npc) {
		this.npc = npc;
		this.setFlags(EnumSet.of(Goal.Flag.TARGET));
		this.countdown = Math.floorMod(npc.getId(), Math.max(1, CombatConfig.get().retargetIntervalTicks));
	}

	@Override
	public boolean canUse() {
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return true;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void stop() {
		npc.setTarget(null);
	}

	@Override
	public void tick() {
		CombatConfig cfg = CombatConfig.get();
		LivingEntity current = npc.getTarget();
		boolean rescan = false;
		if (current != null && !isValidTarget(npc, current, cfg)) {
			npc.setTarget(null);
			current = null;
			rescan = true;
		}
		if (--countdown > 0 && !rescan) {
			return;
		}
		countdown = Math.max(2, (int) Math.round(cfg.retargetIntervalTicks / npc.difficulty().reaction));
		LivingEntity best = findNearest(cfg);
		if (best == null || best == current) {
			return;
		}
		// Players and enemy NPCs always come before hostile mobs: never swap an enemy for a mob, and
		// drop a mob as soon as an enemy shows up, however far away it is.
		if (current != null && !isHostileMob(current, cfg) && isHostileMob(best, cfg)) {
			return;
		}
		if (current == null || (isHostileMob(current, cfg) && !isHostileMob(best, cfg))
			|| npc.distanceTo(best) + SWITCH_MARGIN < npc.distanceTo(current)) {
			npc.setTarget(best);
		}
	}

	/** A hostile mob (zombie, creeper, ...) that NPCs only fight when nothing better is around. */
	static boolean isHostileMob(LivingEntity e, CombatConfig cfg) {
		return cfg.npcsFightHostileMobs && e instanceof Enemy && !(e instanceof Player);
	}

	private LivingEntity findNearest(CombatConfig cfg) {
		if (!(npc.level() instanceof ServerLevel level)) {
			return null;
		}
		List<LivingEntity> candidates = TargetCache.candidates(level);
		// Nearest player / enemy NPC first; the nearest hostile mob only if there is none.
		LivingEntity best = null;
		LivingEntity bestMob = null;
		double bestSq = Double.MAX_VALUE;
		double bestMobSq = Double.MAX_VALUE;
		for (LivingEntity e : candidates) {
			double d = npc.distanceToSqr(e);
			boolean mob = isHostileMob(e, cfg);
			if (d < (mob ? bestMobSq : bestSq) && isValidTarget(npc, e, cfg)) {
				if (mob) {
					bestMob = e;
					bestMobSq = d;
				} else {
					best = e;
					bestSq = d;
				}
			}
		}
		return best != null ? best : bestMob;
	}

	/** Why an entity is not a valid target (or {@link Reject#NONE} if it is). Cheap enough for the hot path. */
	public enum Reject {
		NONE(""),
		SELF("it is this NPC"),
		DEAD("dead or removed"),
		OTHER_DIMENSION("in a different dimension"),
		NPC_NO_TEAM("this NPC is not on any team"),
		TARGET_NO_TEAM("not on any team (join one with /team join)"),
		SAME_TEAM("on the same team"),
		SPECTATOR("in spectator mode"),
		CREATIVE("in creative mode (set targetCreativePlayers to true in combat.json to attack them)"),
		NO_HURT_PLAYERS("npcsCanHurtPlayers is off in combat.json"),
		IGNORED("unreachable a moment ago, ignored for a few seconds"),
		TOO_FAR("out of detection range");

		public final String reason;

		Reject(String reason) {
			this.reason = reason;
		}
	}

	/** Enemy = alive, on a different scoreboard team, in range, not a spectator (or creative) player. */
	public static boolean isValidTarget(ScriptNpc npc, LivingEntity e, CombatConfig cfg) {
		return check(npc, e, cfg) == Reject.NONE;
	}

	public static Reject check(ScriptNpc npc, LivingEntity e, CombatConfig cfg) {
		if (e == npc) {
			return Reject.SELF;
		}
		if (!e.isAlive() || e.isRemoved()) {
			return Reject.DEAD;
		}
		if (e.level() != npc.level()) {
			return Reject.OTHER_DIMENSION;
		}
		// Hostile mobs are enemies of every NPC, on any team (or none), unless turned off in combat.json.
		boolean hostileMob = isHostileMob(e, cfg);
		if (!hostileMob) {
			if (npc.getTeam() == null) {
				return Reject.NPC_NO_TEAM;
			}
			if (e.getTeam() == null) {
				return Reject.TARGET_NO_TEAM;
			}
			if (npc.isAlliedTo(e)) {
				return Reject.SAME_TEAM;
			}
		}
		if (e instanceof Player p) {
			if (p.isSpectator()) {
				return Reject.SPECTATOR;
			}
			if (!cfg.npcsCanHurtPlayers) {
				return Reject.NO_HURT_PLAYERS;
			}
			if (p.isCreative() && !cfg.targetCreativePlayers) {
				return Reject.CREATIVE;
			}
		}
		if (npc.isIgnoring(e)) {
			return Reject.IGNORED;
		}
		double range = cfg.detectionRange * npc.difficulty().range;
		if (npc.distanceToSqr(e) > range * range) {
			return Reject.TOO_FAR;
		}
		return Reject.NONE;
	}
}
