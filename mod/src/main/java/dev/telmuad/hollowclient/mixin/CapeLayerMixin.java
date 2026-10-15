package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.telmuad.hollowclient.render.CosmeticsLayer;
import dev.telmuad.hollowclient.styles.AnimatedCapes;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Cape effects for your own cape: animated textures are brought up to date, glow draws the cape at full brightness,
 * and shimmer adds a second, see-through pass with a moving theme sheen over the same model.
 */
@Mixin(CapeLayer.class)
abstract class CapeLayerMixin {
	@WrapOperation(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
	private void hollowclient$capeFx(SubmitNodeCollector collector, Model<?> model, Object state, PoseStack pose, RenderType type, int light,
									 int overlay, int outline, ModelFeatureRenderer.CrumblingOverlay crumbling, Operation<Void> original) {
		CosmeticsLayer.CapeFx fx = state instanceof AvatarRenderState avatar ? avatar.getData(CosmeticsLayer.CAPE_FX) : null;
		if (fx == null) {
			original.call(collector, model, state, pose, type, light, overlay, outline, crumbling);
			return;
		}
		if (state instanceof AvatarRenderState avatar && avatar.skin.cape() != null) AnimatedCapes.update(avatar.skin.cape().texturePath());
		int lit = fx.glow() > 0f ? glowLight(light, fx.glow()) : light;
		original.call(collector, model, state, pose, type, lit, overlay, outline, crumbling);
		if (fx.shimmer() > 0f) {
			@SuppressWarnings({"unchecked", "rawtypes"})
			Model<Object> m = (Model) model;
			collector.submitModel(m, state, pose, RenderTypes.entityTranslucentEmissive(AnimatedCapes.shimmerTexture(fx.shimmer())),
					LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1, null, outline, null);
		}
	}

	/** Raise the light to at least block 7..15 (by glow strength) and full sky light. */
	private static int glowLight(int light, float glow) {
		int block = Math.max(LightTexture.block(light), 7 + Math.round(8 * glow));
		return LightTexture.pack(Math.min(15, block), 15);
	}
}
