package dev.telmuad.hollowclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Soft boxes around entities that SkyBlock features point at (your slayer boss). They're drawn in the world with the
 * depth test, like the block outline's fill, so walls and blocks hide them: nothing is shown through walls.
 */
public final class EntityHighlights {
	public record Box(AABB box, int color) {
	}

	private static final List<Box> BOXES = new CopyOnWriteArrayList<>();

	private EntityHighlights() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(EntityHighlights::render);
	}

	private static final java.util.Map<String, List<Box>> OWNED = new java.util.concurrent.ConcurrentHashMap<>();

	/** The boxes one feature wants drawn (it sets them again whenever they change; empty clears them). */
	public static void set(String owner, List<Box> boxes) {
		if (boxes.isEmpty()) OWNED.remove(owner);
		else OWNED.put(owner, List.copyOf(boxes));
		List<Box> all = new java.util.ArrayList<>();
		OWNED.values().forEach(all::addAll);
		BOXES.clear();
		BOXES.addAll(all);
	}

	/** Every box being drawn right now. */
	public static List<Box> boxes() {
		return List.copyOf(BOXES);
	}

	private static void render(LevelRenderContext context) {
		if (BOXES.isEmpty()) return;
		PoseStack pose = context.poseStack();
		if (pose == null) return;
		Vec3 cam = WorldProjection.camera();
		context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugQuads(), (at, v) -> {
			Matrix4f m = at.pose();
			for (Box b : BOXES) {
				AABB a = b.box().move(-cam.x, -cam.y, -cam.z);
				int c = b.color();
				float x0 = (float) a.minX, y0 = (float) a.minY, z0 = (float) a.minZ, x1 = (float) a.maxX, y1 = (float) a.maxY, z1 = (float) a.maxZ;
				int side = ColorUtil.fade(c, 0.6f);
				// six faces, each a quad
				quad(v, m, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, side);
				quad(v, m, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, side);
				quad(v, m, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, side);
				quad(v, m, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, side);
				quad(v, m, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, c);
				quad(v, m, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, c);
			}
		});
	}

	private static void quad(com.mojang.blaze3d.vertex.VertexConsumer v, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
							 float cx, float cy, float cz, float dx, float dy, float dz, int color) {
		v.addVertex(m, ax, ay, az).setColor(color);
		v.addVertex(m, bx, by, bz).setColor(color);
		v.addVertex(m, cx, cy, cz).setColor(color);
		v.addVertex(m, dx, dy, dz).setColor(color);
	}
}
