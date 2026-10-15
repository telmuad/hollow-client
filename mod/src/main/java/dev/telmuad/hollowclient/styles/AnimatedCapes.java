package dev.telmuad.hollowclient.styles;

import com.mojang.blaze3d.platform.NativeImage;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Moving capes: the built-in "Void Flow" cape (drawn from the theme colors every few frames), imported animated GIFs
 * and PNG strips (frames stacked top to bottom), and the shimmer overlay that can go over any cape. Each is one
 * dynamic texture whose pixels are replaced when the frame changes, so the cape model needs nothing special.
 */
public final class AnimatedCapes {
	public static final int MAX_FRAMES = 128;
	private static final long FLOW_FRAME_MS = 50;

	/** One animated cape: its texture, the frames and how long each shows. */
	private static final class Animation {
		final DynamicTexture texture;
		final NativeImage[] frames;
		final int[] delays;
		final int total;
		int shown = -1;

		Animation(DynamicTexture texture, NativeImage[] frames, int[] delays) {
			this.texture = texture;
			this.frames = frames;
			this.delays = delays;
			int sum = 0;
			for (int d : delays) sum += d;
			this.total = Math.max(1, sum);
		}

		void update() {
			long t = Util.getMillis() % total;
			int i = 0;
			for (; i < delays.length - 1; i++) {
				t -= delays[i];
				if (t < 0) break;
			}
			if (i == shown) return;
			shown = i;
			NativeImage target = texture.getPixels();
			if (target != null) {
				target.copyFrom(frames[i]);
				texture.upload();
			}
		}
	}

	private static final Map<Identifier, Animation> ANIMATIONS = new HashMap<>();
	private static final Identifier FLOW = HollowClient.id("capes/void_flow");
	private static final Identifier SHIMMER = HollowClient.id("capes/shimmer");
	private static DynamicTexture flow;
	private static DynamicTexture shimmer;
	private static long flowDrawn;
	private static long shimmerDrawn;

	private AnimatedCapes() {
	}

	// ------------------------------------------------------------------------------------------------ frames from files

	/** A decoded animation: frames (all the same size) and per-frame delays in ms. */
	public record Frames(List<BufferedImage> images, List<Integer> delays) {
	}

	/**
	 * Read the frames of a GIF (composited the way browsers do: offsets, and "restore to background" disposal) or of a
	 * PNG strip whose height is a whole number of 2:1 frames. A plain 2:1 PNG gives one frame.
	 */
	public static Frames read(Path file) throws IOException {
		String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
		if (name.endsWith(".gif")) return readGif(file);
		BufferedImage img = ImageIO.read(file.toFile());
		if (img == null) throw new IOException("Not an image");
		int w = img.getWidth();
		int frameH = w / 2;
		List<BufferedImage> frames = new ArrayList<>();
		List<Integer> delays = new ArrayList<>();
		if (isStrip(w, img.getHeight())) {
			int n = Math.min(MAX_FRAMES, img.getHeight() / frameH);
			for (int i = 0; i < n; i++) {
				frames.add(img.getSubimage(0, i * frameH, w, frameH));
				delays.add(100);
			}
		} else {
			frames.add(img);
			delays.add(1000);
		}
		return new Frames(frames, delays);
	}

