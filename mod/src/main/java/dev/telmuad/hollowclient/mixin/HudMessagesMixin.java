package dev.telmuad.hollowclient.mixin;

import dev.telmuad.hollowclient.skyblock.SkyBlock;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** SkyBlock Mode reads the action bar and titles the server shows. Only reading: what's shown is unchanged. */
@Mixin(Hud.class)
abstract class HudMessagesMixin {
	@Inject(method = "setOverlayMessage", at = @At("HEAD"))
	private void hollowclient$actionBar(Component message, boolean animate, CallbackInfo ci) {
		try {
			SkyBlock.onActionBar(message);
		} catch (RuntimeException e) {
			dev.telmuad.hollowclient.HollowClient.LOG.error("SkyBlock Mode couldn't read the action bar", e);
		}
	}

	@Inject(method = "setSubtitle", at = @At("HEAD"))
	private void hollowclient$subtitle(Component subtitle, CallbackInfo ci) {
		SkyBlock.onSubtitle(subtitle);
	}

	@Inject(method = "setTitle", at = @At("HEAD"))
	private void hollowclient$title(Component title, CallbackInfo ci) {
		try {
			SkyBlock.onTitle(title);
		} catch (RuntimeException e) {
			dev.telmuad.hollowclient.HollowClient.LOG.error("SkyBlock Mode couldn't read a title", e);
		}
	}
}
