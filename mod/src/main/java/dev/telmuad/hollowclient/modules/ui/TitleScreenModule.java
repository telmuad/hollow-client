package dev.telmuad.hollowclient.modules.ui;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.Effect;
import dev.telmuad.hollowclient.setting.EnumSetting;
import dev.telmuad.hollowclient.setting.NumberSetting;

/** Hollow's own title screen: an animated nebula (or the vanilla panorama), mouse parallax, glowing logo, clock and FPS. */
public final class TitleScreenModule extends Module {
	public enum Background {NEBULA, PANORAMA}

	private final EnumSetting<Background> background = add(new EnumSetting<>("background", "Background", Background.NEBULA));
	private final NumberSetting brightness = add(new NumberSetting("brightness", "Nebula brightness", 80, 20, 100, 5).format(0, "%"));
	private final Effect parallax = effect("parallax", "Mouse parallax", true, 60, "The background and logo shift a little as you move the mouse.");
	private final BooleanSetting clock = add(new BooleanSetting("clock", "Show the clock", true));
	private final BooleanSetting twentyFour = add(new BooleanSetting("twentyFour", "24-hour clock", true));
	private final BooleanSetting fps = add(new BooleanSetting("fps", "Show FPS", true));

	public TitleScreenModule() {
		super("title_screen", "Title Screen", "A Hollow title screen with an animated nebula, mouse parallax and a glowing logo.", Category.INTERFACE, true);
		brightness.visibleWhen(() -> background.get() == Background.NEBULA);
		twentyFour.visibleWhen(clock::get);
	}

	public Background background() {
		return background.get();
	}

	public float brightness() {
		return brightness.getFloat() / 100f;
	}

	public float parallax() {
		return parallax.amount();
	}

	public boolean clock() {
		return clock.on();
	}

	public boolean twentyFour() {
		return twentyFour.on();
	}

	public boolean fps() {
		return fps.on();
	}
}
