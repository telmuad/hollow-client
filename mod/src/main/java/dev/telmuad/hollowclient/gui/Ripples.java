package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Click ripples: a circle grows out of the click point and fades, clipped to the thing that was clicked. */
public final class Ripples {
	private static final float DURATION_MS = 520f;
	private static final int MAX = 12;

	private record Ripple(float x, float y, Rect clip, long start, int color, float strength) {
	}

	private static final Ripples SHARED = new Ripples();

	private final List<Ripple> ripples = new ArrayList<>();

	/** The ripples of whatever Hollow screen is open (there is only ever one). */
	public static Ripples shared() {
		return SHARED;
	}

	public void add(double x, double y, Rect clip, int color, float strength) {
		if (strength <= 0f || clip.w() <= 0 || clip.h() <= 0) return;
		if (ripples.size() >= MAX) ripples.remove(0);
		ripples.add(new Ripple((float) x, (float) y, clip, Util.getMillis(), color, strength));
	}

	public void render(GuiGraphicsExtractor g, float alpha) {
		long now = Util.getMillis();
		float speed = Theme.speed();
		for (Iterator<Ripple> it = ripples.iterator(); it.hasNext(); ) {
			Ripple r = it.next();
			float t = (now - r.start) * speed / DURATION_MS;
			if (t >= 1f) {
				it.remove();
				continue;
			}
			Rect c = r.clip;
			float far = 0f;
			for (int cx : new int[]{c.x(), c.right()}) {
				for (int cy : new int[]{c.y(), c.bottom()}) far = Math.max(far, (float) Math.hypot(cx - r.x, cy - r.y));
			}
			float radius = 4f + far * Anim.easeOutCubic(t);
			float a = alpha * r.strength * 0.38f * (1f - t) * (1f - t);
			g.enableScissor(c.x(), c.y(), c.right(), c.bottom());
			Fx.disc(g, r.x, r.y, radius, ColorUtil.fade(r.color, a));
			g.disableScissor();
		}
	}
}