	private static Frames readGif(Path file) throws IOException {
		try (ImageInputStream in = ImageIO.createImageInputStream(Files.newInputStream(file))) {
			Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
			if (!readers.hasNext()) throw new IOException("This Java has no GIF reader");
			ImageReader reader = readers.next();
			reader.setInput(in, false);
			int count = Math.min(MAX_FRAMES, reader.getNumImages(true));
			List<BufferedImage> frames = new ArrayList<>();
			List<Integer> delays = new ArrayList<>();
			BufferedImage canvas = null;
			for (int i = 0; i < count; i++) {
				BufferedImage frame = reader.read(i);
				IIOMetadata meta = reader.getImageMetadata(i);
				Node root = meta.getAsTree("javax_imageio_gif_image_1.0");
				int x = 0;
				int y = 0;
				int delay = 100;
				String disposal = "none";
				for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
					NamedNodeMap at = n.getAttributes();
					if ("ImageDescriptor".equals(n.getNodeName())) {
						x = Integer.parseInt(at.getNamedItem("imageLeftPosition").getNodeValue());
						y = Integer.parseInt(at.getNamedItem("imageTopPosition").getNodeValue());
					} else if ("GraphicControlExtension".equals(n.getNodeName())) {
						delay = Integer.parseInt(at.getNamedItem("delayTime").getNodeValue()) * 10;
						disposal = at.getNamedItem("disposalMethod").getNodeValue();
					}
				}
				if (canvas == null) {
					int cw = Math.max(reader.getWidth(0), x + frame.getWidth());
					int ch = Math.max(reader.getHeight(0), y + frame.getHeight());
					canvas = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
				}
				Graphics2D g = canvas.createGraphics();
				g.drawImage(frame, x, y, null);
				g.dispose();
				BufferedImage copy = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
				Graphics2D cg = copy.createGraphics();
				cg.drawImage(canvas, 0, 0, null);
				cg.dispose();
				frames.add(copy);
				// browsers treat very short delays as 100 ms; so do we
				delays.add(delay < 20 ? 100 : delay);
				if ("restoreToBackgroundColor".equals(disposal)) {
					Graphics2D clear = canvas.createGraphics();
					clear.setComposite(java.awt.AlphaComposite.Clear);
					clear.fillRect(x, y, frame.getWidth(), frame.getHeight());
					clear.dispose();
				}
			}
			reader.dispose();
			if (frames.isEmpty()) throw new IOException("The GIF has no frames");
			return new Frames(frames, delays);
		}
	}

	public static NativeImage toNative(BufferedImage img) {
		NativeImage out = new NativeImage(img.getWidth(), img.getHeight(), true);
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) out.setPixel(x, y, img.getRGB(x, y));
		}
		return out;
	}

	/**
	 * Whether a PNG is a strip of cape frames stacked top to bottom: at least 3 frames of 2:1. (Two frames would be a
	 * square, which is far more likely a skin uploaded by mistake.)
	 */
	public static boolean isStrip(int width, int height) {
		int frameH = width / 2;
		return frameH > 0 && height >= frameH * 3 && height % frameH == 0;
	}

	/** Register an animated cape texture. Frames must be the same size. */
	public static void register(Identifier id, List<NativeImage> frames, List<Integer> delays) {
		NativeImage first = new NativeImage(frames.get(0).getWidth(), frames.get(0).getHeight(), true);
		first.copyFrom(frames.get(0));
		DynamicTexture texture = new DynamicTexture(id::toString, first);
		Minecraft.getInstance().getTextureManager().register(id, texture);
		int[] d = delays.stream().mapToInt(Integer::intValue).toArray();
		ANIMATIONS.put(id, new Animation(texture, frames.toArray(NativeImage[]::new), d));
	}

	/** Bring a cape texture up to date before it is drawn (cheap when the frame didn't change). */
	public static void update(Identifier texture) {
		if (texture == null) return;
		if (texture.equals(FLOW)) {
			flowTexture();
			return;
		}
		Animation a = ANIMATIONS.get(texture);
		if (a != null) a.update();
	}

	// ------------------------------------------------------------------------------------------------ Void Flow

	/** The built-in animated cape: theme-gradient bands flowing down, with twinkling specks. 128x64 (2x the cape layout). */
	public static Identifier flowTexture() {
		long now = Util.getMillis();
		if (flow == null) {
			flow = new DynamicTexture(FLOW::toString, new NativeImage(128, 64, true));
			Minecraft.getInstance().getTextureManager().register(FLOW, flow);
		}
		if (now - flowDrawn >= FLOW_FRAME_MS) {
			flowDrawn = now;
			drawFlow(flow.getPixels(), now);
			flow.upload();
		}
		return FLOW;
	}

	private static void drawFlow(NativeImage img, long now) {
		double t = now / 1000.0 * Theme.speed();
		int dark = 0xFF07070F;
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				double band = Math.sin(y * 0.18 - t * 2.2 + Math.sin(x * 0.11 + t) * 1.4);
				double k = Math.max(0, band) * 0.85;
				int grad = Theme.gradient((float) ((y / 64.0 + t * 0.05) % 1.0));
				int c = ColorUtil.mix(dark, grad, (float) (0.25 + k * 0.75));
				// twinkling specks
				long h = (x * 73856093L) ^ (y * 19349663L);
				if ((h & 127) == 3) {
					float tw = (float) (0.5 + 0.5 * Math.sin(t * 4 + (h & 31)));
					c = ColorUtil.mix(c, 0xFFFFFFFF, 0.7f * tw);
				}
				img.setPixel(x, y, c | 0xFF000000);
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ shimmer

	/** A see-through moving gradient sheen drawn over a cape (alpha = strength). */
	public static Identifier shimmerTexture(float strength) {
		long now = Util.getMillis();
		if (shimmer == null) {
			shimmer = new DynamicTexture(SHIMMER::toString, new NativeImage(64, 32, true));
			Minecraft.getInstance().getTextureManager().register(SHIMMER, shimmer);
		}
		if (now - shimmerDrawn >= 40) {
			shimmerDrawn = now;
			NativeImage img = shimmer.getPixels();
			double t = now / 1000.0 * Theme.speed();
			for (int y = 0; y < img.getHeight(); y++) {
				for (int x = 0; x < img.getWidth(); x++) {
					double d = (x + y * 1.6) * 0.12 - t * 2.5;
					double s = Math.pow(Math.max(0, Math.sin(d)), 6);
					int a = (int) Math.round(255 * Math.min(1, strength) * (0.18 + 0.82 * s) * 0.6);
					int c = Theme.gradient((float) ((x / 64.0 + t * 0.1) % 1.0));
					img.setPixel(x, y, ColorUtil.withAlpha(c, a));
				}
			}
			shimmer.upload();
		}
		return SHIMMER;
	}
}
