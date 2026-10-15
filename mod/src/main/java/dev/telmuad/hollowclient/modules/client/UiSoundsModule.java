package dev.telmuad.hollowclient.modules.client;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.NumberSetting;

/** Soft original click, hover, toggle and whoosh sounds in Hollow's menus. */
public final class UiSoundsModule extends Module {
	private final NumberSetting volume = add(new NumberSetting("volume", "Volume", 70, 0, 100, 5).format(0, "%"));
	private final BooleanSetting hover = add(new BooleanSetting("hover", "Hover sounds", true));

	public UiSoundsModule() {
		super("ui_sounds", "UI Sounds", "Clicks, toggles and whooshes in Hollow's menus.", Category.CLIENT, true);
	}

	public float volume() {
		return volume.getFloat() / 100f;
	}

	public boolean hoverSounds() {
		return hover.on();
	}
}
