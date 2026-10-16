package dev.telmuad.hollowclient.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.telmuad.hollowclient.render.SkyFx;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sky module: sky color tint, star count and brightness, and the aurora / shooting stars after the stars. */
@Mixin(SkyRenderer.class)
abstract class SkyRendererMixin {
	@Shadow
	@Final
	@Mutable
	private GpuBuffer starBuffer;

	@Unique
	private float hollowclient$starsBuiltFor = 1f;

	@Shadow
	private GpuBuffer buildStars() {
		throw new AssertionError();
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void hollowclient$skyColor(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
		state.skyColor = SkyFx.skyColor(state.skyColor);
	}

	/** Vanilla makes 1500 stars; "More stars" multiplies that. */
	@ModifyConstant(method = "buildStars", constant = @Constant(intValue = 1500))
	private int hollowclient$starCount(int vanilla) {
		hollowclient$starsBuiltFor = SkyFx.starMultiplier();
		return Math.round(vanilla * hollowclient$starsBuiltFor);
	}

	@ModifyVariable(method = "renderStars", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float hollowclient$starBrightness(float brightness) {
		return SkyFx.starBrightness(brightness);
	}

	@Inject(method = "renderSunMoonAndStars", at = @At("HEAD"))
	private void hollowclient$rebuildStars(PoseStack pose, float sunAngle, float moonAngle, float starAngle, MoonPhase phase,
										   float rainBrightness, float starBrightness, CallbackInfo ci) {
		// "More stars" changed: rebuild the star buffer right away instead of waiting for a resource reload
		if (Math.abs(SkyFx.starMultiplier() - hollowclient$starsBuiltFor) > 0.01f) {
			GpuBuffer old = starBuffer;
			starBuffer = buildStars();
			old.close();
		}
	}

	@Inject(method = "renderSunMoonAndStars", at = @At("TAIL"))
	private void hollowclient$nightSky(PoseStack pose, float sunAngle, float moonAngle, float starAngle, MoonPhase phase,
									   float rainBrightness, float starBrightness, CallbackInfo ci) {
		SkyFx.renderNight(starBrightness, rainBrightness);
	}
}
