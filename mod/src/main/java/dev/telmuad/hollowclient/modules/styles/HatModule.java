package dev.telmuad.hollowclient.modules.styles;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.render.CosmeticModels.Hat;
import dev.telmuad.hollowclient.setting.ColorSetting;
import dev.telmuad.hollowclient.setting.EnumSetting;

/** A hat on your own player (third person and the inventory preview). Only you can see it. */
public final class HatModule extends Module {
	private final EnumSetting<Hat> style = add(new EnumSetting<>("style", "Hat", Hat.TOP_HAT));
	private final ColorSetting tint = add(new ColorSetting("tint", "Tint (white = original colors)", 0xFFFFFFFF, false));

	public HatModule() {
		super("hat", "Hat", "A hat on your player. Only you can see it.", Category.STYLES, false);
	}

	public EnumSetting<Hat> styleSetting() {
		return style;
	}

	public ColorSetting tintSetting() {
		return tint;
	}

	public Hat hat() {
		return style.get();
	}

	public int tint() {
		return tint.argb();
	}
}
