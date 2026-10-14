package dev.telmuad.hollowclient.modules;

import dev.telmuad.hollowclient.module.ModuleRegistry;
import net.minecraft.client.gui.screens.Screen;

/** The one place that lists every module. Order here = order in the menu. */
public final class Modules {
	private Modules() {
	}

	public static void registerAll(ModuleRegistry registry) {
	}

	/** Fabric event hooks that aren't tied to one module (HUD layer, cosmetics layer, reloads). */
	public static void registerHooks() {
	}

	public static Screen createMenu() {
		return null;
	}
}
