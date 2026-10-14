package dev.telmuad.hollowclient.modules;

import dev.telmuad.hollowclient.gui.HollowMenuScreen;
import dev.telmuad.hollowclient.hud.HudRenderer;
import dev.telmuad.hollowclient.module.ModuleRegistry;
import dev.telmuad.hollowclient.modules.hud.ArmorHud;
import dev.telmuad.hollowclient.modules.hud.ClockHud;
import dev.telmuad.hollowclient.modules.hud.CoordinatesHud;
import dev.telmuad.hollowclient.modules.hud.CpsHud;
import dev.telmuad.hollowclient.modules.hud.FpsHud;
import dev.telmuad.hollowclient.modules.hud.KeystrokesHud;
import dev.telmuad.hollowclient.modules.hud.PingHud;
import dev.telmuad.hollowclient.modules.hud.PotionHud;
import net.minecraft.client.gui.screens.Screen;

/** The one place that lists every module. Order here = order in the menu. */
public final class Modules {
	private Modules() {
	}

	public static void registerAll(ModuleRegistry registry) {
		// HUD
		registry.register(new FpsHud());
		registry.register(new CoordinatesHud());
		registry.register(new CpsHud());
		registry.register(new KeystrokesHud());
		registry.register(new ArmorHud());
		registry.register(new PotionHud());
		registry.register(new PingHud());
		registry.register(new ClockHud());
	}

	/** Fabric event hooks that aren't tied to one module (HUD layer, cosmetics layer, reloads). */
	public static void registerHooks() {
		HudRenderer.register();
	}

	public static Screen createMenu() {
		return new HollowMenuScreen();
	}
}
