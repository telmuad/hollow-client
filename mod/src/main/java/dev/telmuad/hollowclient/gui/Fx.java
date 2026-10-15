package dev.telmuad.hollowclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Glow, the Hollow sigil and animated gradient outlines. Glows use one small soft texture made at runtime and drawn
 * as nine pieces, so any size of glow costs the same few quads.
 */
public final class Fx {
	public static final Identifier SIGIL = HollowClient.id("textures/gui/sigil.png");
	public static final Identifier SIGIL_GLOW = HollowClient.id("textures/gui/sigil_glow.png");
	private static final Identifier GLOW = HollowClient.id("generated/soft_glow");
	private static final int G = 64;
	private static final int E = 24;
	private static boolean glowReady;

	private Fx() {
	}

	// ------------------------------------------------------------------------------------------------ sigil

	/** The sigil at (x, y) with a pulsing glow. {@code alpha} fades everything. */
	public static void sigil(GuiGraphics g, int x, int y, int size, float alpha, boolean pulse) {
		float glow = Theme.glow();
		float p = pulse ? 0.65f + 0.35f * (float) Math.sin(Util.getMillis() * Theme.speed() / 520.0) : 0.8f;
		int glowColor = ColorUtil.fade(Theme.accent2(), alpha * glow * p);
		int pad = size / 3;
		g.blit(RenderPipelines.GUI_TEXTURED, SIGIL_GLOW, x - pad, y - pad, 0f, 0f, size + 2 * pad, size + 2 * pad, 256, 256, 256, 256, glowColor);
		g.blit(RenderPipelines.GUI_TEXTURED, SIGIL_GLOW, x, y, 0f, 0f, size, size, 256, 256, 256, 256, ColorUtil.fade(Theme.accent(), alpha * glow * p));
		g.blit(RenderPipelines.GUI_TEXTURED, SIGIL, x, y, 0f, 0f, size, size, 256, 256, 256, 256,
				ColorUtil.fade(ColorUtil.mix(Theme.accent(), 0xFFFFFFFF, 0.25f), alpha));
	}

	// ------------------------------------------------------------------------------------------------ soft glow

	private static void ensureGlow() {
		if (glowReady) return;
		NativeImage img = new NativeImage(G, G, true);
		for (int y = 0; y < G; y++) {
			for (int x = 0; x < G; x++) {
				// distance outside the inner square (E..G-E), with a smooth falloff
				double dx = Math.max(0, Math.max(E - x - 0.5, x + 0.5 - (G - E)));
				double dy = Math.max(0, Math.max(E - y - 0.5, y + 0.5 - (G - E)));
				double d = Math.sqrt(dx * dx + dy * dy) / E;
				double a = Math.max(0, 1 - d);
				a = a * a * (3 - 2 * a);
				img.setPixel(x, y, ColorUtil.withAlpha(0xFFFFFF, (int) Math.round(a * a * 255)));
			}
		}
		Minecraft.getInstance().getTextureManager().register(GLOW, new DynamicTexture(GLOW::toString, img));
		glowReady = true;
	}

	/**
	 * A soft glow around a rectangle, {@code spread} pixels wide, tinted {@code color} (alpha = strength).
	 * The middle is left empty so it works behind translucent panels.
	 */
	public static void glow(GuiGraphics g, int x, int y, int w, int h, int spread, int color) {
		if (spread <= 0 || (color >>> 24) == 0) return;
		ensureGlow();
		int s = spread;
		// corners
		piece(g, x - s, y - s, s, s, 0, 0, E, E, color);
		piece(g, x + w, y - s, s, s, G - E, 0, E, E, color);
		piece(g, x - s, y + h, s, s, 0, G - E, E, E, color);
		piece(g, x + w, y + h, s, s, G - E, G - E, E, E, color);
		// edges
		piece(g, x, y - s, w, s, E, 0, G - 2 * E, E, color);
		piece(g, x, y + h, w, s, E, G - E, G - 2 * E, E, color);
		piece(g, x - s, y, s, h, 0, E, E, G - 2 * E, color);
		piece(g, x + w, y, s, h, G - E, E, E, G - 2 * E, color);
	}

