package dev.telmuad.hollowclient.modules.hud;

import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.hud.TextHudModule;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import net.minecraft.client.Minecraft;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Real-world time from your PC clock. */
public final class ClockHud extends TextHudModule {
	private final BooleanSetting twentyFour = add(new BooleanSetting("24h", "24-hour clock", true));
	private final BooleanSetting seconds = add(new BooleanSetting("seconds", "Show seconds", false));

	public ClockHud() {
		super("clock", "Clock", "The real-world time.", false, at(Anchor.CENTER, 0, Anchor.START, 18));
	}

	@Override
	protected void lines(Minecraft mc, boolean preview, List<Line> out) {
		String pattern = (twentyFour.on() ? "HH:mm" : "h:mm") + (seconds.on() ? ":ss" : "") + (twentyFour.on() ? "" : " a");
		out.add(Line.of(LocalTime.now().format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))));
	}
}
