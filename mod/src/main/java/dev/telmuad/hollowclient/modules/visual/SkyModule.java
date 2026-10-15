package dev.telmuad.hollowclient.modules.visual;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.ColorSetting;
import dev.telmuad.hollowclient.setting.Effect;

/**
 * The overworld sky on your screen: tinted top-to-horizon colors, more (and brighter) stars, shooting stars and an
 * aurora at night. Drawn by your client only.
 */
public final class SkyModule extends Module {
	private final Effect tint = effect("tint", "Sky colors", false, 50, "Tint the sky (top) and horizon toward your own colors.");
	private final BooleanSetting themeColors = add(new BooleanSetting("themeColors", "Use the theme colors", true));
	private final ColorSetting topColor = add(new ColorSetting("topColor", "Sky top color", 0xFF3B2A80));
	private final ColorSetting horizonColor = add(new ColorSetting("horizonColor", "Horizon color", 0xFF7DD3FC));
	private final Effect moreStars = effect("moreStars", "More stars", false, 50, "Up to four times as many stars.");
	private final Effect starGlow = effect("starGlow", "Brighter stars", true, 40, "Stars shine brighter at night.");
	private final Effect shootingStars = effect("shootingStars", "Shooting stars", true, 40, "Now and then a streak crosses the night sky. Strength = how often.");
	private final Effect aurora = effect("aurora", "Aurora", true, 50, "Glowing curtains of light in the night sky, in the theme colors.");

	public SkyModule() {
		super("sky", "Sky", "Sky and horizon colors, more and brighter stars, shooting stars and an aurora at night.", Category.VISUAL, true);
		themeColors.visibleWhen(tint.switchSetting()::get);
		topColor.visibleWhen(() -> tint.on() && !themeColors.on());
		horizonColor.visibleWhen(() -> tint.on() && !themeColors.on());
	}

	public float tint() {
		return tint.amount();
	}

	public boolean themeColors() {
		return themeColors.on();
	}

	public int topColor() {
		return topColor.argb();
	}

	public int horizonColor() {
		return horizonColor.argb();
	}

	/** Star count multiplier: 1 (vanilla) up to 4. */
	public float starMultiplier() {
		return 1f + 3f * moreStars.amount();
	}

	public float starGlow() {
		return starGlow.amount();
	}

	public float shootingStars() {
		return shootingStars.amount();
	}

	public float aurora() {
		return aurora.amount();
	}
}
