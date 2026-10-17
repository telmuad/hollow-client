package dev.telmuad.hollowclient.mixin;

import dev.telmuad.hollowclient.skyblock.features.general.ClutterModule;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Clutter Hider: entities it hides aren't drawn on your screen. Nothing else about them changes. */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void hollowclient$hideClutter(E entity, Frustum culler, double camX, double camY, double camZ,
															CallbackInfoReturnable<Boolean> cir) {
		if (ClutterModule.hide(entity)) cir.setReturnValue(false);
	}
}
