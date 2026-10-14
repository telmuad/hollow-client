package dev.telmuad.hollowclient.gui;

public record Rect(int x, int y, int w, int h) {
	public static final Rect EMPTY = new Rect(0, 0, 0, 0);

	public int right() {
		return x + w;
	}

	public int bottom() {
		return y + h;
	}

	public int centerX() {
		return x + w / 2;
	}

	public int centerY() {
		return y + h / 2;
	}

	public boolean contains(double mx, double my) {
		return w > 0 && h > 0 && mx >= x && my >= y && mx < x + w && my < y + h;
	}

	public Rect inset(int d) {
		return new Rect(x + d, y + d, Math.max(0, w - 2 * d), Math.max(0, h - 2 * d));
	}

	public Rect offset(int dx, int dy) {
		return new Rect(x + dx, y + dy, w, h);
	}
}
