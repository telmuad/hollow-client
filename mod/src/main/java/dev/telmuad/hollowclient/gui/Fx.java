package dev.telmuad.hollowclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Glow, the Hollow sigil and animated gradient outlines. Glows use one small soft texture made at runtime and drawn
 * as nine pieces, so any size of glow costs the same few quads.
 */
public final class Fx {
	public static final JarTexture SIGIL = new JarTexture("textures/gui/sigil.png", "sigil");
	public static final JarTexture SIGIL_GLOW = new JarTexture("textures/gui/sigil_glow.png", "sigil_glow");
	public static final JarTexture WORDMARK_HOLLOW = new JarTexture("textures/gui/wordmark_hollow.png", "wordmark_hollow");
	public static final JarTexture WORDMARK_CLIENT = new JarTexture("textures/gui/wordmark_client.png", "wordmark_client");
	private static final Identifier GLOW = HollowClient.id("generated/soft_glow");
	private static final Identifier DOT = HollowClient.id("generated/soft_dot");
	private static final Identifier DISC = HollowClient.id("generated/disc");
	private static final int G = 64;
	private static final int E = 24;
	private static final int D = 64;
	private static boolean glowReady;
	private static boolean dotsReady;

	private Fx() {
	}

	// ------------------------------------------------------------------------------------------------ sigil

	/** The sigil at (x, y) with a pulsing glow. {@code alpha} fades everything. */
	public static void sigil(GuiGraphicsExtractor g, int x, int y, int size, float alpha, boolean pulse) {
		sigil(g, (float) x, (float) y, size, alpha, pulse ? 1f : 0f, Theme.glow());
	}

	/**
	 * The sigil with float position and size. {@code pulse} 0..1 is how much the glow breathes, {@code glow} 0..1 how
	 * bright it is.
	 */
	public static void sigil(GuiGraphicsExtractor g, float x, float y, float size, float alpha, float pulse, float glow) {
		Emblem.draw(g, x, y, size, alpha, 1f, glow, pulse);
	}

	/** The emblem playing its intro: {@code intro} 0 → 1 (facets fly in, the H hollows out, the glow comes up). */
	public static void sigilIntro(GuiGraphicsExtractor g, float x, float y, float size, float alpha, float intro, float glow) {
		Emblem.draw(g, x, y, size, alpha, intro, glow, 1f);
	}

	/**
	 * The "HOLLOW CLIENT" wordmark, {@code height} GUI pixels tall: HOLLOW in a slowly moving theme gradient, CLIENT
	 * in a dim tone next to it. Returns the total width.
	 */
	public static float wordmark(GuiGraphicsExtractor g, float x, float y, float height, float alpha, boolean withClient) {
		float hw = height * WORDMARK_HOLLOW.aspect();
		int strips = 12;
		float phase = (float) ((Util.getMillis() * Theme.speed() / 4000.0) % 1.0);
		for (int i = 0; i < strips; i++) {
			float u0 = i / (float) strips;
			float u1 = (i + 1) / (float) strips;
			float t = (u0 + u1) / 2f;
			int c = Theme.current().gradientAt(Math.abs(((t * 0.8f + phase) % 1f) * 2 - 1), Util.getMillis());
			WORDMARK_HOLLOW.drawSlice(g, x + hw * u0, y, hw * (u1 - u0), height, u0, u1, ColorUtil.fade(c, alpha));
		}
		if (!withClient) return hw;
		// "CLIENT" small and wide, sitting on HOLLOW's baseline
		float ch = height * CLIENT_SCALE;
		float gap = height * 0.28f;
		float cw = ch * WORDMARK_CLIENT.aspect();
		WORDMARK_CLIENT.draw(g, x + hw + gap, y + height - ch, cw, ch, ColorUtil.fade(Theme.TEXT_DIM, alpha));
		return hw + gap + cw;
	}

	private static final float CLIENT_SCALE = 0.42f;

	/** Width of {@link #wordmark} at this height. */
	public static float wordmarkWidth(float height, boolean withClient) {
		float hw = height * WORDMARK_HOLLOW.aspect();
		if (!withClient) return hw;
		return hw + height * 0.28f + height * CLIENT_SCALE * WORDMARK_CLIENT.aspect();
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
		Minecraft.getInstance().getTextureManager().register(GLOW, new JarTexture.Smooth(GLOW::toString, img));
		glowReady = true;
	}

