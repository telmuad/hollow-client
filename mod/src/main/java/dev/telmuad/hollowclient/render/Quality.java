package dev.telmuad.hollowclient.render;

import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.util.AutoQuality;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

/**
 * What every effect asks before doing work: is Performance Mode on, how strong may effects be right now (Auto
 * Quality), and is an Iris shader pack active (then Hollow's post-processing stays off).
 */
public final class Quality {
	private static final AutoQuality AUTO = new AutoQuality();
	private static long lastSample;

	private Quality() {
	}

	/** Called every client tick; samples FPS once a second. */
	public static void tick() {
		long now = Util.getMillis();
		if (now - lastSample < 1000) return;
		lastSample = now;
		if (Modules.PERFORMANCE == null || !Modules.PERFORMANCE.autoQuality()) {
			AUTO.reset();
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.isPaused()) return;
		AUTO.update(mc.getFps(), Modules.PERFORMANCE.targetFps(), now);
	}

	public static boolean performanceMode() {
		return Modules.PERFORMANCE != null && Modules.PERFORMANCE.performanceMode();
	}

	public static AutoQuality.Level level() {
		return AUTO.level();
	}

	/** 0 in Performance Mode; otherwise 1 (high), 0.65 (medium) or 0.35 (low). Multiply counts/radii by this. */
	public static float scale() {
		if (performanceMode()) return 0f;
		return AutoQuality.scale(AUTO.level());
	}

	/** Heavy effects (post-processing, blur, menu particles) are allowed. */
	public static boolean heavyAllowed() {
		return !performanceMode();
	}

	/** Hollow's own post-processing may run (no Performance Mode, and no active Iris shader pack). */
	public static boolean postProcessingAllowed() {
		return heavyAllowed() && !Compat.irisShadersActive();
	}

	public static boolean reducedMotion() {
		return performanceMode() || (Modules.PERFORMANCE != null && Modules.PERFORMANCE.reducedMotion());
	}
}
