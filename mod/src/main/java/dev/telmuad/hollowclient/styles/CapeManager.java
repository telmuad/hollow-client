package dev.telmuad.hollowclient.styles;

import com.mojang.blaze3d.platform.NativeImage;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.util.FileNames;
import dev.telmuad.hollowclient.util.PngInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Built-in capes (shipped textures) and capes you import from PNG files. Imported capes are copied to
 * {@code config/hollowclient/capes/} and loaded into a texture the first time they are shown.
 * Cape ids are {@code builtin:<name>} or {@code custom:<file name>}.
 */
public final class CapeManager {
	public static final String DEFAULT = "builtin:hollow";

	public record Cape(String id, String name, boolean builtin) {
	}

	private record Loaded(Identifier texture, int width, int height) {
	}

	private static final List<Cape> BUILTIN = List.of(
			new Cape("builtin:hollow", "Hollow", true),
			new Cape("builtin:nightfall", "Nightfall", true),
			new Cape("builtin:ember", "Ember", true),
			new Cape("builtin:glacier", "Glacier", true),
			new Cape("builtin:verdant", "Verdant", true));

	private static final Map<String, Loaded> LOADED = new HashMap<>();
	private static List<Cape> cached;
	private static final Set<String> FAILED = new HashSet<>();

	private CapeManager() {
	}

	public static Path folder() {
		return HollowClient.dataDir().resolve("capes");
	}

	/** Built-in capes first, then imported ones (by file name). The folder is read once until {@link #refresh()}. */
	public static List<Cape> all() {
		if (cached == null) cached = List.copyOf(scan());
		return cached;
	}

	/** Re-read the capes folder (after an import, a removal, or when the Styles screen opens). */
	public static void refresh() {
		cached = null;
	}

	private static List<Cape> scan() {
		List<Cape> out = new ArrayList<>(BUILTIN);
		List<Cape> custom = new ArrayList<>();
		Path dir = folder();
		if (Files.isDirectory(dir)) {
			try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.png")) {
				for (Path f : files) {
					String file = f.getFileName().toString();
					custom.add(new Cape("custom:" + file, file.substring(0, file.length() - 4), false));
				}
			} catch (IOException e) {
				HollowClient.LOG.warn("Could not list imported capes", e);
			}
		}
		custom.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
		out.addAll(custom);
		return out;
	}

	public static String nameOf(String id) {
		for (Cape c : all()) if (c.id().equals(id)) return c.name();
		return "Missing cape";
	}

	/** Texture for a cape id, loading imported capes on first use. Null if it can't be shown. */
	public static Identifier texture(String id) {
		Loaded l = load(id);
		return l == null ? null : l.texture();
	}

	/** Texture size, for drawing a preview of the cape's front. */
	public static int[] size(String id) {
		Loaded l = load(id);
		return l == null ? new int[]{64, 32} : new int[]{l.width(), l.height()};
	}

	private static Loaded load(String id) {
		if (id == null) return null;
		if (id.startsWith("builtin:")) {
			String name = id.substring("builtin:".length());
			for (Cape c : BUILTIN) {
				if (c.id().equals(id)) return new Loaded(HollowClient.id("textures/capes/" + name + ".png"), 64, 32);
			}
			return null;
		}
		if (!id.startsWith("custom:")) return null;
		Loaded cached = LOADED.get(id);
		if (cached != null || FAILED.contains(id)) return cached;
		String file = id.substring("custom:".length());
		Path path = folder().resolve(file);
		if (!path.normalize().startsWith(folder().normalize()) || !Files.isRegularFile(path)) {
			FAILED.add(id);
			return null;
		}
		try (InputStream in = Files.newInputStream(path)) {
			NativeImage image = NativeImage.read(in);
			if (PngInfo.capeLayout(image.getWidth(), image.getHeight()) == PngInfo.CapeLayout.LEGACY) image = padLegacy(image);
			Identifier tex = HollowClient.id("capes/custom/" + FileNames.textureId(file));
			int w = image.getWidth();
			int h = image.getHeight();
			Minecraft.getInstance().getTextureManager().register(tex, new DynamicTexture(tex::toString, image));
			Loaded l = new Loaded(tex, w, h);
			LOADED.put(id, l);
			return l;
		} catch (IOException | RuntimeException e) {
			HollowClient.LOG.warn("Could not load cape {}", file, e);
			FAILED.add(id);
			return null;
		}
	}

	/** Old 22x17 capes: put the image in the top-left of a 64x32 (scaled) canvas, which is where that layout lives. */
	private static NativeImage padLegacy(NativeImage src) {
		int s = src.getWidth() / 22;
		NativeImage out = new NativeImage(64 * s, 32 * s, true);
		for (int y = 0; y < src.getHeight(); y++) {
			for (int x = 0; x < src.getWidth(); x++) out.setPixel(x, y, src.getPixel(x, y));
		}
		src.close();
		return out;
	}

	/**
	 * Copy a PNG into the capes folder after checking it is a usable cape image. Returns the new cape id.
	 *
	 * @throws PackImporter.ImportException with a friendly message when the file can't be used
	 */
	public static String importCape(Path source) throws PackImporter.ImportException {
		if (source == null || !Files.isRegularFile(source)) throw new PackImporter.ImportException("That file doesn't exist anymore.");
		String problem;
		try {
			problem = PngInfo.capeProblem(PngInfo.read(source));
		} catch (IOException e) {
			throw new PackImporter.ImportException("Couldn't read that file: " + e.getMessage());
		}
		if (problem != null) throw new PackImporter.ImportException(problem);
		try {
			Files.createDirectories(folder());
			String name = FileNames.safeFileName(source.getFileName().toString());
			if (!name.toLowerCase(Locale.ROOT).endsWith(".png")) name = name + ".png";
			Path target = FileNames.unique(folder(), name);
			Files.copy(source, target);
			String id = "custom:" + target.getFileName();
			FAILED.remove(id);
			refresh();
			return id;
		} catch (IOException e) {
			throw new PackImporter.ImportException("Couldn't copy the cape: " + e.getMessage());
		}
	}

	/** Delete an imported cape (built-in ones can't be removed). */
	public static void remove(String id) {
		if (id == null || !id.startsWith("custom:")) return;
		Loaded l = LOADED.remove(id);
		if (l != null) Minecraft.getInstance().getTextureManager().release(l.texture());
		Path path = folder().resolve(id.substring("custom:".length()));
		try {
			if (path.normalize().startsWith(folder().normalize())) Files.deleteIfExists(path);
		} catch (IOException e) {
			HollowClient.LOG.warn("Could not delete cape {}", path, e);
		}
		refresh();
	}
}
