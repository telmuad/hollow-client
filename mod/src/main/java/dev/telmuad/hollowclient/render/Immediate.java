package dev.telmuad.hollowclient.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.function.Predicate;

/**
 * Draws a small mesh that changes every frame right away with a render type, through the Blaze3D API (works on
 * OpenGL and Vulkan). This replaces 1.21's Tesselator + RenderType.draw, which 26.2 removed: the vertices go into a
 * staged buffer like vanilla's GUI uses, get uploaded, and are drawn at once with the render type's pipeline and
 * the current model-view matrix.
 */
public final class Immediate {
	private static StagedVertexBuffer buffer;

	private Immediate() {
	}

	/**
	 * Builds the mesh with {@code build} (which returns whether it added anything) and draws it.
	 *
	 * @return whether something was drawn
	 */
	public static boolean draw(RenderType type, Predicate<VertexConsumer> build) {
		if (buffer == null) buffer = new StagedVertexBuffer(() -> "Hollow immediate draws", 1 << 16);
		StagedVertexBuffer.Draw draw = buffer.appendDraw(type.format(), type.primitiveTopology());
		boolean any = build.test(buffer.getVertexBuilder(draw));
		// always upload, even when empty, so the staging state is clean for the next draw
		buffer.upload();
		StagedVertexBuffer.ExecuteInfo info = any ? buffer.getExecuteInfo(draw) : null;
		if (info != null) type.prepare().drawFromBuffer(info);
		buffer.endFrame();
		return info != null;
	}
}
