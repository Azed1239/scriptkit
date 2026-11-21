package dev.scriptkit.npc;

import dev.scriptkit.ScriptKitMod;
import dev.scriptkit.ai.CombatConfig;
import dev.scriptkit.ai.Difficulty;
import java.util.Locale;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import dev.scriptkit.ai.NpcGoals;
import dev.scriptkit.config.ScriptKitConfig;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import dev.scriptkit.platform.NpcRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * A lightweight, player-looking NPC. Not a ServerPlayer, so it is cheap to run many of them.
 * Phase 1: stands still and never despawns. {@code aiEnabled} is reserved for phase 2.
 */
public class ScriptNpc extends PathfinderMob {
	/** Movement-speed attribute so that goal speed modifier 1.0 equals normal player walking speed. */
	public static final double WALK_SPEED = Math.sqrt(0.1);
	private static final Identifier HEALTH_MOD = Identifier.fromNamespaceAndPath(ScriptKitMod.MOD_ID, "difficulty_health");
	private static final Identifier SPEED_MOD = Identifier.fromNamespaceAndPath(ScriptKitMod.MOD_ID, "difficulty_speed");
	private static final Identifier DAMAGE_MOD = Identifier.fromNamespaceAndPath(ScriptKitMod.MOD_ID, "damage_multiplier");
	/**
	 * Path speed while in the vanilla sprint state. Sprinting adds +30% to the speed attribute, and mob
	 * ground speed grows with the square of speed, so 1/sqrt(1.3) lands on a player's 5.6 blocks/s.
	 */
	public static final double SPRINT_PATH_SPEED = 1.0 / Math.sqrt(1.3);

	private static final EntityDataAccessor<String> DATA_SKIN =
		SynchedEntityData.defineId(ScriptNpc.class, EntityDataSerializers.STRING);
	/** Item shown in the main hand instead of the weapon while mining or building (visual only, never dropped). */
	private static final EntityDataAccessor<ItemStack> DATA_DISPLAY_ITEM =
		SynchedEntityData.defineId(ScriptNpc.class, EntityDataSerializers.ITEM_STACK);

	private String npcName = "";
	private String teamName = "";
	private String kit = "none";
	/** Name of this NPC's difficulty preset; empty means the config default. */
	private String difficultyName = "";
	private boolean aiEnabled = true;
	/** Formation spot to regroup at (where the NPC was spawned, or where its AI was last switched on). */
	private double homeX;
	private double homeY;
	private double homeZ;
	private boolean hasHome;
	/** Client-only: this entity is a translucent preview of where an army would stand (Command Stick), not a real NPC. */
	private boolean ghost;
	private boolean ghostBlocked;
	private UUID skinSeed;
	private UUID ignoredTarget;
	private long ignoreUntil;
	private int attackCooldown = Integer.MAX_VALUE;

	public ScriptNpc(EntityType<? extends ScriptNpc> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.setCustomNameVisible(true);
		this.setCanPickUpLoot(false);
		refreshGoals();
	}

	/** Goals are registered via {@link #refreshGoals()} (the super constructor runs before our fields exist). */
	@Override
	protected void registerGoals() {
	}

	public GoalSelector getGoalSelector() {
		return this.goalSelector;
	}

	public GoalSelector getTargetSelector() {
		return this.targetSelector;
	}

	/** Installs the NPC goals when aiEnabled, removes them all otherwise. Server side only. */
	private void refreshGoals() {
		if (this.level().isClientSide()) {
			return;
		}
		NpcGoals.clear(this);
		if (aiEnabled) {
			NpcGoals.register(this);
		}
		// A switched-off NPC is fully frozen: vanilla's "no AI" also stops its look and body control, so it
		// no longer turns its head or body towards players or wanders its gaze.
		this.setNoAi(!aiEnabled);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		if (attacker != null && attacker != this && this.isAlliedTo(attacker)) {
			if (attacker instanceof Player) {
				// Players can hurt NPCs on their own team (accidental friendly fire) unless turned off.
				if (!CombatConfig.get().playerFriendlyFire) {
					return false;
				}
			} else {
				// NPC-vs-NPC same-team damage needs both the mod config and the team itself to allow it,
				// so teams created before the config existed are still safe by default.
				PlayerTeam team = this.getTeam();
				if (!ScriptKitConfig.get().friendlyFire || (team != null && !team.isAllowFriendlyFire())) {
					return false;
				}
			}
		}
		return super.hurtServer(level, source, amount);
	}

	/** Ticks until this NPC's next melee swing (MAX_VALUE when not fighting). Read by shield users to time counters. */
	public int getAttackCooldown() {
		return attackCooldown;
	}

	public void setAttackCooldown(int ticks) {
		this.attackCooldown = ticks;
	}

	/** Ignore {@code target} for {@code ticks} (used when it can't be reached). */
	public void ignore(LivingEntity target, int ticks) {
		this.ignoredTarget = target.getUUID();
		this.ignoreUntil = this.level().getGameTime() + ticks;
	}

	public boolean isIgnoring(LivingEntity target) {
		return ignoredTarget != null && this.level().getGameTime() < ignoreUntil && ignoredTarget.equals(target.getUUID());
	}

