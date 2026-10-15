package dev.telmuad.hollowclient.modules.client;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;

/** Hollow's UI font (Inter, SIL Open Font License). Off = the vanilla Minecraft font everywhere. */
public final class UiFontModule extends Module {
	private final BooleanSetting hud = add(new BooleanSetting("hud", "Use it for HUD elements too", true));

	public UiFontModule() {
		super("ui_font", "UI Font", "Use the Inter font in Hollow's menus and HUD. Off = Minecraft's font.", Category.CLIENT, true);
	}

	public boolean inHud() {
		return hud.on();
	}
}
