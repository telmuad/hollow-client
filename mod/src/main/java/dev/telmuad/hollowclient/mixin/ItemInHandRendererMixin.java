package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.effects.HandAnimationsModule;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hand Animations: eased item switching, no cooldown dip (1.7) and the classic held-item position (1.7). */
@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin {
	private static HandAnimationsModule hollowclient$module() {
		HandAnimationsModule m = Modules.HAND_ANIMATIONS;
		return m != null && m.isEnabled() ? m : null;
	}

	/** 1.7: the held item doesn't sink while the attack cooldown recharges (the cooldown is unchanged). */
	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F"))
	private float hollowclient$noCooldownDip(float scale) {
		HandAnimationsModule m = hollowclient$module();
		return m != null && m.noCooldownDip() ? 1f : scale;
	}

	/** Smoother switching: ease the interpolated hand height (the 3rd and 4th lerps are the two hands). */
	@ModifyExpressionValue(method = "submitHandsWithItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F", ordinal = 2))
	private float hollowclient$easeMain(float height) {
		return hollowclient$ease(height);
	}

	@ModifyExpressionValue(method = "submitHandsWithItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F", ordinal = 3))
	private float hollowclient$easeOff(float height) {
		return hollowclient$ease(height);
	}

	private static float hollowclient$ease(float h) {
		HandAnimationsModule m = hollowclient$module();
		if (m == null || m.smoothSwitch() <= 0f) return h;
		float c = Math.max(0f, Math.min(1f, h));
		float eased = c * c * (3f - 2f * c);
		return h + (eased - h) * m.smoothSwitch();
	}

	/** 1.7: hold items a bit lower, closer and bigger. */
	@Inject(method = "applyItemArmTransform", at = @At("TAIL"))
	private void hollowclient$classicPosition(PoseStack pose, HumanoidArm arm, float equip, CallbackInfo ci) {
		HandAnimationsModule m = hollowclient$module();
		if (m == null || m.classicPosition() <= 0f) return;
		float a = m.classicPosition();
		int side = arm == HumanoidArm.RIGHT ? 1 : -1;
		pose.translate(side * -0.03f * a, -0.06f * a, 0.06f * a);
		float s = 1f + 0.12f * a;
		pose.scale(s, s, s);
	}
}
