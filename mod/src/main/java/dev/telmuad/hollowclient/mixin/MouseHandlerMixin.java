package dev.telmuad.hollowclient.mixin;

import dev.telmuad.hollowclient.input.Clicks;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts mouse presses for the CPS displays. Only reads input; nothing is changed or sent. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "onButton", at = @At("HEAD"))
	private void hollowclient$countClick(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		Clicks.onMouseButton(info.button(), action);
	}
}
