package dev.telmuad.hollowclient.test;

import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.gui.HollowMenuScreen;
import dev.telmuad.hollowclient.gui.HudEditorScreen;
import dev.telmuad.hollowclient.gui.HollowScreen;
import dev.telmuad.hollowclient.gui.HollowTitleScreen;
import dev.telmuad.hollowclient.gui.StylesScreen;
import dev.telmuad.hollowclient.gui.ThemeScreen;
import dev.telmuad.hollowclient.gui.Toasts;
import dev.telmuad.hollowclient.theme.HollowTheme;
import dev.telmuad.hollowclient.theme.Themes;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.render.CosmeticModels;
import dev.telmuad.hollowclient.styles.CapeManager;
import dev.telmuad.hollowclient.styles.PackImporter;
import dev.telmuad.hollowclient.styles.PackManager;
import dev.telmuad.hollowclient.styles.PresetManager;
import net.minecraft.client.CameraType;
import dev.telmuad.hollowclient.hud.HudModule;
import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.EnumSetting;
import dev.telmuad.hollowclient.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Automated in-game walkthrough, run with {@code gradlew runClientGameTest}. It opens every Hollow screen, tries the
 * modules in a test world and saves screenshots to {@code build/run/clientGameTest/screenshots}. Behaviour checks
 * are logged as "CHECK ... PASS/FAIL"; any failure fails the run at the end (after all screenshots are taken).
 */
