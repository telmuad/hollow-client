package dev.telmuad.hollowclient.modules.styles;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.Effect;
import dev.telmuad.hollowclient.setting.EnumSetting;

/** Glowing particles around your player (seen in third person). Only you can see them. */
public final class AuraModule extends Module {
	public enum Style {VOID, SPARKLES, ORBIT}

	private final EnumSetting<Style> style = add(new EnumSetting<>("style", "Aura", Style.VOID));
	private final Effect density = effect("density", "Particles", true, 50, "How many particles the aura has.");

	public AuraModule() {
		super("aura", "Aura", "Glowing particles around your player, in the theme colors. Only you can see them.", Category.STYLES, false);
	}

	public EnumSetting<Style> styleSetting() {
		return style;
	}

	public Effect densityEffect() {
		return density;
	}

	public Style style() {
		return style.get();
	}

	public float density() {
		return density.amount();
	}
}
