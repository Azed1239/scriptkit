package dev.scriptkit.client.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.scriptkit.npc.ScriptNpc;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Draws a {@link ScriptNpc} with the vanilla player model and a skin looked up by username.
 * Holds a wide-arm and a slim-arm renderer and picks one per frame from the resolved skin.
 * All version-specific rendering code for NPCs lives in this package.
 */
public class NpcRenderer extends EntityRenderer<ScriptNpc, NpcRenderer.State> {
	private final Part wide;
	private final Part slim;

	public NpcRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.wide = new Part(context, false);
		this.slim = new Part(context, true);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ScriptNpc npc, State state, float partialTick) {
		PlayerSkin skin = NpcSkins.skinFor(npc.getSkinName());
		state.slimModel = skin.model() == PlayerModelType.SLIM;
		(state.slimModel ? slim : wide).extractRenderState(npc, state, partialTick);
		state.skin = skin;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		(state.slimModel ? slim : wide).submit(state, poseStack, collector, camera);
	}

	public static class State extends AvatarRenderState {
		public boolean slimModel;
	}

	/** One player-model renderer (wide or slim arms). */
	private static final class Part extends LivingEntityRenderer<ScriptNpc, AvatarRenderState, PlayerModel> {
		Part(EntityRendererProvider.Context context, boolean slim) {
			super(context, new PlayerModel(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim), 0.5F);
			ArmorModelSet<PlayerModel> armor = ArmorModelSet.bake(
				slim ? ModelLayers.PLAYER_SLIM_ARMOR : ModelLayers.PLAYER_ARMOR,
				context.getModelSet(),
				part -> new PlayerModel(part, slim));
			this.addLayer(new HumanoidArmorLayer<>(this, armor, context.getEquipmentRenderer()));
			this.addLayer(new PlayerItemInHandLayer<>(this));
		}

		@Override
		public AvatarRenderState createRenderState() {
			return new AvatarRenderState();
		}

		@Override
		public void extractRenderState(ScriptNpc npc, AvatarRenderState state, float partialTick) {
			super.extractRenderState(npc, state, partialTick);
			HumanoidMobRenderer.extractHumanoidRenderState(npc, state, partialTick, this.itemModelResolver);
			state.showHat = true;
			state.showJacket = true;
			state.showLeftSleeve = true;
			state.showRightSleeve = true;
			state.showLeftPants = true;
			state.showRightPants = true;
			state.showCape = false;
		}

		@Override
		public Identifier getTextureLocation(AvatarRenderState state) {
			return state.skin.body().texturePath();
		}

		@Override
		protected void scale(AvatarRenderState state, PoseStack poseStack) {
			poseStack.scale(0.9375F, 0.9375F, 0.9375F);
		}
	}
}
