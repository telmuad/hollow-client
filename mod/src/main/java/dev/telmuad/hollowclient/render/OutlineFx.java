package dev.telmuad.hollowclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.visual.BlockOutlineModule;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Block Outline module: the outline's color and width, and a see-through fill on the block's faces. */
public final class OutlineFx {
	private static final float GROW = 0.002f;

	private OutlineFx() {
	}

	public static BlockOutlineModule module() {
		BlockOutlineModule m = Modules.BLOCK_OUTLINE;
		return m != null && m.isEnabled() ? m : null;
	}

	private static float pulse(BlockOutlineModule m) {
		if (m.pulse() <= 0f) return 1f;
		float s = 0.5f + 0.5f * (float) Math.sin(Util.getMillis() * Theme.speed() / 380.0);
		return 1f - m.pulse() * 0.6f * s;
	}

	public static int color(BlockOutlineModule m) {
		long now = Util.getMillis();
		int c = switch (m.mode()) {
			case THEME -> ColorUtil.withAlpha(Theme.flowingGradient(0f), 0xE6);
			case CUSTOM -> m.customColor();
			case RAINBOW -> ColorUtil.withAlpha(ColorUtil.fromHsv((float) ((now / 3000.0) % 1.0), 0.7f, 1f), 0xE6);
		};
		return ColorUtil.fade(c, pulse(m));
	}

	public static float width(BlockOutlineModule m, float vanilla) {
		return vanilla * m.thickness();
	}

	/** Fill the faces of the outlined shape with the outline color, very see-through. */
	public static void fill(SubmitNodeCollector collector, PoseStack pose, BlockOutlineRenderState state, Vec3 camera) {
		BlockOutlineModule m = module();
		if (m == null || m.fill() <= 0f) return;
		int color = ColorUtil.fade(color(m), 0.45f * m.fill());
		if ((color >>> 24) == 0) return;
		BlockPos p = state.pos();
		double ox = p.getX() - camera.x;
		double oy = p.getY() - camera.y;
		double oz = p.getZ() - camera.z;
		collector.submitCustomGeometry(pose, RenderTypes.debugQuads(), (at, v) -> {
			Matrix4f mat = at.pose();
			state.shape().forAllBoxes((x0, y0, z0, x1, y1, z1) -> box(v, mat,
					(float) (x0 + ox) - GROW, (float) (y0 + oy) - GROW, (float) (z0 + oz) - GROW,
					(float) (x1 + ox) + GROW, (float) (y1 + oy) + GROW, (float) (z1 + oz) + GROW, color));
		});
	}

	private static void box(VertexConsumer v, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int c) {
		// bottom, top, north, south, west, east
		quad(v, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, c);
		quad(v, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, c);
		quad(v, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, c);
		quad(v, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, c);
		quad(v, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, c);
		quad(v, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, c);
	}

	private static void quad(VertexConsumer v, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
							 float cx, float cy, float cz, float dx, float dy, float dz, int c) {
		v.addVertex(m, ax, ay, az).setColor(c);
		v.addVertex(m, bx, by, bz).setColor(c);
		v.addVertex(m, cx, cy, cz).setColor(c);
		v.addVertex(m, dx, dy, dz).setColor(c);
	}
}
