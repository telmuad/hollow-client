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
		// The emblem, mod icon and wordmark are made by branding/tools/emblem.py (see branding/).
		particles();
		moreCosmetics();
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
	/** The emblem on the brand cape, 10 x 11 pixels: '#' is crystal, '.' is the cut-out H or outside. */
	private static final String[] EMBLEM_PIXELS = {
			"....##....",
			"..######..",
			".########.",
			"##.####.##",
			"##.####.##",
			"##......##",
			"##.####.##",
			"##.####.##",
			".########.",
			"..######..",
			"....##....",
	};

	private static void capes() throws IOException {
		cape("hollow", (x, y, w, h) -> {
			int base = mix(rgb(0x14121C), rgb(0x241C3A), y / (double) h);
			// the Hollow emblem in pixels: a faceted hexagon with the H cut out (see branding/)
			int ey = y - 2;
			if (ey >= 0 && ey < EMBLEM_PIXELS.length && x < EMBLEM_PIXELS[ey].length() && EMBLEM_PIXELS[ey].charAt(x) == '#') {
				int col = mix(rgb(0x9B6CFF), rgb(0x22D3EE), x / 9.0);
				double light = ey < 3 ? 1.18 : ey > 7 ? 0.72 : 0.95;
				return shade(col, light);
			}
			if (ey >= 3 && ey <= 7 && x >= 2 && x <= 7) return mix(base, rgb(0x22D3EE), 0.18);
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

	// ------------------------------------------------------------------------------------------------ halo, horns, pet

	/** Textures for the halo / wisp (plain white, tinted at runtime), horns and the cube pet. 32x16 each. */
	private static void moreCosmetics() throws IOException {
		BufferedImage white = new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) for (int x = 0; x < 32; x++) white.setRGB(x, y, argb(255, 245 + (x + y) % 3 * 5, 245 + (x * 3 + y) % 2 * 5, 255));
		save(white, "textures/cosmetics/glow_white.png");

		// horns: ivory at the tip, darker toward the base, with faint rings
		BufferedImage horns = new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 32; x++) {
				double t = y / 15.0;
				int base = mix(rgb(0xF2E8D5), rgb(0x5A4B3C), t);
				if (y % 4 == 0) base = mix(base, rgb(0x2B2219), 0.25);
				int noise = ((x * 7 + y * 13) % 5) * 3;
				horns.setRGB(x, y, mix(base, rgb(0x000000), noise / 100.0));
			}
		}
		save(horns, "textures/cosmetics/horns.png");

		// cube pet: soft violet body with two eyes and a little shine, on the 8x8 front face at (8, 8)
		BufferedImage pet = new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 32; x++) {
				double t = (x + y) / 46.0;
				pet.setRGB(x, y, mix(rgb(0xB79BFF), rgb(0x7DD3FC), t));
			}
		}
		int fx = 8;
		int fy = 8;
		for (int[] eye : new int[][]{{fx + 1, fy + 3}, {fx + 5, fy + 3}}) {
			for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) pet.setRGB(eye[0] + dx, eye[1] + dy, rgb(0x1A1530));
			pet.setRGB(eye[0], eye[1], rgb(0xFFFFFF));
		}
		pet.setRGB(fx + 3, fy + 6, rgb(0x5B3F9E));
		pet.setRGB(fx + 4, fy + 6, rgb(0x5B3F9E));
		save(pet, "textures/cosmetics/pet_cube.png");
	}
}
