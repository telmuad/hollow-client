package dev.telmuad.hollowclient.gui;

import net.minecraft.client.gui.screens.Screen;

/** A module with its own screen instead of the generic settings list (for example the theme editor). */
public interface ScreenProvider {
	Screen createScreen(Screen parent);
}
