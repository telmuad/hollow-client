package dev.telmuad.hollowclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.telmuad.hollowclient.modules.styles.HaloModule;
import dev.telmuad.hollowclient.modules.styles.HornsModule;
import dev.telmuad.hollowclient.modules.styles.PetModule;
import dev.telmuad.hollowclient.render.CosmeticModels.Hat;
import dev.telmuad.hollowclient.render.CosmeticModels.Wings;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * Draws the hat, wings, halo, horns and pet on the local player only. {@code AvatarRendererMixin} puts a
 * {@link Look} on the local player's render state; every other player gets none, so nothing shows on them.
 */
public final class CosmeticsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** What to draw, captured when the render state is filled in. Null fields = that piece is off. */
	public record Look(Hat hat, int hatTint, Wings wings, int wingsTint, float flapSpeed,
					   HaloModule.Style halo, int haloColor, float haloGlow, float haloSpin,
					   HornsModule.Style horns, int hornsTint, PetModule.Style pet, int petTint) {
		public boolean any() {
			return hat != null || wings != null || halo != null || horns != null || pet != null;
		}
	}

	public static final RenderStateDataKey<Look> LOOK = RenderStateDataKey.create(() -> "hollowclient:look");

	/** Cape effects for the local player: glow (0..1) and theme shimmer (0..1). */
	public record CapeFx(float glow, float shimmer) {
	}

	public static final RenderStateDataKey<CapeFx> CAPE_FX = RenderStateDataKey.create(() -> "hollowclient:cape_fx");

	public CosmeticsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		Look look = state.getData(LOOK);
		if (look == null || state.isInvisible) return;
		if (look.hat() != null) hat(pose, collector, light, state, look);
		if (look.horns() != null) horns(pose, collector, light, state, look);
		if (look.halo() != null) halo(pose, collector, light, state, look);
		if (look.wings() != null) wings(pose, collector, light, state, look);
		if (look.pet() != null) pet(pose, collector, light, state, look);
	}

	private void part(SubmitNodeCollector collector, ModelPart part, PoseStack pose, RenderType type, int light, int color) {
		collector.submitModelPart(part, pose, type, light, OverlayTexture.NO_OVERLAY, null, false, false, color, null, 0);
	}

	private void hat(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, Look look) {
		pose.pushPose();
		getParentModel().head.translateAndRotate(pose);
		if (!state.headEquipment.isEmpty()) {
			// Sit on top of a helmet instead of inside it.
			pose.translate(0f, -0.07f, 0f);
			pose.scale(1.14f, 1.14f, 1.14f);
		}
		part(collector, CosmeticModels.hat(look.hat()), pose, RenderTypes.entityCutoutNoCull(look.hat().texture()), light, look.hatTint());
		pose.popPose();
	}

	private void horns(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, Look look) {
		pose.pushPose();
		getParentModel().head.translateAndRotate(pose);
		if (!state.headEquipment.isEmpty()) pose.scale(1.1f, 1.1f, 1.1f);
		part(collector, CosmeticModels.horns(look.horns()), pose, RenderTypes.entityCutoutNoCull(CosmeticModels.HORNS), light, look.hornsTint());
		pose.popPose();
	}

	private void halo(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, Look look) {
		pose.pushPose();
		getParentModel().head.translateAndRotate(pose);
		float t = state.ageInTicks;
		pose.translate(0f, (state.headEquipment.isEmpty() ? 0f : -0.1f) + Mth.sin(t * 0.08f) * 0.035f, 0f);
		if (look.haloSpin() > 0f) pose.mulPose(Axis.YP.rotation(t * 0.04f * look.haloSpin()));
		pose.mulPose(Axis.XP.rotation(0.12f)); // a slight tilt reads better than perfectly flat
		boolean glow = look.haloGlow() > 0f;
		float pulse = glow ? 0.75f + 0.25f * Mth.sin(t * 0.12f) : 1f;
		int color = ColorUtil.fade(look.haloColor(), glow ? (0.55f + 0.45f * look.haloGlow()) * pulse : 1f);
		RenderType type = glow ? RenderTypes.entityTranslucentEmissive(CosmeticModels.GLOW_WHITE) : RenderTypes.entityCutoutNoCull(CosmeticModels.GLOW_WHITE);
		part(collector, CosmeticModels.halo(look.halo()), pose, type, glow ? LightTexture.FULL_BRIGHT : light, color);
		pose.popPose();
	}

	private void wings(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, Look look) {
		// Gentle idle flap; faster and wider while falling or gliding.
		float speed = look.flapSpeed();
		boolean airborne = state.isFallFlying || state.fallFlyingTimeInTicks > 0;
		float rate = airborne ? 0.35f : 0.09f;
		float amp = airborne ? 0.45f : 0.18f;
		float flap = speed <= 0 ? 0 : Mth.sin(state.ageInTicks * rate * speed) * amp;
		float spread = 0.42f + flap;

		pose.pushPose();
		getParentModel().body.translateAndRotate(pose);
		for (int side = -1; side <= 1; side += 2) {
			boolean left = side > 0;
			pose.pushPose();
			// Root of the wing: on the back, a little out from the spine, at shoulder-blade height.
			pose.translate(side * 1.0f / 16f, 1.0f / 16f, 2.3f / 16f);
			pose.mulPose(Axis.YP.rotation(left ? -spread : spread));
			// Tilt so the leading edge rises up and out from the shoulder and the feathers hang below it.
			pose.mulPose(Axis.ZP.rotation(left ? -0.75f : 0.75f));
			part(collector, CosmeticModels.wing(left), pose, RenderTypes.entityCutoutNoCull(look.wings().texture()), light, look.wingsTint());
			pose.popPose();
		}
		pose.popPose();
	}

	/** The pet floats above your right shoulder, bobbing and slowly turning; it doesn't follow your body's lean. */
	private void pet(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, Look look) {
		float t = state.ageInTicks;
		pose.pushPose();
		pose.translate(-13f / 16f, -6f / 16f + Mth.sin(t * 0.1f) * 0.06f, 2f / 16f);
		switch (look.pet()) {
			case WISP -> {
				int glow = ColorUtil.mix(dev.telmuad.hollowclient.gui.Theme.accent(), look.petTint(), 0.4f);
				pose.pushPose();
				pose.mulPose(Axis.YP.rotation(t * 0.06f));
				pose.mulPose(Axis.XP.rotation(t * 0.04f));
				part(collector, CosmeticModels.wispCore(), pose, RenderTypes.entityTranslucentEmissive(CosmeticModels.GLOW_WHITE),
						LightTexture.FULL_BRIGHT, ColorUtil.fade(glow, 0.85f));
				pose.popPose();
				for (int i = 0; i < 3; i++) {
					float a = t * 0.15f + i * (float) (Math.PI * 2 / 3);
					pose.pushPose();
					pose.translate(Mth.cos(a) * 0.28f, Mth.sin(a * 1.3f) * 0.08f, Mth.sin(a) * 0.28f);
					part(collector, CosmeticModels.wispMote(), pose, RenderTypes.entityTranslucentEmissive(CosmeticModels.GLOW_WHITE),
							LightTexture.FULL_BRIGHT, ColorUtil.fade(ColorUtil.mix(glow, 0xFFFFFFFF, 0.4f), 0.9f));
					pose.popPose();
				}
			}
			case CUBE -> {
				pose.mulPose(Axis.YP.rotation(Mth.sin(t * 0.03f) * 0.5f));
				pose.scale(0.55f, 0.55f, 0.55f);
				part(collector, CosmeticModels.petCube(), pose, RenderTypes.entityCutoutNoCull(CosmeticModels.PET_CUBE), light, look.petTint());
			}
		}
		pose.popPose();
	}
}
