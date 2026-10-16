package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.ui.LoadingScreenModule;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The loading screen in Hollow's look (used by LoadingOverlayMixin): dark background, the glowing sigil and
 * wordmark, drifting particles and a rounded gradient progress bar. Everything here works before resources are
 * loaded: the art comes straight from the jar and no text is drawn.
 */
public final class LoadingStyle {
	public static final int BACKGROUND = 0xFF06070C;
	private static final ParticleField PARTICLES = new ParticleField();
	private static long lastNanos;

	private LoadingStyle() {
	}

	public static boolean enabled() {
		LoadingScreenModule m = Modules.LOADING_SCREEN;
		return m != null && m.isEnabled();
	}

	/** Draw the logo block. {@code alpha} follows vanilla's fade in/out. */
	public static void drawLogo(GuiGraphicsExtractor g, float alpha) {
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;
		LoadingScreenModule m = Modules.LOADING_SCREEN;
		int w = g.guiWidth();
		int h = g.guiHeight();
		PARTICLES.render(g, w, h, -1000, -1000, dt, alpha, m.particles());
		float size = (float) (Math.min(w * 0.75, h) * 0.25 * 1.25);
		float cx = w / 2f;
		float cy = h / 2f - size * 0.22f;
		Fx.sigil(g, cx - size / 2f, cy - size / 2f, size, alpha, 1f, m.glow());
		float wordH = Math.max(8f, size * 0.2f);
		float ww = wordH * (Fx.WORDMARK_HOLLOW.aspect() + 0.35f + Fx.WORDMARK_CLIENT.aspect());
		Fx.wordmark(g, cx - ww / 2f, cy + size * 0.62f, wordH, alpha, true);
	}

	/** The progress bar, inside the box vanilla would use. */
	public static void drawBar(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float alpha, float progress) {
		int w = x1 - x0;
		int cy = (y0 + y1) / 2;
		Draw.roundRect(g, x0, cy - 2, w, 4, 2, ColorUtil.fade(0x26FFFFFF, alpha));
		int fill = Math.round(w * Math.max(0f, Math.min(1f, progress)));
		if (fill <= 0) return;
		int step = 3;
		for (int x = 0; x < fill; x += step) {
			int seg = Math.min(step, fill - x);
			int c = Theme.gradient(x / (float) Math.max(1, w));
			int inset = x < 2 ? 1 : 0;
			g.fill(x0 + x, cy - 2 + inset, x0 + x + seg, cy + 2 - inset, ColorUtil.fade(c, alpha));
		}
		Fx.dot(g, x0 + fill, cy, 9f, ColorUtil.fade(Theme.gradient(fill / (float) w), alpha * 0.8f));
	}
}
