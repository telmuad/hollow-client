package dev.telmuad.hollowclient.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** File name helpers for imported packs and capes. */
public final class FileNames {
	private FileNames() {
	}

	/** Make a name safe for a Windows file name (no reserved characters, no trailing dots/spaces). */
	public static String safeFileName(String raw) {
		String s = raw == null ? "" : raw;
		StringBuilder out = new StringBuilder();
		s.codePoints().forEach(cp -> {
			if (cp < 0x20 || "<>:\"/\\|?*".indexOf(cp) >= 0 || cp == 0xA7) out.append('_');
			else out.appendCodePoint(cp);
		});
		String r = out.toString().trim();
		while (r.endsWith(".") || r.endsWith(" ")) r = r.substring(0, r.length() - 1);
		if (r.length() > 80) r = r.substring(0, 80).trim();
		String upper = r.toUpperCase(Locale.ROOT);
		String stem = upper.contains(".") ? upper.substring(0, upper.indexOf('.')) : upper;
		if (r.isEmpty() || stem.matches("CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9]")) r = "pack_" + r;
		return r;
	}

	/** Lower-case id usable in a texture path: a-z, 0-9, '_', '-', '.'. */
	public static String textureId(String raw) {
		String s = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
		StringBuilder out = new StringBuilder();
		for (char c : s.toCharArray()) {
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') out.append(c);
			else out.append('_');
		}
		String r = out.toString().replaceAll("_+", "_");
		if (r.length() > 60) r = r.substring(0, 60);
		return r.isEmpty() ? "cape" : r;
	}

	/** {@code dir/name}, or {@code dir/name (2)}, {@code (3)} ... before the extension if it exists already. */
	public static Path unique(Path dir, String name) {
		Path p = dir.resolve(name);
		if (!Files.exists(p)) return p;
		String stem = name;
		String ext = "";
		int dot = name.lastIndexOf('.');
		if (dot > 0 && !Files.isDirectory(p)) {
			stem = name.substring(0, dot);
			ext = name.substring(dot);
		}
		for (int i = 2; i < 1000; i++) {
			Path candidate = dir.resolve(stem + " (" + i + ")" + ext);
			if (!Files.exists(candidate)) return candidate;
		}
		throw new IllegalStateException("Too many files named " + name);
	}
}
