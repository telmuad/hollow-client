package dev.telmuad.hollowclient.mixin;

import dev.telmuad.hollowclient.gui.ScreenStyles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pause menu background in Hollow's style: theme blur, particles and a glass panel behind the buttons. */
@Mixin(PauseScreen.class)
abstract class PauseScreenMixin {
	@Shadow
	@Final
	private boolean showPauseMenu;

	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
	private void hollowclient$background(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (!showPauseMenu || !ScreenStyles.pauseStyled()) return;
		ScreenStyles.pauseBackground((Screen) (Object) this, g, mouseX, mouseY);
		Minecraft.getInstance().gui.renderDeferredSubtitles();
		ci.cancel();
	}
}
