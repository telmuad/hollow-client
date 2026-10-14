package dev.telmuad.hollowclient.util;

/**
 * Zoom math, kept free of Minecraft classes. The zoom factor is how many times closer things look:
 * the field of view is divided by it.
 */
public final class ZoomState {
	public static final double MIN_ZOOM = 1.5;
	public static final double MAX_ZOOM = 50.0;
	/** Each scroll notch changes the zoom by this factor. */
	public static final double SCROLL_STEP = 1.15;

	private boolean active;
	private double target = 1.0;
	private double current = 1.0;
	private double held = 4.0;

	/** Start zooming at {@code baseZoom} (the configured default). */
	public void start(double baseZoom) {
		if (!active) {
			active = true;
			held = clamp(baseZoom);
		}
		target = held;
	}

	public void stop() {
		active = false;
		target = 1.0;
	}

	public boolean isActive() {
		return active;
	}

	/** Scroll while zooming: positive is in, negative is out. */
	public void scroll(double notches) {
		if (!active) return;
		held = clamp(held * Math.pow(SCROLL_STEP, notches));
		target = held;
	}

	/**
	 * Move the current zoom toward the target. With {@code smooth} off it jumps straight there.
	 * {@code speed} is roughly 1 / time constant in seconds.
	 */
	public void update(double dtSeconds, boolean smooth, double speed) {
		if (!smooth || dtSeconds <= 0) {
			current = target;
			return;
		}
		double k = 1 - Math.exp(-speed * Math.min(dtSeconds, 0.25));
		// Interpolate in log space so zooming in and out feel equally fast.
		double next = Math.exp(Math.log(current) + (Math.log(target) - Math.log(current)) * k);
		current = Math.abs(next - target) < 0.001 ? target : next;
	}

	/** Factor to multiply the FOV by (1 when not zoomed). */
	public double fovMultiplier() {
		return 1.0 / current;
	}

	public double current() {
		return current;
	}

	public double held() {
		return held;
	}

	/** Whether the zoom is visibly applied (also true while smoothly zooming back out). */
	public boolean isZoomed() {
		return current > 1.0001;
	}

	public static double clamp(double zoom) {
		if (Double.isNaN(zoom)) return MIN_ZOOM;
		return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
	}
}
