package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.telmuad.hollowclient.modules.visual.BlockOutlineModule;
import dev.telmuad.hollowclient.render.OutlineFx;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block Outline: a see-through fill (drawn before vanilla takes its line buffer) and the color/width of the normal
 * outline. The high-contrast outline (accessibility option) is left alone.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererOutlineMixin {
	@Inject(method = "renderBlockOutline", at = @At("HEAD"))
	private void hollowclient$fill(MultiBufferSource.BufferSource buffers, PoseStack pose, boolean translucentPass, LevelRenderState state, CallbackInfo ci) {
		BlockOutlineRenderState outline = state.blockOutlineRenderState;
		if (outline == null || outline.isTranslucent() != translucentPass || outline.highContrast()) return;
		OutlineFx.fill(buffers, pose, outline, state.cameraRenderState.pos);
	}

	/** The second outline call draws the normal outline (the first is the high-contrast backing). */
	@WrapOperation(method = "renderBlockOutline", at = @At(value = "INVOKE", ordinal = 1,
			target = "Lnet/minecraft/client/renderer/LevelRenderer;renderHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;DDDLnet/minecraft/client/renderer/state/BlockOutlineRenderState;IF)V"))
	private void hollowclient$outline(LevelRenderer self, PoseStack pose, VertexConsumer consumer, double x, double y, double z,
									  BlockOutlineRenderState state, int color, float width, Operation<Void> original) {
		BlockOutlineModule m = OutlineFx.module();
		if (m == null || state.highContrast()) {
			original.call(self, pose, consumer, x, y, z, state, color, width);
			return;
		}
		original.call(self, pose, consumer, x, y, z, state, OutlineFx.color(m), OutlineFx.width(m, width));
	}
}
