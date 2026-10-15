package dev.telmuad.hollowclient.mixin;

import dev.telmuad.hollowclient.gui.Toasts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draw Hollow's notification cards right after vanilla's toasts: above menus and the HUD. */
@Mixin(ToastManager.class)
abstract class ToastManagerMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void hollowclient$renderToasts(GuiGraphics g, CallbackInfo ci) {
		Toasts.render(g);
	}
}
