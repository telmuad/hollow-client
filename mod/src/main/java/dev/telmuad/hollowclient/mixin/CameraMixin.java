package dev.telmuad.hollowclient.mixin;

import dev.telmuad.hollowclient.camera.CameraPath;
import dev.telmuad.hollowclient.camera.CameraPaths;
import dev.telmuad.hollowclient.modules.Modules;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hand Animations → 1.7 instant sneak: the camera height jumps to the target instead of gliding. Camera Paths: while a
 * singleplayer flight plays, the camera takes the path's position and direction (only the picture moves).
 */
@Mixin(Camera.class)
abstract class CameraMixin {
	@Shadow
	private Entity entity;
	@Shadow
	private float eyeHeight;
	@Shadow
	private float eyeHeightOld;

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Inject(method = "tick", at = @At("TAIL"))
	private void hollowclient$instantSneak(CallbackInfo ci) {
		var m = Modules.HAND_ANIMATIONS;
		if (entity == null || m == null || !m.isEnabled() || !m.instantSneak()) return;
		eyeHeight = entity.getEyeHeight();
		eyeHeightOld = eyeHeight;
	}

	/** Right after vanilla places the camera on the player, and before the culling frustum is worked out from it. */
	@Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
	private void hollowclient$cameraPath(DeltaTracker deltaTracker, CallbackInfo ci) {
		CameraPath.Pose pose = CameraPaths.pose();
		if (pose == null) return;
		setRotation(pose.yaw(), pose.pitch());
		setPosition(new Vec3(pose.x(), pose.y(), pose.z()));
	}
}
