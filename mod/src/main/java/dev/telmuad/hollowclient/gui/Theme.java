package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.util.ColorUtil;

/** Hollow's palette. All colors are ARGB; text needs a non-zero alpha to show up. */
public final class Theme {
	public static final int DIM_TOP = 0x8C05060A;
	public static final int DIM_BOTTOM = 0xC005060A;
	public static final int PANEL = 0xEC0F1015;
	public static final int PANEL_RAISED = 0xF5171920;
	public static final int RAIL = 0xFF13141A;
	public static final int CARD = 0xFF1B1D25;
	public static final int CARD_HOVER = 0xFF23262F;
	public static final int CARD_ON = 0xFF1F1B2E;
	public static final int STROKE = 0x26FFFFFF;
	public static final int STROKE_STRONG = 0x4DFFFFFF;
	public static final int TEXT = 0xFFECEDF2;
	public static final int TEXT_DIM = 0xFF9EA3B0;
	public static final int TEXT_FAINT = 0xFF636875;
	public static final int ACCENT = 0xFF8B6CFF;
	public static final int ACCENT_SOFT = 0xFF5B47B0;
	public static final int SWITCH_OFF = 0xFF3A3E4A;
	public static final int DANGER = 0xFFF2706B;
	public static final int SUCCESS = 0xFF5BD18B;
	public static final int GUIDE = 0xFFFF5CAA;

	private Theme() {
	}

	public static int fade(int argb, float alpha) {
		return ColorUtil.fade(argb, alpha);
	}

	public static int mix(int from, int to, float t) {
		return ColorUtil.mix(from, to, t);
	}
}
