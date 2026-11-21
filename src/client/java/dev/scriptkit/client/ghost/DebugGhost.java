package dev.scriptkit.client.ghost;

import dev.scriptkit.ScriptKitMod;
import dev.scriptkit.network.GhostPayload;
import dev.scriptkit.platform.ItemRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Developer smoke test for the Command Stick ghosts ({@code -Dscriptkit.debug.ghost=true}): opens the
 * throwaway world "ScriptKitSmoke", holds a stick locally, feeds in a fake army formation, looks at
 * the ground, saves screenshots (run/screenshots) and quits. Never used in normal play.
 */
public final class DebugGhost {
	private static int phase;
	private static int wait;

	private DebugGhost() {}

	public static void init() {
		if (Boolean.getBoolean("scriptkit.debug.ghost")) {
			ClientTickEvents.END_CLIENT_TICK.register(DebugGhost::tick);
			ScriptKitMod.LOGGER.info("Ghost smoke test enabled");
		}
	}

	private static List<GhostPayload.Slot> formation(int n) {
		List<GhostPayload.Slot> slots = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			double a = 2 * Math.PI * i / n;
			UUID id = UUID.nameUUIDFromBytes(("ghost" + i).getBytes());
			slots.add(new GhostPayload.Slot("", id.getMostSignificantBits(), id.getLeastSignificantBits(),
				(float) (Math.cos(a) * 3.5), (float) (Math.sin(a) * 3.5)));
		}
		return slots;
	}

	private static void shot(Minecraft mc, String name) {
		Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), 1, m -> {});
	}

	private static void tick(Minecraft mc) {
		switch (phase) {
			case 0 -> {
				if (mc.screen instanceof TitleScreen && ++wait > 40) {
					ScriptKitMod.LOGGER.info("Ghost smoke: opening world ScriptKitSmoke");
					mc.createWorldOpenFlows().openWorld("ScriptKitSmoke", () -> {});
					phase = 1;
					wait = 0;
				}
			}
			case 1 -> {
				if (mc.player != null && mc.level != null && mc.screen == null && ++wait > 100) {
					ScriptKitMod.LOGGER.info("Ghost smoke: in world, holding the stick and aiming at the ground");
					mc.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.COMMAND_STICK));
					mc.player.setXRot(35.0F);
					ArmyGhosts.setSlotsForTest(formation(12));
					phase = 2;
					wait = 0;
				}
			}
			case 2 -> {
				if (++wait == 60) {
					ScriptKitMod.LOGGER.info("Ghost smoke: {} ghosts in the world", ArmyGhosts.ghostCount());
					shot(mc, "ghost-1.png");
				} else if (wait == 80) {
					mc.player.setYRot(mc.player.getYRot() + 70.0F);
					mc.player.setXRot(25.0F);
				} else if (wait == 140) {
					shot(mc, "ghost-2.png");
					mc.player.setXRot(-60.0F); // look at the sky: no ground to aim at, ghosts must disappear
				} else if (wait == 170) {
					ScriptKitMod.LOGGER.info("Ghost smoke: aiming at the sky leaves {} ghosts (expect 0)", ArmyGhosts.ghostCount());
					mc.player.setXRot(35.0F);
				} else if (wait == 200) {
					ScriptKitMod.LOGGER.info("Ghost smoke: aiming back down shows {} ghosts (expect 12)", ArmyGhosts.ghostCount());
					ArmyGhosts.setSlotsForTest(List.of());
				} else if (wait == 230) {
					ScriptKitMod.LOGGER.info("Ghost smoke: after clearing the formation {} ghosts remain (expect 0)", ArmyGhosts.ghostCount());
					ScriptKitMod.LOGGER.info("Ghost smoke test finished");
					mc.stop();
				}
			}
			default -> {
			}
		}
	}
}
