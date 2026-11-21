package dev.scriptkit.ai;

import dev.scriptkit.ScriptKitMod;
import dev.scriptkit.npc.ScriptNpc;
import java.util.EnumSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
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
	private static final Identifier CRIT_MOD = Identifier.fromNamespaceAndPath(ScriptKitMod.MOD_ID, "critical_hit");
	/** Jump this many ticks before the swing is ready so it lands on the way down (jump apex is ~6 ticks). */
	private static final int CRIT_JUMP_LEAD = 6;
	private static final int CRIT_MAX_WAIT = 14;
	/** A player's walking acceleration per unit of input (speed 0.1). */
	private static final double PLAYER_WALK = 0.1;
	/** Players move at 20% speed while using an item (drawing a bow). */
	private static final float USING_ITEM_SPEED = 0.2F;
	/** Minimum draw time before an archer will release early when an enemy rushes in. */
	private static final int PANIC_SHOT_TICKS = 8;
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
	private boolean critPending;
	private boolean critTried;
	private int critWait;
	private int repathCooldown;
	private int strafeTicks;
	private float strafeSide = 1.0F;
	private boolean strafing;
	private int retreatTicks;
	private int blockTicks;
	private int blockDelay;
	private int lastSeenHurtTime;
	private int shotCooldown;
	private int multiplierCheck;

	private int stuckCheck = 20;
	private final NpcTerrainSkills terrain;
	private boolean digging;
	private int stuckStage;
	private Vec3 lastPos = Vec3.ZERO;

	public NpcCombatGoal(ScriptNpc npc) {
		this.npc = npc;
		this.random = npc.getRandom();
		this.terrain = new NpcTerrainSkills(npc);
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
		npc.applyDifficulty(false);
	}

	@Override
	public void stop() {
		if (digging) {
			terrain.stop();
			digging = false;
		}
		npc.setAttackCooldown(Integer.MAX_VALUE);
		npc.setSprinting(false);
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
			npc.applyDifficulty(false);
		}
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
		Difficulty diff = npc.difficulty();
		npc.getLookControl().setLookAt(target, 30.0F, 30.0F);
		double distSq = npc.distanceToSqr(target);
		// Only hit what we are facing, like a player.
		boolean closeEnough = npc.isWithinMeleeAttackRange(target);
		boolean inReach = closeEnough && isFacing(target, 60.0F);
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

		updateShield(hasShield, distSq, justHurt, holdForCounter, diff);

		// Jump-crit like a player: sometimes hop just before the swing is ready and strike while
		// falling. If the jump fails (ceiling, water) the swing goes ahead normally after a short wait.
		if (critPending) {
			critWait++;
		} else if (!critTried && attackCooldown <= CRIT_JUMP_LEAD && attackCooldown >= 3 && distSq < 9.0
			&& npc.onGround() && !npc.isInWater() && !npc.isSprinting()) {
			critTried = true; // one roll per swing
			if (random.nextFloat() < diff.critChance) {
				npc.getJumpControl().jump();
				critPending = true;
				critWait = 0;
			}
		}
		boolean falling = !npc.onGround() && npc.getDeltaMovement().y < 0.0;
		boolean waitForFall = critPending && !falling && critWait < CRIT_MAX_WAIT;

		if (inReach && attackCooldown <= 0 && !holdForCounter && !waitForFall) {
			counterWait = 0;
			if (npc.isUsingItem()) {
				npc.stopUsingItem();
			}
			boolean sprintHit = npc.isSprinting();
			boolean crit = critPending && falling && !sprintHit && !npc.isInWater();
			critPending = false;
			critTried = false;
			npc.swing(InteractionHand.MAIN_HAND);
			if (crit) {
				setCritBonus(true);
			}
			boolean connects = random.nextDouble() < hitChance(diff, target);
			boolean hit = connects && npc.doHurtTarget(level, target);
			if (!connects) {
				npc.playSound(SoundEvents.PLAYER_ATTACK_WEAK, 1.0F, 1.0F);
			}
			if (crit) {
				setCritBonus(false);
				if (hit) {
					critEffects(level, target);
				}
			}
			if (hit && sprintHit) {
				sprintKnockback(target);
			}
			attackCooldown = attackInterval(diff);
		}

		// Path to the target until it is within arm's reach (also covers targets just out of reach above
		// or below, e.g. at the edge of a hole, so stuck handling and digging can kick in).
		if (digging || !closeEnough) {
			chase(level, target, distSq);
			return;
		}
		setRunning(false);
		npc.getNavigation().stop();
		stuckStage = 0;
		if (!cfg.strafe) {
			strafe(0.0F, 0.0F);
			return;
		}
		if (justHurt && random.nextFloat() < 0.3F) {
			retreatTicks = 6 + random.nextInt(8);
		}
		// Fractions of player walking speed.
		float forward = distSq < 1.5 ? -0.5F : 0.0F;
		if (retreatTicks > 0) {
			retreatTicks--;
			forward = -1.0F;
		}
		strafe(forward, nextStrafeSide(0.6F));
		npc.lookAt(target, 30.0F, 30.0F);
	}

	private void updateShield(boolean hasShield, double distSq, boolean justHurt, boolean holdForCounter, Difficulty diff) {
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
		if (blockDelay > 0) {
			blockDelay--;
			wantBlock = false; // fumbled the shield: no block until the delay is over
		}
		if (wantBlock && !npc.isUsingItem()) {
			// Skill check each time it goes to raise the shield; a failure means a short fumble.
			if (random.nextDouble() < diff.blockSkill) {
				npc.startUsingItem(InteractionHand.OFF_HAND);
			} else {
				blockDelay = 8 + random.nextInt(10);
			}
		} else if (!wantBlock && npc.isUsingItem() && npc.getUsedItemHand() == InteractionHand.OFF_HAND) {
			npc.stopUsingItem();
		}
	}

	/** Ticks between swings from the held weapon's attack speed (fist 4.0, sword 1.6, axe ~0.9), plus jitter. */
	private int attackInterval(Difficulty diff) {
		double speed = Math.max(0.5, npc.getAttributeValue(Attributes.ATTACK_SPEED)) * diff.attackSpeed;
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

		boolean drawing = npc.isUsingItem() && npc.getUsedItemHand() == InteractionHand.MAIN_HAND;

		// Like a player, an archer can't run while drawing: running away or repositioning cancels the
		// draw, and the NPC looks where it runs, not back at the target.
		if (dist < FLEE_DISTANCE || dist > RANGED_MAX || !los) {
			if (drawing) {
				int drawn = npc.getTicksUsingItem();
				npc.stopUsingItem();
				// Enemy closing in mid-draw: loose a weaker early shot first if we are mostly drawn and aimed.
				if (dist < FLEE_DISTANCE && los && drawn >= PANIC_SHOT_TICKS && isFacing(target, 25.0F)) {
					shoot(level, target, BowItem.getPowerForTime(drawn), cfg);
					shotCooldown = 10 + random.nextInt(10);
				}
			}
			if (dist < FLEE_DISTANCE) {
				flee(target);
			} else {
				chase(level, target, dist * dist);
			}
			return;
		}

		// In position: turn to face the target. While drawing, move at a player's item-use speed.
		setRunning(false);
		npc.getNavigation().stop();
		stuckStage = 0;
		npc.getLookControl().setLookAt(target, 30.0F, 30.0F);
		npc.lookAt(target, 30.0F, 30.0F);
		float slow = drawing ? USING_ITEM_SPEED : 1.0F;
		float forward = dist < RANGED_MIN ? -0.7F : 0.0F;
		float side = cfg.strafe ? nextStrafeSide(0.5F) : 0.0F;
		strafe(forward * slow, side * slow);

		if (drawing) {
			int drawn = npc.getTicksUsingItem();
			if (drawn >= 20 && isFacing(target, 15.0F)) {
				npc.stopUsingItem();
				shoot(level, target, BowItem.getPowerForTime(drawn), cfg);
				shotCooldown = 10 + random.nextInt(15);
			}
		} else if (shotCooldown <= 0 && isFacing(target, 45.0F)) {
			npc.startUsingItem(InteractionHand.MAIN_HAND);
		}
	}

	/** True when both the head and the body point at the target (within the given angle for the head). */
	private boolean isFacing(LivingEntity target, float maxDegrees) {
		double dx = target.getX() - npc.getX();
		double dz = target.getZ() - npc.getZ();
		float yawToTarget = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
		return Math.abs(Mth.wrapDegrees(yawToTarget - npc.getYHeadRot())) <= maxDegrees
			&& Math.abs(Mth.wrapDegrees(yawToTarget - npc.getYRot())) <= maxDegrees + 30.0F;
	}

	private void shoot(ServerLevel level, LivingEntity target, float power, CombatConfig cfg) {
		ItemStack bow = npc.getMainHandItem();
		ItemStack ammo = new ItemStack(Items.ARROW);
		AbstractArrow arrow = ProjectileUtil.getMobArrow(npc, ammo, power, bow);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		Difficulty diff = npc.difficulty();
		arrow.setBaseDamage(2.0 * cfg.damageMultiplier * diff.damage);

		double dist = npc.distanceTo(target);
		double lead = Math.min(dist / ARROW_VELOCITY, 20.0);
		Vec3 motion = target.getDeltaMovement();
		double dx = target.getX() + motion.x * lead - npc.getX();
		double dz = target.getZ() + motion.z * lead - npc.getZ();
		double dy = target.getY(0.3333) - arrow.getY();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float inaccuracy = (float) ((1.0 - diff.archerAccuracy) * 10.0);
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
			setRunning(true);
			npc.getNavigation().moveTo(away.x, away.y, away.z, ScriptNpc.SPRINT_PATH_SPEED);
		} else {
			// Cornered: back off and strafe.
			setRunning(false);
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

	/**
	 * Vanilla sprint state (particles, run animation, sprint-hit knockback). Sprinting adds +30% to the
	 * speed attribute, so the current path is switched to the matching path speed right away.
	 */
	private void setRunning(boolean run) {
		if (npc.isSprinting() == run) {
			return;
		}
		npc.setSprinting(run);
		if (npc.getNavigation().isInProgress()) {
			npc.getNavigation().setSpeedModifier(run ? ScriptNpc.SPRINT_PATH_SPEED : 1.0);
		}
	}

	/** Probability that a swing lands: the configured accuracy, lowered by 15% against a moving target. */
	private static double hitChance(Difficulty diff, LivingEntity target) {
		double chance = diff.meleeAccuracy;
		if (target.getDeltaMovement().horizontalDistanceSqr() > 0.1 * 0.1) {
			chance *= 0.85;
		}
		return chance;
	}

	/** +50% attack damage for one swing (vanilla critical hit), on top of the damage multiplier. */
	private void setCritBonus(boolean on) {
		AttributeInstance inst = npc.getAttribute(Attributes.ATTACK_DAMAGE);
		if (inst == null) {
			return;
		}
		inst.removeModifier(CRIT_MOD);
		if (on) {
			inst.addTransientModifier(new AttributeModifier(CRIT_MOD, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** Crit particles on the target and the crit sound, exactly as for a player's critical hit. */
	private void critEffects(ServerLevel level, LivingEntity target) {
		level.getChunkSource().sendToTrackingPlayersAndSelf(target,
			new ClientboundAnimatePacket(target, ClientboundAnimatePacket.CRITICAL_HIT));
		level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.PLAYER_ATTACK_CRIT,
			npc.getSoundSource(), 1.0F, 1.0F);
	}

	/** A sprinting hit knocks back harder and ends the sprint, like a player's. */
	private void sprintKnockback(LivingEntity target) {
		float yaw = npc.getYRot() * ((float) Math.PI / 180.0F);
		target.knockback(0.5, Mth.sin(yaw), -Mth.cos(yaw));
		npc.setDeltaMovement(npc.getDeltaMovement().multiply(0.6, 1.0, 0.6));
		npc.playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 1.0F);
		setRunning(false);
	}

	/**
	 * Throttled pathing toward the target with stuck handling: jump, then repath, then mine/build
	 * toward it (if allowed), then give up on that target.
	 */
	private void chase(ServerLevel level, LivingEntity target, double distSq) {
		if (digging) {
			NpcTerrainSkills.Result result = terrain.tick(level, target);
			if (result == NpcTerrainSkills.Result.CONTINUE) {
				return;
			}
			digging = false;
			stuckStage = 0;
			repathCooldown = 0;
			if (result == NpcTerrainSkills.Result.GIVE_UP) {
				npc.ignore(target, 100);
				npc.setTarget(null);
			}
			return;
		}
		if (--repathCooldown <= 0 || npc.getNavigation().isDone()) {
			repathCooldown = 10 + random.nextInt(6);
			boolean run = distSq > 64.0;
			setRunning(run);
			npc.getNavigation().moveTo(target, run ? ScriptNpc.SPRINT_PATH_SPEED : 1.0);
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
		} else if (NpcTerrainSkills.enabled()) {
			setRunning(false);
			digging = true;
			stuckStage = 0;
			terrain.start();
		} else {
			npc.ignore(target, 100);
			npc.setTarget(null);
			stuckStage = 0;
		}
	}
}
