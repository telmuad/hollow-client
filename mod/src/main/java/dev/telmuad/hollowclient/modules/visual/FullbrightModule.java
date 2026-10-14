package dev.telmuad.hollowclient.modules.visual;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.NumberSetting;

/**
 * Raises the brightness value the lightmap uses, on your screen only. Your saved Brightness option is not touched,
 * so turning this off returns to your normal brightness.
 */
public final class FullbrightModule extends Module {
	private final NumberSetting strength = add(new NumberSetting("strength", "Strength", 10, 1, 15, 0.5).format(1, "x"));

	public FullbrightModule() {
		super("fullbright", "Fullbright", "See in the dark: boosts brightness on your screen only.", Category.VISUAL, false);
		strength.describe("1x is the normal maximum (\"Bright\"); higher lights up caves completely.");
	}

	/** The brightness (gamma) to use instead of the option value, or {@code original} when off. */
	public double gamma(double original) {
		return isEnabled() ? Math.max(original, strength.get()) : original;
	}
}