	@Override
	public void die(DamageSource source) {
		boolean announce = !this.level().isClientSide() && !this.isRemoved() && !this.dead;
		Component deathMessage = announce ? this.getCombatTracker().getDeathMessage() : null;
		super.die(source);
		if (announce && this.level() instanceof ServerLevel serverLevel) {
			NpcMessages.death(serverLevel, this, deathMessage);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		if (CombatConfig.get().dropKitOnDeath) {
			super.dropCustomDeathLoot(level, source, killedByPlayer);
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 20.0)
			// In 1.21.11 Mob.setSpeed(s) also sets forward input to s, so ground acceleration is ~s^2
			// (a player walks with speed 0.1 and input ~1). sqrt(0.1) therefore walks at a player's 4.3 blocks/s.
			.add(Attributes.MOVEMENT_SPEED, WALK_SPEED)
			.add(Attributes.ATTACK_DAMAGE, 1.0)
			// Same base as a player, so held weapons change the swing rate (sword 1.6, axe ~0.9).
			.add(Attributes.ATTACK_SPEED, 4.0)
			.add(Attributes.ATTACK_KNOCKBACK, 0.0)
			// Pathfinding range; must exceed the combat detection range.
			.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	/** Convenience factory used by commands. */
	public static ScriptNpc create(Level level) {
		return new ScriptNpc(NpcRegistry.NPC_TYPE, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SKIN, "");
		builder.define(DATA_DISPLAY_ITEM, ItemStack.EMPTY);
	}

	@Override
	public void aiStep() {
		// Vanilla only advances the arm-swing timer for monsters and players; without this the
		// sword swing never animates on clients.
		this.updateSwingTime();
		super.aiStep();
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return false;
	}

	public String getNpcName() {
		return npcName;
	}

	public void setNpcName(String name) {
		this.npcName = name;
		this.setCustomName(Component.literal(name));
		this.setCustomNameVisible(true);
	}

	public ItemStack getDisplayItem() {
		return this.entityData.get(DATA_DISPLAY_ITEM);
	}

	public void setDisplayItem(ItemStack stack) {
		if (!ItemStack.matches(getDisplayItem(), stack)) {
			this.entityData.set(DATA_DISPLAY_ITEM, stack.copy());
		}
	}

	public String getSkinName() {
		return this.entityData.get(DATA_SKIN);
	}

	public void setSkinName(String skin) {
		this.entityData.set(DATA_SKIN, skin);
	}

	public String getTeamName() {
		return teamName;
	}

	/** Sets the team (blank = none) and joins the vanilla scoreboard team, creating it if missing. */
	public void setTeamName(String team) {
		this.teamName = team == null ? "" : team;
		applyTeam();
	}

	public String getKit() {
		return kit;
	}

	/** The difficulty preset name in effect (this NPC's own, or the config default). */
	public String getDifficultyName() {
		return difficultyName.isEmpty() ? CombatConfig.get().defaultDifficulty : difficultyName;
	}

	/** This NPC's preset values (re-read from the config each call, so editing combat.json + reload takes effect). */
	public Difficulty difficulty() {
		return CombatConfig.get().difficulty(getDifficultyName());
	}

	public void setDifficulty(String name) {
		this.difficultyName = name.toLowerCase(Locale.ROOT);
		applyDifficulty(true);
	}

	/**
	 * Applies the difficulty to attributes: max health, speed and damage (global damageMultiplier
	 * times the preset's). Idempotent and cheap, so the combat goal re-runs it now and then to pick up
	 * config reloads. When {@code scaleHealth} is set, current health keeps its fraction of the new max.
	 */
	public void applyDifficulty(boolean scaleHealth) {
		if (this.level().isClientSide()) {
			return;
		}
		Difficulty d = difficulty();
		float oldMax = this.getMaxHealth();
		setModifier(Attributes.MAX_HEALTH, HEALTH_MOD, d.health - 1.0);
		setModifier(Attributes.MOVEMENT_SPEED, SPEED_MOD, Math.sqrt(d.moveSpeed) - 1.0); // mob speed grows with attr^2
		setModifier(Attributes.ATTACK_DAMAGE, DAMAGE_MOD, CombatConfig.get().damageMultiplier * d.damage - 1.0);
		float newMax = this.getMaxHealth();
		if (scaleHealth && oldMax > 0.0F && newMax != oldMax) {
			this.setHealth(Math.min(newMax, this.getHealth() * newMax / oldMax));
		} else if (this.getHealth() > newMax) {
			this.setHealth(newMax);
		}
	}

	private void setModifier(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
		Identifier id, double amount) {
		AttributeInstance inst = this.getAttribute(attribute);
		if (inst == null) {
			return;
		}
		AttributeModifier current = inst.getModifier(id);
		if (current != null && Math.abs(current.amount() - amount) < 1.0E-9) {
			return;
		}
		inst.removeModifier(id);
		if (Math.abs(amount) > 1.0E-9) {
			inst.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	public void setKit(String kit) {
		this.kit = kit;
		KitManager.apply(this, kit);
	}

	public boolean isAiEnabled() {
		return aiEnabled;
	}

	public void setAiEnabled(boolean aiEnabled) {
		if (this.aiEnabled != aiEnabled) {
			this.aiEnabled = aiEnabled;
			if (aiEnabled && !this.level().isClientSide()) {
				setHome(this.position()); // wherever it stands when switched on becomes its formation spot
				this.setGlowingTag(false); // no longer selected with the Command Stick
			}
			refreshGoals();
		}
	}

	public void setHome(net.minecraft.world.phys.Vec3 pos) {
		this.homeX = pos.x;
		this.homeY = pos.y;
		this.homeZ = pos.z;
		this.hasHome = true;
	}

	public boolean isGhost() {
		return ghost;
	}

	public boolean isGhostBlocked() {
		return ghostBlocked;
	}

	public void setGhost(boolean ghost, boolean blocked) {
		this.ghost = ghost;
		this.ghostBlocked = blocked;
	}

	/** The UUID that picks this NPC's default skin; ghosts use the UUID of the NPC they copy. */
	public UUID skinSeed() {
		return skinSeed != null ? skinSeed : this.getUUID();
	}

	public void setSkinSeed(UUID seed) {
		this.skinSeed = seed;
	}

	/** Ghosts glow with a colour: blue where the army fits, red where a slot has no ground. */
	@Override
	public boolean isCurrentlyGlowing() {
		return ghost || super.isCurrentlyGlowing();
	}

	@Override
	public int getTeamColor() {
		return ghost ? (ghostBlocked ? 0xFF5555 : 0x66CCFF) : super.getTeamColor();
	}

	@Override
	public boolean isPickable() {
		return !ghost && super.isPickable();
	}

	@Override
	public boolean isPushable() {
		return !ghost && super.isPushable();
	}

	public boolean hasHome() {
		return hasHome;
	}

	public net.minecraft.world.phys.Vec3 getHome() {
		return new net.minecraft.world.phys.Vec3(homeX, homeY, homeZ);
	}

	/** Whether this NPC is a bunny hopper (a fixed trait of the NPC): about bunnyHopChance of all NPCs are. */
	public boolean isBunnyHopper() {
		return Math.floorMod(this.getUUID().hashCode(), 100) < (int) Math.round(CombatConfig.get().bunnyHopChance * 100);
	}

	private void applyTeam() {
		if (this.level().isClientSide() || teamName.isEmpty()) {
			return;
		}
		Scoreboard scoreboard = this.level().getScoreboard();
		PlayerTeam team = ArmyManager.ensureTeam(scoreboard, teamName);
		if (scoreboard.getPlayersTeam(this.getScoreboardName()) != team) {
			scoreboard.addPlayerToTeam(this.getScoreboardName(), team);
		}
	}

	/** Drops the NPC's scoreboard entry; call before discarding. */
	public void leaveTeam() {
		if (!this.level().isClientSide()) {
			this.level().getScoreboard().removePlayerFromTeam(this.getScoreboardName());
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("NpcName", npcName);
		output.putString("SkinName", getSkinName());
		output.putString("TeamName", teamName);
		output.putString("Kit", kit);
		output.putBoolean("AiEnabled", aiEnabled);
		output.putString("Difficulty", difficultyName);
		output.putFloat("HealthFraction", this.getHealth() / Math.max(1.0F, this.getMaxHealth()));
		output.putBoolean("HasHome", hasHome);
		output.putDouble("HomeX", homeX);
		output.putDouble("HomeY", homeY);
		output.putDouble("HomeZ", homeZ);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		// NPCs saved by older versions stored a different base speed; always use the current one.
		AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.setBaseValue(WALK_SPEED);
		}
		this.npcName = input.getStringOr("NpcName", "");
		this.setSkinName(input.getStringOr("SkinName", this.npcName));
		this.teamName = input.getStringOr("TeamName", "");
		this.kit = input.getStringOr("Kit", "none");
		this.aiEnabled = input.getBooleanOr("AiEnabled", true);
		this.difficultyName = input.getStringOr("Difficulty", "");
		// Modifiers aren't saved, so re-apply them, then restore health as a fraction of the (new) maximum.
		applyDifficulty(false);
		this.hasHome = input.getBooleanOr("HasHome", false);
		this.homeX = input.getDoubleOr("HomeX", 0.0);
		this.homeY = input.getDoubleOr("HomeY", 0.0);
		this.homeZ = input.getDoubleOr("HomeZ", 0.0);
		if (!hasHome) {
			setHome(this.position()); // NPCs saved before regrouping existed: regroup where they were loaded
		}
		this.setGlowingTag(false); // a selection outline never survives a restart
		refreshGoals(); // goals and the frozen state must match the loaded aiEnabled
		float fraction = input.getFloatOr("HealthFraction", -1.0F);
		if (fraction >= 0.0F) {
			this.setHealth(Math.max(0.5F, fraction * this.getMaxHealth()));
		}
		refreshGoals();
		if (!npcName.isEmpty()) {
			this.setCustomName(Component.literal(npcName));
		}
		this.setCustomNameVisible(true);
		this.setPersistenceRequired();
	}
}
