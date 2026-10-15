package dev.telmuad.hollowclient.mixin;

import dev.telmuad.hollowclient.gui.ScreenStyles;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Inventory Blur: container screens (inventory, chests ...) blur the world behind them. */
@Mixin(Screen.class)
abstract class ScreenMixin {
	@Inject(method = "renderTransparentBackground", at = @At("HEAD"), cancellable = true)
	private void hollowclient$containerBlur(GuiGraphics g, CallbackInfo ci) {
		Screen self = (Screen) (Object) this;
		if (!ScreenStyles.inventoryBlur(self)) return;
		ScreenStyles.containerBackground(self, g);
		ci.cancel();
	}
}
