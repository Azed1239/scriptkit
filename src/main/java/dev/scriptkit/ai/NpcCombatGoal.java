package dev.scriptkit.ai;

import dev.scriptkit.ScriptKitMod;
import dev.scriptkit.npc.ScriptNpc;
import java.util.EnumSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Fights the current target: melee (chase, weapon-speed cooldown, strafe, shield) or, when holding a
 * bow, ranged (keep 10-15 blocks, full draw, lead, back off). Runs only while the NPC has a target;
 * targets come from {@link NpcTargetGoal}.
 */
public class NpcCombatGoal extends Goal {
	private static final Identifier DAMAGE_MOD = Identifier.fromNamespaceAndPath(ScriptKitMod.MOD_ID, "damage_multiplier");
	private static final double SPRINT = ScriptNpc.SPRINT_MODIFIER;
	/** A player's walking acceleration per unit of input (speed 0.1). */
	private static final double PLAYER_WALK = 0.1;
	private static final double RANGED_MIN = 10.0;
	private static final double RANGED_MAX = 15.0;
	/** Archers run (not back-step) when an enemy is closer than this. */
	private static final double FLEE_DISTANCE = 6.0;
	private static final float ARROW_VELOCITY = 1.6F;

	private final ScriptNpc npc;
	private final RandomSource random;

	private static final int MAX_COUNTER_WAIT = 15;

	private int attackCooldown;
	private int counterWait;
	private int repathCooldown;
	private int strafeTicks;
	private float strafeSide = 1.0F;
	private boolean strafing;
	private int retreatTicks;
	private int blockTicks;
	private int lastSeenHurtTime;
	private int shotCooldown;
	private int multiplierCheck;

	private int stuckCheck = 20;
	private int stuckStage;
	private Vec3 lastPos = Vec3.ZERO;

	public NpcCombatGoal(ScriptNpc npc) {
		this.npc = npc;
		this.random = npc.getRandom();
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		LivingEntity t = npc.getTarget();
		return t != null && t.isAlive();
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		npc.setAggressive(true);
		attackCooldown = 8 + random.nextInt(8);
		repathCooldown = 0;
		stuckStage = 0;
		stuckCheck = 20;
		lastPos = npc.position();
		applyDamageMultiplier();
	}

	@Override
	public void stop() {
		npc.setAttackCooldown(Integer.MAX_VALUE);
		npc.setAggressive(false);
		npc.getNavigation().stop();
		halt(npc);
		if (npc.isUsingItem()) {
			npc.stopUsingItem();
		}
	}

	/** Cancels any pending strafe/move so the NPC stands still. */
	static void halt(ScriptNpc npc) {
		npc.getMoveControl().setWantedPosition(npc.getX(), npc.getY(), npc.getZ(), 0.0);
		npc.setXxa(0.0F);
		npc.setZza(0.0F);
	}

	@Override
	public void tick() {
		LivingEntity target = npc.getTarget();
		if (target == null || !(npc.level() instanceof ServerLevel level)) {
			return;
		}
		boolean justHurt = npc.hurtTime > 0 && lastSeenHurtTime == 0;
		lastSeenHurtTime = npc.hurtTime;
		if (--multiplierCheck <= 0) {
			multiplierCheck = 100;
			applyDamageMultiplier();
		}
		npc.getLookControl().setLookAt(target, 30.0F, 30.0F);
		if (npc.getMainHandItem().getItem() instanceof BowItem) {
			rangedTick(level, target);
		} else {
			meleeTick(level, target, justHurt);
		}
		npc.setAttackCooldown(attackCooldown);
	}

	// ---------------------------------------------------------------- melee

	private void meleeTick(ServerLevel level, LivingEntity target, boolean justHurt) {
		CombatConfig cfg = CombatConfig.get();
		double distSq = npc.distanceToSqr(target);
		boolean inReach = npc.isWithinMeleeAttackRange(target);
		if (attackCooldown > 0) {
			attackCooldown--;
		}

		boolean hasShield = cfg.block && npc.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS);
		// Counter-attack timing: if an NPC opponent is about to swing, keep the shield up, take the
		// blow on it, then strike. Capped so two shield users don't wait on each other forever.
		boolean holdForCounter = false;
		if (hasShield && attackCooldown <= 0 && counterWait < MAX_COUNTER_WAIT
			&& target instanceof ScriptNpc enemy && enemy.getAttackCooldown() <= 4 && distSq < 16.0) {
			holdForCounter = true;
			counterWait++;
		}

