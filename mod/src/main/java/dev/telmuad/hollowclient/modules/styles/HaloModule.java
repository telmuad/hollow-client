package dev.telmuad.hollowclient.modules.styles;

import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.ColorSetting;
import dev.telmuad.hollowclient.setting.Effect;
import dev.telmuad.hollowclient.setting.EnumSetting;

/** A glowing ring floating above your head. Only you can see it. */
public final class HaloModule extends Module {
	public enum Style {RING, DOUBLE, SPIKED}

	private final EnumSetting<Style> style = add(new EnumSetting<>("style", "Halo", Style.RING));
	private final BooleanSetting themeColor = add(new BooleanSetting("themeColor", "Theme color", true));
	private final ColorSetting tint = add(new ColorSetting("tint", "Color", 0xFFFFE27A, false));
	private final Effect glow = effect("glow", "Glow", true, 80, "The halo shines in the dark and gently pulses.");
	private final Effect spin = effect("spin", "Spin", true, 40, "The halo slowly turns. Strength = speed.");

	public HaloModule() {
		super("halo", "Halo", "A glowing ring above your head. Only you can see it.", Category.STYLES, false);
		tint.visibleWhen(() -> !themeColor.on());
	}

	public EnumSetting<Style> styleSetting() {
		return style;
	}

	public BooleanSetting themeColorSetting() {
		return themeColor;
	}

	public ColorSetting tintSetting() {
		return tint;
	}

	public Effect glowEffect() {
		return glow;
	}

	public Effect spinEffect() {
		return spin;
	}

	public Style style() {
		return style.get();
	}

	public int color() {
		return themeColor.on() ? Theme.accent() : tint.argb();
	}

	public float glow() {
		return glow.amount();
	}

	public float spin() {
		return spin.amount();
	}
}
