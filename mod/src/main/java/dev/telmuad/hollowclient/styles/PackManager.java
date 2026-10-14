package dev.telmuad.hollowclient.styles;

import com.mojang.blaze3d.platform.NativeImage;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.config.StylesConfig;
import dev.telmuad.hollowclient.util.FileNames;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Texture packs imported through Hollow: copies them into the game's resourcepacks folder (see {@link PackImporter}),
 * remembers which ones Hollow imported, and switches between them. Switching keeps every other selected pack
 * (vanilla, Fabric, packs you turned on yourself) and only swaps Hollow's own.
 */
public final class PackManager {
	public record Entry(String name, boolean exists, boolean active) {
	}

	private static final Map<String, Identifier> ICONS = new HashMap<>();
	private static final Set<String> NO_ICON = new HashSet<>();
	private static volatile boolean busy;

	private PackManager() {
	}

	private static StylesConfig styles() {
		return HollowClient.styles();
	}

	public static Path folder() {
		return Minecraft.getInstance().getResourcePackDirectory();
	}

	public static boolean busy() {
		return busy;
	}

	private static String repoId(String name) {
		return "file/" + name;
	}

	/** Import on a background thread (big zips take a moment); callbacks run on the game thread. */
	public static void importAsync(Path source, Consumer<String> onDone, Consumer<String> onError) {
		if (busy) {
			onError.accept("Another pack is still importing.");
			return;
		}
		busy = true;
		Minecraft mc = Minecraft.getInstance();
		Thread t = new Thread(() -> {
			try {
				Path imported = PackImporter.importPack(source, folder());
				String name = imported.getFileName().toString();
				mc.execute(() -> {
					busy = false;
					register(name);
					onDone.accept(name);
				});
			} catch (PackImporter.ImportException e) {
				mc.execute(() -> {
					busy = false;
					onError.accept(e.getMessage());
				});
			} catch (RuntimeException e) {
				HollowClient.LOG.error("Pack import failed", e);
				mc.execute(() -> {
					busy = false;
					onError.accept("Something went wrong importing that pack: " + e.getMessage());
				});
			}
		}, "Hollow Client pack import");
		t.setDaemon(true);
		t.start();
	}

	private static void register(String name) {
		if (!styles().packs.contains(name) && styles().packs.size() < StylesConfig.MAX_PACKS) styles().packs.add(name);
		NO_ICON.remove(name);
		HollowClient.config().markDirty();
	}

	public static List<Entry> list() {
		PackRepository repo = Minecraft.getInstance().getResourcePackRepository();
		List<Entry> out = new ArrayList<>();
		for (String name : styles().packs) {
			boolean exists = Files.exists(folder().resolve(name), LinkOption.NOFOLLOW_LINKS);
			out.add(new Entry(name, exists, repo.getSelectedIds().contains(repoId(name))));
		}
		return out;
	}

	/** The Hollow pack that is switched on right now, or "" if none. */
	public static String active() {
		PackRepository repo = Minecraft.getInstance().getResourcePackRepository();
		for (String name : styles().packs) if (repo.getSelectedIds().contains(repoId(name))) return name;
		return "";
	}

	public static boolean isKnown(String name) {
		return name != null && styles().packs.contains(name) && Files.exists(folder().resolve(name), LinkOption.NOFOLLOW_LINKS);
	}

	/**
	 * Switch to {@code name} (null or "" = switch Hollow's packs off). Minecraft reloads its resources when the pack
	 * list changes (a few seconds of loading screen). Returns false if the pack can't be found.
	 */
	public static boolean use(String name) {
		Minecraft mc = Minecraft.getInstance();
		PackRepository repo = mc.getResourcePackRepository();
		repo.reload();
		boolean off = name == null || name.isEmpty();
		if (!off && repo.getPack(repoId(name)) == null) return false;
		List<String> selected = new ArrayList<>(repo.getSelectedIds());
		for (String p : styles().packs) selected.remove(repoId(p));
		if (!off) selected.add(repoId(name));
		repo.setSelected(selected);
		styles().activePack = off ? "" : name;
		HollowClient.config().markDirty();
		mc.options.updateResourcePacks(repo);
		return true;
	}

	/** Forget a pack and delete Hollow's copy of it from the resourcepacks folder. */
	public static void remove(String name) {
		if (!styles().packs.contains(name)) return;
		if (name.equals(active())) use(null);
		Path target = folder().resolve(name).normalize();
		try {
			if (target.getParent() != null && target.getParent().equals(folder().normalize()) && !Files.isSymbolicLink(target)) {
				PackImporter.deleteTree(target);
			}
		} catch (IOException e) {
			HollowClient.LOG.warn("Could not delete pack {}", target, e);
		}
		styles().packs.remove(name);
		if (name.equals(styles().activePack)) styles().activePack = "";
		Identifier icon = ICONS.remove(name);
		if (icon != null) Minecraft.getInstance().getTextureManager().release(icon);
		HollowClient.config().markDirty();
		Minecraft.getInstance().getResourcePackRepository().reload();
	}

	/** The pack's pack.png as a texture (loaded once), or null if it has none. */
	public static Identifier icon(String name) {
		Identifier cached = ICONS.get(name);
		if (cached != null || NO_ICON.contains(name)) return cached;
		NO_ICON.add(name);
		Path path = folder().resolve(name);
		try {
			NativeImage image = null;
			if (Files.isDirectory(path)) {
				Path png = path.resolve("pack.png");
				if (Files.isRegularFile(png) && Files.size(png) < 4 << 20) {
					try (InputStream in = Files.newInputStream(png)) {
						image = NativeImage.read(in);
					}
				}
			} else if (Files.isRegularFile(path)) {
				try (ZipFile zip = new ZipFile(path.toFile())) {
					ZipEntry entry = zip.getEntry("pack.png");
					if (entry != null && entry.getSize() < 4 << 20) {
						try (InputStream in = zip.getInputStream(entry)) {
							image = NativeImage.read(in);
						}
					}
				}
			}
			if (image == null) return null;
			Identifier id = HollowClient.id("pack_icons/" + FileNames.textureId(name) + "_" + Integer.toHexString(name.hashCode()));
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
			ICONS.put(name, id);
			NO_ICON.remove(name);
			return id;
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	public static void openFolder() {
		net.minecraft.util.Util.getPlatform().openPath(folder());
	}
}
