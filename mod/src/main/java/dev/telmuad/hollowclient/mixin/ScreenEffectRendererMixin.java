package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.telmuad.hollowclient.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Low fire (moves the first-person fire overlay down) and Clear Water (skips the underwater overlay). Visual only. */
@Mixin(ScreenEffectRenderer.class)
abstract class ScreenEffectRendererMixin {
	@WrapOperation(method = "submitFire", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V"))
	private static void hollowclient$lowFire(SubmitNodeCollector collector, PoseStack pose, RenderType type,
											 SubmitNodeCollector.CustomGeometryRenderer renderer, Operation<Void> original) {
		float offset = Modules.LOW_FIRE == null ? 0f : Modules.LOW_FIRE.offset();
		if (offset == 0f) {
			original.call(collector, pose, type, renderer);
			return;
		}
		pose.pushPose();
		pose.translate(0f, -offset, 0f);
		original.call(collector, pose, type, renderer);
		pose.popPose();
	}

	@Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
	private static void hollowclient$clearWater(Minecraft minecraft, PoseStack pose, SubmitNodeCollector collector, CallbackInfo ci) {
		if (Modules.CLEAR_WATER != null && Modules.CLEAR_WATER.hideOverlay()) ci.cancel();
	}
}
