package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.theme.HollowTheme;
import dev.telmuad.hollowclient.theme.Themes;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.util.Util;

/**
 * Hollow's palette. Neutral "void" colors are fixed; accent, gradient, glow, rounding and animation speed come from
 * the current theme (see {@link Themes}). All colors are ARGB; text needs a non-zero alpha to show up.
 */
public final class Theme {
	public static final int DIM_TOP = 0x7005060A;
	public static final int DIM_BOTTOM = 0xB005060A;
	public static final int PANEL = 0xD80A0B10;
	public static final int PANEL_RAISED = 0xEE12131A;
	public static final int RAIL = 0xC00E0F15;
	public static final int CARD = 0xB0171922;
	public static final int CARD_HOVER = 0xC8202330;
	public static final int STROKE = 0x24FFFFFF;
	public static final int STROKE_STRONG = 0x4AFFFFFF;
	public static final int TEXT = 0xFFF1F2F7;
	public static final int TEXT_DIM = 0xFFA3A8B8;
	public static final int TEXT_FAINT = 0xFF676C7C;
	public static final int SWITCH_OFF = 0xFF353948;
	public static final int DANGER = 0xFFF2706B;
	public static final int SUCCESS = 0xFF5BD18B;

	private Theme() {
	}

	public static HollowTheme current() {
		return Themes.current();
	}

	public static int accent() {
		return current().accentAt(Util.getMillis());
	}

	/** The gradient's second color (also used for snapping guides and secondary highlights). */
	public static int accent2() {
		return current().gradientAt(1f, Util.getMillis());
	}

	public static int gradient(float t) {
		return current().gradientAt(t, Util.getMillis());
	}

	/** A gradient color that drifts over time, for animated borders. {@code offset} spreads it along an edge. */
	public static int flowingGradient(float offset) {
		float speed = current().animationSpeed();
		float t = (float) ((Util.getMillis() * speed / 3000.0 + offset) % 1.0);
		float tri = t < 0.5f ? t * 2f : 2f - t * 2f;
		return gradient(tri);
	}

	public static int accentSoft() {
		return ColorUtil.mix(0xFF14121F, accent(), 0.35f);
	}

	public static int cardOn() {
		return ColorUtil.withAlpha(ColorUtil.mix(0xFF15141F, accent(), 0.16f), 0xD0);
	}

	public static int guide() {
		return accent2();
	}

	public static float glow() {
		return current().glow();
	}

	/** Theme corner radius scaled from a design value made for roundness 6. */
	public static int radius(int design) {
		return Math.max(0, Math.round(design * current().roundness() / 6f));
	}

	/** Multiplier for animation rates (higher = faster). */
	public static float speed() {
		return current().animationSpeed();
	}

	public static int fade(int argb, float alpha) {
		return ColorUtil.fade(argb, alpha);
	}

	public static int mix(int from, int to, float t) {
		return ColorUtil.mix(from, to, t);
	}
}
