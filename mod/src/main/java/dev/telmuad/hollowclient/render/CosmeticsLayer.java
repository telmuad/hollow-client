package dev.telmuad.hollowclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.telmuad.hollowclient.render.CosmeticModels.Hat;
import dev.telmuad.hollowclient.render.CosmeticModels.Wings;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * Draws the hat and wings on the local player only. {@code AvatarRendererMixin} puts a {@link Look} on the local
 * player's render state; every other player gets none, so nothing shows on them.
 */
public final class CosmeticsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** What to draw, captured when the render state is filled in. Null fields = that piece is off. */
	public record Look(Hat hat, int hatTint, Wings wings, int wingsTint, float flapSpeed) {
	}

	public static final RenderStateDataKey<Look> LOOK = RenderStateDataKey.create(() -> "hollowclient:look");

	public CosmeticsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		Look look = state.getData(LOOK);
		if (look == null || state.isInvisible) return;
		if (look.hat() != null) hat(pose, collector, light, state, look);
		if (look.wings() != null) wings(pose, collector, light, state, look);
	}

	private void hat(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, Look look) {
		pose.pushPose();
		getParentModel().head.translateAndRotate(pose);
		if (!state.headEquipment.isEmpty()) {
			// Sit on top of a helmet instead of inside it.
			pose.translate(0f, -0.07f, 0f);
			pose.scale(1.14f, 1.14f, 1.14f);
		}
		collector.submitModelPart(CosmeticModels.hat(look.hat()), pose, RenderTypes.entityCutoutNoCull(look.hat().texture()),
				light, OverlayTexture.NO_OVERLAY, null, false, false, look.hatTint(), null, 0);
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
			collector.submitModelPart(CosmeticModels.wing(left), pose, RenderTypes.entityCutoutNoCull(look.wings().texture()),
					light, OverlayTexture.NO_OVERLAY, null, false, false, look.wingsTint(), null, 0);
			pose.popPose();
		}
		pose.popPose();
	}
}