		updateShield(hasShield, distSq, justHurt, holdForCounter);

		if (inReach && attackCooldown <= 0 && !holdForCounter) {
			counterWait = 0;
			if (npc.isUsingItem()) {
				npc.stopUsingItem();
			}
			npc.swing(InteractionHand.MAIN_HAND);
			npc.doHurtTarget(level, target);
			attackCooldown = attackInterval();
		}

		if (!inReach && distSq > 9.0) {
			chase(target, distSq);
			return;
		}
		npc.getNavigation().stop();
		stuckStage = 0;
		if (!cfg.strafe) {
			strafe(inReach ? 0.0F : 0.8F, 0.0F);
			return;
		}
		if (justHurt && random.nextFloat() < 0.3F) {
			retreatTicks = 6 + random.nextInt(8);
		}
		// Fractions of player walking speed.
		float forward = inReach ? (distSq < 1.5 ? -0.5F : 0.0F) : 0.8F;
		if (retreatTicks > 0) {
			retreatTicks--;
			forward = -1.0F;
		}
		strafe(forward, nextStrafeSide(0.6F));
		npc.lookAt(target, 30.0F, 30.0F);
	}

	private void updateShield(boolean hasShield, double distSq, boolean justHurt, boolean holdForCounter) {
		if (!hasShield) {
			if (npc.isUsingItem() && npc.getUsedItemHand() == InteractionHand.OFF_HAND) {
				npc.stopUsingItem();
			}
			return;
		}
		// Like a PvP player: keep the shield up while an enemy is close (or right after being hit) and
		// drop it only for the tick before our own swing. Occasionally hold it down a moment longer so
		// the rhythm is not perfectly regular.
		if (justHurt) {
			blockTicks = 15 + random.nextInt(15);
		}
		if (blockTicks > 0) {
			blockTicks--;
		}
		boolean threatened = distSq < 5.0 * 5.0 || blockTicks > 0;
		if (attackCooldown == 1 && random.nextInt(4) == 0) {
			attackCooldown++; // brief hesitation
		}
		boolean wantBlock = threatened && (attackCooldown > 1 || holdForCounter);
		if (wantBlock && !npc.isUsingItem()) {
			npc.startUsingItem(InteractionHand.OFF_HAND);
		} else if (!wantBlock && npc.isUsingItem() && npc.getUsedItemHand() == InteractionHand.OFF_HAND) {
			npc.stopUsingItem();
		}
	}

	/** Ticks between swings from the held weapon's attack speed (fist 4.0, sword 1.6, axe ~0.9), plus jitter. */
	private int attackInterval() {
		double speed = Math.max(0.5, npc.getAttributeValue(Attributes.ATTACK_SPEED));
		return (int) Math.ceil(20.0 / speed) + random.nextInt(3);
	}

	// ---------------------------------------------------------------- ranged

	private void rangedTick(ServerLevel level, LivingEntity target) {
		CombatConfig cfg = CombatConfig.get();
		double dist = npc.distanceTo(target);
		boolean los = npc.getSensing().hasLineOfSight(target);
		if (shotCooldown > 0) {
			shotCooldown--;
		}

		if (dist < FLEE_DISTANCE) {
			flee(target);
		} else if (dist > RANGED_MAX || !los) {
			chase(target, dist * dist);
		} else {
			npc.getNavigation().stop();
			stuckStage = 0;
			float forward = dist < RANGED_MIN ? -0.7F : 0.0F;
			float side = cfg.strafe ? nextStrafeSide(0.5F) : 0.0F;
			strafe(forward, side);
			npc.lookAt(target, 30.0F, 30.0F);
		}

		if (npc.isUsingItem() && npc.getUsedItemHand() == InteractionHand.MAIN_HAND) {
			int drawn = npc.getTicksUsingItem();
			if (los && drawn >= 20) {
				npc.stopUsingItem();
				shoot(level, target, BowItem.getPowerForTime(drawn), cfg);
				shotCooldown = 10 + random.nextInt(15);
			} else if (!los && drawn > 60) {
				npc.stopUsingItem();
			}
		} else if (shotCooldown <= 0 && los) {
			npc.startUsingItem(InteractionHand.MAIN_HAND);
		}
	}

	private void shoot(ServerLevel level, LivingEntity target, float power, CombatConfig cfg) {
		ItemStack bow = npc.getMainHandItem();
		ItemStack ammo = new ItemStack(Items.ARROW);
		AbstractArrow arrow = ProjectileUtil.getMobArrow(npc, ammo, power, bow);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		arrow.setBaseDamage(2.0 * cfg.damageMultiplier);

		double dist = npc.distanceTo(target);
		double lead = Math.min(dist / ARROW_VELOCITY, 20.0);
		Vec3 motion = target.getDeltaMovement();
		double dx = target.getX() + motion.x * lead - npc.getX();
		double dz = target.getZ() + motion.z * lead - npc.getZ();
		double dy = target.getY(0.3333) - arrow.getY();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float inaccuracy = (float) ((1.0 - cfg.archerAccuracy) * 10.0);
		Projectile.spawnProjectileUsingShoot(arrow, level, ammo, dx, dy + horizontal * 0.2, dz, ARROW_VELOCITY * power, inaccuracy);
		npc.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (random.nextFloat() * 0.4F + 0.8F));
	}

	/** Sprint away along a real path; backing up by strafing is slower than a chasing enemy. */
	private void flee(LivingEntity threat) {
		stuckStage = 0;
		if (--repathCooldown > 0 && !npc.getNavigation().isDone()) {
			return;
		}
		repathCooldown = 10 + random.nextInt(6);
		Vec3 away = DefaultRandomPos.getPosAway(npc, 12, 4, threat.position());
		if (away != null) {
			npc.getNavigation().moveTo(away.x, away.y, away.z, SPRINT);
		} else {
			// Cornered: back off and strafe.
			npc.getNavigation().stop();
			strafe(-1.0F, nextStrafeSide(0.6F));
		}
	}

	/**
	 * Strafe at fractions of player walking speed (1.0 = 4.3 blocks/s). MoveControl's strafe
	 * acceleration is (mob speed x input), so the input is scaled by player-walk / mob-speed.
	 */
	private void strafe(float forward, float side) {
		// Reset the move control's speed modifier to 1.0 (it otherwise keeps the last path speed, or 0 after a halt).
		npc.getMoveControl().setWantedPosition(npc.getX(), npc.getY(), npc.getZ(), 1.0);
		float scale = (float) (PLAYER_WALK / Math.max(0.01, npc.getAttributeValue(Attributes.MOVEMENT_SPEED)));
		npc.getMoveControl().strafe(forward * scale, side * scale);
	}

	// ---------------------------------------------------------------- shared movement

	private float nextStrafeSide(float speed) {
		if (--strafeTicks <= 0) {
			strafeTicks = 20 + random.nextInt(40);
			strafeSide = random.nextBoolean() ? 1.0F : -1.0F;
			strafing = random.nextFloat() < 0.75F;
		}
		return strafing ? strafeSide * speed : 0.0F;
	}

	/** Throttled pathing toward the target with stuck handling: jump, then repath, then give up. */
	private void chase(LivingEntity target, double distSq) {
		if (--repathCooldown <= 0 || npc.getNavigation().isDone()) {
			repathCooldown = 10 + random.nextInt(6);
			npc.getNavigation().moveTo(target, distSq > 64.0 ? SPRINT : 1.0);
		}
		if (--stuckCheck > 0) {
			return;
		}
		stuckCheck = 20;
		Vec3 pos = npc.position();
		boolean moved = pos.distanceToSqr(lastPos) > 0.25;
		lastPos = pos;
		if (moved) {
			stuckStage = 0;
			return;
		}
		stuckStage++;
		if (stuckStage == 1) {
			npc.getJumpControl().jump();
		} else if (stuckStage == 2) {
			npc.getNavigation().stop();
			repathCooldown = 0;
		} else {
			npc.ignore(target, 100);
			npc.setTarget(null);
			stuckStage = 0;
		}
	}

	private void applyDamageMultiplier() {
		AttributeInstance inst = npc.getAttribute(Attributes.ATTACK_DAMAGE);
		if (inst == null) {
			return;
		}
		double amount = CombatConfig.get().damageMultiplier - 1.0;
		AttributeModifier current = inst.getModifier(DAMAGE_MOD);
		if (current != null && current.amount() == amount) {
			return;
		}
		inst.removeModifier(DAMAGE_MOD);
		if (amount != 0.0) {
			inst.addTransientModifier(new AttributeModifier(DAMAGE_MOD, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}
}
