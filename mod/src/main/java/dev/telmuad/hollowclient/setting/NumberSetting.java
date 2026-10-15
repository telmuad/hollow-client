package dev.telmuad.hollowclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

/** A number between {@code min} and {@code max}, snapped to {@code step}. */
public final class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;
	private int decimals;
	private String suffix = "";
	private java.util.function.DoubleFunction<String> displayAs;

	public NumberSetting(String id, String name, double defaultValue, double min, double max, double step) {
		super(id, name, defaultValue);
		if (!(min < max)) throw new IllegalArgumentException("min must be below max");
		this.min = min;
		this.max = max;
		this.step = step;
		this.decimals = step >= 1 ? 0 : (step >= 0.1 ? 1 : 2);
	}

	public double min() {
		return min;
	}

	public double max() {
		return max;
	}

	public double step() {
		return step;
	}

	public void set(double value) {
		set(Double.valueOf(value));
	}

	public float getFloat() {
		return get().floatValue();
	}

	public int getInt() {
		return (int) Math.round(get());
	}

	public NumberSetting format(int decimals, String suffix) {
		this.decimals = Math.max(0, decimals);
		this.suffix = suffix == null ? "" : suffix;
		return this;
	}

	/** Show the value in a custom way (for example a clock time) instead of number + suffix. */
	public NumberSetting display(java.util.function.DoubleFunction<String> formatter) {
		this.displayAs = formatter;
		return this;
	}

	/** Position of the value along the slider, 0..1. */
	public double fraction() {
		return (get() - min) / (max - min);
	}

	public void setFraction(double fraction) {
		set(min + Math.max(0, Math.min(1, fraction)) * (max - min));
	}

	@Override
	protected Double sanitize(Double raw) {
		double v = raw;
		if (Double.isNaN(v) || Double.isInfinite(v)) v = defaultValue();
		if (step > 0) v = min + Math.round((v - min) / step) * step;
		v = Math.max(min, Math.min(max, v));
		// Trim floating point noise from the step math (0.30000000000000004 -> 0.3).
		v = Math.round(v * 1e6) / 1e6;
		return v;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(get());
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			set(json.getAsDouble());
		} else {
			reset();
		}
	}

	@Override
	public String display() {
		if (displayAs != null) return displayAs.apply(get());
		return String.format(Locale.ROOT, "%." + decimals + "f", get()) + suffix;
	}
}
