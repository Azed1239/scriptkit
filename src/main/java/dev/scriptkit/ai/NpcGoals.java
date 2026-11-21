package dev.scriptkit.ai;

import dev.scriptkit.npc.ScriptNpc;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;

/**
 * Single place where NPC goals are registered. Goals exist only while the NPC's aiEnabled flag is
 * true; when it is turned off {@link #clear} removes them all so the NPC pays no goal-ticking or
 * target-scanning cost. To add behaviour, add a goal here.
 */
public final class NpcGoals {
	private NpcGoals() {}

	public static void register(ScriptNpc npc) {
		npc.getGoalSelector().addGoal(0, new FloatGoal(npc));
		npc.getGoalSelector().addGoal(2, new NpcCombatGoal(npc));
		// Idle when there is no target: stand in place and look around. No wandering, so formations hold.
		npc.getGoalSelector().addGoal(7, new LookAtPlayerGoal(npc, Player.class, 8.0F));
		npc.getGoalSelector().addGoal(8, new RandomLookAroundGoal(npc));

		npc.getTargetSelector().addGoal(1, new NpcTargetGoal(npc));
	}

	public static void clear(ScriptNpc npc) {
		removeAll(npc.getGoalSelector());
		removeAll(npc.getTargetSelector());
		npc.getNavigation().stop();
		npc.setTarget(null);
		npc.setAggressive(false);
		if (npc.isUsingItem()) {
			npc.stopUsingItem();
		}
		npc.setSprinting(false);
		NpcCombatGoal.halt(npc);
	}

	/**
	 * In 1.21.11 {@link GoalSelector#removeAllGoals} only drops goals from the list without stopping
	 * running ones, which leaves their control flags locked so newly added goals can never start.
	 * Stop them first.
	 */
	private static void removeAll(GoalSelector selector) {
		for (WrappedGoal wrapped : selector.getAvailableGoals()) {
			if (wrapped.isRunning()) {
				wrapped.stop();
			}
		}
		selector.removeAllGoals(goal -> true);
	}
}
