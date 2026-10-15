package dev.telmuad.hollowclient.modules.ui;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.Effect;

/** The Esc menu with a theme-blurred background, a glass panel and Hollow-style buttons. The buttons do the same as vanilla's. */
public final class PauseMenuModule extends Module {
	private final Effect blur = effect("blur", "Background blur", true, 60, "Blur the world behind the pause menu.");
	private final Effect particles = effect("particles", "Particles", true, 50, "Void particles behind the menu.");
	private final BooleanSetting buttons = add(new BooleanSetting("buttons", "Hollow-style buttons", true));

	public PauseMenuModule() {
		super("pause_menu", "Pause Menu", "A blurred, glassy pause menu with Hollow-style buttons.", Category.INTERFACE, true);
	}

	public float blur() {
		return blur.amount();
	}

	public float particles() {
		return particles.amount();
	}

	public boolean buttons() {
		return buttons.on();
	}
}
