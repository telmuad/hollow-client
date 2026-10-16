package dev.telmuad.hollowclient.render;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/**
 * Where a point of the world shows up on the screen, for HUD labels (waypoints, the camera path preview). The
 * camera's view and projection matrices are copied at the start of every world render, so labels line up with the
 * frame that was just drawn, zoom and view bobbing included.
 */
public final class WorldProjection {
	private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
	private static Vec3 camera = Vec3.ZERO;
	private static boolean valid;

	/** A projected point: GUI coordinates and the distance from the camera. */
	public record Point(float x, float y, double distance) {
	}

	private WorldProjection() {
	}

	public static void capture(Vec3 cameraPos, Matrix4fc view, Matrix4fc projection) {
		VIEW_PROJECTION.set(projection).mul(view);
		camera = cameraPos;
		valid = true;
	}

	public static Vec3 camera() {
		return camera;
	}

	/** GUI position of a world point, or null if it is behind the camera (or nothing was rendered yet). */
	public static Point toGui(double x, double y, double z, int guiWidth, int guiHeight) {
		if (!valid) return null;
		double dx = x - camera.x;
		double dy = y - camera.y;
		double dz = z - camera.z;
		Vector4f clip = VIEW_PROJECTION.transform(new Vector4f((float) dx, (float) dy, (float) dz, 1f));
		if (clip.w <= 0.05f) return null;
		float nx = clip.x / clip.w;
		float ny = clip.y / clip.w;
		return new Point((nx + 1f) * 0.5f * guiWidth, (1f - ny) * 0.5f * guiHeight, Math.sqrt(dx * dx + dy * dy + dz * dz));
	}
}
