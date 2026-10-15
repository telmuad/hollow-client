package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.telmuad.hollowclient.modules.Modules;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Own Nametag: vanilla hides your own name; show it in third person (and only for you). */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererNameMixin {
	@ModifyReturnValue(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("RETURN"))
	private boolean hollowclient$ownName(boolean show, LivingEntity entity, double distanceSq) {
		if (show) return true;
		Minecraft mc = Minecraft.getInstance();
		var m = Modules.OWN_NAMETAG;
		return m != null && m.isEnabled() && entity == mc.player && Minecraft.renderNames()
				&& mc.options.getCameraType() != CameraType.FIRST_PERSON && !entity.isInvisible();
	}
}
