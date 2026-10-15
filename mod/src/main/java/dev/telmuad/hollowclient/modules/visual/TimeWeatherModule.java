package dev.telmuad.hollowclient.modules.visual;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.Effect;
import dev.telmuad.hollowclient.setting.EnumSetting;
import dev.telmuad.hollowclient.setting.NumberSetting;

import java.util.Locale;

/**
 * What time and weather your screen shows. Only your client's picture changes: the server's time and weather, and
 * what other players see, stay the same. Also prettier rain and snow.
 */
public final class TimeWeatherModule extends Module {
	public enum Weather {SERVER, CLEAR, RAIN, THUNDER}

	private final BooleanSetting fixTime = add(new BooleanSetting("fixTime", "Change the time", false));
	private final NumberSetting hour = add(new NumberSetting("hour", "Time of day", 12, 0, 23.75, 0.25).display(h -> {
		int hh = (int) Math.floor(h);
		int mm = (int) Math.round((h - hh) * 60);
		return String.format(Locale.ROOT, "%02d:%02d", hh, mm);
	}));
	private final EnumSetting<Weather> weather = add(new EnumSetting<>("weather", "Weather", Weather.SERVER));
	private final Effect prettyWeather = effect("prettyWeather", "Prettier rain and snow", true, 60,
			"Softer, slightly tinted rain and brighter snow.");

	public TimeWeatherModule() {
		super("time_weather", "Time & Weather", "Pick the time and weather you see (client only), and prettier rain and snow.", Category.VISUAL, true);
		hour.visibleWhen(fixTime::get);
	}

	public boolean fixTime() {
		return fixTime.on();
	}

	/** The chosen time as Minecraft day time (0 = 6:00). */
	public long dayTime() {
		double h = hour.get();
		return Math.round(((h - 6.0 + 24.0) % 24.0) * 1000.0);
	}

	public Weather weather() {
		return weather.get();
	}

	public float prettyWeather() {
		return prettyWeather.amount();
	}
}
