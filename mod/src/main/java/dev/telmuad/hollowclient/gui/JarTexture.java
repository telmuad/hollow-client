package dev.telmuad.hollowclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.telmuad.hollowclient.HollowClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A texture read straight from Hollow's jar (not through resource packs), so it can be drawn before resources have
 * loaded (the loading screen) and can't be replaced by a pack. Half-size copies are made at load time and the closest
 * one is picked for the size on screen, with smooth filtering, so large art stays clean when drawn small.
 */
public final class JarTexture {
	private static final int MIN_LEVEL = 8;

	/** A dynamic texture with smooth (linear) filtering and clamped edges. */
	public static final class Smooth extends DynamicTexture {
		public Smooth(Supplier<String> label, NativeImage image) {
			super(label, image);
			this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		}
	}

	private final String path;
	private final String name;
	private Identifier[] levels;
	private int[] widths;
	private int[] heights;
	private boolean failed;

	public JarTexture(String path, String name) {
		this.path = path;
		this.name = name;
	}

	private boolean ensure() {
		if (levels != null) return true;
		if (failed) return false;
		try (InputStream in = JarTexture.class.getResourceAsStream("/assets/" + HollowClient.MOD_ID + "/" + path)) {
			if (in == null) {
				failed = true;
				HollowClient.LOG.warn("Missing texture in the jar: {}", path);
				return false;
			}
			List<NativeImage> chain = new ArrayList<>();
			chain.add(NativeImage.read(in));
			while (chain.size() < 7) {
				NativeImage last = chain.get(chain.size() - 1);
				if (Math.min(last.getWidth(), last.getHeight()) / 2 < MIN_LEVEL) break;
				chain.add(half(last));
			}
			levels = new Identifier[chain.size()];
			widths = new int[chain.size()];
			heights = new int[chain.size()];
			var tm = Minecraft.getInstance().getTextureManager();
			for (int i = 0; i < chain.size(); i++) {
				NativeImage img = chain.get(i);
				Identifier id = HollowClient.id("generated/" + name + "_" + i);
				widths[i] = img.getWidth();
				heights[i] = img.getHeight();
				tm.register(id, new Smooth(id::toString, img));
				levels[i] = id;
			}
			return true;
		} catch (IOException | RuntimeException e) {
			failed = true;
			HollowClient.LOG.warn("Couldn't load {}", path, e);
			return false;
		}
	}

	/** Box-filtered half-size copy (premultiplied so transparent edges don't turn dark). */
	private static NativeImage half(NativeImage src) {
		int w = Math.max(1, src.getWidth() / 2);
		int h = Math.max(1, src.getHeight() / 2);
		NativeImage out = new NativeImage(w, h, true);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				long a = 0;
				long r = 0;
				long gr = 0;
				long b = 0;
				for (int dy = 0; dy < 2; dy++) {
					for (int dx = 0; dx < 2; dx++) {
						int p = src.getPixel(Math.min(src.getWidth() - 1, x * 2 + dx), Math.min(src.getHeight() - 1, y * 2 + dy));
						int pa = (p >>> 24) & 255;
						a += pa;
						r += (long) ((p >>> 16) & 255) * pa;
						gr += (long) ((p >>> 8) & 255) * pa;
						b += (long) (p & 255) * pa;
					}
				}
				int oa = (int) (a / 4);
				int or = a == 0 ? 0 : (int) (r / a);
				int og = a == 0 ? 0 : (int) (gr / a);
				int ob = a == 0 ? 0 : (int) (b / a);
				out.setPixel(x, y, (oa << 24) | (or << 16) | (og << 8) | ob);
			}
		}
		return out;
	}

	public boolean available() {
		return ensure();
	}

	/** Width / height of the full-size image (1 if it failed to load). */
	public float aspect() {
		if (!ensure()) return 1f;
		return widths[0] / (float) heights[0];
	}

	private int levelFor(float guiWidth) {
		double scale = Minecraft.getInstance().getWindow().getGuiScale();
		double px = guiWidth * scale * 1.2;
		int pick = 0;
		for (int i = 0; i < levels.length; i++) if (widths[i] >= px) pick = i;
		return pick;
	}

	/** Draw the whole image stretched to (x, y, w, h) in GUI pixels; float positions move smoothly. */
	public void draw(GuiGraphicsExtractor g, float x, float y, float w, float h, int color) {
		drawSlice(g, x, y, w, h, 0f, 1f, color);
	}

	/** Draw a vertical slice of the image (u0..u1 as fractions of its width) into (x, y, w, h). */
	public void drawSlice(GuiGraphicsExtractor g, float x, float y, float w, float h, float u0, float u1, int color) {
		if (w <= 0 || h <= 0 || (color >>> 24) == 0 || !ensure()) return;
		float fullW = w / Math.max(0.0001f, u1 - u0);
		int lv = levelFor(fullW);
		int tw = widths[lv];
		int th = heights[lv];
		int a = Math.round(u0 * tw);
		int b = Math.max(a + 1, Math.round(u1 * tw));
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(w / (b - a), h / th);
		g.blit(RenderPipelines.GUI_TEXTURED, levels[lv], 0, 0, a, 0f, b - a, th, b - a, th, tw, th, color);
		g.pose().popMatrix();
	}
}
