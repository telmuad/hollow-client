package dev.telmuad.hollowclient.gui;

/**
 * A damped spring: the value chases its target with a little overshoot, like a physical spring. Stepped in small
 * fixed slices, so it behaves the same at 30 and 300 FPS. Plain Java, unit tested.
 */
public final class Spring {
	private static final float STEP = 1f / 240f;

	private final float stiffness;
	private final float damping;
	private float value;
	private float velocity;
	private float target;

	/**
	 * @param stiffness how hard it pulls toward the target (higher = faster)
	 * @param damping   how quickly the wobble dies down (about 2*sqrt(stiffness) = no overshoot)
	 */
	public Spring(float value, float stiffness, float damping) {
		this.value = value;
		this.target = value;
		this.stiffness = stiffness;
		this.damping = damping;
	}

	/** A lively spring with a small overshoot (about 5 %). */
	public static Spring bouncy(float value) {
		return new Spring(value, 260f, 22f);
	}

	/** A calm spring that barely overshoots. */
	public static Spring smooth(float value) {
		return new Spring(value, 220f, 28f);
	}

	public float value() {
		return value;
	}

	public float target() {
		return target;
	}

	public Spring target(float target) {
		this.target = target;
		return this;
	}

	/** Jump straight to a value with no motion. */
	public Spring snap(float v) {
		value = v;
		target = v;
		velocity = 0f;
		return this;
	}

	public boolean settled() {
		return Math.abs(value - target) < 0.001f && Math.abs(velocity) < 0.01f;
	}

	/** Advance by {@code dt} seconds and return the new value. */
	public float update(float dt) {
		if (!(dt > 0f)) return value;
		float left = Math.min(dt, 0.25f);
		while (left > 0f) {
			float h = Math.min(STEP, left);
			float accel = -stiffness * (value - target) - damping * velocity;
			velocity += accel * h;
			value += velocity * h;
			left -= h;
		}
		if (settled()) {
			value = target;
			velocity = 0f;
		}
		return value;
	}
}
