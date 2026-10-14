package dev.telmuad.hollowclient.modules.visual;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.NumberSetting;

/** Moves the first-person fire overlay down so it covers less of the screen. Visual only. */
public final class LowFireModule extends Module {
	private final NumberSetting lower = add(new NumberSetting("lower", "How far down", 40, 0, 100, 5).format(0, "%"));

	public LowFireModule() {
		super("low_fire", "Low Fire", "Lowers the fire overlay when you are burning.", Category.VISUAL, false);
	}

	/** Extra downward offset for the fire overlay, in the overlay's own units. */
	public float offset() {
		return isEnabled() ? lower.getFloat() / 100f * 0.45f : 0f;
	}
}
