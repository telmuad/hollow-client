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

	/** Frame-rate independent exponential approach. {@code speed} is roughly "1/time constant" in seconds. */
	public static float approach(float current, float target, float dtSeconds, float speed) {
		float k = 1f - (float) Math.exp(-speed * dtSeconds);
		float next = current + (target - current) * k;
		return Math.abs(target - next) < 0.001f ? target : next;
	}
}
