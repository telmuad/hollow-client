package dev.telmuad.hollowclient.hud;

import dev.telmuad.hollowclient.gui.Anim;
import dev.telmuad.hollowclient.gui.Fx;
import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.modules.hud.HudStyleModule;
import dev.telmuad.hollowclient.render.Quality;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Util;

/**
 * Animated hotbar (HUD Style → Animated hotbar): the selection frame glides between slots with a theme glow behind
 * it, and the selected item pops a little when you switch to it. Used by GuiMixin.
 */
public final class HotbarFx {
	private static final float POP_MS = 240f;
	private static float x = Float.NaN;
	private static long lastNanos;
	private static int lastSlot = -1;
	private static long changedAt;

	private HotbarFx() {
	}

	public static float amount() {
		HudStyleModule m = HudStyle.module();
		return m == null ? 0f : m.hotbar();
	}

	/** The frame's drawn x for vanilla's target x (in GUI pixels). */
	public static float selectionX(int targetX, int slot) {
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;
		if (slot != lastSlot) {
			if (lastSlot >= 0) changedAt = Util.getMillis();
			lastSlot = slot;
		}
		// snap on big jumps (resizing) or when motion is reduced
		if (Float.isNaN(x) || Math.abs(x - targetX) > 400 || Quality.reducedMotion()) x = targetX;
		else x = Anim.approach(x, targetX, dt, 14f + 18f * amount());
		return x;
	}

	public static void glow(GuiGraphics g, float fx, int y, int w, int h) {
		float a = amount();
		if (a <= 0f) return;
		Fx.glow(g, Math.round(fx) + 2, y + 2, w - 4, h - 4, 7, ColorUtil.fade(Theme.accent(), a * (0.35f + 0.45f * Theme.glow())));
	}

	/** Scale for the item in a hotbar slot: the selected one is a touch bigger and pops when chosen. */
	public static float itemScale(int slot, int selected) {
		float a = amount();
		if (a <= 0f || slot != selected) return 1f;
		float t = Anim.clamp01((Util.getMillis() - changedAt) * Theme.speed() / POP_MS);
		float pop = Quality.reducedMotion() ? 0f : (1f - Anim.easeOutCubic(t)) * 0.22f;
		return 1f + a * (0.06f + pop);
	}
}
