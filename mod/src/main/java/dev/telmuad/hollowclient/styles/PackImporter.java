package dev.telmuad.hollowclient.styles;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.telmuad.hollowclient.util.FileNames;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Checks and copies a resource pack (a .zip or a folder) into the game's resourcepacks folder. Plain Java so it is
 * unit tested.
 * <ul>
 *   <li>The pack needs a {@code pack.mcmeta} (valid JSON with a "pack" section) at its top level.</li>
 *   <li>If the zip or folder only wraps the real pack in one extra folder, that inner folder is imported instead.</li>
 *   <li>A normal zip is copied as is (Minecraft reads it directly). A wrapped zip is extracted with size limits and a
 *       guard against entries that try to escape the target folder.</li>
 * </ul>
 */
public final class PackImporter {
	public static final long MAX_ZIP_BYTES = 1L << 30;
	public static final int MAX_ENTRIES = 100_000;
	public static final long MAX_ENTRY_BYTES = 256L << 20;
	public static final long MAX_TOTAL_BYTES = 2L << 30;

	public enum Kind {ZIP, ZIP_NESTED, FOLDER, FOLDER_NESTED}

	/** What was found: the kind of pack and, for wrapped packs, the inner folder name. */
	public record Check(Kind kind, String inner) {
	}

	/** A problem with a friendly message for the screen. */
	public static final class ImportException extends Exception {
		public ImportException(String message) {
			super(message);
		}
	}

	private PackImporter() {
	}

	// ------------------------------------------------------------------------------------------------ checking

	public static Check check(Path source) throws ImportException {
		if (source == null || !Files.exists(source)) throw new ImportException("That file or folder doesn't exist anymore.");
		if (Files.isDirectory(source)) return checkFolder(source);
		return checkZip(source);
	}

	private static Check checkZip(Path zip) throws ImportException {
		try {
			if (Files.size(zip) > MAX_ZIP_BYTES) throw new ImportException("That pack is bigger than 1 GB, which is too big to import.");
		} catch (IOException e) {
			throw new ImportException("Couldn't read that file: " + e.getMessage());
		}
		try (ZipFile zf = new ZipFile(zip.toFile())) {
			if (zf.size() > MAX_ENTRIES) throw new ImportException("That zip has too many files (over " + MAX_ENTRIES + ").");
			ZipEntry rootMeta = zf.getEntry("pack.mcmeta");
			if (rootMeta != null && !rootMeta.isDirectory()) {
				try (InputStream in = zf.getInputStream(rootMeta)) {
					validateMcmeta(in);
				}
				return new Check(Kind.ZIP, null);
			}
			Set<String> wrapped = new HashSet<>();
			String deeper = null;
			Enumeration<? extends ZipEntry> entries = zf.entries();
			while (entries.hasMoreElements()) {
				String name = entries.nextElement().getName().replace('\\', '/');
				if (name.startsWith("__MACOSX/") || !name.endsWith("pack.mcmeta")) continue;
				String[] parts = name.split("/");
				if (parts.length == 2 && parts[1].equals("pack.mcmeta")) wrapped.add(parts[0]);
				else if (parts.length > 2 && parts[parts.length - 1].equals("pack.mcmeta")) deeper = name;
			}
			if (wrapped.size() == 1) {
				String inner = wrapped.iterator().next();
				try (InputStream in = zf.getInputStream(zf.getEntry(inner + "/pack.mcmeta"))) {
					validateMcmeta(in);
				}
				return new Check(Kind.ZIP_NESTED, inner);
			}
			if (wrapped.size() > 1) {
				throw new ImportException("That zip holds " + wrapped.size() + " packs (" + String.join(", ", wrapped)
						+ "). Unzip it and import each pack folder on its own.");
			}
			if (deeper != null) {
				throw new ImportException("The pack.mcmeta is buried too deep inside the zip (" + deeper
						+ "). Zip the folder that contains it and import that.");
			}
			throw new ImportException("This zip isn't a resource pack: there's no pack.mcmeta at the top level.");
		} catch (ZipException e) {
			throw new ImportException("That file isn't a valid .zip. Pick a resource pack .zip or a pack folder.");
		} catch (IOException e) {
			throw new ImportException("Couldn't read that zip: " + e.getMessage());
		}
	}

