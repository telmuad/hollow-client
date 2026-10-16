package dev.telmuad.hollowclient.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.camera.CameraPaths;
import dev.telmuad.hollowclient.gui.Draw;
import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.gui.Ui;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.qol.WaypointsModule;
import dev.telmuad.hollowclient.util.ColorUtil;
import dev.telmuad.hollowclient.waypoints.Waypoint;
import dev.telmuad.hollowclient.waypoints.Waypoints;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Waypoint drawing: soft light beams in the world (hidden behind terrain like real light, additive, no depth
 * writes) and HUD labels with the name and distance. Far waypoints get their beam drawn at the edge of your view
 * distance, in the right direction, so you can still find them.
 */
public final class WaypointFx {
	// position + color quads, depth-tested against terrain but not writing depth (vanilla's debug-filled base),
	// with additive "lightning" blending so beams glow
	public static final RenderPipeline BEAM_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(HollowClient.id("pipeline/waypoint_beam"))
			.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
			.build();
	private static final RenderType BEAM = RenderType.create("hollowclient_waypoint_beam", RenderSetup.builder(BEAM_PIPELINE).createRenderSetup());
	private static final float BEAM_HEIGHT = 320f;

	private WaypointFx() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(WaypointFx::renderBeams);
	}

	private static WaypointsModule module() {
		WaypointsModule m = Modules.WAYPOINTS;
		return m != null && m.isEnabled() ? m : null;
	}

	public static int color(Waypoint w) {
		return w.color == 0 ? Theme.accent() : w.color;
	}

	private static void renderBeams(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		WaypointsModule m = module();
		if (m == null || m.beams() <= 0f || !Waypoints.allowed(mc) || CameraPaths.playing()) return;
		List<Waypoint> list = Waypoints.here(mc);
		if (list.isEmpty()) return;
		PoseStack pose = context.poseStack();
		if (pose == null) return;
		Vec3 cam = WorldProjection.camera();
		context.submitNodeCollector().submitCustomGeometry(pose, BEAM, (p, v) -> beams(mc, m, list, cam, v, p.pose()));
	}

	private static void beams(Minecraft mc, WaypointsModule m, List<Waypoint> list, Vec3 cam, VertexConsumer v, Matrix4f mat) {
		double maxDist = Math.max(32, mc.options.getEffectiveRenderDistance() * 16 - 12);
		float strength = m.beams();
		float pulse = 0.85f + 0.15f * (float) Math.sin(Util.getMillis() / 600.0);
		for (Waypoint w : list) {
			if (!w.visible) continue;
			double dx = w.x - cam.x;
			double dz = w.z - cam.z;
			double horizontal = Math.sqrt(dx * dx + dz * dz);
			if (horizontal > maxDist) {
				// pull the beam in to the edge of the view distance, in the same direction
				double k = maxDist / horizontal;
				dx *= k;
				dz *= k;
			}
			// wider with distance so far beams stay visible; fades away as you walk up to it
			float scale = Math.max(1f, Math.min(6f, (float) Math.sqrt(Math.max(1, Math.min(horizontal, maxDist)) / 24)));
			float near = (float) Math.max(0, Math.min(1, (horizontal - 3) / 14));
			if (near <= 0.01f) continue;
			float bottom = (float) (w.y - cam.y);
			int c = color(w);
			float k = strength * near;
			beam(v, mat, (float) dx, (float) dz, bottom, 0.14f * scale, ColorUtil.withAlpha(ColorUtil.mix(c, 0xFFFFFFFF, 0.35f), (int) (130 * k * pulse)));
			beam(v, mat, (float) dx, (float) dz, bottom, 0.55f * scale, ColorUtil.withAlpha(c, (int) (45 * k)));
		}
	}

	/** One camera-facing vertical strip, brightest at the bottom and fading out upward. */
	private static void beam(VertexConsumer v, Matrix4f m, float x, float z, float bottom, float halfWidth, int color) {
		float len = (float) Math.sqrt(x * x + z * z);
		float rx = len < 1e-3f ? 1f : -z / len;
		float rz = len < 1e-3f ? 0f : x / len;
		float ax = x - rx * halfWidth;
		float az = z - rz * halfWidth;
		float bx = x + rx * halfWidth;
		float bz = z + rz * halfWidth;
		int clear = color & 0x00FFFFFF;
		float mid = bottom + 24f;
		// a short bright foot, then a long fade to nothing
		v.addVertex(m, ax, bottom, az).setColor(color);
		v.addVertex(m, bx, bottom, bz).setColor(color);
		v.addVertex(m, bx, mid, bz).setColor(color);
		v.addVertex(m, ax, mid, az).setColor(color);
		v.addVertex(m, ax, mid, az).setColor(color);
		v.addVertex(m, bx, mid, bz).setColor(color);
		v.addVertex(m, bx, bottom + BEAM_HEIGHT, bz).setColor(clear);
		v.addVertex(m, ax, bottom + BEAM_HEIGHT, az).setColor(clear);
	}

	private record Label(Waypoint w, double dist, int x, int y, int w2, float alpha) {
	}

	/** HUD labels: name and distance over each waypoint. Labels that would overlap are stacked upward. */
	public static void renderLabels(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		WaypointsModule m = module();
		if (m == null || !m.labels() || !Waypoints.allowed(mc) || CameraPaths.playing()) return;
		List<Waypoint> list = new ArrayList<>(Waypoints.here(mc));
		if (list.isEmpty()) return;
		Vec3 eye = mc.player.getEyePosition();
		list.sort(Comparator.comparingDouble((Waypoint w) -> w.distanceTo(eye.x, eye.y, eye.z)));
		int gw = g.guiWidth();
		int gh = g.guiHeight();
		List<Label> placed = new ArrayList<>();
		for (Waypoint w : list) {
			if (!w.visible) continue;
			double dist = w.distanceTo(eye.x, eye.y, eye.z);
			if (m.range() > 0 && dist > m.range()) continue;
			WorldProjection.Point p = WorldProjection.toGui(w.x, w.y + 1.6, w.z, gw, gh);
			if (p == null || p.x() < -40 || p.x() > gw + 40 || p.y() < -20 || p.y() > gh + 20) continue;
			float fade = (float) Math.min(1, Math.max(0, (dist - 2) / 4));
			if (fade <= 0.02f) continue;
			// labels near the crosshair are fully shown, the rest a little quieter
			float cx = p.x() - gw / 2f;
			float cy = p.y() - gh / 2f;
			float focus = (float) Math.max(0.65, 1 - Math.sqrt(cx * cx + cy * cy) / (gw * 0.6));
			String text = w.name + (m.distance() ? "  " + formatDistance(dist) : "");
			int width = Ui.hudWidth(mc.font, text) + 14;
			int x = Math.round(p.x() - width / 2f);
			int y = Math.round(p.y() - 6);
			// nearer labels keep their spot; farther ones move up until they're clear
			for (int tries = 0; tries < 12; tries++) {
				Label hit = null;
				for (Label o : placed) {
					if (x < o.x() + o.w2() + 2 && o.x() < x + width + 2 && y < o.y() + 14 && o.y() < y + 14) {
						hit = o;
						break;
					}
				}
				if (hit == null) break;
				y = hit.y() - 15;
			}
			placed.add(new Label(w, dist, x, y, width, fade * focus));
		}
		// draw the farthest first, so the nearest is on top
		for (int i = placed.size() - 1; i >= 0; i--) {
			Label l = placed.get(i);
			int c = color(l.w());
			float a = l.alpha();
			Draw.roundRect(g, l.x(), l.y(), l.w2(), 13, 4, Theme.fade(0xC0101018, a));
			Draw.roundOutline(g, l.x(), l.y(), l.w2(), 13, 4, Theme.fade(ColorUtil.withAlpha(c, 150), a));
			Draw.circle(g, l.x() + 4, l.y() + 4, 5, Theme.fade(c, a));
			Ui.drawHud(g, mc.font, l.w().name, l.x() + 11, l.y() + 3, Theme.fade(0xFFFFFFFF, a), false);
			if (m.distance()) {
				Ui.drawHud(g, mc.font, formatDistance(l.dist()), l.x() + 11 + Ui.hudWidth(mc.font, l.w().name + "  "), l.y() + 3,
						Theme.fade(0xFFB8B8C8, a), false);
			}
		}
	}

	public static String formatDistance(double d) {
		if (d >= 10000) return String.format(Locale.ROOT, "%.1f km", d / 1000);
		return Math.round(d) + " m";
	}
}
