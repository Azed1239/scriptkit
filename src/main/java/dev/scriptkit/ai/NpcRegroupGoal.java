package dev.scriptkit.ai;

import dev.scriptkit.npc.ScriptNpc;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * After a fight, with no target left, walk back to the formation spot the NPC was spawned at (its
 * "home"), so a victorious army re-forms instead of staying scattered where the fight ended. Far
 * trips are run; short ones walked. Gives up for a while if the way back is blocked.
 */
public class NpcRegroupGoal extends Goal {
	private static final double ARRIVED_SQ = 1.5 * 1.5;
	private static final double START_SQ = 3.0 * 3.0;
	private static final double RUN_SQ = 20.0 * 20.0;
	private static final int GIVE_UP_COOLDOWN = 200;

	private final ScriptNpc npc;
	private int retryCooldown;
	private int repathTicks;
	private int progressTicks;
	private double lastDistSq;

	public NpcRegroupGoal(ScriptNpc npc) {
		this.npc = npc;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	private Vec3 home() {
		return npc.getHome();
	}

	@Override
	public boolean canUse() {
		if (retryCooldown > 0) {
			retryCooldown--;
			return false;
		}
		return CombatConfig.get().regroupAfterFight && npc.getTarget() == null && npc.hasHome()
			&& npc.position().distanceToSqr(home()) > START_SQ;
	}

	@Override
	public boolean canContinueToUse() {
		return CombatConfig.get().regroupAfterFight && npc.getTarget() == null
			&& npc.position().distanceToSqr(home()) > ARRIVED_SQ;
	}

	@Override
	public void start() {
		repathTicks = 0;
		progressTicks = 0;
		lastDistSq = npc.position().distanceToSqr(home());
	}

	@Override
	public void stop() {
		npc.setSprinting(false);
		npc.getNavigation().stop();
	}

	@Override
	public void tick() {
		Vec3 home = home();
		double distSq = npc.position().distanceToSqr(home);
		boolean run = distSq > RUN_SQ;
		if (npc.isSprinting() != run) {
			npc.setSprinting(run);
		}
		if (--repathTicks <= 0 || npc.getNavigation().isDone()) {
			repathTicks = 10;
			npc.getNavigation().moveTo(home.x, home.y, home.z, run ? ScriptNpc.SPRINT_PATH_SPEED : 1.0);
		}
		// Give up (and try again later) if two seconds pass without getting closer.
		if (++progressTicks >= 40) {
			progressTicks = 0;
			if (distSq > lastDistSq - 0.5) {
				retryCooldown = GIVE_UP_COOLDOWN;
				npc.getNavigation().stop();
				npc.setSprinting(false);
			}
			lastDistSq = distSq;
		}
	}
}
