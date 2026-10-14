package dev.telmuad.hollowclient.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads a PNG's size from its header without decoding it, and knows which sizes work as a cape. */
public final class PngInfo {
	private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
	/** Biggest cape accepted (2048x1024 is already very high resolution for a cape). */
	public static final int MAX_CAPE_WIDTH = 2048;

	public record Size(int width, int height) {
	}

	private PngInfo() {
	}

	/** The image size, or null if the bytes don't start like a PNG. */
	public static Size read(byte[] header) {
		if (header == null || header.length < 24) return null;
		for (int i = 0; i < SIGNATURE.length; i++) if (header[i] != SIGNATURE[i]) return null;
		// First chunk must be IHDR: length (4) + "IHDR" (4), then width and height (big endian).
		if (header[12] != 'I' || header[13] != 'H' || header[14] != 'D' || header[15] != 'R') return null;
		int w = readInt(header, 16);
		int h = readInt(header, 20);
		if (w <= 0 || h <= 0) return null;
		return new Size(w, h);
	}

	public static Size read(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			return read(in.readNBytes(24));
		}
	}

	private static int readInt(byte[] b, int off) {
		return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16) | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
	}

	/** How a cape image will be used. */
	public enum CapeLayout {
		/** Standard layout, 2:1 (64x32, 128x64, ...). */
		STANDARD,
		/** Old 22x17 layout (or a multiple): placed on a 64x32 canvas. */
		LEGACY
	}

	/** The cape layout for an image of this size, or null if it can't be used as a cape. */
	public static CapeLayout capeLayout(int w, int h) {
		if (w == 2 * h && w >= 64 && w <= MAX_CAPE_WIDTH && w % 64 == 0) return CapeLayout.STANDARD;
		if (w % 22 == 0 && h == w / 22 * 17 && w <= 22 * 16) return CapeLayout.LEGACY;
		return null;
	}

	/** A friendly reason why an image of this size can't be a cape, or null if it can. */
	public static String capeProblem(Size size) {
		if (size == null) return "That file isn't a PNG image.";
		if (capeLayout(size.width(), size.height()) != null) return null;
		return "A cape image must be 64x32 (or 128x64, 256x128 ... up to 2048x1024) or the old 22x17 size. This one is "
				+ size.width() + "x" + size.height() + ".";
	}
}
