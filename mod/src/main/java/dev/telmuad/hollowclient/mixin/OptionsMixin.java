package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.telmuad.hollowclient.gui.ScreenStyles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * While a Hollow screen, the styled pause menu or a blurred inventory is open, the menu blur radius comes from Hollow
 * (theme blur, Pause Menu blur, Inventory Blur strength) instead of the Accessibility setting. The saved option is
 * never changed.
 */
@Mixin(Options.class)
abstract class OptionsMixin {
	@ModifyReturnValue(method = "getMenuBackgroundBlurriness", at = @At("RETURN"))
	private int hollowclient$themeBlur(int original) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.gui.screen() == null) return original;
		int r = ScreenStyles.blurRadius(mc.gui.screen(), original);
		return r >= 0 ? r : original;
	}
}
