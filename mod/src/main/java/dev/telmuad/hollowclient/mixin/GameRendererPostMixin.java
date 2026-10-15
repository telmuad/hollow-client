package dev.telmuad.hollowclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.render.PostFx;
import dev.telmuad.hollowclient.render.Quality;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Post Processing: runs Hollow's screen effects right after the world (and vanilla's own post effects) are drawn,
 * before the HUD and menus; and adds a short camera shake when you are hurt, on top of vanilla's damage tilt.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererPostMixin {
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
	private void hollowclient$postEffects(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (renderLevel && mc.level != null) PostFx.process(mc);
	}

	@Inject(method = "bobHurt", at = @At("TAIL"))
	private void hollowclient$damageShake(PoseStack pose, float partialTick, CallbackInfo ci) {
		var m = Modules.POST_PROCESSING;
		if (m == null || !m.isEnabled() || m.shake() <= 0f || Quality.reducedMotion()) return;
		if (!(Minecraft.getInstance().getCameraEntity() instanceof LivingEntity living) || living.hurtDuration <= 0) return;
		float t = (living.hurtTime - partialTick) / living.hurtDuration;
		if (t <= 0f) return;
		float amp = m.shake() * 2.2f * t * t;
		double time = Util.getMillis() / 1000.0;
		pose.mulPose(Axis.XP.rotationDegrees((float) Math.sin(time * 53.0) * amp));
		pose.mulPose(Axis.YP.rotationDegrees((float) Math.cos(time * 47.0) * amp));
		pose.translate(Math.sin(time * 61.0) * 0.01 * amp, Math.cos(time * 57.0) * 0.01 * amp, 0.0);
	}
}
