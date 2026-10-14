package dev.telmuad.hollowclient.hud;

import dev.telmuad.hollowclient.hud.HudPosition.Anchor;

import java.util.List;

/** Pure layout math for HUD elements: placing, storing, clamping and snapping. All sizes are in GUI pixels. */
public final class HudLayout {
	private HudLayout() {
	}

	public record Box(double x, double y, double w, double h) {
		public double right() {
			return x + w;
		}

		public double bottom() {
			return y + h;
		}

		public double centerX() {
			return x + w / 2;
		}

		public double centerY() {
			return y + h / 2;
		}

		public boolean contains(double px, double py) {
			return px >= x && py >= y && px < x + w && py < y + h;
		}
	}

	/** Snap result: the new top-left, plus the guide line coordinates that caused a snap (NaN when none). */
	public record Snap(double x, double y, double guideX, double guideY) {
	}

	/** Top-left of an element of size {@code w}×{@code h} (already scaled), clamped to stay on screen. */
	public static Box place(HudPosition p, double screenW, double screenH, double w, double h) {
		double x = axisPlace(p.anchorX, p.offsetX, screenW, w);
		double y = axisPlace(p.anchorY, p.offsetY, screenH, h);
		return new Box(clamp(x, 0, screenW - w), clamp(y, 0, screenH - h), w, h);
	}

	private static double axisPlace(Anchor anchor, double offset, double screen, double size) {
		return switch (anchor) {
			case START -> offset * screen;
			case CENTER -> screen / 2 + offset * screen - size / 2;
			case END -> screen - offset * screen - size;
		};
	}

	/**
	 * Store a top-left position into {@code target}, keeping its scale. The anchor on each axis is picked from where
	 * the element's center is: the first third of the screen anchors to the start, the last third to the end.
	 */
	public static void store(HudPosition target, double x, double y, double w, double h, double screenW, double screenH) {
		x = clamp(x, 0, screenW - w);
		y = clamp(y, 0, screenH - h);
		target.anchorX = anchorFor(x + w / 2, screenW);
		target.offsetX = axisOffset(target.anchorX, x, w, screenW);
		target.anchorY = anchorFor(y + h / 2, screenH);
		target.offsetY = axisOffset(target.anchorY, y, h, screenH);
	}

	static Anchor anchorFor(double center, double screen) {
		if (center < screen / 3) return Anchor.START;
		if (center > screen * 2 / 3) return Anchor.END;
		return Anchor.CENTER;
	}

	private static double axisOffset(Anchor anchor, double pos, double size, double screen) {
		if (screen <= 0) return 0;
		return switch (anchor) {
			case START -> pos / screen;
			case CENTER -> (pos + size / 2 - screen / 2) / screen;
			case END -> (screen - (pos + size)) / screen;
		};
	}

	public static double clamp(double v, double min, double max) {
		if (max < min) return min;
		return Math.max(min, Math.min(max, v));
	}

	/**
	 * Snap a dragged element to the screen edges, the screen center lines and the edges/centers of other elements,
	 * when one of its edges or its center is within {@code threshold} pixels.
	 */
	public static Snap snap(double x, double y, double w, double h, double screenW, double screenH,
							List<Box> others, double threshold) {
		double[] sx = snapAxis(x, w, screenW, others, threshold, true);
		double[] sy = snapAxis(y, h, screenH, others, threshold, false);
		return new Snap(sx[0], sy[0], sx[1], sy[1]);
	}

	private static double[] snapAxis(double pos, double size, double screen, List<Box> others, double threshold,
									 boolean horizontal) {
		double bestDelta = Double.POSITIVE_INFINITY;
		double bestGuide = Double.NaN;
		double[] mine = {pos, pos + size / 2, pos + size};

		// Screen edges: start edge to 0, end edge to the screen size, center to the middle.
		double[][] screenTargets = {{0, 0}, {screen / 2, 1}, {screen, 2}};
		for (double[] t : screenTargets) {
			double d = t[0] - mine[(int) t[1]];
			if (Math.abs(d) < Math.abs(bestDelta)) {
				bestDelta = d;
				bestGuide = t[0];
			}
		}
		// Other elements: any of my edges/center to any of theirs.
		for (Box o : others) {
			double[] theirs = horizontal ? new double[]{o.x(), o.centerX(), o.right()} : new double[]{o.y(), o.centerY(), o.bottom()};
			for (int i = 0; i < 3; i++) {
				for (int j = 0; j < 3; j++) {
					// Only line up centers with centers; edges with edges.
					if ((i == 1) != (j == 1)) continue;
					double d = theirs[j] - mine[i];
					if (Math.abs(d) < Math.abs(bestDelta)) {
						bestDelta = d;
						bestGuide = theirs[j];
					}
				}
			}
		}
		if (Math.abs(bestDelta) <= threshold) {
			return new double[]{clamp(pos + bestDelta, 0, screen - size), bestGuide};
		}
		return new double[]{clamp(pos, 0, screen - size), Double.NaN};
	}
}
