package dev.telmuad.hollowclient.modules.ui;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.NumberSetting;

/** Blur the world behind inventories and chests (any container screen). */
public final class InventoryBlurModule extends Module {
	private final NumberSetting strength = add(new NumberSetting("strength", "Blur strength", 50, 10, 100, 5).format(0, "%"));
	private final BooleanSetting dim = add(new BooleanSetting("dim", "Darken the background too", true));

	public InventoryBlurModule() {
		super("inventory_blur", "Inventory Blur", "Blur the world behind your inventory, chests and other containers.", Category.INTERFACE, true);
	}

	public float strength() {
		return strength.getFloat() / 100f;
	}

	public boolean dim() {
		return dim.on();
	}
}
