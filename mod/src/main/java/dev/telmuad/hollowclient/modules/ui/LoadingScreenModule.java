package dev.telmuad.hollowclient.modules.ui;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.Effect;

/** The loading screen (game start and resource reloads) in Hollow's colors: dark background, glowing sigil, gradient bar. */
public final class LoadingScreenModule extends Module {
	private final Effect glow = effect("glow", "Logo glow", true, 70, "The sigil pulses with a soft glow while loading.");
	private final Effect particles = effect("particles", "Particles", true, 50, "Void particles drift up behind the logo.");

	public LoadingScreenModule() {
		super("loading_screen", "Loading Screen", "Hollow's dark loading screen with the glowing sigil and a gradient progress bar.", Category.INTERFACE, true);
	}

	public float glow() {
		return glow.amount();
	}

	public float particles() {
		return particles.amount();
	}
}