	private static Check checkFolder(Path dir) throws ImportException {
		Path meta = dir.resolve("pack.mcmeta");
		if (Files.isRegularFile(meta)) {
			readMcmeta(meta);
			return new Check(Kind.FOLDER, null);
		}
		List<String> wrapped = new ArrayList<>();
		try (DirectoryStream<Path> children = Files.newDirectoryStream(dir)) {
			for (Path child : children) {
				if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS) && Files.isRegularFile(child.resolve("pack.mcmeta"))) {
					wrapped.add(child.getFileName().toString());
				}
			}
		} catch (IOException e) {
			throw new ImportException("Couldn't read that folder: " + e.getMessage());
		}
		if (wrapped.size() == 1) {
			readMcmeta(dir.resolve(wrapped.get(0)).resolve("pack.mcmeta"));
			return new Check(Kind.FOLDER_NESTED, wrapped.get(0));
		}
		if (wrapped.size() > 1) throw new ImportException("That folder holds several packs. Import each pack folder on its own.");
		throw new ImportException("This folder isn't a resource pack: there's no pack.mcmeta inside it.");
	}

	private static void readMcmeta(Path meta) throws ImportException {
		try (InputStream in = Files.newInputStream(meta)) {
			validateMcmeta(in);
		} catch (IOException e) {
			throw new ImportException("Couldn't read pack.mcmeta: " + e.getMessage());
		}
	}

	static void validateMcmeta(InputStream in) throws ImportException {
		JsonElement json;
		try {
			json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (JsonParseException | IllegalStateException e) {
			throw new ImportException("The pack's pack.mcmeta is broken (it isn't valid JSON).");
		}
		if (json == null || !json.isJsonObject() || !json.getAsJsonObject().has("pack") || !json.getAsJsonObject().get("pack").isJsonObject()) {
			throw new ImportException("The pack's pack.mcmeta has no \"pack\" section, so Minecraft won't load it.");
		}
	}

	// ------------------------------------------------------------------------------------------------ importing

	/**
	 * Check and copy {@code source} into {@code packsDir}. Returns the path of the imported pack (a .zip file or a
	 * folder directly inside {@code packsDir}). A pack that is already in {@code packsDir} is used where it is.
	 */
	public static Path importPack(Path source, Path packsDir) throws ImportException {
		Check check = check(source);
		try {
			Files.createDirectories(packsDir);
			Path abs = source.toAbsolutePath().normalize();
			Path parent = abs.getParent();
			if (parent != null && parent.equals(packsDir.toAbsolutePath().normalize()) && (check.kind == Kind.ZIP || check.kind == Kind.FOLDER)) {
				return abs;
			}
			String fileName = source.getFileName().toString();
			switch (check.kind) {
				case ZIP -> {
					String name = FileNames.safeFileName(fileName);
					if (!name.toLowerCase(Locale.ROOT).endsWith(".zip")) name = name + ".zip";
					Path target = FileNames.unique(packsDir, name);
					Path tmp = packsDir.resolve(".hollow-import-" + UUID.randomUUID() + ".tmp");
					try {
						Files.copy(source, tmp);
						Files.move(tmp, target);
					} finally {
						Files.deleteIfExists(tmp);
					}
					return target;
				}
				case ZIP_NESTED -> {
					String stem = fileName.toLowerCase(Locale.ROOT).endsWith(".zip") ? fileName.substring(0, fileName.length() - 4) : fileName;
					Path target = FileNames.unique(packsDir, FileNames.safeFileName(stem));
					extractFolder(source, check.inner + "/", target, packsDir);
					return target;
				}
				case FOLDER, FOLDER_NESTED -> {
					Path from = check.kind == Kind.FOLDER ? source : source.resolve(check.inner);
					Path target = FileNames.unique(packsDir, FileNames.safeFileName(from.getFileName().toString()));
					copyFolder(from, target, packsDir);
					return target;
				}
			}
			throw new IllegalStateException();
		} catch (IOException e) {
			throw new ImportException("Couldn't copy the pack: " + e.getMessage());
		}
	}

	private static Path tempFolder(Path packsDir) throws IOException {
		return Files.createDirectory(packsDir.resolve(".hollow-import-" + UUID.randomUUID()));
	}

	/** Whether a zip entry name is safe to write under a folder. */
	static boolean safeEntryName(String name) {
		if (name.isEmpty() || name.length() > 240 || name.indexOf('\0') >= 0 || name.indexOf(':') >= 0) return false;
		if (name.startsWith("/") || name.startsWith("\\")) return false;
		for (String part : name.replace('\\', '/').split("/")) {
			if (part.isEmpty() || part.equals(".") || part.equals("..")) return false;
		}
		return true;
	}

	private static void extractFolder(Path zip, String prefix, Path target, Path packsDir) throws IOException, ImportException {
		Path tmp = tempFolder(packsDir);
		boolean done = false;
		try (ZipFile zf = new ZipFile(zip.toFile())) {
			long total = 0;
			byte[] buffer = new byte[64 * 1024];
			Enumeration<? extends ZipEntry> entries = zf.entries();
			while (entries.hasMoreElements()) {
				ZipEntry e = entries.nextElement();
				String name = e.getName().replace('\\', '/');
				if (e.isDirectory() || !name.startsWith(prefix)) continue;
				String rel = name.substring(prefix.length());
				if (!safeEntryName(rel)) continue;
				Path out = tmp.resolve(rel).normalize();
				if (!out.startsWith(tmp)) continue;
				Files.createDirectories(out.getParent());
				long written = 0;
				try (InputStream in = zf.getInputStream(e); OutputStream os = Files.newOutputStream(out)) {
					int n;
					while ((n = in.read(buffer)) > 0) {
						written += n;
						total += n;
						if (written > MAX_ENTRY_BYTES) throw new ImportException("A file inside that zip is bigger than 256 MB, which is too big.");
						if (total > MAX_TOTAL_BYTES) throw new ImportException("That zip unpacks to more than 2 GB, which is too big.");
						os.write(buffer, 0, n);
					}
				}
			}
			Files.move(tmp, target);
			done = true;
		} finally {
			if (!done) deleteTree(tmp);
		}
	}

	private static void copyFolder(Path from, Path target, Path packsDir) throws IOException, ImportException {
		Path tmp = tempFolder(packsDir);
		boolean done = false;
		long[] total = {0};
		try {
			Files.walkFileTree(from, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					if (attrs.isSymbolicLink()) return FileVisitResult.SKIP_SUBTREE;
					Files.createDirectories(tmp.resolve(from.relativize(dir).toString()));
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (!attrs.isRegularFile()) return FileVisitResult.CONTINUE;
					total[0] += attrs.size();
					if (total[0] > MAX_TOTAL_BYTES) throw new IOException("the folder is bigger than 2 GB");
					Files.copy(file, tmp.resolve(from.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES);
					return FileVisitResult.CONTINUE;
				}
			});
			Files.move(tmp, target);
			done = true;
		} finally {
			if (!done) deleteTree(tmp);
		}
	}

	/** Delete a folder tree (used for temp folders and for removing a pack Hollow imported). Symlinks are not followed. */
	public static void deleteTree(Path dir) throws IOException {
		if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) return;
		if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
			Files.delete(dir);
			return;
		}
		Files.walkFileTree(dir, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
				Files.delete(d);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
