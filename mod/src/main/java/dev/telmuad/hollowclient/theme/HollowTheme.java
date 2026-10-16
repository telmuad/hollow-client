package dev.telmuad.hollowclient.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.telmuad.hollowclient.setting.StringSetting;
import dev.telmuad.hollowclient.util.ColorUtil;

import java.util.List;
import java.util.Locale;

/**
 * One look for Hollow's UI (mod and launcher): accent color, a two-color gradient, and how strong the glow, blur,
 * corner rounding and animations are. Plain Java so it is unit tested; shared with the launcher through
 * {@code config/hollowclient/theme.json}.
 *
 * @param glow           0..1, how bright glows and borders are
 * @param blur           0..1, how strongly the world is blurred behind menus
 * @param roundness      corner radius in GUI pixels, 0..8
 * @param animationSpeed 0.25..3, multiplier for every UI animation
 * @param rainbow        cycle the accent and gradient through the color wheel
 */
public record HollowTheme(String name, int accent, int gradientStart, int gradientEnd, float glow, float blur,
						  int roundness, float animationSpeed, boolean rainbow) {
	public static final int MAX_NAME = 24;

	public HollowTheme {
		name = cleanName(name);
		accent |= 0xFF000000;
		gradientStart |= 0xFF000000;
		gradientEnd |= 0xFF000000;
		glow = clamp(glow, 0f, 1f);
		blur = clamp(blur, 0f, 1f);
		roundness = Math.max(0, Math.min(8, roundness));
		animationSpeed = Float.isNaN(animationSpeed) ? 1f : Math.round(clamp(animationSpeed, 0.25f, 3f) * 100f) / 100f;
	}

	public static final HollowTheme VOID = new HollowTheme("Void", 0xFF9B6CFF, 0xFF8B5CF6, 0xFF22D3EE, 0.65f, 0.6f, 6, 1f, false);
	public static final HollowTheme INFERNO = new HollowTheme("Inferno", 0xFFFF7A2F, 0xFFFFB02E, 0xFFE11D48, 0.7f, 0.55f, 5, 1.1f, false);
	public static final HollowTheme TOXIC = new HollowTheme("Toxic", 0xFF7CFF4F, 0xFFB6FF3B, 0xFF10B981, 0.75f, 0.5f, 4, 1f, false);
	public static final HollowTheme FROST = new HollowTheme("Frost", 0xFF7DD3FC, 0xFFE0F2FE, 0xFF38BDF8, 0.55f, 0.75f, 8, 0.85f, false);
	public static final HollowTheme BLOODMOON = new HollowTheme("Bloodmoon", 0xFFFF2D55, 0xFFFF4D6D, 0xFF7F1D1D, 0.7f, 0.6f, 5, 0.9f, false);
	public static final HollowTheme RAINBOW = new HollowTheme("Rainbow", 0xFFFF5CAA, 0xFFFF5CAA, 0xFF5CC8FF, 0.7f, 0.6f, 6, 1f, true);
	/** Sky blue and gold on the void base, for SkyBlock Mode. */
	public static final HollowTheme SKYBLOCK = new HollowTheme("SkyBlock", 0xFF38BDF8, 0xFF38BDF8, 0xFFF5C542, 0.65f, 0.6f, 6, 1f, false);

	public static final List<HollowTheme> PRESETS = List.of(VOID, INFERNO, TOXIC, FROST, BLOODMOON, RAINBOW, SKYBLOCK);

	public static HollowTheme preset(String name) {
		for (HollowTheme t : PRESETS) if (t.name.equalsIgnoreCase(name)) return t;
		return null;
	}

	public boolean isPreset() {
		HollowTheme p = preset(name);
		return p != null && p.equals(this);
	}

	public HollowTheme withName(String n) {
		return new HollowTheme(n, accent, gradientStart, gradientEnd, glow, blur, roundness, animationSpeed, rainbow);
	}

	public HollowTheme withAccent(int c) {
		return new HollowTheme(name, c, gradientStart, gradientEnd, glow, blur, roundness, animationSpeed, rainbow);
	}

	public HollowTheme withGradient(int start, int end) {
		return new HollowTheme(name, accent, start, end, glow, blur, roundness, animationSpeed, rainbow);
	}

	public HollowTheme withGlow(float g) {
		return new HollowTheme(name, accent, gradientStart, gradientEnd, g, blur, roundness, animationSpeed, rainbow);
	}

	public HollowTheme withBlur(float b) {
		return new HollowTheme(name, accent, gradientStart, gradientEnd, glow, b, roundness, animationSpeed, rainbow);
	}

	public HollowTheme withRoundness(int r) {
		return new HollowTheme(name, accent, gradientStart, gradientEnd, glow, blur, r, animationSpeed, rainbow);
	}

	public HollowTheme withAnimationSpeed(float s) {
		return new HollowTheme(name, accent, gradientStart, gradientEnd, glow, blur, roundness, s, rainbow);
	}

	public HollowTheme withRainbow(boolean r) {
		return new HollowTheme(name, accent, gradientStart, gradientEnd, glow, blur, roundness, animationSpeed, r);
	}

	// ------------------------------------------------------------------------------------------------ colors over time

	/** Hue offset (0..1) for rainbow themes at a time in milliseconds; one full turn every 8 s at speed 1. */
	public float rainbowPhase(long millis) {
		return (float) ((millis * (double) animationSpeed / 8000.0) % 1.0);
	}

	public int accentAt(long millis) {
		if (!rainbow) return accent;
		return ColorUtil.fromHsv(rainbowPhase(millis), 0.65f, 1f);
	}

	/** Gradient color at position t (0..1); rainbow themes shift the whole gradient around the wheel. */
	public int gradientAt(float t, long millis) {
		if (rainbow) return ColorUtil.fromHsv((rainbowPhase(millis) + t * 0.35f) % 1f, 0.65f, 1f);
		return ColorUtil.mix(gradientStart, gradientEnd, t);
	}

	// ------------------------------------------------------------------------------------------------ JSON

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("name", name);
		o.addProperty("accent", ColorUtil.toHex(accent));
		o.addProperty("gradientStart", ColorUtil.toHex(gradientStart));
		o.addProperty("gradientEnd", ColorUtil.toHex(gradientEnd));
		o.addProperty("glow", glow);
		o.addProperty("blur", blur);
		o.addProperty("roundness", roundness);
		o.addProperty("animationSpeed", animationSpeed);
		o.addProperty("rainbow", rainbow);
		return o;
	}

	/** Read a theme; missing or broken fields come from {@code fallback}. Returns fallback for non-objects. */
	public static HollowTheme fromJson(JsonElement json, HollowTheme fallback) {
		if (json == null || !json.isJsonObject()) return fallback;
		JsonObject o = json.getAsJsonObject();
		return new HollowTheme(
				string(o.get("name"), fallback.name),
				color(o.get("accent"), fallback.accent),
				color(o.get("gradientStart"), fallback.gradientStart),
				color(o.get("gradientEnd"), fallback.gradientEnd),
				number(o.get("glow"), fallback.glow),
				number(o.get("blur"), fallback.blur),
				Math.round(number(o.get("roundness"), fallback.roundness)),
				number(o.get("animationSpeed"), fallback.animationSpeed),
				bool(o.get("rainbow"), fallback.rainbow));
	}

	private static String string(JsonElement e, String fallback) {
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : fallback;
	}

	private static int color(JsonElement e, int fallback) {
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) return fallback;
		Integer c = ColorUtil.parseHex(e.getAsString());
		return c == null ? fallback : c;
	}

	private static float number(JsonElement e, float fallback) {
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return fallback;
		float v = e.getAsFloat();
		return Float.isNaN(v) || Float.isInfinite(v) ? fallback : v;
	}

	private static boolean bool(JsonElement e, boolean fallback) {
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : fallback;
	}

	public static String cleanName(String raw) {
		String s = raw == null ? "" : StringSetting.clean(raw, MAX_NAME).trim();
		return s.isEmpty() ? "Custom" : s;
	}

	private static float clamp(float v, float lo, float hi) {
		if (Float.isNaN(v)) return lo;
		return Math.max(lo, Math.min(hi, v));
	}

	public String id() {
		return name.toLowerCase(Locale.ROOT);
	}
}