public final class HollowClientGameTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("Hollow Client test");
	private final List<String> failures = new ArrayList<>();
	private int shot;
	private int x;
	private int y;
	private int z;

	/** Absolute coordinates relative to the spawn point. */
	private String at(int dx, int dy, int dz) {
		return (x + dx) + " " + (y + dy) + " " + (z + dz);
	}

	private void home(TestSingleplayerContext sp) {
		sp.getServer().runCommand("tp @a " + (x + 0.5) + " " + y + " " + (z + 0.5) + " 0 15");
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		ctx.waitTicks(40);
		check("Hollow's title screen replaces vanilla's", ctx.computeOnClient(mc -> mc.screen instanceof HollowTitleScreen));
		screenshot(ctx, "title_screen");
		// a resource reload shows the loading screen
		ctx.runOnClient(mc -> mc.reloadResourcePacks());
		ctx.waitTicks(4);
		screenshot(ctx, "loading_screen");
		ctx.waitFor(mc -> mc.getOverlay() == null, 1200);
		ctx.waitTicks(10);
		ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
		ctx.waitTicks(5);
		boolean fromTitle = ctx.computeOnClient(mc -> mc.screen instanceof HollowMenuScreen);
		check("Right Shift opens the menu on the title screen", fromTitle);
		ctx.setScreen(() -> HollowMenuScreen.at(Category.HUD));
		ctx.waitTicks(10);
		screenshot(ctx, "menu_on_title_screen");
		ctx.setScreen(() -> null);

		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("weather clear");
			sp.getServer().runCommand("gamerule doDaylightCycle false");
			sp.getServer().runCommand("difficulty peaceful");
			sp.getServer().runCommand("gamemode survival @a");
			sp.getServer().runCommand("effect give @a fire_resistance infinite 0 true");
			ctx.waitTicks(20);
			int[] spawn = ctx.computeOnClient(mc -> new int[]{mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()});
			x = spawn[0];
			y = spawn[1];
			z = spawn[2];
			sp.getServer().runCommand("fill " + (x - 8) + " " + y + " " + (z - 8) + " " + (x + 8) + " " + (y + 8) + " " + (z + 8) + " air");
			sp.getServer().runCommand("fill " + (x - 8) + " " + (y - 1) + " " + (z - 8) + " " + (x + 8) + " " + (y - 1) + " " + (z + 8) + " grass_block");
			home(sp);
			ctx.waitTicks(40);
			screenshot(ctx, "world_default_hud");

			menus(ctx);
			screens(ctx);
			hud(ctx);
			qolAndVisual(ctx, sp);
			postProcessing(ctx, sp);
			skyAndWorld(ctx, sp);
			effects(ctx, sp);
			styles(ctx, sp);
		}
		if (!failures.isEmpty()) throw new AssertionError("Hollow Client checks failed: " + failures);
	}

	// ------------------------------------------------------------------------------------------------ helpers

	private void screenshot(ClientGameTestContext ctx, String name) {
		shot++;
		ctx.takeScreenshot(String.format("%02d_%s", shot, name));
	}

	private void check(String what, boolean ok) {
		LOG.info("CHECK {}: {}", what, ok ? "PASS" : "FAIL");
		if (!ok) failures.add(what);
	}

	private static Module module(String id) {
		return HollowClient.modules().get(id).orElseThrow(() -> new IllegalStateException("No module " + id));
	}

	private static void setEnabled(ClientGameTestContext ctx, String id, boolean on) {
		ctx.runOnClient(mc -> module(id).setEnabled(on));
	}

	@SuppressWarnings("unchecked")
	private static <E extends Enum<E>> void setEnum(ClientGameTestContext ctx, String id, String setting, String value) {
		ctx.runOnClient(mc -> {
			Setting<?> s = module(id).setting(setting);
			if (s instanceof EnumSetting<?> es) {
				EnumSetting<E> e = (EnumSetting<E>) es;
				for (E v : e.values()) if (v.name().equalsIgnoreCase(value)) e.set(v);
			}
		});
	}

	/** Click at GUI coordinates (converted to window pixels). */
	private static void clickGui(ClientGameTestContext ctx, double gx, double gy) {
		double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
		ctx.getInput().setCursorPos(gx * scale, gy * scale);
		ctx.waitTick();
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(3);
	}

	// ------------------------------------------------------------------------------------------------ steps

	private void menus(ClientGameTestContext ctx) {
		ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
		ctx.waitTicks(5);
		boolean open = ctx.computeOnClient(mc -> mc.screen instanceof HollowMenuScreen);
		check("Right Shift opens the menu", open);
		ctx.waitTicks(5);
		screenshot(ctx, "menu_hud");
		// hover the first card: glow, tilt and shine
		int[] card = ctx.computeOnClient(mc -> {
			int w = mc.getWindow().getGuiScaledWidth();
			int h = mc.getWindow().getGuiScaledHeight();
			int margin = w < 420 ? 4 : 14;
			int pw = Math.min(w - 2 * margin, 620);
			int ph = Math.min(h - 2 * margin, 370);
			int px = (w - pw) / 2;
			int py = (h - ph) / 2;
			int railW = pw >= 440 ? 108 : 28;
			return new int[]{px + 8 + railW + 8 + 30, py + 30 + 2 + 14 + 2 + 12};
		});
		double gui = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
		ctx.getInput().setCursorPos(card[0] * gui, card[1] * gui);
		ctx.waitTicks(8);
		screenshot(ctx, "menu_card_hover");
		// live search
		ctx.getInput().pressKey(GLFW.GLFW_KEY_SLASH);
		ctx.waitTick();
		ctx.getInput().typeChars("co");
		ctx.waitTicks(10);
		screenshot(ctx, "menu_search_co");
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		ctx.waitTick();
		// notification card
		ctx.runOnClient(mc -> Toasts.success("Theme saved", "Void is also used by the launcher."));
		ctx.waitTicks(12);
		check("Hollow notifications show up", ctx.computeOnClient(mc -> Toasts.count() == 1));
		screenshot(ctx, "toast_over_menu");
		// closing animates: still open for a moment, then gone
		ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
		boolean stillThere = ctx.computeOnClient(mc -> mc.screen instanceof HollowMenuScreen);
		ctx.waitTicks(10);
		boolean gone = ctx.computeOnClient(mc -> mc.screen == null);
		check("The menu closes with an animation (" + stillThere + ", " + gone + ")", stillThere && gone);
		ctx.setScreen(() -> HollowMenuScreen.at(Category.HUD));
		ctx.waitTicks(8);
		for (Category c : new Category[]{Category.VISUAL, Category.QOL, Category.STYLES, Category.CLIENT}) {
			ctx.setScreen(() -> HollowMenuScreen.at(c));
			ctx.waitTicks(8);
			screenshot(ctx, "menu_" + c.name().toLowerCase());
		}
		theme(ctx);
		ctx.setScreen(() -> HollowMenuScreen.settingsFor(module("crosshair"), null));
		ctx.waitTicks(8);
		screenshot(ctx, "settings_crosshair");
		// Open the color picker on the "Color" row (second setting row, after the keybind and shape rows).
		int[] swatch = ctx.computeOnClient(mc -> {
			int w = mc.getWindow().getGuiScaledWidth();
			int h = mc.getWindow().getGuiScaledHeight();
			int pw = Math.min(w - 24, 380);
			int ph = Math.min(h - 24, 320);
			int px = (w - pw) / 2;
			int py = (h - ph) / 2;
			int listY = py + 48;
			int right = px + 8 + (pw - 16) - 6 - 6;
			return new int[]{right - 13, listY + 2 * 22 + 10};
		});
		clickGui(ctx, swatch[0], swatch[1]);
		ctx.waitTicks(5);
		screenshot(ctx, "color_picker");
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		ctx.waitTicks(3);
		ctx.setScreen(() -> HollowMenuScreen.settingsFor(module("coordinates"), null));
		ctx.waitTicks(8);
		screenshot(ctx, "settings_coordinates");
		ctx.setScreen(() -> null);
		ctx.waitTicks(5);
	}

	private void screens(ClientGameTestContext ctx) {
		// pause menu: blurred, glass panel, Hollow buttons
		ctx.setScreen(() -> new net.minecraft.client.gui.screens.PauseScreen(true));
		ctx.waitTicks(8);
		screenshot(ctx, "pause_menu");
		// inventory with blur
		ctx.setScreen(() -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(Minecraft.getInstance().player));
		ctx.waitTicks(6);
		screenshot(ctx, "inventory_blur");
		ctx.setScreen(() -> null);
		// chat: a few messages, the newest one mid slide-in, then the chat screen
		ctx.runOnClient(mc -> {
			for (int i = 1; i <= 4; i++) mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal("Hollow chat line " + i));
		});
		ctx.waitTicks(20);
		ctx.runOnClient(mc -> mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal("A new message slides in")));
		ctx.waitTicks(1);
		screenshot(ctx, "chat_slide_in");
		ctx.setScreen(() -> new net.minecraft.client.gui.screens.ChatScreen("hello", false));
		ctx.waitTicks(6);
		screenshot(ctx, "chat_open");
		ctx.setScreen(() -> null);
		ctx.waitTicks(3);
	}

	private void theme(ClientGameTestContext ctx) {
		ctx.setScreen(() -> HollowMenuScreen.settingsFor(module("theme"), null));
		ctx.waitTicks(8);
		check("The Theme card opens the theme editor", ctx.computeOnClient(mc -> mc.screen instanceof ThemeScreen));
		screenshot(ctx, "theme_editor_void");
		ctx.runOnClient(mc -> Themes.store().setCurrent(HollowTheme.INFERNO));
		ctx.waitTicks(5);
		screenshot(ctx, "theme_editor_inferno");
		ctx.setScreen(() -> HollowMenuScreen.at(Category.VISUAL));
		ctx.waitTicks(8);
		screenshot(ctx, "menu_visual_inferno");
		boolean shared = ctx.computeOnClient(mc -> {
			try {
				return java.nio.file.Files.readString(HollowClient.dataDir().resolve("theme.json")).contains("Inferno");
			} catch (java.io.IOException e) {
				return false;
			}
		});
		check("The theme is saved to the shared theme.json", shared);
		int blur = ctx.computeOnClient(mc -> ((HollowScreen) mc.screen).blurRadius(5));
		ctx.runOnClient(mc -> Modules.PERFORMANCE.performanceModeSetting().set(true));
		int blurPerf = ctx.computeOnClient(mc -> ((HollowScreen) mc.screen).blurRadius(5));
		check("Performance Mode turns the menu blur off (" + blur + " -> " + blurPerf + ")", blur > 0 && blurPerf == 0);
		ctx.runOnClient(mc -> {
			Modules.PERFORMANCE.performanceModeSetting().set(false);
			Themes.store().setCurrent(HollowTheme.VOID);
			Modules.UI_FONT.setEnabled(false);
		});
		ctx.setScreen(() -> HollowMenuScreen.at(Category.HUD));
		ctx.waitTicks(8);
		screenshot(ctx, "menu_vanilla_font");
		ctx.runOnClient(mc -> Modules.UI_FONT.setEnabled(true));
		ctx.setScreen(() -> null);
		ctx.waitTicks(3);
	}

	private void hud(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			for (Module m : HollowClient.modules().all()) if (m instanceof HudModule) m.setEnabled(true);
		});
		ctx.getInput().holdKey(GLFW.GLFW_KEY_W);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		ctx.waitTicks(2);
		int cps = ctx.computeOnClient(mc -> dev.telmuad.hollowclient.input.Clicks.leftCps());
		check("CPS counts left clicks (got " + cps + ")", cps >= 2);
		screenshot(ctx, "all_hud_elements");
		ctx.getInput().releaseKey(GLFW.GLFW_KEY_W);
		ctx.waitTicks(5);
		// hotbar: the selection glides to slot 6 (caught mid-way), then back to slot 1
		ctx.getInput().pressKey(GLFW.GLFW_KEY_6);
		ctx.waitTick();
		screenshot(ctx, "hotbar_glide");
		ctx.getInput().pressKey(GLFW.GLFW_KEY_1);
		ctx.waitTicks(10);
		// other HUD looks
		ctx.runOnClient(mc -> ((dev.telmuad.hollowclient.setting.BooleanSetting) module("hud_style").setting("gradientText")).set(true));
		setEnum(ctx, "hud_style", "look", "outline");
		ctx.waitTicks(3);
		screenshot(ctx, "hud_outline_gradient_text");
		ctx.runOnClient(mc -> ((dev.telmuad.hollowclient.setting.BooleanSetting) module("hud_style").setting("gradientText")).set(false));
		setEnum(ctx, "hud_style", "look", "glass");
		setEnabled(ctx, "hud_style", false);
		ctx.waitTicks(3);
		screenshot(ctx, "hud_classic");
		setEnabled(ctx, "hud_style", true);
		ctx.waitTicks(3);
		ctx.setScreen(() -> new HudEditorScreen(null));
		ctx.waitTicks(8);
		screenshot(ctx, "hud_editor");
		ctx.setScreen(() -> null);
		ctx.waitTicks(3);
	}

	private void qolAndVisual(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		// Crosshair
		setEnabled(ctx, "crosshair", true);
		setEnum(ctx, "crosshair", "shape", "circle");
		ctx.waitTicks(3);
		screenshot(ctx, "crosshair_circle");
		setEnum(ctx, "crosshair", "shape", "cross");

		// Zoom
		double before = ctx.computeOnClient(mc -> (double) mc.options.fov().get());
		ctx.getInput().holdKey(GLFW.GLFW_KEY_C);
		ctx.waitTicks(20);
		double mult = ctx.computeOnClient(mc -> dev.telmuad.hollowclient.modules.Modules.ZOOM.fovMultiplier());
		check("Zoom divides the FOV while C is held (x" + String.format("%.2f", mult) + ", fov " + before + ")", mult < 0.5);
		screenshot(ctx, "zoom_held");
		ctx.getInput().scroll(3);
		ctx.waitTicks(10);
		double mult2 = ctx.computeOnClient(mc -> dev.telmuad.hollowclient.modules.Modules.ZOOM.fovMultiplier());
		check("Scrolling while zoomed zooms further", mult2 < mult);
		int slot = ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
		check("Scrolling while zoomed keeps the hotbar slot (slot " + slot + ")", slot == 0);
		ctx.getInput().releaseKey(GLFW.GLFW_KEY_C);
		ctx.waitTicks(20);
		double mult3 = ctx.computeOnClient(mc -> dev.telmuad.hollowclient.modules.Modules.ZOOM.fovMultiplier());
		check("Zoom goes back to normal after release", mult3 > 0.99);

		// Toggle sprint
		setEnabled(ctx, "toggle_sprint", true);
		ctx.getInput().pressKey(o -> o.keySprint);
		ctx.waitTicks(2);
		ctx.getInput().holdKey(o -> o.keyUp);
		ctx.waitTicks(20);
		boolean sprinting = ctx.computeOnClient(mc -> mc.player.isSprinting());
		check("Toggle sprint keeps sprinting after one press", sprinting);
		screenshot(ctx, "toggle_sprint_status");
		ctx.getInput().releaseKey(o -> o.keyUp);
		ctx.getInput().pressKey(o -> o.keySprint);
		ctx.waitTicks(5);
		ctx.getInput().holdKey(o -> o.keyUp);
		ctx.waitTicks(10);
		boolean stillSprinting = ctx.computeOnClient(mc -> mc.player.isSprinting());
		check("Second sprint press turns toggle sprint off", !stillSprinting);
		ctx.getInput().releaseKey(o -> o.keyUp);
		setEnabled(ctx, "toggle_sprint", false);
		home(sp);
		ctx.waitTicks(10);

		// Fullbright: a closed stone box at midnight
		sp.getServer().runCommand("time set midnight");
		sp.getServer().runCommand("fill " + at(-3, -1, -3) + " " + at(3, 4, 3) + " stone hollow");
		ctx.waitTicks(30);
		screenshot(ctx, "dark_box_without_fullbright");
		setEnabled(ctx, "fullbright", true);
		ctx.waitTicks(10);
		screenshot(ctx, "dark_box_with_fullbright");
		setEnabled(ctx, "fullbright", false);
		sp.getServer().runCommand("fill " + at(-3, 0, -3) + " " + at(3, 4, 3) + " air");
		sp.getServer().runCommand("time set noon");

		// Hit color: a pig three blocks in front, hurt it
		sp.getServer().runCommand("summon pig " + (x + 0.5) + " " + y + " " + (z + 3.5) + " {NoAI:1b,Rotation:[180f,0f]}");
		setEnabled(ctx, "hit_color", true);
		ctx.waitTicks(10);
		sp.getServer().runCommand("damage @e[type=pig,limit=1] 1");
		ctx.waitTicks(2);
		screenshot(ctx, "hit_color");
		sp.getServer().runCommand("kill @e[type=pig]");
		setEnabled(ctx, "hit_color", false);

		// Low fire
		sp.getServer().runCommand("setblock " + at(0, 0, 0) + " fire");
		ctx.waitTicks(10);
		screenshot(ctx, "fire_normal");
		setEnabled(ctx, "low_fire", true);
		ctx.waitTicks(3);
		screenshot(ctx, "fire_low");
		setEnabled(ctx, "low_fire", false);
		sp.getServer().runCommand("setblock " + at(0, 0, 0) + " air");
		ctx.runOnClient(mc -> {
			if (mc.player != null) mc.player.clearFire();
		});
		ctx.waitTicks(40);

		// Clear water
		sp.getServer().runCommand("fill " + at(-6, 0, -6) + " " + at(6, 6, 6) + " water");
		ctx.waitTicks(30);
		screenshot(ctx, "underwater_normal");
		setEnabled(ctx, "clear_water", true);
		ctx.waitTicks(5);
		screenshot(ctx, "underwater_clear");
		setEnabled(ctx, "clear_water", false);
		sp.getServer().runCommand("fill " + at(-6, 0, -6) + " " + at(6, 6, 6) + " air");
		ctx.waitTicks(10);
	}

	// ------------------------------------------------------------------------------------------------ post processing

	private void postProcessing(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		home(sp);
		sp.getServer().runCommand("time set noon");
		ctx.waitTicks(10);
		for (String grade : new String[]{"cinematic", "vibrant", "noir", "dreamy"}) {
			setEnum(ctx, "post_processing", "grade", grade);
			ctx.waitTicks(3);
			screenshot(ctx, "grade_" + grade);
		}
		setEnum(ctx, "post_processing", "grade", "none");
		check("Post-processing shaders compile and run", ctx.computeOnClient(mc -> dev.telmuad.hollowclient.render.PostFx.healthy()
				&& dev.telmuad.hollowclient.render.PostFx.active()));
		// bloom at night: a few glowstone blocks in front
		sp.getServer().runCommand("time set midnight");
		sp.getServer().runCommand("fill " + at(-2, 0, 4) + " " + at(2, 2, 4) + " glowstone");
		ctx.waitTicks(30);
		screenshot(ctx, "bloom_night");
		ctx.runOnClient(mc -> setBool("post_processing", "bloom", false));
		ctx.waitTicks(3);
		screenshot(ctx, "bloom_off_night");
		ctx.runOnClient(mc -> setBool("post_processing", "bloom", true));
		sp.getServer().runCommand("fill " + at(-2, 0, 4) + " " + at(2, 2, 4) + " air");
		sp.getServer().runCommand("time set noon");
		// chromatic aberration + grain, then motion blur while turning
		ctx.runOnClient(mc -> {
			setBool("post_processing", "chromatic", true);
			setBool("post_processing", "grain", true);
		});
		ctx.waitTicks(10);
		screenshot(ctx, "chromatic_grain");
		ctx.runOnClient(mc -> {
			setBool("post_processing", "chromatic", false);
			setBool("post_processing", "grain", false);
			setBool("post_processing", "motionBlur", true);
		});
		for (int i = 0; i < 6; i++) {
			ctx.runOnClient(mc -> mc.player.setYRot(mc.player.getYRot() + 9f));
			ctx.waitTick();
		}
		screenshot(ctx, "motion_blur_turning");
		ctx.runOnClient(mc -> setBool("post_processing", "motionBlur", false));
		home(sp);
		ctx.waitTicks(5);
	}

	private void skyAndWorld(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		home(sp);
		sp.getServer().runCommand("weather clear");
		sp.getServer().runCommand("time set midnight");
		// look north and up at the night sky: aurora, brighter stars and a shooting star
		sp.getServer().runCommand("tp @a " + (x + 0.5) + " " + y + " " + (z + 0.5) + " 180 -30");
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> dev.telmuad.hollowclient.render.SkyFx.spawnTestStreak());
		ctx.waitTicks(2);
		screenshot(ctx, "night_sky_aurora");
		ctx.runOnClient(mc -> setBool("sky", "moreStars", true));
		ctx.waitTicks(3);
		screenshot(ctx, "night_sky_more_stars");
		ctx.runOnClient(mc -> setBool("sky", "moreStars", false));
		// sky colors (theme) at day
		sp.getServer().runCommand("time set noon");
		ctx.runOnClient(mc -> setBool("sky", "tint", true));
		ctx.waitTicks(5);
		screenshot(ctx, "sky_tint_day");
		ctx.runOnClient(mc -> setBool("sky", "tint", false));
		// client-only time: sunset on screen while the server stays at noon
		ctx.runOnClient(mc -> {
			setBool("time_weather", "fixTime", true);
			((dev.telmuad.hollowclient.setting.NumberSetting) module("time_weather").setting("hour")).set(18.5);
			mc.player.setYRot(-90f);
			mc.player.setXRot(-10f);
		});
		ctx.waitTicks(5);
		long shown = ctx.computeOnClient(mc -> mc.level.getDayTime() % 24000);
		long server = sp.getServer().computeOnServer(s -> s.overworld().getDayTime() % 24000);
		check("Client time changer shows 18:30 while the server stays at noon (" + shown + " / " + server + ")", shown == 12500 && server == 6000);
		screenshot(ctx, "client_time_sunset");
		ctx.runOnClient(mc -> setBool("time_weather", "fixTime", false));
		// client-only weather
		setEnum(ctx, "time_weather", "weather", "thunder");
		ctx.waitTicks(20);
		float rain = ctx.computeOnClient(mc -> mc.level.getRainLevel(1f));
		check("Client weather changer shows rain (" + rain + ")", rain >= 0.99f);
		screenshot(ctx, "client_weather_rain");
		setEnum(ctx, "time_weather", "weather", "server");
		// block outline with fill: a stone block right in front at eye level
		sp.getServer().runCommand("setblock " + at(0, 1, 2) + " stone");
		ctx.runOnClient(mc -> {
			mc.player.setYRot(0f);
			mc.player.setXRot(20f);
		});
		ctx.waitTicks(5);
		screenshot(ctx, "block_outline");
		sp.getServer().runCommand("setblock " + at(0, 1, 2) + " air");
		home(sp);
		ctx.waitTicks(5);
	}

	private static int particleCount(Minecraft mc) {
		String s = mc.particleEngine.countParticles();
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private void effects(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		home(sp);
		sp.getServer().runCommand("time set noon");
		sp.getServer().runCommand("weather clear");
		sp.getServer().runCommand("kill @e[type=!player]");
		// a pig right in front; hit it: colored hit burst
		sp.getServer().runCommand("summon pig " + (x + 0.5) + " " + y + " " + (z + 2.5) + " {NoAI:1b,Rotation:[180f,0f]}");
		sp.getServer().runCommand("tp @a " + (x + 0.5) + " " + y + " " + (z + 0.5) + " 0 20");
		ctx.waitTicks(10);
		int before = ctx.computeOnClient(EffectsTestHelper::particles);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTick();
		int after = ctx.computeOnClient(EffectsTestHelper::particles);
		check("Hitting a mob spawns a hit burst (" + before + " -> " + after + " particles)", after > before);
		screenshot(ctx, "hit_burst");
		// a critical hit the server confirmed: sparks and an edge flash
		int beforeCrit = ctx.computeOnClient(EffectsTestHelper::particles);
		ctx.runOnClient(mc -> {
			var pig = mc.level.getEntitiesOfClass(net.minecraft.world.entity.animal.pig.Pig.class, mc.player.getBoundingBox().inflate(6)).stream().findFirst().orElse(null);
			if (pig != null) {
				dev.telmuad.hollowclient.render.EffectsFx.onLocalAttack(pig);
				dev.telmuad.hollowclient.render.EffectsFx.onCritPacket(pig, 4);
			}
		});
		ctx.waitTick();
		int afterCrit = ctx.computeOnClient(EffectsTestHelper::particles);
		check("A confirmed crit adds sparks (" + beforeCrit + " -> " + afterCrit + " particles)", afterCrit - beforeCrit >= 10);
		screenshot(ctx, "crit_sparks_flash");
		ctx.waitTicks(10);
		// kill it: kill burst
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		sp.getServer().runCommand("kill @e[type=pig]");
		ctx.waitTicks(3);
		screenshot(ctx, "kill_burst");
		ctx.waitTicks(30);
		// block break: pieces and sparkles
		sp.getServer().runCommand("setblock " + at(0, 0, 2) + " amethyst_block");
		ctx.waitTicks(3);
		sp.getServer().runCommand("setblock " + at(0, 0, 2) + " air destroy");
		ctx.waitTicks(2);
		screenshot(ctx, "block_break_sparkles");
		sp.getServer().runCommand("kill @e[type=item]");
		ctx.waitTicks(20);
		// trail and footprints, seen from behind in third person
		ctx.runOnClient(mc -> {
			setBool("particles", "trail", true);
			setBool("particles", "footprints", true);
			mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
		});
		sp.getServer().runCommand("tp @a " + (x + 0.5) + " " + y + " " + (z - 4.5) + " 0 30");
		ctx.waitTicks(5);
		int prints = ctx.computeOnClient(mc -> dev.telmuad.hollowclient.render.EffectsFx.footprintsPlaced);
		ctx.getInput().holdKey(GLFW.GLFW_KEY_W);
		ctx.waitTicks(25);
		ctx.getInput().releaseKey(GLFW.GLFW_KEY_W);
		ctx.waitTicks(2);
		int printsAfter = ctx.computeOnClient(mc -> dev.telmuad.hollowclient.render.EffectsFx.footprintsPlaced);
		check("Walking leaves footprints (" + (printsAfter - prints) + " placed)", printsAfter - prints >= 3);
		screenshot(ctx, "trail_and_footprints");
		// look down at the footprints from above
		ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
		double[] pos = ctx.computeOnClient(mc -> new double[]{mc.player.getX(), mc.player.getY(), mc.player.getZ()});
		sp.getServer().runCommand("tp @a " + pos[0] + " " + pos[1] + " " + pos[2] + " 180 65");
		ctx.waitTicks(3);
		screenshot(ctx, "footprints_from_above");
		ctx.runOnClient(mc -> {
			setBool("particles", "trail", false);
			setBool("particles", "footprints", false);
		});
		// own nametag in third person (front view)
		ctx.runOnClient(mc -> {
			module("own_nametag").setEnabled(true);
			mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
		});
		ctx.waitTicks(5);
		screenshot(ctx, "own_nametag");
		ctx.runOnClient(mc -> {
			module("own_nametag").setEnabled(false);
			mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
		});
		// item physics: items on the ground and one falling
		home(sp);
		ctx.runOnClient(mc -> module("item_physics").setEnabled(true));
		sp.getServer().runCommand("summon item " + (x + 0.2) + " " + y + " " + (z + 2.0) + " {Item:{id:\"minecraft:diamond_sword\",count:1},PickupDelay:32767}");
		sp.getServer().runCommand("summon item " + (x + 1.0) + " " + y + " " + (z + 2.3) + " {Item:{id:\"minecraft:apple\",count:3},PickupDelay:32767}");
		sp.getServer().runCommand("summon item " + (x - 0.6) + " " + y + " " + (z + 2.2) + " {Item:{id:\"minecraft:grass_block\",count:1},PickupDelay:32767}");
		sp.getServer().runCommand("summon item " + (x + 0.5) + " " + (y + 3) + " " + (z + 2.5) + " {Item:{id:\"minecraft:golden_apple\",count:1},PickupDelay:32767}");
		sp.getServer().runCommand("tp @a " + (x + 0.5) + " " + y + " " + (z + 0.5) + " 0 40");
		ctx.waitTicks(6);
		screenshot(ctx, "item_physics");
		ctx.runOnClient(mc -> module("item_physics").setEnabled(false));
		sp.getServer().runCommand("kill @e[type=item]");
		// classic (1.7) held-item position, holding a sword
		sp.getServer().runCommand("item replace entity @a weapon.mainhand with diamond_sword");
		ctx.runOnClient(mc -> setBool("hand_animations", "classicPosition", true));
		ctx.waitTicks(10);
		screenshot(ctx, "hand_classic_position");
		ctx.runOnClient(mc -> setBool("hand_animations", "classicPosition", false));
		sp.getServer().runCommand("item replace entity @a weapon.mainhand with air");
		home(sp);
		ctx.waitTicks(5);
	}

	/** Small helpers the effects test calls on the client thread. */
	private static final class EffectsTestHelper {
		static int particles(Minecraft mc) {
			return particleCount(mc);
		}
	}

	private static void setBool(String module, String setting, boolean value) {
		((dev.telmuad.hollowclient.setting.BooleanSetting) module(module).setting(setting)).set(value);
	}

	// ------------------------------------------------------------------------------------------------ styles

	private void styles(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		home(sp);
		sp.getServer().runCommand("time set noon");
		ctx.runOnClient(mc -> {
			for (Module m : HollowClient.modules().all()) if (m instanceof HudModule && !m.id().equals("fps")) m.setEnabled(false);
		});
		for (StylesScreen.Tab t : StylesScreen.Tab.values()) {
			ctx.setScreen(() -> new StylesScreen(t, null));
			ctx.waitTicks(8);
			screenshot(ctx, "styles_" + t.name().toLowerCase());
		}
		ctx.setScreen(() -> null);

		// Cosmetics in third person
		ctx.runOnClient(mc -> {
			Modules.CAPE.select("builtin:nightfall");
			Modules.HAT.styleSetting().set(CosmeticModels.Hat.WIZARD_HAT);
			Modules.HAT.setEnabled(true);
			Modules.WINGS.styleSetting().set(CosmeticModels.Wings.ANGEL);
			Modules.WINGS.setEnabled(true);
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		});
		ctx.waitTicks(10);
		screenshot(ctx, "cosmetics_back");
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		ctx.waitTicks(10);
		screenshot(ctx, "cosmetics_front");
		boolean capeOk = ctx.computeOnClient(mc -> Modules.CAPE.texture() != null);
		check("Cape texture resolves for the built-in cape", capeOk);

		// Import a PNG cape (64x32, generated here)
		Path capePng = ctx.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("test-cape.png"));
		writeImage(capePng, 64, 32, (x, y) -> ((x / 4 + y / 4) % 2 == 0) ? 0xFF22CC88 : 0xFF114433);
		String capeResult = ctx.computeOnClient(mc -> {
			try {
				String id = CapeManager.importCape(capePng);
				Modules.CAPE.select(id);
				return id;
			} catch (PackImporter.ImportException e) {
				return "ERROR " + e.getMessage();
			}
		});
		check("PNG cape import (" + capeResult + ")", capeResult.startsWith("custom:"));
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		ctx.waitTicks(10);
		screenshot(ctx, "imported_cape_back");
		Path badPng = ctx.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("square.png"));
		writeImage(badPng, 64, 64, (x, y) -> 0xFFFFFFFF);
		String badResult = ctx.computeOnClient(mc -> {
			try {
				CapeManager.importCape(badPng);
				return "accepted";
			} catch (PackImporter.ImportException e) {
				return e.getMessage();
			}
		});
		check("Wrong-size cape is refused with a message (" + badResult + ")", badResult.contains("64x64"));
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		ctx.setScreen(() -> new StylesScreen(StylesScreen.Tab.CAPES, null));
		ctx.waitTicks(8);
		screenshot(ctx, "styles_capes_with_import");
		ctx.setScreen(() -> null);

		// Texture pack: a pack that turns stone into a magenta checkerboard
		sp.getServer().runCommand("fill " + at(-3, -1, 1) + " " + at(3, -1, 8) + " stone");
		Path packZip = ctx.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("Hollow Test Pack.zip"));
		writePack(packZip);
		ctx.runOnClient(mc -> PackManager.importAsync(packZip, name -> LOG.info("Imported {}", name), err -> LOG.error("Import failed: {}", err)));
		ctx.waitFor(mc -> !PackManager.busy(), 200);
		boolean listed = ctx.computeOnClient(mc -> HollowClient.styles().packs.contains("Hollow Test Pack.zip"));
		check("Imported pack is listed", listed);
		boolean used = ctx.computeOnClient(mc -> PackManager.use("Hollow Test Pack.zip"));
		ctx.waitTicks(5);
		ctx.waitFor(mc -> mc.getOverlay() == null, 1200);
		ctx.waitTicks(20);
		String active = ctx.computeOnClient(mc -> PackManager.active());
		check("Switching to the imported pack works (" + active + ")", used && active.equals("Hollow Test Pack.zip"));
		screenshot(ctx, "pack_active_magenta_stone");
		ctx.setScreen(() -> new StylesScreen(StylesScreen.Tab.PACKS, null));
		ctx.waitTicks(8);
		screenshot(ctx, "styles_packs_with_import");
		ctx.setScreen(() -> null);

		// Presets: save, change things, apply, check they came back
		ctx.runOnClient(mc -> PresetManager.save("Test look"));
		ctx.runOnClient(mc -> {
			Modules.HAT.setEnabled(false);
			Modules.CAPE.select("builtin:ember");
			PackManager.use(null);
		});
		ctx.waitTicks(5);
		ctx.waitFor(mc -> mc.getOverlay() == null, 1200);
		String warning = ctx.computeOnClient(mc -> PresetManager.apply(HollowClient.styles().findPreset("Test look")));
		ctx.waitTicks(5);
		ctx.waitFor(mc -> mc.getOverlay() == null, 1200);
		ctx.waitTicks(10);
		boolean restored = ctx.computeOnClient(mc -> Modules.HAT.isEnabled() && Modules.CAPE.capeId().startsWith("custom:")
				&& PackManager.active().equals("Hollow Test Pack.zip"));
		check("Applying a preset restores cape, hat and pack (warning: " + warning + ")", restored && warning == null);
		ctx.setScreen(() -> new StylesScreen(StylesScreen.Tab.PRESETS, null));
		ctx.waitTicks(8);
		screenshot(ctx, "styles_presets");
		ctx.setScreen(() -> null);

		// Remove the pack again so the run ends on vanilla textures
		ctx.runOnClient(mc -> PackManager.remove("Hollow Test Pack.zip"));
		ctx.waitTicks(5);
		ctx.waitFor(mc -> mc.getOverlay() == null, 1200);
		boolean gone = ctx.computeOnClient(mc -> !HollowClient.styles().packs.contains("Hollow Test Pack.zip")
				&& !java.nio.file.Files.exists(PackManager.folder().resolve("Hollow Test Pack.zip")));
		check("Removing a pack forgets it and deletes Hollow's copy", gone);
		styles2(ctx, sp);
	}

	/** E8: wardrobe cosmetics, emotes and the wheel, animated capes, GIF import and full-look presets. */
	private void styles2(ClientGameTestContext ctx, TestSingleplayerContext sp) {
		home(sp);
		sp.getServer().runCommand("time set noon");
		ctx.runOnClient(mc -> {
			Modules.HAT.setEnabled(false);
			Modules.WINGS.setEnabled(false);
			Modules.HALO.setEnabled(true);
			Modules.HORNS.setEnabled(true);
			Modules.PET.setEnabled(true);
			Modules.AURA.setEnabled(true);
			Modules.AURA.styleSetting().set(dev.telmuad.hollowclient.modules.styles.AuraModule.Style.ORBIT);
			mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		});
		ctx.waitTicks(20);
		screenshot(ctx, "wardrobe_halo_horns_pet_aura");
		ctx.runOnClient(mc -> {
			Modules.PET.styleSetting().set(dev.telmuad.hollowclient.modules.styles.PetModule.Style.CUBE);
			Modules.HORNS.styleSetting().set(dev.telmuad.hollowclient.modules.styles.HornsModule.Style.RAM);
			Modules.HALO.styleSetting().set(dev.telmuad.hollowclient.modules.styles.HaloModule.Style.SPIKED);
		});
		ctx.waitTicks(5);
		screenshot(ctx, "wardrobe_cube_pet_ram_horns");
		// presets remember the whole look, including the theme
		ctx.runOnClient(mc -> {
			Themes.store().setCurrent(HollowTheme.VOID);
			PresetManager.save("E8 look");
			Themes.store().setCurrent(HollowTheme.INFERNO);
			Modules.HALO.setEnabled(false);
		});
		String presetWarning = ctx.computeOnClient(mc -> PresetManager.apply(HollowClient.styles().findPreset("E8 look")));
		boolean restored = ctx.computeOnClient(mc -> Modules.HALO.isEnabled() && Themes.current().name().equals("Void"));
		check("A preset brings back the halo and the theme (" + presetWarning + ")", restored);
		ctx.runOnClient(mc -> {
			Modules.HALO.setEnabled(false);
			Modules.HORNS.setEnabled(false);
			Modules.PET.setEnabled(false);
			Modules.AURA.setEnabled(false);
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		});
		// emotes: the wheel, then a wave in third person
		ctx.setScreen(() -> {
			var wheel = new dev.telmuad.hollowclient.gui.EmoteWheelScreen();
			return wheel;
		});
		ctx.waitTicks(8);
		screenshot(ctx, "emote_wheel");
		ctx.setScreen(() -> null);
		ctx.runOnClient(mc -> dev.telmuad.hollowclient.styles.Emotes.play(dev.telmuad.hollowclient.styles.Emotes.Emote.WAVE));
		ctx.waitTicks(15);
		boolean waving = ctx.computeOnClient(mc -> dev.telmuad.hollowclient.styles.Emotes.current() == dev.telmuad.hollowclient.styles.Emotes.Emote.WAVE
				&& mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT);
		check("Playing an emote switches to third person and waves", waving);
		screenshot(ctx, "emote_wave");
		ctx.runOnClient(mc -> dev.telmuad.hollowclient.styles.Emotes.play(dev.telmuad.hollowclient.styles.Emotes.Emote.DAB));
		ctx.waitTicks(12);
		screenshot(ctx, "emote_dab");
		ctx.waitTicks(40);
		boolean back = ctx.computeOnClient(mc -> dev.telmuad.hollowclient.styles.Emotes.current() == null && mc.options.getCameraType() == CameraType.FIRST_PERSON);
		check("The emote ends and the camera comes back", back);
		// animated cape with glow and shimmer, at night
		sp.getServer().runCommand("time set midnight");
		ctx.runOnClient(mc -> {
			Modules.CAPE.select("builtin:void_flow");
			((dev.telmuad.hollowclient.setting.BooleanSetting) Modules.CAPE.setting("glow")).set(true);
			((dev.telmuad.hollowclient.setting.BooleanSetting) Modules.CAPE.setting("shimmer")).set(true);
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		});
		ctx.waitTicks(10);
		screenshot(ctx, "cape_void_flow_glow_shimmer");
		ctx.runOnClient(mc -> {
			((dev.telmuad.hollowclient.setting.BooleanSetting) Modules.CAPE.setting("glow")).set(false);
			((dev.telmuad.hollowclient.setting.BooleanSetting) Modules.CAPE.setting("shimmer")).set(false);
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		});
		sp.getServer().runCommand("time set noon");
		// import a two-frame GIF cape
		Path gif = ctx.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("blink-cape.gif"));
		writeGif(gif);
		String gifResult = ctx.computeOnClient(mc -> {
			try {
				String id = CapeManager.importCape(gif);
				return CapeManager.texture(id) != null ? id : "no texture for " + id;
			} catch (PackImporter.ImportException e) {
				return "ERROR " + e.getMessage();
			}
		});
		check("An animated GIF imports as a cape (" + gifResult + ")", gifResult.startsWith("custom:") && gifResult.endsWith(".gif"));
		ctx.runOnClient(mc -> CapeManager.remove(gifResult));
		// the Styles screen's wardrobe and emotes tabs
		ctx.setScreen(() -> new StylesScreen(StylesScreen.Tab.COSMETICS, null));
		ctx.waitTicks(8);
		screenshot(ctx, "styles_wardrobe");
		ctx.setScreen(() -> new StylesScreen(StylesScreen.Tab.EMOTES, null));
		ctx.waitTicks(8);
		screenshot(ctx, "styles_emotes");
		ctx.setScreen(() -> null);
	}

	/** A 64x32 GIF with two frames (violet, then cyan). */
	private static void writeGif(Path file) {
		try (javax.imageio.stream.ImageOutputStream out = javax.imageio.ImageIO.createImageOutputStream(file.toFile())) {
			javax.imageio.ImageWriter writer = javax.imageio.ImageIO.getImageWritersByFormatName("gif").next();
			writer.setOutput(out);
			writer.prepareWriteSequence(null);
			for (int color : new int[]{0xFF8B5CF6, 0xFF22D3EE}) {
				java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(64, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
				for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++) img.setRGB(x, y, color);
				writer.writeToSequence(new javax.imageio.IIOImage(img, null, null), null);
			}
			writer.endWriteSequence();
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
	}

	private interface Pixel {
		int argb(int x, int y);
	}

	private static void writeImage(Path file, int w, int h, Pixel pixel) {
		java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, pixel.argb(x, y));
		try {
			javax.imageio.ImageIO.write(img, "png", file.toFile());
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	private static void writePack(Path zip) {
		try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(zip))) {
			out.putNextEntry(new java.util.zip.ZipEntry("pack.mcmeta"));
			out.write("{\"pack\":{\"description\":\"Hollow test pack\",\"pack_format\":75,\"min_format\":75,\"max_format\":75}}"
					.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			out.closeEntry();
			java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
			java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) img.setRGB(x, y, ((x / 4 + y / 4) % 2 == 0) ? 0xFFFF00FF : 0xFF200020);
			javax.imageio.ImageIO.write(img, "png", png);
			out.putNextEntry(new java.util.zip.ZipEntry("assets/minecraft/textures/block/stone.png"));
			out.write(png.toByteArray());
			out.closeEntry();
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}
}
