package dev.scriptkit.ai;

import dev.scriptkit.npc.ScriptNpc;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;

/**
 * Single place where NPC goals are registered. To add behaviour (phase 3 combat, etc.) add one
 * line in {@link #register}. Goals exist only while the NPC's aiEnabled flag is true; when it is
 * turned off {@link #clear} removes them all so the NPC pays no goal-ticking cost.
 */
public final class NpcGoals {
	private NpcGoals() {}

	public static void register(ScriptNpc npc) {
		npc.getGoalSelector().addGoal(0, new FloatGoal(npc));
		// Placeholder behaviour so the aiEnabled toggle is visible: wander and look around.
		npc.getGoalSelector().addGoal(5, new WaterAvoidingRandomStrollGoal(npc, 1.0));
		npc.getGoalSelector().addGoal(6, new LookAtPlayerGoal(npc, Player.class, 8.0F));
		npc.getGoalSelector().addGoal(7, new RandomLookAroundGoal(npc));
	}

	public static void clear(ScriptNpc npc) {
		npc.getGoalSelector().removeAllGoals(goal -> true);
		npc.getTargetSelector().removeAllGoals(goal -> true);
		npc.getNavigation().stop();
	}
}
