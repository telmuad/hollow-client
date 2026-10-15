import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Generates every placeholder texture Hollow Client ships: built-in capes, hat and wing textures and the mod icon.
 * All art is original and drawn here in code (fixed random seed, so the output is the same every run).
 * <p>
 * Run from the mod folder:  java tools/texturegen/HollowTextureGen.java src/main/resources/assets/hollowclient
 * Re-running overwrites the PNGs, so don't run it after hand-editing them.
 */
public final class HollowTextureGen {
	private static File root;

	public static void main(String[] args) throws IOException {
		if (args.length < 1) {
			System.err.println("usage: java HollowTextureGen.java <assets/hollowclient folder>");
			System.exit(1);
		}
		root = new File(args[0]);
		if (!new File(root, "lang").isDirectory()) {
			System.err.println("That doesn't look like assets/hollowclient (no lang folder): " + root);
			System.exit(1);
		}
		capes();
		hats();
		wings();
		icon();
		wordmark();
		particles();
		System.out.println("Textures written to " + root.getAbsolutePath());
	}

	// ------------------------------------------------------------------------------------------------ helpers

	private static int argb(int a, int r, int g, int b) {
		return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	private static int rgb(int hex) {
		return 0xFF000000 | hex;
	}

	private static int mix(int a, int b, double t) {
		t = Math.max(0, Math.min(1, t));
		int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, aa = (a >>> 24);
		int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255, ba = (b >>> 24);
		return argb((int) Math.round(aa + (ba - aa) * t), (int) Math.round(ar + (br - ar) * t),
				(int) Math.round(ag + (bg - ag) * t), (int) Math.round(ab + (bb - ab) * t));
	}

	private static int shade(int c, double f) {
		int r = (int) Math.round(((c >> 16) & 255) * f);
		int g = (int) Math.round(((c >> 8) & 255) * f);
		int b = (int) Math.round((c & 255) * f);
		return argb(c >>> 24, r, g, b);
	}

	private static void save(BufferedImage img, String path) throws IOException {
		File f = new File(root, path);
		f.getParentFile().mkdirs();
		ImageIO.write(img, "png", f);
	}

	private interface Painter {
		int color(int x, int y, int w, int h);
	}

	private static void paint(BufferedImage img, int x0, int y0, int w, int h, Painter p) {
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) img.setRGB(x0 + x, y0 + y, p.color(x, y, w, h));
		}
	}

	// ------------------------------------------------------------------------------------------------ capes

	/**
	 * Vanilla cape layout on a 64x32 sheet: the cape box is 10x16x1 at UV (0,0) (outer face at 1,1; inner face at
	 * 12,1), the elytra box is 10x20x2 at UV (22,0).
	 */
	private static void capes() throws IOException {
		cape("hollow", (x, y, w, h) -> {
			int base = mix(rgb(0x14121C), rgb(0x241C3A), y / (double) h);
			double cx = w / 2.0 - 0.5, cy = h * 0.42;
			double d = Math.hypot((x - cx) * 1.0, (y - cy) * 1.0);
			if (d > 2.4 && d < 3.9) return rgb(0x8B6CFF);
			if (d <= 1.0) return rgb(0xC9B8FF);
			if (y == h - 1 || y == h - 2) return rgb(0x8B6CFF);
			return base;
		});
		Random stars = new Random(7);
		boolean[][] starMap = new boolean[64][32];
		for (int i = 0; i < 40; i++) starMap[stars.nextInt(64)][stars.nextInt(32)] = true;
		cape("nightfall", (x, y, w, h) -> {
			int base = mix(rgb(0x0B1030), rgb(0x2B1F5C), y / (double) h);
			if (starMap[(x * 5 + y * 3) % 64][(y * 7 + x) % 32]) return mix(base, rgb(0xFFF6C8), 0.85);
			if (y > h * 0.8 && (x + y) % 4 == 0) return mix(base, rgb(0x5B47B0), 0.5);
			return base;
		});
		cape("ember", (x, y, w, h) -> {
			double t = y / (double) h;
			int base = mix(rgb(0x3A0E08), rgb(0xE2531C), t);
			double flame = Math.sin(x * 1.3 + y * 0.35) * 0.5 + 0.5;
			if (t > 0.45 && flame > 0.72) return mix(base, rgb(0xFFC24A), (t - 0.45) * 1.6);
			if (y == 0 || x == 0 || x == w - 1) return shade(base, 0.7);
			return base;
		});
		cape("glacier", (x, y, w, h) -> {
			int base = mix(rgb(0xDDF3FF), rgb(0x3D8FD8), y / (double) h);
			boolean crack = ((x * 3 + y * 2) % 11 == 0) || ((x * 2 - y + 40) % 13 == 0);
			if (crack) return mix(base, rgb(0xFFFFFF), 0.7);
			return base;
		});
		cape("verdant", (x, y, w, h) -> {
			int base = mix(rgb(0x163A1E), rgb(0x2E7A3A), y / (double) h);
			boolean vein = (x == w / 2) || (Math.abs((x - w / 2.0)) == ((y % 6) * 0.8 + 1) && y % 6 < 4);
			if (vein) return rgb(0x8EDB6A);
			if ((x * 7 + y * 13) % 17 == 0) return shade(base, 1.25);
			return base;
		});
	}

	private static void cape(String name, Painter design) throws IOException {
		BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		// outer face (seen from behind), inner face, then the thin edges
		paint(img, 1, 1, 10, 16, design);
		paint(img, 12, 1, 10, 16, (x, y, w, h) -> shade(design.color(w - 1 - x, y, w, h), 0.72));
		int edge = shade(design.color(0, 8, 10, 16), 0.6);
		paint(img, 1, 0, 10, 1, (x, y, w, h) -> edge);
		paint(img, 11, 0, 10, 1, (x, y, w, h) -> edge);
		paint(img, 0, 1, 1, 16, (x, y, w, h) -> edge);
		paint(img, 11, 1, 1, 16, (x, y, w, h) -> edge);
		// elytra: the whole 24x22 block, stretched from the same design
		paint(img, 22, 0, 24, 22, (x, y, w, h) -> design.color((x * 10) / w, (y * 16) / h, 10, 16));
		save(img, "textures/capes/" + name + ".png");
	}

	// ------------------------------------------------------------------------------------------------ hats

	/** 64x32: rows 0-15 are the main material, rows 16-31 the accent (bands, pom-poms, trims). */
	private static void hats() throws IOException {
		hat("top_hat", rgb(0x1C1C22), rgb(0x8B6CFF), 7, false);
		hat("wizard_hat", rgb(0x24306E), rgb(0xE8C24A), 11, true);
		hat("beanie", rgb(0xB8323A), rgb(0xF2EEE8), 13, false);
	}

	private static void hat(String name, int main, int accent, long seed, boolean stars) throws IOException {
		BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		Random r = new Random(seed);
		paint(img, 0, 0, 64, 16, (x, y, w, h) -> {
			double n = 0.9 + r.nextDouble() * 0.18;
			int c = shade(main, n);
			if (name.equals("beanie") && y % 3 == 0) c = shade(main, 0.82);
			if (stars && r.nextInt(23) == 0) c = mix(c, rgb(0xFFF3B0), 0.9);
			return c;
		});
		paint(img, 0, 16, 64, 16, (x, y, w, h) -> shade(accent, 0.92 + r.nextDouble() * 0.12));
		save(img, "textures/cosmetics/" + name + ".png");
	}

	// ------------------------------------------------------------------------------------------------ wings

	/**
	 * Wing box: 14x16x1 at UV (0,0). Front face at (1,1), back face at (16,1), both 14x16. The silhouette is a
	 * feathered wing whose root is at the face's left edge (x = 0), top-left = shoulder.
	 */
	private static void wings() throws IOException {
		wing("shadow", rgb(0x1A1426), rgb(0x5B3E99), rgb(0x9C7BFF));
		wing("angel", rgb(0xF4F1EA), rgb(0xD9D2C2), rgb(0xF2CF6B));
		wing("ember", rgb(0x5A1408), rgb(0xE2531C), rgb(0xFFD15A));
	}

	private static boolean inWing(int x, int y) {
		// Leading edge along the top, feather tips forming a scalloped lower edge that rises toward the tip.
		if (y < 1) return x < 12;
		double reach = 13.5 - y * 0.55;
		if (x > reach) return false;
		int feather = x / 3;
		double tipY = 15.5 - feather * 2.2 - (x % 3) * 0.6;
		return y <= tipY;
	}

	private static void wing(String name, int dark, int mid, int light) throws IOException {
		BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		Painter face = (x, y, w, h) -> {
			if (!inWing(x, y)) return 0;
			if (y <= 1) return light;
			if (x % 3 == 2) return shade(mid, 0.8);
			double t = y / 16.0;
			return mix(mid, dark, t * 0.9);
		};
		paint(img, 1, 1, 14, 16, face);
		paint(img, 16, 1, 14, 16, (x, y, w, h) -> {
			int c = face.color(w - 1 - x, y, w, h);
			return c == 0 ? 0 : shade(c, 0.85);
		});
		// The 1-pixel edge faces follow the silhouette, so only real wing edges show (no stray lines).
		paint(img, 1, 0, 14, 1, (x, y, w, h) -> inWing(x, 0) ? light : 0);
		paint(img, 15, 0, 14, 1, (x, y, w, h) -> inWing(x, 15) ? shade(mid, 0.8) : 0);
		paint(img, 0, 1, 1, 16, (x, y, w, h) -> inWing(0, y) ? mid : 0);
		paint(img, 15, 1, 1, 16, (x, y, w, h) -> inWing(13, y) ? mid : 0);
		save(img, "textures/cosmetics/wings_" + name + ".png");
	}

	// ------------------------------------------------------------------------------------------------ sigil + icon

	/**
	 * The Hollow sigil: a pointy-top hexagon ring with a sharp, slanted "H" inside and two orbit dots. Shape in
	 * unit coordinates (0..1), scaled to any size. The launcher draws the same shape with Qt (hollow_launcher/ui/logo.py).
	 */
	static java.awt.geom.Area sigilShape(double size) {
		java.awt.geom.Area area = new java.awt.geom.Area();
		double c = size / 2.0;
		double ro = size * 0.44;
		double ri = size * 0.385;
		area.add(new java.awt.geom.Area(hexagon(c, c, ro)));
		area.subtract(new java.awt.geom.Area(hexagon(c, c, ri)));
		// H: two slanted bars and a crossbar
		double top = size * 0.29;
		double bottom = size * 0.71;
		double barW = size * 0.085;
		double slant = size * 0.035;
		area.add(new java.awt.geom.Area(parallelogram(size * 0.335, top, barW, bottom - top, slant)));
		area.add(new java.awt.geom.Area(parallelogram(size * 0.58, top, barW, bottom - top, slant)));
		java.awt.geom.Path2D cross = new java.awt.geom.Path2D.Double();
		double cy = size * 0.5;
		double ch = size * 0.06;
		cross.moveTo(size * 0.40, cy - ch / 2 + size * 0.012);
		cross.lineTo(size * 0.60, cy - ch / 2 - size * 0.012);
		cross.lineTo(size * 0.60, cy + ch / 2 - size * 0.012);
		cross.lineTo(size * 0.40, cy + ch / 2 + size * 0.012);
		cross.closePath();
		area.add(new java.awt.geom.Area(cross));
		// orbit dots on the top and bottom points of the hexagon
		double dot = size * 0.045;
		area.add(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(c - dot, c - ro - dot * 0.2 - dot, dot * 2, dot * 2)));
		area.add(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(c - dot, c + ro + dot * 0.2 - dot, dot * 2, dot * 2)));
		return area;
	}

	private static java.awt.geom.Path2D hexagon(double cx, double cy, double r) {
		java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
		for (int i = 0; i < 6; i++) {
			double a = Math.toRadians(-90 + 60 * i);
			double x = cx + r * Math.cos(a);
			double y = cy + r * Math.sin(a);
			if (i == 0) p.moveTo(x, y);
			else p.lineTo(x, y);
		}
		p.closePath();
		return p;
	}

	private static java.awt.geom.Path2D parallelogram(double x, double y, double w, double h, double slant) {
		java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
		p.moveTo(x + slant, y);
		p.lineTo(x + w + slant, y);
		p.lineTo(x + w, y + h);
		p.lineTo(x, y + h);
		p.closePath();
		return p;
	}

	private static BufferedImage drawShape(java.awt.Shape shape, int size, int argb) {
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = img.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
		g.setColor(new Color(argb, true));
		g.fill(shape);
		g.dispose();
		return img;
	}

	/** Blur the alpha channel with three box-blur passes (close to a gaussian). White, alpha = blurred coverage. */
	private static BufferedImage glow(BufferedImage src, int radius, float strength) {
		int w = src.getWidth();
		int h = src.getHeight();
		float[] a = new float[w * h];
		for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) a[y * w + x] = ((src.getRGB(x, y) >>> 24) & 255) / 255f;
		for (int pass = 0; pass < 3; pass++) {
			a = boxBlur(a, w, h, radius, true);
			a = boxBlur(a, w, h, radius, false);
		}
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int i = 0; i < a.length; i++) {
			int alpha = clamp(Math.round(Math.min(1f, a[i] * strength) * 255));
			out.setRGB(i % w, i / w, (alpha << 24) | 0xFFFFFF);
		}
		return out;
	}

	private static float[] boxBlur(float[] in, int w, int h, int r, boolean horizontal) {
		float[] out = new float[in.length];
		int n = horizontal ? w : h;
		int lines = horizontal ? h : w;
		for (int l = 0; l < lines; l++) {
			float sum = 0;
			for (int i = -r; i <= r; i++) sum += sample(in, w, h, horizontal, l, i);
			for (int i = 0; i < n; i++) {
				out[horizontal ? l * w + i : i * w + l] = sum / (2 * r + 1);
				sum += sample(in, w, h, horizontal, l, i + r + 1) - sample(in, w, h, horizontal, l, i - r);
			}
		}
		return out;
	}

	private static float sample(float[] a, int w, int h, boolean horizontal, int line, int i) {
		if (horizontal) return i < 0 || i >= w ? 0 : a[line * w + i];
		return i < 0 || i >= h ? 0 : a[i * w + line];
	}

	private static void icon() throws IOException {
		// White sigil + its glow for the game (tinted with the theme colors at draw time)
		int s = 256;
		save(drawShape(sigilShape(s), s, 0xFFFFFFFF), "textures/gui/sigil.png");
		save(glow(drawShape(sigilShape(s), s, 0xFFFFFFFF), 10, 1.6f), "textures/gui/sigil_glow.png");

		// Colored mod icon: dark rounded square, violet-to-cyan sigil with a soft glow
		int is = 128;
		BufferedImage img = new BufferedImage(is, is, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = img.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setPaint(new java.awt.GradientPaint(0, 0, new Color(0x1B1636), is, is, new Color(0x08090E)));
		g.fill(new java.awt.geom.RoundRectangle2D.Double(0, 0, is, is, is * 0.42, is * 0.42));
		java.awt.geom.Area shape = sigilShape(is * 0.86);
		shape.transform(java.awt.geom.AffineTransform.getTranslateInstance(is * 0.07, is * 0.07));
		BufferedImage mask = drawShape(shape, is, 0xFFFFFFFF);
		BufferedImage halo = glow(mask, 4, 1.3f);
		for (int y = 0; y < is; y++) {
			for (int x = 0; x < is; x++) {
				float t = (x + y) / (2f * is);
				int col = mix(rgb(0x9B6CFF), rgb(0x22D3EE), t);
				int ha = (halo.getRGB(x, y) >>> 24) & 255;
				int base = img.getRGB(x, y);
				if ((base >>> 24) == 0) continue;
				base = mix(base, col, ha / 255f * 0.45);
				int ma = (mask.getRGB(x, y) >>> 24) & 255;
				img.setRGB(x, y, mix(base, mix(col, rgb(0xFFFFFF), 0.12), ma / 255f));
			}
		}
		g.dispose();
		save(img, "icon.png");
	}

	// ------------------------------------------------------------------------------------------------ wordmark

	/**
	 * "HOLLOW" and "CLIENT" set in Inter (SIL OFL, bundled in font/) with wide letter spacing, as white masks. Used
	 * where text must look crisp at large sizes or before fonts are loaded (loading screen, title screen).
	 */
	private static void wordmark() throws IOException {
		save(textMask("HOLLOW", new File(root, "font/inter_semibold.ttf"), 112f, 0.16f), "textures/gui/wordmark_hollow.png");
		save(textMask("CLIENT", new File(root, "font/inter_medium.ttf"), 112f, 0.30f), "textures/gui/wordmark_client.png");
	}

	private static BufferedImage textMask(String text, File fontFile, float size, float tracking) throws IOException {
		java.awt.Font font;
		try {
			font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, fontFile).deriveFont(size);
		} catch (java.awt.FontFormatException e) {
			throw new IOException("Bad font " + fontFile, e);
		}
		java.util.Map<java.awt.font.TextAttribute, Object> attrs = new java.util.HashMap<>();
		attrs.put(java.awt.font.TextAttribute.TRACKING, tracking);
		font = font.deriveFont(attrs);
		BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D pg = probe.createGraphics();
		java.awt.font.FontRenderContext frc = new java.awt.font.FontRenderContext(null, true, true);
		java.awt.font.GlyphVector gv = font.createGlyphVector(frc, text);
		java.awt.Rectangle bounds = gv.getPixelBounds(frc, 0, 0);
		pg.dispose();
		int pad = 6;
		int w = bounds.width + pad * 2;
		int h = bounds.height + pad * 2;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = img.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(java.awt.RenderingHints.KEY_FRACTIONALMETRICS, java.awt.RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setColor(java.awt.Color.WHITE);
		g.fill(gv.getOutline(pad - bounds.x, pad - bounds.y));
		g.dispose();
		return img;
	}

	// ------------------------------------------------------------------------------------------------ particles

	/** White particle sprites (tinted at runtime): a soft dot, a four-point sparkle and a footprint. 16x16 each. */
	private static void particles() throws IOException {
		int n = 16;
		BufferedImage dot = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
		BufferedImage sparkle = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
		double c = (n - 1) / 2.0;
		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) {
				double dx = x - c;
				double dy = y - c;
				double d = Math.sqrt(dx * dx + dy * dy) / (n / 2.0);
				double a = Math.max(0, 1 - d);
				dot.setRGB(x, y, argb((int) Math.round(Math.pow(a, 1.6) * 255), 255, 255, 255));
				// four thin rays plus a bright core
				double ray = Math.max(Math.exp(-Math.abs(dx) * 1.6) * Math.max(0, 1 - Math.abs(dy) / 7.5),
						Math.exp(-Math.abs(dy) * 1.6) * Math.max(0, 1 - Math.abs(dx) / 7.5));
				double core = Math.max(0, 1 - d * 2.2);
				double s = Math.min(1, ray + core);
				sparkle.setRGB(x, y, argb((int) Math.round(s * 255), 255, 255, 255));
			}
		}
		save(dot, "textures/particle/dot.png");
		save(sparkle, "textures/particle/sparkle.png");

		// a left shoe print, toes up: an oval sole and a smaller heel, soft edges
		BufferedImage print = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) {
				double px = x + 0.5;
				double py = y + 0.5;
				double sole = 1 - Math.hypot((px - 8.3) / 3.4, (py - 5.2) / 4.4);
				double heel = 1 - Math.hypot((px - 7.7) / 2.6, (py - 12.2) / 2.6);
				double v = Math.max(sole, heel);
				double a = Math.max(0, Math.min(1, v * 4));
				print.setRGB(x, y, argb((int) Math.round(a * 200), 255, 255, 255));
			}
		}
		save(print, "textures/particle/footprint.png");
	}
}
