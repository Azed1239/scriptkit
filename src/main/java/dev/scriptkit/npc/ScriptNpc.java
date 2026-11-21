package dev.scriptkit.npc;

import dev.scriptkit.ai.NpcGoals;
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
	public static final double WALK_SPEED = 0.33;

	private static final EntityDataAccessor<String> DATA_SKIN =
		SynchedEntityData.defineId(ScriptNpc.class, EntityDataSerializers.STRING);

	private String npcName = "";
	private String teamName = "";
	private String kit = "none";
	private boolean aiEnabled = true;

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
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		if (attacker != null && attacker != this && this.isAlliedTo(attacker)) {
			PlayerTeam team = this.getTeam();
			if (team != null && !team.isAllowFriendlyFire()) {
				return false;
			}
		}
		return super.hurtServer(level, source, amount);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 20.0)
			// Measured on a dev server: with speed modifier 1.0 this walks at about a player's 4.3 blocks/s.
			// (Mobs need ~3x the attribute value a player has for the same speed.)
			.add(Attributes.MOVEMENT_SPEED, WALK_SPEED)
			.add(Attributes.ATTACK_DAMAGE, 1.0);
	}

	/** Convenience factory used by commands. */
	public static ScriptNpc create(Level level) {
		return new ScriptNpc(NpcRegistry.NPC_TYPE, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SKIN, "");
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
			refreshGoals();
		}
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
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.npcName = input.getStringOr("NpcName", "");
		this.setSkinName(input.getStringOr("SkinName", this.npcName));
		this.teamName = input.getStringOr("TeamName", "");
		this.kit = input.getStringOr("Kit", "none");
		this.aiEnabled = input.getBooleanOr("AiEnabled", true);
		refreshGoals();
		if (!npcName.isEmpty()) {
			this.setCustomName(Component.literal(npcName));
		}
		this.setCustomNameVisible(true);
		this.setPersistenceRequired();
	}
}
