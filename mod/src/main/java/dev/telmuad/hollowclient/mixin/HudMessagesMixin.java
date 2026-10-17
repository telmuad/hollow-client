package dev.telmuad.hollowclient.mixin;

import dev.telmuad.hollowclient.skyblock.SkyBlock;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** SkyBlock Mode reads the action bar and titles the server shows (and Status Bars can trim the action bar text). */
@Mixin(Hud.class)
abstract class HudMessagesMixin {
	/** Reads the action bar, and (Status Bars on) shows it without the numbers the bars already show. */
	@ModifyVariable(method = "setOverlayMessage", at = @At("HEAD"), argsOnly = true)
	private Component hollowclient$actionBar(Component message) {
		try {
			SkyBlock.onActionBar(message);
			return dev.telmuad.hollowclient.skyblock.features.general.StatusBarsHud.filterActionBar(message);
		} catch (RuntimeException e) {
			dev.telmuad.hollowclient.HollowClient.LOG.error("SkyBlock Mode couldn't read the action bar", e);
			return message;
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
