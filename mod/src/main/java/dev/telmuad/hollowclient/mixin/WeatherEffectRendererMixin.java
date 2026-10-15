package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.visual.TimeWeatherModule;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Prettier rain and snow: rain becomes softer and faintly tinted toward the theme, snow a little brighter and
 * cooler. Only the color of the falling streaks changes.
 */
@Mixin(WeatherEffectRenderer.class)
abstract class WeatherEffectRendererMixin {
	@ModifyExpressionValue(method = "renderInstances", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;white(F)I"))
	private int hollowclient$tint(int color, @Local(argsOnly = true, ordinal = 0) float baseAlpha) {
		TimeWeatherModule m = Modules.TIME_WEATHER;
		if (m == null || !m.isEnabled() || m.prettyWeather() <= 0f) return color;
		float k = m.prettyWeather();
		boolean snow = baseAlpha < 0.9f; // vanilla passes 1.0 for rain and 0.8 for snow
		if (snow) {
			int tinted = ColorUtil.mix(color, ColorUtil.withAlpha(0xE8F4FF, ColorUtil.alpha(color)), 0.6f * k);
			return ColorUtil.withAlpha(tinted, Math.min(255, Math.round(ColorUtil.alpha(color) * (1f + 0.25f * k))));
		}
		int tint = ColorUtil.mix(0xFFCFE3FF, Theme.accent2(), 0.25f);
		int tinted = ColorUtil.mix(color, ColorUtil.withAlpha(tint, ColorUtil.alpha(color)), 0.55f * k);
		return ColorUtil.withAlpha(tinted, Math.round(ColorUtil.alpha(color) * (1f - 0.35f * k)));
	}
}
