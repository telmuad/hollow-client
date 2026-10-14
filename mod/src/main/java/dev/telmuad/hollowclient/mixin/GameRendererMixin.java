package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.telmuad.hollowclient.modules.Modules;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Zoom: divides the field of view while the zoom key is held. Only the world FOV, not the hand. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	@ModifyReturnValue(method = "getFov", at = @At("RETURN"))
	private float hollowclient$zoom(float fov, Camera camera, float partialTick, boolean useFovSetting) {
		if (!useFovSetting || Modules.ZOOM == null) return fov;
		return (float) (fov * Modules.ZOOM.fovMultiplier());
	}
}
