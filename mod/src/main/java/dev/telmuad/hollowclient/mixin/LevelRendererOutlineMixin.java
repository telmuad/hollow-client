package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.telmuad.hollowclient.modules.visual.BlockOutlineModule;
import dev.telmuad.hollowclient.render.OutlineFx;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block Outline: a see-through fill on the outlined block and the color/width of the normal outline. The
 * high-contrast outline (accessibility option) is left alone.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererOutlineMixin {
	@Inject(method = "submitBlockOutline", at = @At("HEAD"))
	private void hollowclient$fill(PoseStack pose, SubmitNodeCollector collector, LevelRenderState state, CallbackInfo ci) {
		BlockOutlineRenderState outline = state.blockOutlineRenderState;
		if (outline == null || outline.highContrast()) return;
		OutlineFx.fill(collector, pose, outline, state.cameraRenderState.pos);
	}

	/** The second outline call draws the normal outline (the first is the high-contrast backing). */
	@WrapOperation(method = "submitBlockOutline", at = @At(value = "INVOKE", ordinal = 1,
			target = "Lnet/minecraft/client/renderer/LevelRenderer;submitHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/state/level/BlockOutlineRenderState;IFZ)V"))
	private void hollowclient$outline(LevelRenderer self, PoseStack pose, SubmitNodeCollector collector, RenderType type,
									  BlockOutlineRenderState state, int color, float width, boolean afterTerrain, Operation<Void> original) {
		BlockOutlineModule m = OutlineFx.module();
		if (m == null || state.highContrast()) {
			original.call(self, pose, collector, type, state, color, width, afterTerrain);
			return;
		}
		original.call(self, pose, collector, type, state, OutlineFx.color(m), OutlineFx.width(m, width), afterTerrain);
	}
}
