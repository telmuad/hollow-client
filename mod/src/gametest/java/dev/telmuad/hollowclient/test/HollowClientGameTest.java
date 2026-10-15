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
