package dev.telmuad.hollowclient.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.visual.SkyModule;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Util;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * The Sky module's drawing: tinted sky and horizon colors, star count and brightness, and, at night, an aurora and
 * shooting stars. The aurora and streaks are drawn in the sky pass (after the stars) as additive, see-through
 * quads that don't write depth, so the world draws over them like the rest of the sky.
 */
public final class SkyFx {
	private static final RenderPipeline GLOW_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
			.withLocation(HollowClient.id("pipeline/sky_glow"))
			.withVertexShader("core/position_color")
			.withFragmentShader("core/position_color")
			.withBlend(BlendFunction.LIGHTNING)
			.withDepthWrite(false)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withCull(false)
			.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
			.build();
	private static final RenderType GLOW = RenderType.create("hollowclient_sky_glow", RenderSetup.builder(GLOW_PIPELINE).createRenderSetup());
	private static final int DAY_SKY = 0xFF78A7FF;

	private record Streak(Vector3f start, Vector3f dir, long born, float life, float length) {
	}

	private static final List<Streak> STREAKS = new ArrayList<>();
	private static final Random RANDOM = new Random();
	private static long lastSpawnCheck;

	private SkyFx() {
	}

	private static SkyModule module() {
		SkyModule m = Modules.SKY;
		return m != null && m.isEnabled() ? m : null;
	}

	// ------------------------------------------------------------------------------------------------ colors

	private static int top(SkyModule m) {
		return m.themeColors() ? ColorUtil.mix(Theme.gradient(0f), 0xFF0A0B1A, 0.25f) : m.topColor();
	}

	private static int horizon(SkyModule m) {
		return m.themeColors() ? ColorUtil.mix(Theme.gradient(1f), 0xFFFFFFFF, 0.15f) : m.horizonColor();
	}

	/**
	 * Tint a vanilla color toward a target, keeping vanilla's brightness (so night stays dark and dusk stays dusky).
	 */
	private static int tintKeepingLight(int vanilla, int target, float amount) {
		float vl = luma(vanilla) / Math.max(0.05f, luma(DAY_SKY));
		float tl = Math.max(0.05f, luma(target));
		float k = Math.min(1.6f, vl / tl * luma(DAY_SKY));
		int scaled = ColorUtil.argb(255, clamp((target >> 16 & 255) * k), clamp((target >> 8 & 255) * k), clamp((target & 255) * k));
		return ColorUtil.mix(vanilla | 0xFF000000, scaled, amount);
	}

	private static float luma(int c) {
		return ((c >> 16 & 255) * 0.2126f + (c >> 8 & 255) * 0.7152f + (c & 255) * 0.0722f) / 255f;
	}

	private static int clamp(float v) {
		return Math.max(0, Math.min(255, Math.round(v)));
	}

	/** The sky (top) color. */
	public static int skyColor(int vanilla) {
		SkyModule m = module();
		if (m == null || m.tint() <= 0f) return vanilla;
		return tintKeepingLight(vanilla, top(m), m.tint());
	}

	/** The fog color, which is what you see at the horizon. */
	public static Vector4f fogColor(Vector4f vanilla) {
		SkyModule m = module();
		if (m == null || m.tint() <= 0f) return vanilla;
		int v = ColorUtil.argb(255, clamp(vanilla.x * 255), clamp(vanilla.y * 255), clamp(vanilla.z * 255));
		int t = tintKeepingLight(v, horizon(m), m.tint() * 0.8f);
		return new Vector4f((t >> 16 & 255) / 255f, (t >> 8 & 255) / 255f, (t & 255) / 255f, vanilla.w);
	}

	// ------------------------------------------------------------------------------------------------ stars

	public static float starMultiplier() {
		SkyModule m = module();
		return m == null ? 1f : m.starMultiplier();
	}

	public static float starBrightness(float vanilla) {
		SkyModule m = module();
		if (m == null || m.starGlow() <= 0f) return vanilla;
		return Math.min(1f, vanilla * (1f + 1.6f * m.starGlow()));
	}

	// ------------------------------------------------------------------------------------------------ aurora + streaks

