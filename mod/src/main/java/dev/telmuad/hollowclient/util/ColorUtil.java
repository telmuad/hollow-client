package dev.telmuad.hollowclient.util;

import java.util.Locale;

/** ARGB color helpers. Pure Java so they can be unit tested. */
public final class ColorUtil {
	private ColorUtil() {
	}

	public static int alpha(int argb) {
		return (argb >>> 24) & 0xFF;
	}

	public static int red(int argb) {
		return (argb >> 16) & 0xFF;
	}

	public static int green(int argb) {
		return (argb >> 8) & 0xFF;
	}

	public static int blue(int argb) {
		return argb & 0xFF;
	}

	public static int argb(int a, int r, int g, int b) {
		return (clamp255(a) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
	}

	public static int withAlpha(int argb, int alpha) {
		return (clamp255(alpha) << 24) | (argb & 0xFFFFFF);
	}

	/** Multiply the alpha channel by {@code factor} (0..1). */
	public static int fade(int argb, float factor) {
		float f = Math.max(0f, Math.min(1f, factor));
		return withAlpha(argb, Math.round(alpha(argb) * f));
	}

	public static int mix(int from, int to, float t) {
		float k = Math.max(0f, Math.min(1f, t));
		return argb(lerp(alpha(from), alpha(to), k), lerp(red(from), red(to), k),
				lerp(green(from), green(to), k), lerp(blue(from), blue(to), k));
	}

	private static int lerp(int a, int b, float t) {
		return Math.round(a + (b - a) * t);
	}

	private static int clamp255(int v) {
		return Math.max(0, Math.min(255, v));
	}

	public static String toHex(int argb) {
		return String.format(Locale.ROOT, "#%08X", argb);
	}

	/**
	 * Parse "#RGB", "#RRGGBB" or "#AARRGGBB" (the # is optional). Colors without alpha are opaque.
	 * Returns null for anything else.
	 */
	public static Integer parseHex(String text) {
		if (text == null) return null;
		String s = text.trim();
		if (s.startsWith("#")) s = s.substring(1);
		if (!s.matches("[0-9a-fA-F]+")) return null;
		try {
			return switch (s.length()) {
				case 3 -> {
					int r = Integer.parseInt(s.substring(0, 1), 16);
					int g = Integer.parseInt(s.substring(1, 2), 16);
					int b = Integer.parseInt(s.substring(2, 3), 16);
					yield argb(255, r * 17, g * 17, b * 17);
				}
				case 6 -> 0xFF000000 | Integer.parseInt(s, 16);
				case 8 -> (int) Long.parseLong(s, 16);
				default -> null;
			};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** RGB (ignores alpha) to HSV, each component 0..1. */
	public static float[] toHsv(int argb) {
		float r = red(argb) / 255f;
		float g = green(argb) / 255f;
		float b = blue(argb) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;
		float h;
		if (delta == 0) h = 0;
		else if (max == r) h = ((g - b) / delta) % 6f;
		else if (max == g) h = (b - r) / delta + 2f;
		else h = (r - g) / delta + 4f;
		h /= 6f;
		if (h < 0) h += 1f;
		float s = max == 0 ? 0 : delta / max;
		return new float[]{h, s, max};
	}

	/** HSV (each 0..1) to an opaque RGB color. */
	public static int fromHsv(float h, float s, float v) {
		float hh = ((h % 1f) + 1f) % 1f * 6f;
		float c = v * s;
		float x = c * (1 - Math.abs(hh % 2f - 1));
		float m = v - c;
		float r, g, b;
		switch ((int) hh) {
			case 0 -> { r = c; g = x; b = 0; }
			case 1 -> { r = x; g = c; b = 0; }
			case 2 -> { r = 0; g = c; b = x; }
			case 3 -> { r = 0; g = x; b = c; }
			case 4 -> { r = x; g = 0; b = c; }
			default -> { r = c; g = 0; b = x; }
		}
		return argb(255, Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
	}
}
