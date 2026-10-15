package dev.telmuad.hollowclient.modules.effects;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;

/** Show your own name above your head in third person, styled in the theme gradient. Only you see it. */
public final class OwnNametagModule extends Module {
	private final BooleanSetting gradient = add(new BooleanSetting("gradient", "Theme gradient", true));
	private final BooleanSetting bold = add(new BooleanSetting("bold", "Bold", false));
	private final BooleanSetting mark = add(new BooleanSetting("mark", "Star before the name", true));

	public OwnNametagModule() {
		super("own_nametag", "Own Nametag", "Show your own name above your head in third person (F5), in the theme gradient.", Category.EFFECTS, false);
	}

	public boolean gradient() {
		return gradient.on();
	}

	public boolean bold() {
		return bold.on();
	}

	public boolean mark() {
		return mark.on();
	}
}