	/**
	 * Draw the night effects. {@code starBrightness} is vanilla's (0 by day, about 0.5 at night) and
	 * {@code clearSky} is 1 minus the rain level.
	 */
	public static void renderNight(float starBrightness, float clearSky) {
		SkyModule m = module();
		if (m == null) {
			STREAKS.clear();
			return;
		}
		float night = Math.min(1f, starBrightness / 0.5f) * clearSky;
		if (night <= 0.02f || (m.aurora() <= 0f && m.shootingStars() <= 0f)) {
			STREAKS.clear();
			return;
		}
		BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		boolean any = false;
		float detail = Quality.performanceMode() ? 0.4f : 0.6f + 0.4f * Quality.scale();
		if (m.aurora() > 0f) any |= aurora(b, night * m.aurora(), detail);
		if (m.shootingStars() > 0f) any |= streaks(b, night, m.shootingStars());
		MeshData mesh = b.build();
		if (mesh == null) return;
		if (!any) {
			mesh.close();
			return;
		}
		GLOW.draw(mesh);
	}

	/** Two wavy curtains toward the north, bright at the bottom edge and fading upward. */
	private static boolean aurora(BufferBuilder b, float strength, float detail) {
		double t = Util.getMillis() / 1000.0 * Theme.speed();
		int segments = Math.max(24, Math.round(64 * detail));
		int[] colors = {Theme.gradient(0f), Theme.gradient(1f)};
		for (int layer = 0; layer < 2; layer++) {
			float dist = 92f - layer * 6f;
			float span = 1.35f - layer * 0.25f;
			float baseY = 22f + layer * 7f;
			int bottomColor = ColorUtil.mix(colors[layer], 0xFF7CFFB2, 0.35f);
			int topColor = colors[1 - layer];
			for (int s = 0; s < segments; s++) {
				float u0 = s / (float) segments;
				float u1 = (s + 1) / (float) segments;
				float[] p0 = curtain(u0, t, layer, dist, span, baseY);
				float[] p1 = curtain(u1, t, layer, dist, span, baseY);
				// brightness ripples along the curtain and fades at both ends
				float a0 = edgeFade(u0) * shimmer(u0, t, layer) * strength;
				float a1 = edgeFade(u1) * shimmer(u1, t, layer) * strength;
				int c0 = ColorUtil.fade(bottomColor, 0.55f * a0);
				int c1 = ColorUtil.fade(bottomColor, 0.55f * a1);
				int c0Top = ColorUtil.fade(topColor, 0f);
				int c1Top = ColorUtil.fade(topColor, 0f);
				int m0 = ColorUtil.fade(ColorUtil.mix(bottomColor, topColor, 0.5f), 0.32f * a0);
				int m1 = ColorUtil.fade(ColorUtil.mix(bottomColor, topColor, 0.5f), 0.32f * a1);
				float mid0 = p0[1] + p0[3] * 0.35f;
				float mid1 = p1[1] + p1[3] * 0.35f;
				// lower band (bright) and upper band (fading out)
				b.addVertex(p0[0], p0[1], p0[2]).setColor(c0);
				b.addVertex(p1[0], p1[1], p1[2]).setColor(c1);
				b.addVertex(p1[0], mid1, p1[2]).setColor(m1);
				b.addVertex(p0[0], mid0, p0[2]).setColor(m0);
				b.addVertex(p0[0], mid0, p0[2]).setColor(m0);
				b.addVertex(p1[0], mid1, p1[2]).setColor(m1);
				b.addVertex(p1[0], p1[1] + p1[3], p1[2]).setColor(c1Top);
				b.addVertex(p0[0], p0[1] + p0[3], p0[2]).setColor(c0Top);
			}
		}
		return true;
	}

