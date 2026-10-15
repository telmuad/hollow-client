package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.telmuad.hollowclient.gui.LoadingStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The loading screen in Hollow's look. Only the drawing changes (background color, logo, progress bar); vanilla still
 * runs the loading, the fades and closing the overlay.
 */
@Mixin(LoadingOverlay.class)
abstract class LoadingOverlayMixin {
	@Shadow
	private float currentProgress;

	@ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
	private int hollowclient$background(int original) {
		return LoadingStyle.enabled() ? LoadingStyle.BACKGROUND : original;
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
	private void hollowclient$logo(GuiGraphics g, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v,
								   int w, int h, int uw, int vh, int tw, int th, int color, Operation<Void> original) {
		if (!LoadingStyle.enabled()) {
			original.call(g, pipeline, texture, x, y, u, v, w, h, uw, vh, tw, th, color);
			return;
		}
		// vanilla draws its logo in two halves; Hollow's logo is drawn once, on the first half
		if (u < 0f) LoadingStyle.drawLogo(g, ((color >>> 24) & 255) / 255f);
	}

	@Inject(method = "drawProgressBar", at = @At("HEAD"), cancellable = true)
	private void hollowclient$progressBar(GuiGraphics g, int x0, int y0, int x1, int y1, float alpha, CallbackInfo ci) {
		if (!LoadingStyle.enabled()) return;
		LoadingStyle.drawBar(g, x0, y0, x1, y1, alpha, currentProgress);
		ci.cancel();
	}
}
