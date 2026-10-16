package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.ui.InventoryBlurModule;
import dev.telmuad.hollowclient.modules.ui.PauseMenuModule;
import dev.telmuad.hollowclient.render.Quality;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/** Restyling of vanilla screens: the pause menu (background, glass panel, buttons) and blur behind inventories. */
public final class ScreenStyles {
	private static long lastNanos;

	private ScreenStyles() {
	}

	private static float frameDt() {
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;
		return dt;
	}

	/** Blur everything drawn so far, unless something already blurred this frame (only one blur per frame is allowed). */
	public static void tryBlur(GuiGraphicsExtractor g) {
		try {
			g.blurBeforeThisStratum();
		} catch (IllegalStateException alreadyBlurred) {
			// fine: the screen is blurred already
		}
	}

	// ------------------------------------------------------------------------------------------------ pause menu

	public static boolean pauseStyled() {
		PauseMenuModule m = Modules.PAUSE_MENU;
		return m != null && m.isEnabled();
	}

	/** Vanilla buttons are drawn in Hollow's style on the (styled) pause menu. */
	public static boolean restyleButtons() {
		Screen s = Minecraft.getInstance().gui.screen();
		return s instanceof PauseScreen && pauseStyled() && Modules.PAUSE_MENU.buttons();
	}

	public static void pauseBackground(Screen screen, GuiGraphicsExtractor g, int mouseX, int mouseY) {
		float dt = frameDt();
		PauseMenuModule m = Modules.PAUSE_MENU;
		if (blurRadius(screen, 0) >= 1) tryBlur(g);
		g.fillGradient(0, 0, screen.width, screen.height, Theme.DIM_TOP, Theme.DIM_BOTTOM);
		float particles = Quality.heavyAllowed() ? m.particles() * Math.max(0.35f, Quality.scale()) : 0f;
		ParticleField.shared().render(g, screen.width, screen.height, mouseX, mouseY, dt, 1f, particles);
		// a glass panel around the title and buttons
		Rect box = widgetBounds(screen);
		if (box != null) {
			Rect panel = new Rect(box.x() - 12, box.y() - 12, box.w() + 24, box.h() + 22);
			Fx.glass(g, panel, Theme.radius(8), 1f, Theme.glow(), HollowScreen.fxAmount(f -> f.borders), 0f);
			Fx.sigil(g, panel.x() + 8, panel.y() + 6, 14, 1f, true);
		}
	}

	/** The rectangle around all of a screen's widgets, or null if it has none. */
	private static Rect widgetBounds(Screen screen) {
		int x0 = Integer.MAX_VALUE;
		int y0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE;
		int y1 = Integer.MIN_VALUE;
		for (GuiEventListener l : screen.children()) {
			if (!(l instanceof AbstractWidget w) || !w.visible) continue;
			x0 = Math.min(x0, w.getX());
			y0 = Math.min(y0, w.getY());
			x1 = Math.max(x1, w.getX() + w.getWidth());
			y1 = Math.max(y1, w.getY() + w.getHeight());
		}
		return x0 == Integer.MAX_VALUE ? null : new Rect(x0, y0, x1 - x0, y1 - y0);
	}

	/** Hollow-style replacement for a vanilla button's background sprite (the label is still drawn by vanilla). */
	public static float drawButton(AbstractButton b, GuiGraphicsExtractor g, float hover, float dt) {
		boolean hovered = b.active && b.isHoveredOrFocused();
		hover = Anim.approach(hover, hovered ? 1f : 0f, dt, 16f);
		Rect r = new Rect(b.getX(), b.getY(), b.getWidth(), b.getHeight());
		HollowScreen.hoverGlow(g, r, hover, 1f);
		int rad = Theme.radius(4);
		int bg = b.active ? Theme.mix(0xB0141620, Theme.CARD_HOVER, hover) : 0x70101118;
		Draw.roundRect(g, r, rad, bg);
		Draw.roundGradient(g, r.x(), r.y(), r.w(), r.h(), rad, ColorUtil.fade(0x18FFFFFF, b.active ? 1f : 0.4f), 0x00FFFFFF);
		Draw.roundOutline(g, r, rad, Theme.mix(Theme.STROKE, Theme.accent(), hover * 0.85f));
		if (hover > 0.01f) {
			int barH = Math.round(4 + 6 * hover);
			Draw.roundRect(g, r.x() + 3, r.y() + (r.h() - barH) / 2, 2, barH, 1, Theme.fade(Theme.accent(), hover));
		}
		return hover;
	}

	// ------------------------------------------------------------------------------------------------ inventories

	public static boolean inventoryBlur(Screen screen) {
		InventoryBlurModule m = Modules.INVENTORY_BLUR;
		return m != null && m.isEnabled() && screen instanceof AbstractContainerScreen<?>;
	}

	public static void containerBackground(Screen screen, GuiGraphicsExtractor g) {
		if (blurRadius(screen, 0) >= 1) tryBlur(g);
		if (Modules.INVENTORY_BLUR.dim()) g.fillGradient(0, 0, screen.width, screen.height, 0xA0101016, 0xC0101016);
		else g.fillGradient(0, 0, screen.width, screen.height, 0x30000000, 0x50000000);
	}

	// ------------------------------------------------------------------------------------------------ blur radius

	/** Menu blur radius Hollow wants for a screen (0..10), or -1 to leave vanilla's setting alone. */
	public static int blurRadius(Screen screen, int vanilla) {
		if (screen instanceof HollowScreen hs) return hs.blurRadius(vanilla);
		if (screen instanceof PauseScreen && pauseStyled()) {
			return Quality.heavyAllowed() ? Math.round(10f * Modules.PAUSE_MENU.blur()) : 0;
		}
		if (inventoryBlur(screen)) {
			return Quality.heavyAllowed() ? Math.round(10f * Modules.INVENTORY_BLUR.strength()) : 0;
		}
		return -1;
	}
}
