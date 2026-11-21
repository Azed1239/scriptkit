package dev.scriptkit.ai;

import dev.scriptkit.npc.ScriptNpc;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
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
		countdown = cfg.retargetIntervalTicks;
		LivingEntity best = findNearest(cfg);
		if (best == null || best == current) {
			return;
		}
		if (current == null || npc.distanceTo(best) + SWITCH_MARGIN < npc.distanceTo(current)) {
			npc.setTarget(best);
		}
	}

	private LivingEntity findNearest(CombatConfig cfg) {
		if (!(npc.level() instanceof ServerLevel level)) {
			return null;
		}
		List<LivingEntity> candidates = TargetCache.candidates(level);
		LivingEntity best = null;
		double bestSq = Double.MAX_VALUE;
		for (LivingEntity e : candidates) {
			double d = npc.distanceToSqr(e);
			if (d < bestSq && isValidTarget(npc, e, cfg)) {
				best = e;
				bestSq = d;
			}
		}
		return best;
	}

	/** Enemy = alive, on a different scoreboard team, in range, not a spectator/creative player. */
	public static boolean isValidTarget(ScriptNpc npc, LivingEntity e, CombatConfig cfg) {
		if (e == npc || !e.isAlive() || e.isRemoved() || e.level() != npc.level()) {
			return false;
		}
		if (npc.getTeam() == null || e.getTeam() == null || npc.isAlliedTo(e)) {
			return false;
		}
		if (e instanceof Player p && (p.isSpectator() || p.isCreative() || !cfg.npcsCanHurtPlayers)) {
			return false;
		}
		if (npc.isIgnoring(e)) {
			return false;
		}
		return npc.distanceToSqr(e) <= cfg.detectionRange * cfg.detectionRange;
	}
}
