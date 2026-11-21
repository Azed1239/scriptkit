package dev.scriptkit.client.ghost;

import dev.scriptkit.client.platform.ClientNetworking;
import dev.scriptkit.network.GhostPayload;
import dev.scriptkit.npc.ScriptNpc;
import dev.scriptkit.platform.ItemRegistry;
import dev.scriptkit.platform.NpcRegistry;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Command Stick's placement preview. While you hold the stick with an army selected, translucent
 * ghost copies of that army (same formation, same skins) are drawn where it would stand if you
 * right-clicked where you are aiming. Slots with no ground are tinted red. The ghosts are client-only
 * entities that are never sent to the server and can't be hit, pushed or targeted.
 */
public final class ArmyGhosts {
	private static final double RANGE = 96.0;
	private static final int FIRST_GHOST_ID = -1000; // negative ids can never clash with real entity ids

	private static List<GhostPayload.Slot> slots = List.of();
	private static final List<ScriptNpc> GHOSTS = new ArrayList<>();
	private static ClientLevel ghostLevel;

	private ArmyGhosts() {}

	public static void init() {
		ClientNetworking.registerGhostReceiver(ArmyGhosts::onSlots);
		ClientTickEvents.END_CLIENT_TICK.register(ArmyGhosts::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			slots = List.of();
			GHOSTS.clear();
			ghostLevel = null;
		});
	}

	private static void onSlots(List<GhostPayload.Slot> newSlots) {
		slots = newSlots;
		removeGhosts(); // rebuilt on the next tick if still needed
	}

	/** For tests: how many ghosts are currently in the world. */
	public static int ghostCount() {
		return GHOSTS.size();
	}

	/** For tests: pretend the server just sent this formation. */
	public static void setSlotsForTest(List<GhostPayload.Slot> newSlots) {
		onSlots(newSlots);
	}

	private static void tick(Minecraft mc) {
		if (mc.level != ghostLevel) {
			GHOSTS.clear(); // the old level's entities are gone with it
			ghostLevel = mc.level;
		}
		if (slots.isEmpty() || mc.level == null || mc.player == null || mc.screen != null
			|| ItemRegistry.COMMAND_STICK == null || !mc.player.getMainHandItem().is(ItemRegistry.COMMAND_STICK)) {
			removeGhosts();
			return;
		}
		Vec3 eye = mc.player.getEyePosition();
		Vec3 end = eye.add(mc.player.getViewVector(1.0F).scale(RANGE));
		BlockHitResult hit = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			removeGhosts();
			return;
		}
		if (GHOSTS.size() != slots.size()) {
			spawnGhosts(mc);
		}
		Vec3 target = hit.getLocation();
		float yaw = mc.player.getYRot();
		for (int i = 0; i < GHOSTS.size(); i++) {
			GhostPayload.Slot s = slots.get(i);
			ScriptNpc g = GHOSTS.get(i);
			double x = target.x + s.dx();
			double z = target.z + s.dz();
			double y = groundY(mc.level, x, target.y, z);
			boolean blocked = Double.isNaN(y);
			if (blocked) {
				y = target.y;
			}
			g.setGhost(true, blocked);
			g.snapTo(x, y, z, yaw, 0.0F);
			g.setYHeadRot(yaw);
			g.setYBodyRot(yaw);
			g.xo = x;
			g.yo = y;
			g.zo = z;
			g.yRotO = yaw;
			g.yBodyRotO = yaw;
			g.yHeadRotO = yaw;
		}
	}

	private static void spawnGhosts(Minecraft mc) {
		removeGhosts();
		for (int i = 0; i < slots.size(); i++) {
			GhostPayload.Slot s = slots.get(i);
			ScriptNpc g = new ScriptNpc(NpcRegistry.NPC_TYPE, mc.level);
			g.setId(FIRST_GHOST_ID - i);
			g.setGhost(true, false);
			g.setCustomNameVisible(false); // an NPC normally shows its name; a ghost has none
			g.setSkinName(s.skin());
			g.setSkinSeed(s.id());
			g.noPhysics = true;
			g.setNoGravity(true);
			g.setInvulnerable(true);
			mc.level.addEntity(g);
			GHOSTS.add(g);
		}
	}

	private static void removeGhosts() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null && mc.level == ghostLevel) {
			for (ScriptNpc g : GHOSTS) {
				mc.level.removeEntity(g.getId(), Entity.RemovalReason.DISCARDED);
			}
		}
		GHOSTS.clear();
	}

	/**
	 * A standing height near {@code originY}, mirroring where the server would put the NPC: climb out of
	 * solid blocks, then drop to the first floor. NaN means there is nowhere to stand (shown as red).
	 */
	private static double groundY(ClientLevel level, double x, double originY, double z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Math.floor(x), Math.floor(originY), Math.floor(z));
		if (!level.isLoaded(pos)) {
			return Double.NaN;
		}
		for (int i = 0; i < 24 && !(isFree(level, pos) && isFree(level, pos.above())); i++) {
			pos.move(0, 1, 0);
		}
		if (!isFree(level, pos) || !isFree(level, pos.above())) {
			return Double.NaN;
		}
		for (int i = 0; i < 12; i++) {
			if (!isFree(level, pos.below())) {
				return pos.getY();
			}
			pos.move(0, -1, 0);
		}
		return Double.NaN;
	}

	private static boolean isFree(ClientLevel level, BlockPos p) {
		return level.getBlockState(p).getCollisionShape(level, p).isEmpty();
	}
}
