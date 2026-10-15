package dev.telmuad.hollowclient.mixin;

import dev.telmuad.hollowclient.gui.ScreenStyles;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** On Hollow's pause menu, vanilla buttons get Hollow's look (the label and behavior stay vanilla's). */
@Mixin(AbstractButton.class)
abstract class AbstractButtonMixin {
	@Unique
	private float hollowclient$hover;
	@Unique
	private long hollowclient$lastNanos;

	@Inject(method = "renderDefaultSprite", at = @At("HEAD"), cancellable = true)
	private void hollowclient$sprite(GuiGraphics g, CallbackInfo ci) {
		if (!ScreenStyles.restyleButtons()) return;
		long now = System.nanoTime();
		float dt = hollowclient$lastNanos == 0 ? 0f : Math.min(0.1f, (now - hollowclient$lastNanos) / 1_000_000_000f);
		hollowclient$lastNanos = now;
		hollowclient$hover = ScreenStyles.drawButton((AbstractButton) (Object) this, g, hollowclient$hover, dt);
		ci.cancel();
	}
}
