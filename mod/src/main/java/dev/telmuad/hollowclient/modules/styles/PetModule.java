package dev.telmuad.hollowclient.modules.styles;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.ColorSetting;
import dev.telmuad.hollowclient.setting.EnumSetting;

/** A small companion floating beside your shoulder. Only you can see it. */
public final class PetModule extends Module {
	public enum Style {WISP, CUBE}

	private final EnumSetting<Style> style = add(new EnumSetting<>("style", "Pet", Style.WISP));
	private final ColorSetting tint = add(new ColorSetting("tint", "Tint (white = original colors)", 0xFFFFFFFF, false));

	public PetModule() {
		super("pet", "Pet", "A little companion floating beside you. Only you can see it.", Category.STYLES, false);
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
