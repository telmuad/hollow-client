package dev.telmuad.hollowclient.gui;

import net.minecraft.client.gui.GuiGraphics;

/** Rounded shapes built from non-overlapping fills, so translucent colors blend evenly. */
public final class Draw {
	private static final int MAX_RADIUS = 8;
	private static final int[][] INSETS = new int[MAX_RADIUS + 1][];

	static {
		for (int r = 0; r <= MAX_RADIUS; r++) {
			int[] rows = new int[r];
			for (int i = 0; i < r; i++) {
				double dy = r - i - 0.5;
				rows[i] = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
			}
			INSETS[r] = rows;
		}
	}

	private Draw() {
	}

	private static int radius(int w, int h, int r) {
		return Math.max(0, Math.min(Math.min(r, MAX_RADIUS), Math.min(w, h) / 2));
	}

	public static void roundRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
		if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
		r = radius(w, h, r);
		int[] inset = INSETS[r];
		g.fill(x, y + r, x + w, y + h - r, color);
		for (int i = 0; i < r; i++) {
			int in = inset[i];
			g.fill(x + in, y + i, x + w - in, y + i + 1, color);
			g.fill(x + in, y + h - 1 - i, x + w - in, y + h - i, color);
		}
	}

	public static void roundRect(GuiGraphics g, Rect r, int radius, int color) {
		roundRect(g, r.x(), r.y(), r.w(), r.h(), radius, color);
	}

	public static void roundOutline(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
		if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
		r = radius(w, h, r);
		int[] inset = INSETS[r];
		int right = x + w;
		int bottom = y + h;
		int top0 = r > 0 ? inset[0] : 0;
		g.fill(x + top0, y, right - top0, y + 1, color);
		g.fill(x + top0, bottom - 1, right - top0, bottom, color);
		for (int i = 1; i < r; i++) {
			int a = inset[i];
			int b = Math.max(a + 1, inset[i - 1]);
			g.fill(x + a, y + i, x + b, y + i + 1, color);
			g.fill(right - b, y + i, right - a, y + i + 1, color);
			g.fill(x + a, bottom - 1 - i, x + b, bottom - i, color);
			g.fill(right - b, bottom - 1 - i, right - a, bottom - i, color);
		}
		int edgeTop = y + Math.max(1, r);
		int edgeBottom = bottom - Math.max(1, r);
		g.fill(x, edgeTop, x + 1, edgeBottom, color);
		g.fill(right - 1, edgeTop, right, edgeBottom, color);
	}

	public static void roundOutline(GuiGraphics g, Rect r, int radius, int color) {
		roundOutline(g, r.x(), r.y(), r.w(), r.h(), radius, color);
	}

	/** A filled circle of diameter {@code d} with its top-left at x, y. */
	public static void circle(GuiGraphics g, int x, int y, int d, int color) {
		roundRect(g, x, y, d, d, d / 2, color);
	}

	/** Light-and-dark checkerboard, used behind translucent color previews. */
	public static void checker(GuiGraphics g, int x, int y, int w, int h, int cell) {
		g.fill(x, y, x + w, y + h, 0xFFB8B8B8);
		for (int cy = 0; cy < h; cy += cell) {
			for (int cx = ((cy / cell) % 2) * cell; cx < w; cx += cell * 2) {
				g.fill(x + cx, y + cy, x + Math.min(w, cx + cell), y + Math.min(h, cy + cell), 0xFF6E6E6E);
			}
		}
	}

	/** The Hollow mark: a ring with a gap, drawn from fills. */
	public static void logo(GuiGraphics g, int x, int y, int size, int color) {
		int t = Math.max(2, size / 5);
		roundOutline(g, x, y, size, size, size / 2, color);
		roundOutline(g, x + 1, y + 1, size - 2, size - 2, (size - 2) / 2, color);
		if (t > 2) roundOutline(g, x + 2, y + 2, size - 4, size - 4, (size - 4) / 2, color);
		int c = size / 2;
		g.fill(x + c - 1, y + c - 1, x + c + 1, y + c + 1, color);
	}
}
