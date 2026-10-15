package dev.telmuad.hollowclient.camera;

import dev.telmuad.hollowclient.gui.Draw;
import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.gui.Toasts;
import dev.telmuad.hollowclient.gui.Ui;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.visual.CameraPathModule;
import dev.telmuad.hollowclient.render.WorldProjection;
import dev.telmuad.hollowclient.util.ColorUtil;
import dev.telmuad.hollowclient.waypoints.Waypoints;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Singleplayer cinematic camera paths: place points where the camera is, then play a smooth flight through them.
 * Only the picture moves (your player stays where it is, and nothing is sent to the server), and it only works in
 * your own singleplayer worlds. Moving, or opening any screen, stops the flight.
 */
public final class CameraPaths {
	private static CameraPath path;
	private static boolean playing;
	private static long startNanos;
	private static CameraType savedCamera;
	private static boolean savedHideGui;

	private CameraPaths() {
	}

	private static CameraPathModule module() {
		CameraPathModule m = Modules.CAMERA_PATH;
		return m != null && m.isEnabled() ? m : null;
	}

	public static List<CameraPath.Key> keys() {
		return Waypoints.data().cameraPath;
	}

	public static boolean playing() {
		return playing;
	}

	private static void changed() {
		path = null;
		Waypoints.changed();
	}

	private static boolean check(Minecraft mc) {
		if (module() == null) {
			Toasts.info("Camera Paths is off", "Switch it on in the Hollow menu → Visual.");
			return false;
		}
		if (!Waypoints.allowed(mc)) {
			Toasts.info("Camera paths are singleplayer only", "They work in your own worlds, never on servers.");
			return false;
		}
		return true;
	}

	/** Add a point at the camera's current position and direction. */
	public static void addPoint(Minecraft mc) {
		if (playing || !check(mc)) return;
		var cam = mc.gameRenderer.getMainCamera();
		Vec3 p = cam.position();
		keys().add(new CameraPath.Key(p.x, p.y, p.z, cam.yRot(), cam.xRot()));
		changed();
		Toasts.success("Camera point " + keys().size() + " added", keys().size() < 2 ? "Add at least one more to play the path." : "Press Play to fly the path.");
	}

	public static void removeLast() {
		if (playing || keys().isEmpty()) return;
		keys().remove(keys().size() - 1);
		changed();
	}

	public static void clear() {
		if (playing) return;
		keys().clear();
		changed();
	}

	public static void toggle(Minecraft mc) {
		if (playing) stop(mc);
		else start(mc);
	}

	public static void start(Minecraft mc) {
		if (playing || !check(mc)) return;
		if (keys().size() < 2) {
			Toasts.info("Add at least two camera points", "Stand where the shot should start, add a point, move, add another.");
			return;
		}
		path = new CameraPath(keys());
		savedCamera = mc.options.getCameraType();
		savedHideGui = mc.options.hideGui;
		// third person so your own player is drawn while the camera flies away from it
		mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		if (module().hideHud()) mc.options.hideGui = true;
		if (mc.screen != null) mc.setScreen(null);
		startNanos = System.nanoTime();
		playing = true;
	}

	public static void stop(Minecraft mc) {
		if (!playing) return;
		playing = false;
		if (savedCamera != null) mc.options.setCameraType(savedCamera);
		mc.options.hideGui = savedHideGui;
	}

	/** Progress 0..1 of the current flight (looping paths wrap around). */
	public static double progress() {
		CameraPathModule m = module();
		if (!playing || m == null) return 0;
		double seconds = (System.nanoTime() - startNanos) / 1e9;
		double t = seconds / m.duration();
		if (m.loop()) t -= Math.floor(t);
		return Math.min(1, t);
	}

	/** Every client tick: stop when the flight ends, you move, a screen opens or you leave the world. */
	public static void tick(Minecraft mc) {
		if (!playing) return;
		CameraPathModule m = module();
		boolean moved = mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown()
				|| mc.options.keyRight.isDown() || mc.options.keyJump.isDown() || mc.options.keyShift.isDown();
		if (m == null || !Waypoints.allowed(mc) || mc.screen != null || moved || (!m.loop() && progress() >= 1)) stop(mc);
	}

	/** The camera pose to use this frame, or null when not playing. */
	public static CameraPath.Pose pose() {
		CameraPathModule m = module();
		if (!playing || path == null || m == null) return null;
		double t = progress();
		return path.sample(m.ease() && !m.loop() ? CameraPath.ease(t) : t);
	}

	/** While editing: the path and its numbered points, drawn over the world. */
	public static void renderPreview(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		CameraPathModule m = module();
		if (m == null || playing || !m.preview() || keys().isEmpty() || !Waypoints.allowed(mc)) return;
		if (path == null) path = new CameraPath(keys());
		int gw = g.guiWidth();
		int gh = g.guiHeight();
		int samples = Math.max(2, Math.min(240, (int) (path.length() * 2)));
		for (int i = 0; i <= samples && path.size() > 1; i++) {
			CameraPath.Pose p = path.sample((double) i / samples);
			WorldProjection.Point s = WorldProjection.toGui(p.x(), p.y(), p.z(), gw, gh);
			if (s == null) continue;
			int c = Theme.gradient((float) i / samples);
			Draw.circle(g, Math.round(s.x()) - 1, Math.round(s.y()) - 1, 3, ColorUtil.withAlpha(c, 200));
		}
		List<CameraPath.Key> ks = keys();
		for (int i = 0; i < ks.size(); i++) {
			CameraPath.Key k = ks.get(i);
			WorldProjection.Point s = WorldProjection.toGui(k.x(), k.y(), k.z(), gw, gh);
			if (s == null) continue;
			String label = String.valueOf(i + 1);
			int w = Math.max(11, Ui.hudWidth(mc.font, label) + 6);
			int x = Math.round(s.x()) - w / 2;
			int y = Math.round(s.y()) - 6;
			Draw.roundRect(g, x, y, w, 12, 6, 0xE0101018);
			Draw.roundOutline(g, x, y, w, 12, 6, Theme.accent());
			Ui.drawHud(g, mc.font, label, x + (w - Ui.hudWidth(mc.font, label)) / 2 + 1, y + 2, 0xFFFFFFFF, false);
		}
	}
}
