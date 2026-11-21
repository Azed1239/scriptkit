package dev.scriptkit.ai;

import dev.scriptkit.npc.ScriptNpc;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;

/**
 * Makes hostile mobs (zombies, skeletons, creepers, spiders, slimes...) notice and attack NPCs the way
 * they attack players. Vanilla mobs look for {@code Player} targets only, and NPCs are not players, so
 * without this they walk right past them. Whether it applies is read from combat.json every time a
 * mob looks for a target, so {@code /scriptkit reload} takes effect at once.
 */
public final class HostileMobs {
	/** The goal we add; a named class so we can tell whether a mob already has it. */
	static final class HuntNpcsGoal extends NearestAttackableTargetGoal<ScriptNpc> {
		HuntNpcsGoal(Mob mob) {
			super(mob, ScriptNpc.class, true);
		}

		@Override
		public boolean canUse() {
			return CombatConfig.get().hostileMobsAttackNpcs && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			return CombatConfig.get().hostileMobsAttackNpcs && super.canContinueToUse();
		}
	}

	private HostileMobs() {}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> addGoal(entity));
	}

	private static void addGoal(Entity entity) {
		if (!(entity instanceof Mob mob) || !(mob instanceof Monster || mob instanceof Slime) || mob instanceof EnderMan) {
			return;
		}
		for (WrappedGoal wrapped : mob.targetSelector.getAvailableGoals()) {
			if (wrapped.getGoal() instanceof HuntNpcsGoal) {
				return; // already added (the event can fire again when a chunk reloads)
			}
		}
		// Vanilla's "attack players" goal sits at priority 2; ours comes right after it.
		mob.targetSelector.addGoal(3, new HuntNpcsGoal(mob));
	}
}
