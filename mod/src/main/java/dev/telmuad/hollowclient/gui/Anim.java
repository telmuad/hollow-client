package dev.telmuad.hollowclient.gui;

/** Small easing helpers; everything is time based so animations look the same at any frame rate. */
public final class Anim {
	private Anim() {
	}

	public static float clamp01(float t) {
		return t < 0f ? 0f : (t > 1f ? 1f : t);
	}

	public static float easeOutCubic(float t) {
		t = clamp01(t);
		float u = 1f - t;
		return 1f - u * u * u;
	}

	public static float easeInOutCubic(float t) {
		t = clamp01(t);
		return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
	}

	/** Overshoots a little past 1 before settling: for things that snap into place. */
	public static float easeOutBack(float t) {
		t = clamp01(t);
		float c1 = 1.70158f;
		float c3 = c1 + 1f;
		float u = t - 1f;
		return 1f + c3 * u * u * u + c1 * u * u;
	}

	/** Frame-rate independent exponential approach. {@code speed} is roughly "1/time constant" in seconds. */
	public static float approach(float current, float target, float dtSeconds, float speed) {
		float k = 1f - (float) Math.exp(-speed * dtSeconds);
		float next = current + (target - current) * k;
		return Math.abs(target - next) < 0.001f ? target : next;
	}
}
