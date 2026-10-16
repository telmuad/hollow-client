package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.visual.TimeWeatherModule;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Time & Weather: in 26.x the time of day is a world clock, and the sky, sun, moon and light all follow the client's
 * copy of the overworld clock. Hollow's chosen time replaces it there only; the server's clocks are never touched.
 */
@Mixin(ClientClockManager.class)
abstract class ClientClockManagerMixin {
	@ModifyReturnValue(method = "getTotalTicks", at = @At("RETURN"))
	private long hollowclient$dayTime(long time, Holder<WorldClock> clock) {
		TimeWeatherModule m = Modules.TIME_WEATHER;
		if (m == null || !m.isEnabled() || !m.fixTime() || !clock.is(WorldClocks.OVERWORLD)) return time;
		// keep the day count (moon phase), replace the time of day
		return Math.floorDiv(time, 24000L) * 24000L + m.dayTime();
	}
}