	private static void piece(GuiGraphics g, int x, int y, int w, int h, int u, int v, int uw, int vh, int color) {
		if (w <= 0 || h <= 0) return;
		g.blit(RenderPipelines.GUI_TEXTURED, GLOW, x, y, u, v, w, h, uw, vh, G, G, color);
	}

	// ------------------------------------------------------------------------------------------------ gradient outlines

	/**
	 * A rounded outline whose color flows along the theme gradient over time. Straight edges are drawn in short
	 * runs, so this is a few dozen quads per panel.
	 */
	public static void gradientOutline(GuiGraphics g, Rect r, int radius, float alpha, float phase) {
		int x = r.x();
		int y = r.y();
		int w = r.w();
		int h = r.h();
		if (w <= 0 || h <= 0) return;
		int rad = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
		float perimeter = 2f * (w + h);
		int step = 6;
		// top edge (left to right), right edge (down), bottom edge (right to left), left edge (up)
		for (int i = rad; i < w - rad; i += step) {
			int len = Math.min(step, w - rad - i);
			g.fill(x + i, y, x + i + len, y + 1, color(i / perimeter + phase, alpha));
			g.fill(x + w - i - len, y + h - 1, x + w - i, y + h, color((w + h + i) / perimeter + phase, alpha));
		}
		for (int i = rad; i < h - rad; i += step) {
			int len = Math.min(step, h - rad - i);
			g.fill(x + w - 1, y + i, x + w, y + i + len, color((w + i) / perimeter + phase, alpha));
			g.fill(x, y + h - i - len, x + 1, y + h - i, color((2 * w + h + i) / perimeter + phase, alpha));
		}
		if (rad > 0) {
			// corners take the color where their edges meet
			Draw.roundOutline(g, x, y, rad * 2 + 1, rad * 2 + 1, rad, color(0f + phase, alpha), Draw.Corner.TOP_LEFT);
			Draw.roundOutline(g, x + w - rad * 2 - 1, y, rad * 2 + 1, rad * 2 + 1, rad, color(w / perimeter + phase, alpha), Draw.Corner.TOP_RIGHT);
			Draw.roundOutline(g, x + w - rad * 2 - 1, y + h - rad * 2 - 1, rad * 2 + 1, rad * 2 + 1, rad, color((w + h) / perimeter + phase, alpha), Draw.Corner.BOTTOM_RIGHT);
			Draw.roundOutline(g, x, y + h - rad * 2 - 1, rad * 2 + 1, rad * 2 + 1, rad, color((2 * w + h) / perimeter + phase, alpha), Draw.Corner.BOTTOM_LEFT);
		}
	}

	private static int color(float t, float alpha) {
		return ColorUtil.fade(Theme.flowingGradient(t), alpha);
	}

	/** Vertical gradient between two colors (used for glassy highlights). */
	public static void vGradient(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
		g.fillGradient(x, y, x + w, y + h, top, bottom);
	}

	/** Text with a horizontal gradient: each character is colored along the theme gradient. */
	public static int gradientText(GuiGraphics g, net.minecraft.client.gui.Font font, String text, int x, int y, float alpha, boolean shadow, boolean hud) {
		int cx = x;
		int n = text.length();
		float phase = (float) ((Util.getMillis() * Theme.speed() / 4000.0) % 1.0);
		for (int i = 0; i < n; ) {
			int cp = text.codePointAt(i);
			String ch = new String(Character.toChars(cp));
			float t = n <= 1 ? 0f : (float) i / (n - 1);
			int c = ColorUtil.fade(Theme.current().gradientAt(Math.abs(((t * 0.8f + phase) % 1f) * 2 - 1), Util.getMillis()), alpha);
			if (hud) Ui.drawHud(g, font, ch, cx, y, c, shadow);
			else Ui.drawShadow(g, font, ch, cx, y, c, shadow);
			cx += hud ? Ui.hudWidth(font, ch) : Ui.width(font, ch);
			i += Character.charCount(cp);
		}
		return cx - x;
	}
}
