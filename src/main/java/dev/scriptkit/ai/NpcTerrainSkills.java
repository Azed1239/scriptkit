package dev.scriptkit.ai;

import dev.scriptkit.npc.BlockChangeLog;
import dev.scriptkit.npc.ScriptNpc;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Lets a stuck NPC dig and build toward its target like a player: mine blocks in the way (with the
 * right tool shown in hand and vanilla break times), pillar up by jumping and placing a block
 * underneath, and staircase down. Used by {@link NpcCombatGoal} only after normal pathfinding fails.
 * Every change is recorded in {@link BlockChangeLog} for {@code /army cleanup}.
 */
final class NpcTerrainSkills {
	enum Result { CONTINUE, RESOLVED, GIVE_UP }

	/** Give up after this many ticks without mining, placing or moving a block's distance. */
	private static final int GIVE_UP_TICKS = 200;
	private static final double MAX_REACH_SQ = 4.5 * 4.5;
	private static final List<ItemStack> TOOLS = List.of(
		new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_SHOVEL),
		new ItemStack(Items.IRON_AXE), new ItemStack(Items.IRON_HOE));

	private final ScriptNpc npc;
	private BlockPos miningPos;
	private ItemStack miningTool = ItemStack.EMPTY;
	private float progress;
	private int lastStage = -1;
	private BlockPos pillarBase;
	private int pillarTicks;
	private int noProgressTicks;
	private BlockPos anchor;
	private int pathCheck;

	NpcTerrainSkills(ScriptNpc npc) {
		this.npc = npc;
	}

	static boolean enabled() {
		CombatConfig cfg = CombatConfig.get();
		return cfg.allowBlockBreaking || cfg.allowBlockPlacing;
	}

	void start() {
		noProgressTicks = 0;
		anchor = npc.blockPosition();
		pathCheck = 20;
	}

	void stop() {
		cancelMining();
		pillarBase = null;
		npc.setDisplayItem(ItemStack.EMPTY);
	}

	Result tick(ServerLevel level, LivingEntity target) {
		if (!enabled()) {
			stop();
			return Result.GIVE_UP;
		}
		// Back to normal pathing as soon as a full path exists again.
		if (--pathCheck <= 0) {
			pathCheck = 20;
			Path path = npc.getNavigation().createPath(target, 0);
			if (path != null && path.canReach()) {
				stop();
				return Result.RESOLVED;
			}
		}
		if (!npc.blockPosition().closerThan(anchor, 1.0)) {
			anchor = npc.blockPosition();
			noProgressTicks = 0;
		}
		if (++noProgressTicks > GIVE_UP_TICKS) {
			stop();
			return Result.GIVE_UP;
		}
		npc.getNavigation().stop();

		if (miningPos != null) {
			continueMining(level);
			return Result.CONTINUE;
		}
		if (pillarBase != null) {
			continuePillar(level);
			return Result.CONTINUE;
		}
		decide(level, target);
		return Result.CONTINUE;
	}

	// ------------------------------------------------------------------ deciding

	private void decide(ServerLevel level, LivingEntity target) {
		CombatConfig cfg = CombatConfig.get();
		BlockPos feet = npc.blockPosition();
		double dy = target.getY() - npc.getY();
		double dx = target.getX() - npc.getX();
		double dz = target.getZ() - npc.getZ();
		boolean overhead = dx * dx + dz * dz < 1.0;

		// Target well above: pillar (clearing headroom first).
		if (dy > 1.5 && (cfg.allowBlockPlacing || overhead)) {
			BlockPos ceiling = feet.above(2);
			if (!isFree(level, ceiling)) {
				tryMine(level, ceiling);
				return;
			}
			if (cfg.allowBlockPlacing && npc.onGround()) {
				npc.getJumpControl().jump();
				pillarBase = feet;
				pillarTicks = 0;
				npc.setDisplayItem(new ItemStack(pillarState().getBlock().asItem()));
				return;
			}
			if (!cfg.allowBlockPlacing && overhead) {
				return; // can't build up and nothing sideways to do; the give-up timer handles it
			}
		}
		if (overhead) {
			if (dy < -1.5) {
				tryMine(level, feet.below()); // straight down
			}
			return;
		}

		Direction primary = Math.abs(dx) > Math.abs(dz)
			? (dx > 0 ? Direction.EAST : Direction.WEST)
			: (dz > 0 ? Direction.SOUTH : Direction.NORTH);
		Direction secondary = Math.abs(dx) > Math.abs(dz)
			? (dz > 0 ? Direction.SOUTH : Direction.NORTH)
			: (dx > 0 ? Direction.EAST : Direction.WEST);
		if (!advance(level, feet, primary, dy)) {
			advance(level, feet, secondary, dy);
		}
	}

	/** Tries to make one step of progress in {@code dir}. Returns false if blocked by something unbreakable. */
	private boolean advance(ServerLevel level, BlockPos feet, Direction dir, double dy) {
		BlockPos ahead = feet.relative(dir);
		if (dy < -1.5) {
			// Staircase down: clear head, feet and the step below, then walk down into it.
			for (BlockPos p : new BlockPos[] {ahead.above(), ahead, ahead.below()}) {
				if (!isFree(level, p)) {
					return tryMine(level, p);
				}
			}
			walkTo(ahead.below());
			return true;
		}
		// Step up onto a single block if there is room to jump.
		if (dy > 0.5 && !isFree(level, ahead) && isFree(level, ahead.above()) && isFree(level, ahead.above(2))
			&& isFree(level, feet.above(2))) {
			walkTo(ahead.above());
			return true;
		}
		// Tunnel: clear the head block, then the feet block.
		for (BlockPos p : new BlockPos[] {ahead.above(), ahead}) {
			if (!isFree(level, p)) {
				return tryMine(level, p);
			}
		}
		walkTo(ahead);
		return true;
	}

	private void walkTo(BlockPos pos) {
		npc.getMoveControl().setWantedPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
		npc.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
	}

	// ------------------------------------------------------------------ mining

	private boolean tryMine(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!canBreak(level, pos, state) || npc.distanceToSqr(pos.getCenter()) > MAX_REACH_SQ) {
			return false;
		}
		miningPos = pos.immutable();
		miningTool = bestTool(state);
		progress = 0.0F;
		lastStage = -1;
		npc.setDisplayItem(miningTool);
		return true;
	}

	private void continueMining(ServerLevel level) {
		BlockState state = level.getBlockState(miningPos);
		if (state.isAir() || !canBreak(level, miningPos, state) || npc.distanceToSqr(miningPos.getCenter()) > MAX_REACH_SQ) {
			cancelMining();
			return;
		}
		npc.getLookControl().setLookAt(miningPos.getX() + 0.5, miningPos.getY() + 0.5, miningPos.getZ() + 0.5);
		if (npc.tickCount % 5 == 0) {
			npc.swing(InteractionHand.MAIN_HAND);
		}
		progress += breakSpeed(level, miningPos, state, miningTool);
		if (progress >= 1.0F) {
			BlockPos pos = miningPos;
			level.destroyBlockProgress(npc.getId(), pos, -1);
			BlockChangeLog.record(level, pos, state, BlockChangeLog.air());
			level.destroyBlock(pos, CombatConfig.get().dropMinedBlocks, npc, 512);
			miningPos = null;
			npc.setDisplayItem(ItemStack.EMPTY);
			noProgressTicks = 0;
			return;
		}
		int stage = (int) (progress * 10.0F);
		if (stage != lastStage) {
			level.destroyBlockProgress(npc.getId(), miningPos, stage);
			lastStage = stage;
		}
	}

	private void cancelMining() {
		if (miningPos != null && npc.level() instanceof ServerLevel level) {
			level.destroyBlockProgress(npc.getId(), miningPos, -1);
		}
		miningPos = null;
	}

	/** Vanilla player break speed per tick: tool speed / hardness / (30 if correct tool, else 100). */
	private static float breakSpeed(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool) {
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness <= 0.0F) {
			return hardness == 0.0F ? 1.0F : 0.0F;
		}
		float speed = tool.isEmpty() ? 1.0F : tool.getDestroySpeed(state);
		boolean correct = !state.requiresCorrectToolForDrops() || (!tool.isEmpty() && tool.isCorrectToolForDrops(state));
		return speed / hardness / (correct ? 30.0F : 100.0F);
	}

	private static ItemStack bestTool(BlockState state) {
		ItemStack best = ItemStack.EMPTY;
		float bestSpeed = 1.0F;
		for (ItemStack tool : TOOLS) {
			float s = tool.getDestroySpeed(state);
			if (s > bestSpeed) {
				best = tool;
				bestSpeed = s;
			}
		}
		return best;
	}

	/** Never: air, fluids, blocks with storage/data, unbreakable or too-hard blocks, or anything next to a fluid. */
	static boolean canBreak(ServerLevel level, BlockPos pos, BlockState state) {
		CombatConfig cfg = CombatConfig.get();
		if (!cfg.allowBlockBreaking || state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) {
			return false;
		}
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0.0F || hardness > cfg.maxBreakHardness || !level.getWorldBorder().isWithinBounds(pos)) {
			return false;
		}
		for (Direction d : Direction.values()) {
			if (!level.getFluidState(pos.relative(d)).isEmpty()) {
				return false; // don't open a hole into water or lava
			}
		}
		return true;
	}

	private static boolean isFree(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	// ------------------------------------------------------------------ pillaring

	private void continuePillar(ServerLevel level) {
		pillarTicks++;
		BlockState current = level.getBlockState(pillarBase);
		if (npc.getY() >= pillarBase.getY() + 1.05 && current.canBeReplaced() && current.getFluidState().isEmpty()) {
			BlockState block = pillarState();
			AABB space = new AABB(pillarBase);
			if (level.getEntitiesOfClass(LivingEntity.class, space, e -> e != npc).isEmpty()
				&& level.isUnobstructed(block, pillarBase, CollisionContext.empty())) {
				BlockChangeLog.record(level, pillarBase, current, block);
				level.setBlock(pillarBase, block, Block.UPDATE_ALL);
				level.gameEvent(npc, GameEvent.BLOCK_PLACE, pillarBase);
				SoundType sound = block.getSoundType();
				level.playSound(null, pillarBase, sound.getPlaceSound(), SoundSource.BLOCKS,
					(sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
				npc.swing(InteractionHand.MAIN_HAND);
				noProgressTicks = 0;
			}
			pillarBase = null;
			npc.setDisplayItem(ItemStack.EMPTY);
		} else if (pillarTicks > 15) {
			pillarBase = null; // jump didn't get high enough (blocked); decide again
			npc.setDisplayItem(ItemStack.EMPTY);
		}
	}

	private static BlockState pillarState() {
		Identifier id = Identifier.tryParse(CombatConfig.get().pillarBlock);
		Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
		if (block == null || block.defaultBlockState().isAir()) {
			block = Blocks.COBBLESTONE;
		}
		return block.defaultBlockState();
	}
}
