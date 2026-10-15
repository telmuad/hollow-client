package dev.telmuad.hollowclient.modules.client;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.EnumSetting;
import dev.telmuad.hollowclient.setting.NumberSetting;

/** Hollow's own notification cards ("Theme saved", "Screenshot saved" ...). Off = vanilla's plain toasts. */
public final class ToastsModule extends Module {
	public enum Corner {TOP_RIGHT, BOTTOM_RIGHT, TOP_LEFT, BOTTOM_LEFT}

	private final EnumSetting<Corner> corner = add(new EnumSetting<>("corner", "Position", Corner.BOTTOM_RIGHT));
	private final NumberSetting seconds = add(new NumberSetting("seconds", "Show for", 4, 2, 10, 0.5).format(1, " s"));
	private final NumberSetting opacity = add(new NumberSetting("opacity", "Opacity", 92, 30, 100, 5).format(0, "%"));
	private final BooleanSetting sound = add(new BooleanSetting("sound", "Play a sound", true));

	public ToastsModule() {
		super("toasts", "Notifications", "Glassy slide-in notification cards. Off = Minecraft's plain toasts.", Category.CLIENT, true);
	}

	public Corner corner() {
		return corner.get();
	}

	public long durationMs() {
		return Math.round(seconds.get() * 1000);
	}

	public float opacity() {
		return opacity.getFloat() / 100f;
	}

	public boolean sound() {
		return sound.on();
	}
}
