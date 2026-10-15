package dev.telmuad.hollowclient.mixin;

import dev.telmuad.hollowclient.modules.Modules;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hand Animations → 1.7 instant sneak: the camera height jumps to the target instead of gliding. */
@Mixin(Camera.class)
abstract class CameraMixin {
	@Shadow
	private Entity entity;
	@Shadow
	private float eyeHeight;
	@Shadow
	private float eyeHeightOld;

	@Inject(method = "tick", at = @At("TAIL"))
	private void hollowclient$instantSneak(CallbackInfo ci) {
		var m = Modules.HAND_ANIMATIONS;
		if (entity == null || m == null || !m.isEnabled() || !m.instantSneak()) return;
		eyeHeight = entity.getEyeHeight();
		eyeHeightOld = eyeHeight;
	}
}
