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
import dev.telmuad.hollowclient.modules.hud.ToggleStatusHud;
import dev.telmuad.hollowclient.modules.qol.ToggleSprintModule;
import dev.telmuad.hollowclient.modules.qol.ZoomModule;
import dev.telmuad.hollowclient.modules.visual.ClearWaterModule;
import dev.telmuad.hollowclient.modules.visual.CrosshairModule;
import dev.telmuad.hollowclient.modules.visual.FullbrightModule;
import dev.telmuad.hollowclient.modules.visual.HitColorModule;
import dev.telmuad.hollowclient.modules.visual.LowFireModule;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.gui.screens.Screen;

/**
 * The one place that lists every module. Order here = order in the menu.
 * Modules that mixins call into are kept in static fields so the hot paths don't search the registry.
 */
public final class Modules {
	public static ZoomModule ZOOM;
	public static ToggleSprintModule TOGGLE_SPRINT;
	public static FullbrightModule FULLBRIGHT;
	public static CrosshairModule CROSSHAIR;
	public static LowFireModule LOW_FIRE;
	public static ClearWaterModule CLEAR_WATER;
	public static HitColorModule HIT_COLOR;

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
		registry.register(new ToggleStatusHud());
		// Visual
		FULLBRIGHT = registry.register(new FullbrightModule());
		CROSSHAIR = registry.register(new CrosshairModule());
		LOW_FIRE = registry.register(new LowFireModule());
		CLEAR_WATER = registry.register(new ClearWaterModule());
		HIT_COLOR = registry.register(new HitColorModule());
		// Quality of life
		ZOOM = registry.register(new ZoomModule());
		TOGGLE_SPRINT = registry.register(new ToggleSprintModule());
	}

	/** Fabric event hooks that aren't tied to one module (HUD layer, crosshair, cosmetics layer, reloads). */
	public static void registerHooks() {
		HudRenderer.register();
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> CROSSHAIR.wrap(vanilla));
	}

	public static Screen createMenu() {
		return new HollowMenuScreen();
	}
}
