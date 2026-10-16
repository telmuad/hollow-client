package dev.telmuad.hollowclient.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.gui.JarTexture;
import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * The animated nebula behind the title screen: one full-screen quad drawn with Hollow's own shader
 * (shaders/core/nebula.*). The shader's inputs ride on a normal GUI blit: the texture coordinate carries the time
 * and parallax strength, the vertex color carries the mouse position, quality and brightness, and the "texture" is a
 * 4-pixel palette with the theme colors. Screen size comes from vanilla's Globals uniform block.
 */
public final class Nebula {
	public static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
			.withLocation(HollowClient.id("pipeline/nebula"))
			.withVertexShader(HollowClient.id("core/nebula"))
			.withFragmentShader(HollowClient.id("core/nebula"))
			.withColorTargetState(com.mojang.blaze3d.pipeline.ColorTargetState.DEFAULT)
			.build();

	private static final Identifier PALETTE = HollowClient.id("generated/nebula_palette");
	private static final int BACKGROUND = 0xFF05060B;
	private static NativeImage paletteImage;
	private static JarTexture.Smooth paletteTexture;
	private static final int[] lastColors = new int[4];
	private static boolean failed;

	private Nebula() {
	}

	private static void updatePalette() {
		int[] colors = {Theme.gradient(0f), Theme.gradient(1f), Theme.accent(), BACKGROUND};
		if (paletteTexture == null) {
			paletteImage = new NativeImage(4, 1, false);
			paletteTexture = new JarTexture.Smooth(PALETTE::toString, paletteImage);
			Minecraft.getInstance().getTextureManager().register(PALETTE, paletteTexture);
		}
		boolean changed = false;
		for (int i = 0; i < 4; i++) {
			if (colors[i] != lastColors[i]) {
				lastColors[i] = colors[i];
				paletteImage.setPixel(i, 0, colors[i] | 0xFF000000);
				changed = true;
			}
		}
		if (changed) paletteTexture.upload();
	}

	/**
	 * Draw the nebula over (0, 0, width, height). Mouse in GUI pixels; {@code parallax}, {@code brightness} 0..1.
	 * Returns false if the shader couldn't be used (the caller then draws the panorama instead).
	 */
	public static boolean draw(GuiGraphicsExtractor g, int width, int height, double mouseX, double mouseY, float parallax, float brightness) {
		if (failed) return false;
		try {
			// compiled once and cached by the device; false if the shader failed to compile (logged by the game)
			if (!RenderSystem.getDevice().precompilePipeline(PIPELINE).isValid()) return false;
			updatePalette();
			float time = (Util.getMillis() % 3_600_000L) / 1000f * Theme.speed();
			int mx = clampByte(mouseX / Math.max(1, width));
			int my = clampByte(mouseY / Math.max(1, height));
			float q = Quality.performanceMode() ? 0f : Quality.scale();
			int quality = clampByte(q);
			int bright = clampByte(Math.max(0.2f, brightness));
			int packed = ColorUtil.argb(bright, mx, my, quality);
			g.blit(PIPELINE, PALETTE, 0, 0, time, parallax, width, height, 0, 0, 1, 1, packed);
			return true;
		} catch (RuntimeException e) {
			failed = true;
			HollowClient.LOG.warn("Nebula background disabled: {}", e.toString());
			return false;
		}
	}

	private static int clampByte(double v) {
		return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
	}
}
