package dev.telmuad.hollowclient.modules.client;

import dev.telmuad.hollowclient.gui.ProfilesScreen;
import dev.telmuad.hollowclient.gui.ScreenProvider;
import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import net.minecraft.client.gui.screens.Screen;

/** Opens the profiles screen: PvP, Building and Cinematic setups in one click, plus your own saved ones. */
public final class ProfilesModule extends Module implements ScreenProvider {
	public ProfilesModule() {
		super("profiles", "Profiles", "One click to set Hollow up for PvP, building or filming, or save your own setups.", Category.CLIENT, true);
	}

	@Override
	public boolean toggleable() {
		return false;
	}

	@Override
	public Screen createScreen(Screen parent) {
		return new ProfilesScreen(parent);
	}
}
