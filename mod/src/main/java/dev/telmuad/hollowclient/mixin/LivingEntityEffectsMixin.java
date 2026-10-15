package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.render.EffectsFx;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Effects: the kill burst listens for the death event on the client's copy of an entity, and Hand Animations' swing
 * speed changes how long *your own* arm swing animation lasts on your screen. Neither changes game logic.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityEffectsMixin {
	@Inject(method = "handleEntityEvent", at = @At("HEAD"))
	private void hollowclient$death(byte event, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (event == 3 && self.level().isClientSide()) EffectsFx.onDeath(self);
	}

	@ModifyReturnValue(method = "getCurrentSwingDuration", at = @At("RETURN"))
	private int hollowclient$swingSpeed(int ticks) {
		var m = Modules.HAND_ANIMATIONS;
		if (m == null || !m.isEnabled() || (Object) this != Minecraft.getInstance().player) return ticks;
		float speed = m.swingSpeed();
		if (speed == 1f) return ticks;
		return Math.max(1, Math.round(ticks / speed));
	}
}
