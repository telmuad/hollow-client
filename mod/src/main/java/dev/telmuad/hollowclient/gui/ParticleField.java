package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Util;

import java.util.Random;

/**
 * "Void" particles behind Hollow's menus: small glowing motes that drift upward, sway, twinkle, and get pushed
 * away from the mouse. One shared field, so the particles carry on smoothly when you move between Hollow screens.
 */
public final class ParticleField {
	public static final int MAX = 110;
	private static final float MOUSE_RADIUS = 70f;

	private static final ParticleField SHARED = new ParticleField();

	private final Random random = new Random();
	private final float[] x = new float[MAX];
	private final float[] y = new float[MAX];
	private final float[] vx = new float[MAX];
	private final float[] vy = new float[MAX];
	private final float[] rise = new float[MAX];
	private final float[] size = new float[MAX];
	private final float[] phase = new float[MAX];
	private final float[] heat = new float[MAX];
	private int width = -1;
	private int height = -1;
	private float fade;

	public static ParticleField shared() {
		return SHARED;
	}

	private void spawn(int i, boolean anywhere) {
		x[i] = random.nextFloat() * width;
		y[i] = anywhere ? random.nextFloat() * height : height + 4 + random.nextFloat() * 20;
		vx[i] = 0f;
		vy[i] = 0f;
		rise[i] = 4f + random.nextFloat() * 10f;
		size[i] = 0.6f + random.nextFloat() * random.nextFloat() * 2.2f;
		phase[i] = random.nextFloat() * 6.2832f;
		heat[i] = 0f;
	}

	/**
	 * Move and draw the particles. {@code amount} (0..1) controls how many are shown; at 0 they fade out.
	 * Mouse coordinates are in GUI pixels.
	 */
	public void render(GuiGraphics g, int w, int h, double mouseX, double mouseY, float dt, float alpha, float amount) {
		if (w != width || h != height) {
			width = w;
			height = h;
			for (int i = 0; i < MAX; i++) spawn(i, true);
		}
		fade = Anim.approach(fade, amount > 0f ? 1f : 0f, dt, 4f);
		if (fade <= 0.001f) return;
		int count = Math.round(MAX * Math.max(amount, 0.15f));
		float speed = Theme.speed();
		float step = dt * speed;
		long now = Util.getMillis();
		float time = now / 1000f * speed;
		for (int i = 0; i < count; i++) {
			// mouse push: away from the cursor, stronger when close
			float dx = x[i] - (float) mouseX;
			float dy = y[i] - (float) mouseY;
			float d2 = dx * dx + dy * dy;
			if (d2 < MOUSE_RADIUS * MOUSE_RADIUS && d2 > 0.01f) {
				float d = (float) Math.sqrt(d2);
				float push = (1f - d / MOUSE_RADIUS) * 260f;
				vx[i] += dx / d * push * dt;
				vy[i] += dy / d * push * dt;
				heat[i] = Math.min(1f, heat[i] + dt * 4f);
			} else {
				heat[i] = Math.max(0f, heat[i] - dt * 1.5f);
			}
			// friction brings the push back to the gentle drift
			float drag = (float) Math.exp(-2.5f * dt);
			vx[i] *= drag;
			vy[i] *= drag;
			float sway = (float) Math.sin(time * 0.7f + phase[i]) * 6f;
			x[i] += (vx[i] + sway * 0.4f) * step;
			y[i] += (vy[i] - rise[i]) * step;
			if (y[i] < -8 || x[i] < -20 || x[i] > width + 20 || y[i] > height + 30) spawn(i, false);

			float twinkle = 0.55f + 0.45f * (float) Math.sin(time * 2.1f + phase[i] * 3f);
			float edge = Math.min(1f, Math.min(y[i], height - y[i]) / 40f);
			float a = alpha * fade * Math.max(0f, edge) * (0.35f + 0.65f * twinkle) * (0.6f + 0.4f * heat[i]);
			if (a <= 0.01f) continue;
			int color = Theme.gradient(Math.max(0f, Math.min(1f, x[i] / Math.max(1, width))));
			color = ColorUtil.mix(color, 0xFFFFFFFF, heat[i] * 0.5f);
			float r = size[i] * (1f + heat[i] * 0.8f);
			Fx.dot(g, x[i], y[i], r * 7f, ColorUtil.fade(color, a * 0.4f));
			Fx.dot(g, x[i], y[i], r * 2.6f, ColorUtil.fade(ColorUtil.mix(color, 0xFFFFFFFF, 0.55f), Math.min(1f, a * 1.3f)));
		}
	}
}
