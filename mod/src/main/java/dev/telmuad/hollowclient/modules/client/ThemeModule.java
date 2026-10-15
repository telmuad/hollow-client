package dev.telmuad.hollowclient.modules.client;

import dev.telmuad.hollowclient.gui.ScreenProvider;
import dev.telmuad.hollowclient.gui.ThemeScreen;
import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import net.minecraft.client.gui.screens.Screen;

/** Opens the theme editor. The theme itself lives in config/hollowclient/theme.json (shared with the launcher). */
public final class ThemeModule extends Module implements ScreenProvider {
	public ThemeModule() {
		super("theme", "Theme", "Colors, glow, blur, rounding and animation speed for Hollow's UI and launcher.", Category.CLIENT, true);
	}

	@Override
	public boolean toggleable() {
		return false;
	}

	@Override
	public Screen createScreen(Screen parent) {
		return new ThemeScreen(parent);
	}
}
