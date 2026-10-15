package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.telmuad.hollowclient.render.SkyFx;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Sky module: the horizon (fog) color follows the sky tint, in the overworld sky only, never under water or lava. */
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
	@ModifyReturnValue(method = "computeFogColor", at = @At("RETURN"))
	private Vector4f hollowclient$horizon(Vector4f color, Camera camera, float partialTick, ClientLevel level, int renderDistance, float darken) {
		if (camera.getFluidInCamera() != FogType.NONE || level.dimensionType().skybox() != Skybox.OVERWORLD) return color;
		return SkyFx.fogColor(color);
	}
}
