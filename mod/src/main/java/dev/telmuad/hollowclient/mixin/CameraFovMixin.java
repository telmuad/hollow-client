package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.telmuad.hollowclient.modules.Modules;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Zoom: divides the field of view while the zoom key is held. Only the world FOV ({@code calculateFov}), not the
 * hand's ({@code calculateHudFov}). In 26.x the FOV lives in {@link Camera}, not GameRenderer.
 */
@Mixin(Camera.class)
abstract class CameraFovMixin {
	@ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
	private float hollowclient$zoom(float fov) {
		if (Modules.ZOOM == null) return fov;
		return (float) (fov * Modules.ZOOM.fovMultiplier());
	}
}
