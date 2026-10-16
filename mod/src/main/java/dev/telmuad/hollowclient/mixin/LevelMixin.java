package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.visual.TimeWeatherModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Time & Weather: the client's own world copy reports the chosen weather, so the rain and sky on your screen follow
 * it (the time of day is in ClientClockManagerMixin). The server's world is a different object and is never touched.
 */
@Mixin(Level.class)
abstract class LevelMixin {
	private static TimeWeatherModule hollowclient$module(Object level) {
		if (!(level instanceof ClientLevel)) return null;
		TimeWeatherModule m = Modules.TIME_WEATHER;
		return m != null && m.isEnabled() ? m : null;
	}

	@ModifyReturnValue(method = "getRainLevel", at = @At("RETURN"))
	private float hollowclient$rain(float rain) {
		TimeWeatherModule m = hollowclient$module(this);
		if (m == null) return rain;
		return switch (m.weather()) {
			case SERVER -> rain;
			case CLEAR -> 0f;
			case RAIN, THUNDER -> 1f;
		};
	}

	@ModifyReturnValue(method = "getThunderLevel", at = @At("RETURN"))
	private float hollowclient$thunder(float thunder) {
		TimeWeatherModule m = hollowclient$module(this);
		if (m == null) return thunder;
		return switch (m.weather()) {
			case SERVER -> thunder;
			case CLEAR, RAIN -> 0f;
			case THUNDER -> 1f;
		};
	}
}