	/** Position on the curtain: {x, y, z, height}. North is -Z. */
	private static float[] curtain(float u, double t, int layer, float dist, float span, float baseY) {
		double angle = (u - 0.5) * span * Math.PI / 2;
		double sway = Math.sin(u * 7.0 + t * 0.35 + layer * 1.7) * 0.08 + Math.sin(u * 17.0 - t * 0.6 + layer) * 0.03;
		double a = angle + sway;
		double r = dist + Math.sin(u * 5.0 + t * 0.25 + layer) * 6.0;
		float x = (float) (Math.sin(a) * r);
		float z = (float) (-Math.cos(a) * r);
		float y = baseY + (float) (Math.sin(u * 9.0 + t * 0.4 + layer * 2.3) * 3.0);
		float h = 26f + (float) (Math.sin(u * 13.0 + t * 0.5 + layer) * 8.0);
		return new float[]{x, y, z, h};
	}

	private static float edgeFade(float u) {
		float e = Math.min(u, 1f - u) / 0.18f;
		return Math.max(0f, Math.min(1f, e));
	}

	private static float shimmer(float u, double t, int layer) {
		return 0.55f + 0.45f * (float) Math.sin(u * 23.0 + t * 1.3 + layer * 4.1);
	}

	/** Occasional shooting stars: a bright head with a fading tail. Strength = how often they appear. */
	private static boolean streaks(BufferBuilder b, float night, float rate) {
		long now = Util.getMillis();
		float dt = lastSpawnCheck == 0 ? 0f : Math.min(0.5f, (now - lastSpawnCheck) / 1000f);
		lastSpawnCheck = now;
		// on average one every 25 s at the lowest rate, one every 3 s at full
		float perSecond = 0.04f + 0.3f * rate;
		if (STREAKS.size() < 4 && RANDOM.nextFloat() < perSecond * dt) {
			double yaw = RANDOM.nextDouble() * Math.PI * 2;
			double pitch = Math.toRadians(25 + RANDOM.nextDouble() * 45);
			Vector3f start = new Vector3f((float) (Math.cos(pitch) * Math.sin(yaw)), (float) Math.sin(pitch), (float) (Math.cos(pitch) * -Math.cos(yaw))).mul(100f);
			Vector3f dir = new Vector3f((float) RANDOM.nextGaussian(), -0.4f - RANDOM.nextFloat() * 0.5f, (float) RANDOM.nextGaussian()).normalize();
			STREAKS.add(new Streak(start, dir, now, 0.6f + RANDOM.nextFloat() * 0.6f, 14f + RANDOM.nextFloat() * 14f));
		}
		boolean any = false;
		for (Iterator<Streak> it = STREAKS.iterator(); it.hasNext(); ) {
			Streak s = it.next();
			float age = (now - s.born) / 1000f;
			if (age >= s.life) {
				it.remove();
				continue;
			}
			float p = age / s.life;
			float alpha = night * (float) Math.sin(p * Math.PI);
			Vector3f head = new Vector3f(s.dir).mul(p * 60f).add(s.start);
			Vector3f tail = new Vector3f(s.dir).mul(-s.length).add(head);
			// a ribbon facing the camera (which sits at the origin)
			Vector3f side = new Vector3f(s.dir).cross(new Vector3f(head).normalize()).normalize().mul(0.35f);
			int headColor = ColorUtil.fade(ColorUtil.mix(0xFFFFFFFF, Theme.accent2(), 0.25f), alpha);
			int tailColor = ColorUtil.fade(Theme.accent(), 0f);
			b.addVertex(tail.x - side.x * 0.2f, tail.y - side.y * 0.2f, tail.z - side.z * 0.2f).setColor(tailColor);
			b.addVertex(tail.x + side.x * 0.2f, tail.y + side.y * 0.2f, tail.z + side.z * 0.2f).setColor(tailColor);
			b.addVertex(head.x + side.x, head.y + side.y, head.z + side.z).setColor(headColor);
			b.addVertex(head.x - side.x, head.y - side.y, head.z - side.z).setColor(headColor);
			any = true;
		}
		return any;
	}

	/** For tests: spawn a shooting star right now. */
	public static void spawnTestStreak() {
		STREAKS.add(new Streak(new Vector3f(0f, 0.7f, -0.7f).normalize().mul(100f), new Vector3f(0.8f, -0.4f, 0.1f).normalize(),
				Util.getMillis() - 250, 1.2f, 24f));
	}
}