	/**
	 * A soft glow around a rectangle, {@code spread} pixels wide, tinted {@code color} (alpha = strength).
	 * The middle is left empty so it works behind translucent panels.
	 */
	public static void glow(GuiGraphicsExtractor g, int x, int y, int w, int h, int spread, int color) {
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

	private static void piece(GuiGraphicsExtractor g, int x, int y, int w, int h, int u, int v, int uw, int vh, int color) {
		if (w <= 0 || h <= 0) return;
		g.blit(RenderPipelines.GUI_TEXTURED, GLOW, x, y, u, v, w, h, uw, vh, G, G, color);
	}

	// ------------------------------------------------------------------------------------------------ dots and discs

	private static void ensureDots() {
		if (dotsReady) return;
		NativeImage soft = new NativeImage(D, D, true);
		NativeImage disc = new NativeImage(D, D, true);
		double c = D / 2.0;
		for (int y = 0; y < D; y++) {
			for (int x = 0; x < D; x++) {
				double d = Math.hypot(x + 0.5 - c, y + 0.5 - c) / c;
				double a = Math.max(0, 1 - d);
				soft.setPixel(x, y, ColorUtil.withAlpha(0xFFFFFF, (int) Math.round(a * a * a * 255)));
				// hard disc with a one-pixel antialiased edge
				double edge = Math.max(0, Math.min(1, (1 - d) * c));
				disc.setPixel(x, y, ColorUtil.withAlpha(0xFFFFFF, (int) Math.round(edge * 255)));
			}
		}
		var tm = Minecraft.getInstance().getTextureManager();
		tm.register(DOT, new JarTexture.Smooth(DOT::toString, soft));
		tm.register(DISC, new JarTexture.Smooth(DISC::toString, disc));
		dotsReady = true;
	}

	/** A soft round light centered on (cx, cy). Float position: moves smoothly between pixels. */
	public static void dot(GuiGraphicsExtractor g, float cx, float cy, float radius, int color) {
		if (radius <= 0 || (color >>> 24) == 0) return;
		ensureDots();
		g.pose().pushMatrix();
		g.pose().translate(cx - radius, cy - radius);
		g.pose().scale(radius / 32f, radius / 32f);
		g.blit(RenderPipelines.GUI_TEXTURED, DOT, 0, 0, 0f, 0f, 64, 64, D, D, D, D, color);
		g.pose().popMatrix();
	}

	/** A filled circle (for ripples), centered on (cx, cy). */
	public static void disc(GuiGraphicsExtractor g, float cx, float cy, float radius, int color) {
		if (radius <= 0 || (color >>> 24) == 0) return;
		ensureDots();
		g.pose().pushMatrix();
		g.pose().translate(cx - radius, cy - radius);
		g.pose().scale(radius / 32f, radius / 32f);
		g.blit(RenderPipelines.GUI_TEXTURED, DISC, 0, 0, 0f, 0f, 64, 64, D, D, D, D, color);
		g.pose().popMatrix();
	}

	// ------------------------------------------------------------------------------------------------ glass panels

	/**
	 * A frosted-glass panel: soft shadow, theme glow, translucent body (the world behind is blurred), a light sheen
	 * at the top, a thin highlight line and a border that flows through the theme gradient.
	 */
	public static void glass(GuiGraphicsExtractor g, Rect r, int radius, float alpha, float glowAmount, float borderAmount, float phase) {
		if (r.w() <= 0 || r.h() <= 0 || alpha <= 0f) return;
		Draw.roundRect(g, r.x() + 1, r.y() + 4, r.w(), r.h(), radius, ColorUtil.fade(0x48000000, alpha));
		if (glowAmount > 0f) glow(g, r.x(), r.y(), r.w(), r.h(), 16, ColorUtil.fade(Theme.accent(), alpha * glowAmount * 0.32f));
		Draw.roundRect(g, r, radius, ColorUtil.fade(Theme.GLASS, alpha));
		Draw.roundGradient(g, r.x(), r.y(), r.w(), Math.min(r.h(), 48), radius, ColorUtil.fade(0x16FFFFFF, alpha), 0x00FFFFFF);
		int in = Math.min(radius, r.w() / 2);
		g.fill(r.x() + in, r.y() + 1, r.right() - in, r.y() + 2, ColorUtil.fade(0x1CFFFFFF, alpha));
		if (borderAmount > 0f) {
			Draw.roundOutline(g, r, radius, ColorUtil.fade(Theme.STROKE, alpha * (1f - borderAmount)));
			gradientOutline(g, r, radius, alpha * borderAmount * (0.45f + 0.55f * Theme.glow()), phase);
		} else {
			Draw.roundOutline(g, r, radius, ColorUtil.fade(Theme.STROKE, alpha));
		}
	}

	// ------------------------------------------------------------------------------------------------ gradient outlines

	/**
	 * A rounded outline whose color flows along the theme gradient over time. Straight edges are drawn in short
	 * runs, so this is a few dozen quads per panel.
	 */
	public static void gradientOutline(GuiGraphicsExtractor g, Rect r, int radius, float alpha, float phase) {
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
	public static void vGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int top, int bottom) {
		g.fillGradient(x, y, x + w, y + h, top, bottom);
	}

	/** Bold title text with a slowly moving theme gradient across its letters. Returns its width. */
	public static int gradientBold(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, String text, int x, int y, float alpha) {
		int cx = x;
		int n = text.length();
		float phase = (float) ((Util.getMillis() * Theme.speed() / 4000.0) % 1.0);
		for (int i = 0; i < n; ) {
			int cp = text.codePointAt(i);
			String ch = new String(Character.toChars(cp));
			float t = n <= 1 ? 0f : (float) i / (n - 1);
			int c = ColorUtil.fade(Theme.current().gradientAt(Math.abs(((t * 0.8f + phase) % 1f) * 2 - 1), Util.getMillis()), alpha);
			g.text(font, Ui.bold(ch), cx, y, c, false);
			i += Character.charCount(cp);
			cx = x + Ui.boldWidth(font, text.substring(0, i));
		}
		return cx - x;
	}

	/** Text with a horizontal gradient: each character is colored along the theme gradient. */
	public static int gradientText(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, String text, int x, int y, float alpha, boolean shadow, boolean hud) {
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
			i += Character.charCount(cp);
			// place by the width of everything so far: per-character widths are rounded and would drift
			String sofar = text.substring(0, i);
			cx = x + (hud ? Ui.hudWidth(font, sofar) : Ui.width(font, sofar));
		}
		return cx - x;
	}
}
