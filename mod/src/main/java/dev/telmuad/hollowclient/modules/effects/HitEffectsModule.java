package dev.telmuad.hollowclient.modules.effects;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.ColorSetting;
import dev.telmuad.hollowclient.setting.Effect;

/**
 * Eye candy when you hit things: more crit particles, a colored burst where you hit, sparks and a short screen flash
 * on critical hits, and a burst when something you hit dies. Visual only, on your screen only.
 */
public final class HitEffectsModule extends Module {
	private final Effect multiplier = effect("multiplier", "More crit particles", true, 35, "Up to four times vanilla's critical-hit particles.");
	private final Effect hitBurst = effect("hitBurst", "Colored hit burst", true, 50, "A small burst of colored sparks where you hit.");
	private final BooleanSetting themeColor = add(new BooleanSetting("themeColor", "Use the theme colors", true));
	private final ColorSetting color = add(new ColorSetting("color", "Hit color", 0xFFFF5470));
	private final Effect critSparks = effect("critSparks", "Crit sparks", true, 60, "Bright sparkles on your critical hits.");
	private final Effect critFlash = effect("critFlash", "Crit flash", true, 35, "A quick flash at the screen edges on your critical hits.");
	private final Effect killBurst = effect("killBurst", "Kill burst", true, 60, "A ring of light when something you hit dies.");

	public HitEffectsModule() {
		super("hit_effects", "Hit Effects", "More crit particles, colored hit bursts, crit sparks and flash, and a burst on kills.", Category.EFFECTS, true);
		themeColor.visibleWhen(() -> hitBurst.on() || killBurst.on());
		color.visibleWhen(() -> (hitBurst.on() || killBurst.on()) && !themeColor.on());
	}

	/** Crit particle emitters: 1 (vanilla) up to 4. */
	public int critMultiplier() {
		return 1 + Math.round(3 * multiplier.amount());
	}

	public float hitBurst() {
		return hitBurst.amount();
	}

	public boolean themeColor() {
		return themeColor.on();
	}

	public int color() {
		return color.argb();
	}

	public float critSparks() {
		return critSparks.amount();
	}

	public float critFlash() {
		return critFlash.amount();
	}

	public float killBurst() {
		return killBurst.amount();
	}
}
