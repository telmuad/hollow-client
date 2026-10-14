package dev.telmuad.hollowclient.modules.styles;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.render.CosmeticModels.Wings;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.ColorSetting;
import dev.telmuad.hollowclient.setting.EnumSetting;
import dev.telmuad.hollowclient.setting.NumberSetting;

/** Flapping wings on your own player's back. Only you can see them. */
public final class WingsModule extends Module {
	private final EnumSetting<Wings> style = add(new EnumSetting<>("style", "Wings", Wings.SHADOW));
	private final ColorSetting tint = add(new ColorSetting("tint", "Tint (white = original colors)", 0xFFFFFFFF, false));
	private final NumberSetting flap = add(new NumberSetting("flapSpeed", "Flap speed", 1, 0, 3, 0.1).format(1, "x"));
	private final BooleanSetting hideWithElytra = add(new BooleanSetting("hideWithElytra", "Hide while wearing elytra", true));

	public WingsModule() {
		super("wings", "Wings", "Wings on your back that gently flap. Only you can see them.", Category.STYLES, false);
	}

	public EnumSetting<Wings> styleSetting() {
		return style;
	}

	public ColorSetting tintSetting() {
		return tint;
	}

	public NumberSetting flapSetting() {
		return flap;
	}

	public BooleanSetting hideWithElytraSetting() {
		return hideWithElytra;
	}

	public Wings wings() {
		return style.get();
	}

	public int tint() {
		return tint.argb();
	}

	public float flapSpeed() {
		return flap.getFloat();
	}

	public boolean hideWithElytra() {
		return hideWithElytra.on();
	}
}
