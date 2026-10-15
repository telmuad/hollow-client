package dev.telmuad.hollowclient.modules.styles;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.ColorSetting;
import dev.telmuad.hollowclient.setting.EnumSetting;

/** A pair of horns on your head. Only you can see them. */
public final class HornsModule extends Module {
	public enum Style {CURVED, DEVIL, RAM}

	private final EnumSetting<Style> style = add(new EnumSetting<>("style", "Horns", Style.CURVED));
	private final ColorSetting tint = add(new ColorSetting("tint", "Tint (white = original colors)", 0xFFFFFFFF, false));

	public HornsModule() {
		super("horns", "Horns", "A pair of horns on your head. Only you can see them.", Category.STYLES, false);
	}

	public EnumSetting<Style> styleSetting() {
		return style;
	}

	public ColorSetting tintSetting() {
		return tint;
	}

	public Style style() {
		return style.get();
	}

	public int tint() {
		return tint.argb();
	}
}
