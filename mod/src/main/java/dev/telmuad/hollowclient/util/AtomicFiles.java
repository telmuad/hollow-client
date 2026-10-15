package dev.telmuad.hollowclient.util;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Write a text file safely: write a temp file next to it, then swap it in. On Windows the swap fails with "access
 * denied" while another program (the launcher, an editor, a test) has the file open for a moment, so it is retried a
 * few times before giving up. Plain Java, unit tested.
 */
public final class AtomicFiles {
	private static final int ATTEMPTS = 12;
	private static final long WAIT_MS = 15;

	private AtomicFiles() {
	}

	public static void write(Path file, String text) throws IOException {
		Path dir = file.toAbsolutePath().getParent();
		if (dir != null) Files.createDirectories(dir);
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
			w.write(text);
		}
		IOException last = null;
		for (int i = 0; i < ATTEMPTS; i++) {
			try {
				try {
					Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				} catch (AtomicMoveNotSupportedException e) {
					Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
				}
				return;
			} catch (IOException e) {
				last = e;
				try {
					Thread.sleep(WAIT_MS * (i + 1));
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
		Files.deleteIfExists(tmp);
		throw last != null ? last : new IOException("Couldn't replace " + file);
	}
}
