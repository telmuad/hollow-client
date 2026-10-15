package dev.telmuad.hollowclient.util;

/**
 * FPS-aware quality: if FPS stays below the target for a few seconds, effects step down a level; if it stays well
 * above the target for a while, they step back up. Hysteresis keeps it from flickering. Plain Java, unit tested.
 */
public final class AutoQuality {
	public enum Level {LOW, MEDIUM, HIGH}

	/** Seconds below target before stepping down. */
	public static final long DOWN_AFTER_MS = 3000;
	/** Seconds comfortably above target before stepping back up. */
	public static final long UP_AFTER_MS = 10000;
	/** How far above the target counts as "comfortable". */
	public static final int UP_MARGIN = 15;

	private Level level = Level.HIGH;
	private long belowSince = -1;
	private long aboveSince = -1;

	public Level level() {
		return level;
	}

	public void reset() {
		level = Level.HIGH;
		belowSince = -1;
		aboveSince = -1;
	}

	/** Feed one FPS sample. */
	public Level update(int fps, int target, long nowMs) {
		if (fps < target) {
			aboveSince = -1;
			if (belowSince < 0) belowSince = nowMs;
			if (nowMs - belowSince >= DOWN_AFTER_MS && level != Level.LOW) {
				level = Level.values()[level.ordinal() - 1];
				belowSince = nowMs;
			}
		} else if (fps >= target + UP_MARGIN) {
			belowSince = -1;
			if (aboveSince < 0) aboveSince = nowMs;
			if (nowMs - aboveSince >= UP_AFTER_MS && level != Level.HIGH) {
				level = Level.values()[level.ordinal() + 1];
				aboveSince = nowMs;
			}
		} else {
			belowSince = -1;
			aboveSince = -1;
		}
		return level;
	}

	/** Effect strength multiplier for a level (counts, sizes, radii). */
	public static float scale(Level level) {
		return switch (level) {
			case LOW -> 0.35f;
			case MEDIUM -> 0.65f;
			case HIGH -> 1f;
		};
	}
}
