package dev.telmuad.hollowclient.gui.widget;

import dev.telmuad.hollowclient.gui.Anim;
import dev.telmuad.hollowclient.gui.Draw;
import dev.telmuad.hollowclient.gui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** An on/off switch: a pill with a sliding knob. Stateless apart from the animation; the caller owns the value. */
public final class UiToggle {
	public static final int W = 20;
	public static final int H = 11;

	private float t = -1f;

	/** Draw at x, y. Returns nothing; use {@link #hit} for clicks. */
	public void render(GuiGraphicsExtractor g, int x, int y, boolean on, boolean hover, float dt, float alpha) {
		if (t < 0) t = on ? 1f : 0f;
		t = Anim.approach(t, on ? 1f : 0f, dt, 20f);
		int track = Theme.mix(Theme.SWITCH_OFF, Theme.accent(), t);
		if (hover) track = Theme.mix(track, 0xFFFFFFFF, 0.12f);
		Draw.roundRect(g, x, y, W, H, H / 2, Theme.fade(track, alpha));
		int knob = 7;
		int kx = x + 2 + Math.round((W - knob - 4) * t);
		Draw.circle(g, kx, y + 2, knob, Theme.fade(0xFFF4F4F8, alpha));
	}

	public static boolean hit(int x, int y, double mx, double my) {
		return mx >= x - 2 && my >= y - 2 && mx < x + W + 2 && my < y + H + 2;
	}
}
