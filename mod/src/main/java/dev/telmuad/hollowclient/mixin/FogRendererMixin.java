package dev.telmuad.hollowclient.mixin;

import dev.telmuad.hollowclient.render.SkyFx;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sky module: the horizon (fog) color follows the sky tint, in the overworld sky only, never under water or lava.
 * In 26.x vanilla writes the color into a vector instead of returning it, so Hollow adjusts it afterwards.
 */
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
	@Inject(method = "computeFogColor", at = @At("TAIL"))
	private void hollowclient$horizon(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darken, Vector4f dest,
									  CallbackInfo ci) {
		if (camera.getFluidInCamera() != FogType.NONE || level.dimensionType().skybox() != Skybox.OVERWORLD) return;
		dest.set(SkyFx.fogColor(dest));
	}
}
