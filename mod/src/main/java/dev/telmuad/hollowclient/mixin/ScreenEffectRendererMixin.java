package dev.telmuad.hollowclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.telmuad.hollowclient.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Low fire (moves the first-person fire overlay down) and Clear Water (skips the underwater overlay). Visual only. */
@Mixin(ScreenEffectRenderer.class)
abstract class ScreenEffectRendererMixin {
	@ModifyArg(method = "renderFire", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"), index = 1)
	private static float hollowclient$lowFire(float y) {
		return Modules.LOW_FIRE == null ? y : y - Modules.LOW_FIRE.offset();
	}

	@Inject(method = "renderWater", at = @At("HEAD"), cancellable = true)
	private static void hollowclient$clearWater(Minecraft minecraft, PoseStack pose, MultiBufferSource buffers, CallbackInfo ci) {
		if (Modules.CLEAR_WATER != null && Modules.CLEAR_WATER.hideOverlay()) ci.cancel();
	}
}
