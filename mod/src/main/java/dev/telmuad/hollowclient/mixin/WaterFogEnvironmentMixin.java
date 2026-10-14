package dev.telmuad.hollowclient.mixin;

import dev.telmuad.hollowclient.modules.Modules;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clear Water: pushes the underwater fog end out. Visual only. */
@Mixin(WaterFogEnvironment.class)
abstract class WaterFogEnvironmentMixin {
	@Inject(method = "setupFog", at = @At("TAIL"))
	private void hollowclient$clearFog(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker delta, CallbackInfo ci) {
		if (Modules.CLEAR_WATER == null) return;
		float end = Modules.CLEAR_WATER.fogEnd(fog.environmentalEnd);
		if (end != fog.environmentalEnd) {
			fog.environmentalEnd = end;
			fog.skyEnd = end;
			fog.cloudEnd = end;
		}
	}
}
