package dev.scriptkit.item;

import dev.scriptkit.npc.ArmySelection;
import dev.scriptkit.npc.ScriptNpc;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Command Stick. Right-click an NPC (from up to 96 blocks away) to select its whole army, then
 * right-click the ground to move that army there with its formation intact. Shift+right-click
 * deselects. Selected armies are frozen and outlined; press G (toggle all armies) to start the fight.
 * Operators only. The actual work lives in {@link ArmySelection}.
 */
public class CommandStickItem extends Item {
	private static final double RANGE = 96.0;

	public CommandStickItem(Properties properties) {
		super(properties);
	}

	/** Right-clicking an NPC up close. */
	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
		if (player instanceof ServerPlayer sp && target instanceof ScriptNpc npc && allowed(sp)) {
			tell(sp, ArmySelection.select(sp.level().getServer(), sp.getUUID(), npc));
		}
		return InteractionResult.SUCCESS;
	}

	/** Right-clicking air or a block: aim at an NPC to select, at the ground to move, or sneak to deselect. */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS; // client: just swing the arm
		}
		if (!allowed(sp)) {
			return InteractionResult.FAIL;
		}
		if (sp.isShiftKeyDown()) {
			tell(sp, ArmySelection.deselect(serverLevel.getServer(), sp.getUUID()));
			return InteractionResult.SUCCESS;
		}

		Vec3 from = sp.getEyePosition();
		Vec3 to = from.add(sp.getViewVector(1.0F).scale(RANGE));
		BlockHitResult block = serverLevel.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(sp, from, end, new AABB(from, end).inflate(1.0),
			e -> e instanceof ScriptNpc && e.isAlive(), 0.3);

		if (entity != null) {
			tell(sp, ArmySelection.select(serverLevel.getServer(), sp.getUUID(), (ScriptNpc) entity.getEntity()));
		} else if (block.getType() == HitResult.Type.BLOCK) {
			tell(sp, ArmySelection.move(serverLevel, sp.getUUID(), block.getLocation(), sp.getYRot()));
		} else {
			tell(sp, new ArmySelection.Result(false, "Aim at an NPC to select its army, or at the ground to move it."));
		}
		return InteractionResult.SUCCESS;
	}

	private static boolean allowed(ServerPlayer player) {
		if (Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
			return true;
		}
		player.displayClientMessage(Component.literal("Only operators can use the Command Stick.").withStyle(ChatFormatting.RED), true);
		return false;
	}

	private static void tell(ServerPlayer player, ArmySelection.Result result) {
		player.displayClientMessage(Component.literal(result.message())
			.withStyle(result.ok() ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
	}
}
