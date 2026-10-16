package dev.telmuad.hollowclient.music;

import dev.telmuad.hollowclient.gui.Draw;
import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.render.Quality;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

/**
 * Draws the music visualizer: spectrum bars (rising fast, falling softly, with small falling peak caps), mirrored
 * bars, or a waveform line, in the theme gradient. Each place that shows one keeps its own instance (for the
 * smoothing).
 */
public final class Visualizer {
	public enum Style {BARS, MIRROR, WAVE}

	private float[] bands = new float[0];
	private float[] shown = new float[0];
	private float[] peaks = new float[0];
	private final float[] wave = new float[256];
	private long lastNanos;

	public void draw(GuiGraphicsExtractor g, int x, int y, int w, int h, int count, Style style, float amount, float alpha, boolean demo) {
		if (w <= 4 || h <= 2 || amount <= 0f) return;
		if (!Quality.heavyAllowed()) count = Math.max(8, count / 2);
		if (bands.length != count) {
			bands = new float[count];
			shown = new float[count];
			peaks = new float[count];
		}
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1e9f);
		lastNanos = now;
		if (style == Style.WAVE) {
			drawWave(g, x, y, w, h, amount, alpha, demo);
			return;
		}
		boolean live = !demo && MusicPlayer.spectrum(bands);
		if (demo) {
			double t = Util.getMillis() / 1000.0;
			for (int i = 0; i < count; i++) {
				bands[i] = (float) (0.45 + 0.3 * Math.sin(t * 3.1 + i * 0.55) * Math.cos(t * 1.7 + i * 0.21) + 0.2 * Math.max(0, 1 - i / (count * 0.4f)));
			}
		} else if (!live) {
			for (int i = 0; i < count; i++) bands[i] = 0f;
		}
		int gap = w / count >= 4 ? 1 : 0;
		float bw = (w - gap * (count - 1)) / (float) count;
		for (int i = 0; i < count; i++) {
			float target = Math.min(1f, bands[i] * amount);
			float rate = target > shown[i] ? 28f : 7f;
			shown[i] += (target - shown[i]) * Math.min(1f, dt * rate);
			peaks[i] = Math.max(shown[i], peaks[i] - dt * 0.6f);
			int bx = x + Math.round(i * (bw + gap));
			int bwI = Math.max(1, Math.round(bx + bw) - bx);
			int color = Theme.fade(Theme.gradient(count <= 1 ? 0f : i / (float) (count - 1)), alpha);
			if (style == Style.MIRROR) {
				int half = h / 2;
				int bh = Math.max(1, Math.round(shown[i] * half));
				Draw.roundRect(g, bx, y + half - bh, bwI, bh * 2, Math.min(2, bwI / 2), color);
			} else {
				int bh = Math.max(1, Math.round(shown[i] * h));
				Draw.roundRect(g, bx, y + h - bh, bwI, bh, Math.min(2, bwI / 2), color);
				int py = y + h - Math.max(2, Math.round(peaks[i] * h)) - 1;
				g.fill(bx, py, bx + bwI, py + 1, Theme.fade(0xFFFFFFFF, alpha * 0.7f));
			}
		}
	}

	private void drawWave(GuiGraphicsExtractor g, int x, int y, int w, int h, float amount, float alpha, boolean demo) {
		boolean live = !demo && MusicPlayer.waveform(wave);
		if (demo) {
			double t = Util.getMillis() / 1000.0;
			for (int i = 0; i < wave.length; i++) wave[i] = (float) (0.6 * Math.sin(i * 0.19 + t * 6) * Math.sin(i * 0.031 + t));
		} else if (!live) {
			java.util.Arrays.fill(wave, 0f);
		}
		int mid = y + h / 2;
		int prevY = mid;
		for (int px = 0; px < w; px++) {
			float s = wave[Math.min(wave.length - 1, px * wave.length / w)];
			int yy = mid - Math.round(Math.max(-1f, Math.min(1f, s * amount * 1.6f)) * (h / 2f - 1));
			int top = Math.min(prevY, yy);
			int bottom = Math.max(prevY, yy) + 1;
			g.fill(x + px, top, x + px + 1, bottom, Theme.fade(Theme.gradient(px / (float) w), alpha));
			prevY = yy;
		}
	}
}
