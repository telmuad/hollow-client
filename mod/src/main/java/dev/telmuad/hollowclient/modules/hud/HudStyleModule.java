package dev.telmuad.hollowclient.modules.hud;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.Effect;
import dev.telmuad.hollowclient.setting.EnumSetting;

/**
 * How every HUD element looks: the panel style, gradient text, glow, a gradient border, rolling numbers, fade/slide-in
 * and the animated hotbar. Off = the classic flat boxes. Colors come from the theme; each element's own text and
 * background colors still apply where the style uses them.
 */
public final class HudStyleModule extends Module {
	public enum Look {GLASS, FLAT, OUTLINE, TEXT_ONLY}

	private final EnumSetting<Look> look = add(new EnumSetting<>("look", "Panel style", Look.GLASS));
	private final Effect gradientText = effect("gradientText", "Gradient text", false, 100,
			"Text in the theme gradient instead of each element's text color.");
	private final Effect glow = effect("glow", "Glow", true, 40, "A soft theme glow around HUD elements.");
	private final Effect border = effect("border", "Gradient border", true, 60, "A thin border in the theme gradient.");
	private final Effect rolling = effect("rolling", "Rolling numbers", true, 100,
			"Changing digits roll up like a counter (FPS, coordinates, CPS, ping ...). Strength = speed.");
	private final Effect appear = effect("appear", "Fade and slide in", true, 60,
			"Elements fade and slide in when they appear, for example when you join a world.");
	private final Effect hotbar = effect("hotbar", "Animated hotbar", true, 60,
			"The hotbar selection glides between slots with a glow, and the selected item pops.");

	public HudStyleModule() {
		super("hud_style", "HUD Style", "Glass panels, gradient text, glow, rolling numbers and an animated hotbar for every HUD element.", Category.HUD, true);
	}

	public Look look() {
		return look.get();
	}

	public float gradientText() {
		return gradientText.amount();
	}

	public float glow() {
		return glow.amount();
	}

	public float border() {
		return border.amount();
	}

	public float rolling() {
		return rolling.amount();
	}

	public float appear() {
		return appear.amount();
	}

	public float hotbar() {
		return hotbar.amount();
	}
}
