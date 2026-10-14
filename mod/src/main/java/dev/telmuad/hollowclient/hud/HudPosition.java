package dev.telmuad.hollowclient.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;

/**
 * Where a HUD element sits, stored relative to the screen so it survives window resizes and GUI scale changes.
 * <p>
 * Each axis has an anchor (start, center or end of the screen) and an offset from that anchor as a fraction of the
 * screen size. For the END anchor the offset is the gap between the element's far edge and the screen edge, so an
 * element flush against the right or bottom edge stays flush whatever the window size.
 */
public final class HudPosition {
	public enum Anchor {START, CENTER, END}

	public static final float MIN_SCALE = 0.5f;
	public static final float MAX_SCALE = 3.0f;

	public Anchor anchorX;
	public Anchor anchorY;
	public double offsetX;
	public double offsetY;
	public float scale;

	public HudPosition(Anchor anchorX, double offsetX, Anchor anchorY, double offsetY, float scale) {
		this.anchorX = anchorX;
		this.offsetX = offsetX;
		this.anchorY = anchorY;
		this.offsetY = offsetY;
		this.scale = clampScale(scale);
	}

	public HudPosition copy() {
		return new HudPosition(anchorX, offsetX, anchorY, offsetY, scale);
	}

	public void copyFrom(HudPosition other) {
		anchorX = other.anchorX;
		anchorY = other.anchorY;
		offsetX = other.offsetX;
		offsetY = other.offsetY;
		scale = other.scale;
	}

	public static float clampScale(float scale) {
		if (Float.isNaN(scale)) return 1f;
		return Math.max(MIN_SCALE, Math.min(MAX_SCALE, Math.round(scale * 20f) / 20f));
	}

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("anchorX", anchorX.name().toLowerCase(Locale.ROOT));
		o.addProperty("x", offsetX);
		o.addProperty("anchorY", anchorY.name().toLowerCase(Locale.ROOT));
		o.addProperty("y", offsetY);
		o.addProperty("scale", scale);
		return o;
	}

	/** Read from JSON; fields that are missing or wrong keep the values from {@code fallback}. */
	public static HudPosition fromJson(JsonElement json, HudPosition fallback) {
		HudPosition p = fallback.copy();
		if (json == null || !json.isJsonObject()) return p;
		JsonObject o = json.getAsJsonObject();
		p.anchorX = anchor(o.get("anchorX"), p.anchorX);
		p.anchorY = anchor(o.get("anchorY"), p.anchorY);
		p.offsetX = fraction(o.get("x"), p.offsetX);
		p.offsetY = fraction(o.get("y"), p.offsetY);
		if (isNumber(o.get("scale"))) p.scale = clampScale(o.get("scale").getAsFloat());
		return p;
	}

	private static Anchor anchor(JsonElement e, Anchor fallback) {
		if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
			for (Anchor a : Anchor.values()) if (a.name().equalsIgnoreCase(e.getAsString())) return a;
		}
		return fallback;
	}

	private static double fraction(JsonElement e, double fallback) {
		if (!isNumber(e)) return fallback;
		double v = e.getAsDouble();
		if (Double.isNaN(v) || Double.isInfinite(v)) return fallback;
		return Math.max(-1, Math.min(1, v));
	}

	private static boolean isNumber(JsonElement e) {
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof HudPosition p)) return false;
		return anchorX == p.anchorX && anchorY == p.anchorY && Double.compare(offsetX, p.offsetX) == 0
				&& Double.compare(offsetY, p.offsetY) == 0 && Float.compare(scale, p.scale) == 0;
	}

	@Override
	public int hashCode() {
		return java.util.Objects.hash(anchorX, anchorY, offsetX, offsetY, scale);
	}

	@Override
	public String toString() {
		return "HudPosition[" + anchorX + " " + offsetX + ", " + anchorY + " " + offsetY + ", x" + scale + "]";
	}
}
