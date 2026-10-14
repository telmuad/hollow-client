package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.telmuad.hollowclient.input.Clicks;
import dev.telmuad.hollowclient.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mouse hooks: counts presses for the CPS displays, lets the wheel change the zoom while zooming (instead of the
 * hotbar slot), and slows mouse turning while zoomed. Input is only read or scaled locally.
 */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
	@Inject(method = "onButton", at = @At("HEAD"))
	private void hollowclient$countClick(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		Clicks.onMouseButton(info.button(), action);
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void hollowclient$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (Modules.ZOOM == null || mc.screen != null || mc.player == null || window != mc.getWindow().handle()) return;
		if (Modules.ZOOM.onScroll(vertical)) ci.cancel();
	}

	@WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void hollowclient$zoomSensitivity(LocalPlayer player, double yaw, double pitch, Operation<Void> original) {
		double m = Modules.ZOOM == null ? 1.0 : Modules.ZOOM.sensitivityMultiplier();
		original.call(player, yaw * m, pitch * m);
	}
}
