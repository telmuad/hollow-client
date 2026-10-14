package dev.telmuad.hollowclient.test;

import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.gui.HollowMenuScreen;
import dev.telmuad.hollowclient.gui.HudEditorScreen;
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
		ctx.waitTicks(20);
		screenshot(ctx, "title_screen");
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
			hud(ctx);
			qolAndVisual(ctx, sp);
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
		for (Category c : new Category[]{Category.VISUAL, Category.QOL, Category.STYLES}) {
			ctx.setScreen(() -> HollowMenuScreen.at(c));
			ctx.waitTicks(8);
			screenshot(ctx, "menu_" + c.name().toLowerCase());
		}
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
}
