package dev.telmuad.hollowclient.modules;

import dev.telmuad.hollowclient.modules.hud.MusicHud;
import dev.telmuad.hollowclient.modules.qol.MusicPlayerModule;
import dev.telmuad.hollowclient.modules.qol.WaypointsModule;
import dev.telmuad.hollowclient.modules.visual.CameraPathModule;
import dev.telmuad.hollowclient.modules.ui.ScoreboardTitlesModule;
import dev.telmuad.hollowclient.modules.client.DiscordModule;
import dev.telmuad.hollowclient.modules.client.ProfilesModule;
import dev.telmuad.hollowclient.gui.HollowMenuScreen;
import dev.telmuad.hollowclient.hud.HudRenderer;
import dev.telmuad.hollowclient.module.ModuleRegistry;
import dev.telmuad.hollowclient.modules.client.MenuFxModule;
import dev.telmuad.hollowclient.modules.client.PerformanceModule;
import dev.telmuad.hollowclient.modules.client.ThemeModule;
import dev.telmuad.hollowclient.modules.client.ToastsModule;
import dev.telmuad.hollowclient.modules.client.UiFontModule;
import dev.telmuad.hollowclient.modules.client.UiSoundsModule;
import dev.telmuad.hollowclient.modules.ui.ChatStyleModule;
import dev.telmuad.hollowclient.modules.ui.InventoryBlurModule;
import dev.telmuad.hollowclient.modules.ui.LoadingScreenModule;
import dev.telmuad.hollowclient.modules.ui.PauseMenuModule;
import dev.telmuad.hollowclient.modules.ui.TitleScreenModule;
import dev.telmuad.hollowclient.modules.hud.ArmorHud;
import dev.telmuad.hollowclient.modules.hud.ClockHud;
import dev.telmuad.hollowclient.modules.hud.CoordinatesHud;
import dev.telmuad.hollowclient.modules.hud.CpsHud;
import dev.telmuad.hollowclient.modules.effects.HandAnimationsModule;
import dev.telmuad.hollowclient.modules.effects.HitEffectsModule;
import dev.telmuad.hollowclient.modules.effects.ItemPhysicsModule;
import dev.telmuad.hollowclient.modules.effects.OwnNametagModule;
import dev.telmuad.hollowclient.modules.effects.ParticlesModule;
import dev.telmuad.hollowclient.modules.hud.FpsHud;
import dev.telmuad.hollowclient.modules.hud.HudStyleModule;
import dev.telmuad.hollowclient.modules.hud.KeystrokesHud;
import dev.telmuad.hollowclient.modules.hud.PingHud;
import dev.telmuad.hollowclient.modules.hud.PotionHud;
import dev.telmuad.hollowclient.modules.hud.SessionStatsHud;
import dev.telmuad.hollowclient.modules.hud.ToggleStatusHud;
import dev.telmuad.hollowclient.modules.qol.AutoGgModule;
import dev.telmuad.hollowclient.modules.qol.ChatMacrosModule;
import dev.telmuad.hollowclient.modules.qol.ChatPlusModule;
import dev.telmuad.hollowclient.modules.qol.ScreenshotsModule;
import dev.telmuad.hollowclient.modules.qol.ToggleSprintModule;
import dev.telmuad.hollowclient.modules.qol.ZoomModule;
import dev.telmuad.hollowclient.modules.visual.ClearWaterModule;
import dev.telmuad.hollowclient.modules.visual.CrosshairModule;
import dev.telmuad.hollowclient.modules.visual.FullbrightModule;
import dev.telmuad.hollowclient.modules.visual.HitColorModule;
import dev.telmuad.hollowclient.modules.visual.LowFireModule;
import dev.telmuad.hollowclient.modules.visual.BlockOutlineModule;
import dev.telmuad.hollowclient.modules.visual.PostProcessingModule;
import dev.telmuad.hollowclient.modules.visual.SkyModule;
import dev.telmuad.hollowclient.modules.visual.TimeWeatherModule;
import dev.telmuad.hollowclient.modules.styles.AuraModule;
import dev.telmuad.hollowclient.modules.styles.CapeModule;
import dev.telmuad.hollowclient.modules.styles.EmotesModule;
import dev.telmuad.hollowclient.modules.styles.HaloModule;
import dev.telmuad.hollowclient.modules.styles.HornsModule;
import dev.telmuad.hollowclient.modules.styles.PetModule;
import dev.telmuad.hollowclient.modules.styles.HatModule;
import dev.telmuad.hollowclient.modules.styles.WingsModule;
import dev.telmuad.hollowclient.render.CosmeticsLayer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import dev.telmuad.hollowclient.input.Keybinds;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;

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
	public static CapeModule CAPE;
	public static HatModule HAT;
	public static WingsModule WINGS;
	public static HaloModule HALO;
	public static HornsModule HORNS;
	public static AuraModule AURA;
	public static PetModule PET;
	public static EmotesModule EMOTES;
	public static ThemeModule THEME;
	public static UiFontModule UI_FONT;
	public static UiSoundsModule UI_SOUNDS;
	public static PerformanceModule PERFORMANCE;
	public static MenuFxModule MENU_FX;
	public static ToastsModule TOASTS;
	public static TitleScreenModule TITLE_SCREEN;
	public static LoadingScreenModule LOADING_SCREEN;
	public static PauseMenuModule PAUSE_MENU;
	public static InventoryBlurModule INVENTORY_BLUR;
	public static ChatStyleModule CHAT_STYLE;
	public static HudStyleModule HUD_STYLE;
	public static PostProcessingModule POST_PROCESSING;
	public static SkyModule SKY;
	public static TimeWeatherModule TIME_WEATHER;
	public static BlockOutlineModule BLOCK_OUTLINE;
	public static HitEffectsModule HIT_EFFECTS;
	public static ParticlesModule PARTICLES;
	public static ItemPhysicsModule ITEM_PHYSICS;
	public static HandAnimationsModule HAND_ANIMATIONS;
	public static OwnNametagModule OWN_NAMETAG;
	public static ChatPlusModule CHAT_PLUS;
	public static AutoGgModule AUTO_GG;
	public static ChatMacrosModule CHAT_MACROS;
	public static ScreenshotsModule SCREENSHOTS;
	public static MusicPlayerModule MUSIC;
	public static WaypointsModule WAYPOINTS;
	public static CameraPathModule CAMERA_PATH;
	public static ScoreboardTitlesModule SCOREBOARD_TITLES;
	public static DiscordModule DISCORD;
	public static ProfilesModule PROFILES;
	public static dev.telmuad.hollowclient.skyblock.modules.SkyBlockModeModule SKYBLOCK_MODE;
	public static dev.telmuad.hollowclient.skyblock.features.general.FairySoulsModule FAIRY_SOULS;

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
		registry.register(new SessionStatsHud());
		registry.register(new MusicHud());
		HUD_STYLE = registry.register(new HudStyleModule());
		// Interface: Hollow's look for vanilla screens
		TITLE_SCREEN = registry.register(new TitleScreenModule());
		LOADING_SCREEN = registry.register(new LoadingScreenModule());
		PAUSE_MENU = registry.register(new PauseMenuModule());
		INVENTORY_BLUR = registry.register(new InventoryBlurModule());
		CHAT_STYLE = registry.register(new ChatStyleModule());
		SCOREBOARD_TITLES = registry.register(new ScoreboardTitlesModule());
		// Visual
		FULLBRIGHT = registry.register(new FullbrightModule());
		CROSSHAIR = registry.register(new CrosshairModule());
		LOW_FIRE = registry.register(new LowFireModule());
		CLEAR_WATER = registry.register(new ClearWaterModule());
		HIT_COLOR = registry.register(new HitColorModule());
		POST_PROCESSING = registry.register(new PostProcessingModule());
		SKY = registry.register(new SkyModule());
		TIME_WEATHER = registry.register(new TimeWeatherModule());
		BLOCK_OUTLINE = registry.register(new BlockOutlineModule());
		CAMERA_PATH = registry.register(new CameraPathModule());
		// Effects: particles and animations
		HIT_EFFECTS = registry.register(new HitEffectsModule());
		PARTICLES = registry.register(new ParticlesModule());
		ITEM_PHYSICS = registry.register(new ItemPhysicsModule());
		HAND_ANIMATIONS = registry.register(new HandAnimationsModule());
		OWN_NAMETAG = registry.register(new OwnNametagModule());
		// Quality of life
		ZOOM = registry.register(new ZoomModule());
		TOGGLE_SPRINT = registry.register(new ToggleSprintModule());
		CHAT_PLUS = registry.register(new ChatPlusModule());
		AUTO_GG = registry.register(new AutoGgModule());
		CHAT_MACROS = registry.register(new ChatMacrosModule());
		SCREENSHOTS = registry.register(new ScreenshotsModule());
		WAYPOINTS = registry.register(new WaypointsModule());
		MUSIC = registry.register(new MusicPlayerModule());
		// Styles (shown as tiles in the menu; picked in the Styles screen)
		CAPE = registry.register(new CapeModule());
		HAT = registry.register(new HatModule());
		WINGS = registry.register(new WingsModule());
		HALO = registry.register(new HaloModule());
		HORNS = registry.register(new HornsModule());
		AURA = registry.register(new AuraModule());
		PET = registry.register(new PetModule());
		EMOTES = registry.register(new EmotesModule());
		// SkyBlock Mode (display-only features for Hypixel SkyBlock, grouped by area in the menu)
		SKYBLOCK_MODE = registry.register(new dev.telmuad.hollowclient.skyblock.modules.SkyBlockModeModule());
		registry.register(new dev.telmuad.hollowclient.skyblock.modules.SkyBlockInfoHud());
		registry.register(new dev.telmuad.hollowclient.skyblock.modules.SkyBlockSessionHud());
		registry.register(new dev.telmuad.hollowclient.skyblock.features.general.StatusBarsHud());
		registry.register(new dev.telmuad.hollowclient.skyblock.features.general.VanillaBarsModule());
		registry.register(new dev.telmuad.hollowclient.skyblock.features.general.GeneralHuds.PetHud());
		registry.register(new dev.telmuad.hollowclient.skyblock.features.general.GeneralHuds.NextEventsHud());
		registry.register(new dev.telmuad.hollowclient.skyblock.features.general.GeneralHuds.DeployablesHud());
		registry.register(new dev.telmuad.hollowclient.skyblock.features.general.DamageNumbersModule());
		registry.register(new dev.telmuad.hollowclient.skyblock.features.general.ClutterModule());
		FAIRY_SOULS = registry.register(new dev.telmuad.hollowclient.skyblock.features.general.FairySoulsModule());
		// Client: theme, font, sounds and the global Performance Mode
		THEME = registry.register(new ThemeModule());
		PROFILES = registry.register(new ProfilesModule());
		PERFORMANCE = registry.register(new PerformanceModule());
		UI_FONT = registry.register(new UiFontModule());
		UI_SOUNDS = registry.register(new UiSoundsModule());
		MENU_FX = registry.register(new MenuFxModule());
		TOASTS = registry.register(new ToastsModule());
		DISCORD = registry.register(new DiscordModule());
	}

	/** Fabric event hooks that aren't tied to one module (HUD layer, crosshair, cosmetics layer, reloads). */
	public static void registerHooks() {
		HudRenderer.register();
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> CROSSHAIR.wrap(vanilla));
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) helper.register(new CosmeticsLayer(avatar));
		});
		// Keybinds don't fire while a screen is open, so also let the menu key work on the title and pause screens.
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof TitleScreen || screen instanceof PauseScreen) {
				ScreenKeyboardEvents.afterKeyPress(screen).register((s, event) -> {
					if (Keybinds.menuKey() != null && Keybinds.menuKey().matches(event) && client.gui.screen() == s) {
						client.gui.setScreen(new HollowMenuScreen(s));
					}
				});
			}
		});
	}

	public static Screen createMenu() {
		return new HollowMenuScreen();
	}
}
