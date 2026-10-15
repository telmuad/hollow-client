package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.telmuad.hollowclient.gui.HollowScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * While a Hollow screen is open, the menu blur radius comes from the theme (Theme → Background blur) instead of the
 * Accessibility setting. The saved option itself is not changed.
 */
@Mixin(Options.class)
abstract class OptionsMixin {
	@ModifyReturnValue(method = "getMenuBackgroundBlurriness", at = @At("RETURN"))
	private int hollowclient$themeBlur(int original) {
		Minecraft mc = Minecraft.getInstance();
		if (mc != null && mc.screen instanceof HollowScreen screen) return screen.blurRadius(original);
		return original;
	}
}
