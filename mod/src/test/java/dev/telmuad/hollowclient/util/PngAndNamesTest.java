package dev.telmuad.hollowclient.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PngAndNamesTest {
	@TempDir
	Path tmp;

	private static byte[] header(int w, int h) {
		byte[] b = new byte[24];
		byte[] sig = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
		System.arraycopy(sig, 0, b, 0, 8);
		b[11] = 13;
		b[12] = 'I';
		b[13] = 'H';
		b[14] = 'D';
		b[15] = 'R';
		b[16] = (byte) (w >>> 24);
		b[17] = (byte) (w >>> 16);
		b[18] = (byte) (w >>> 8);
		b[19] = (byte) w;
		b[20] = (byte) (h >>> 24);
		b[21] = (byte) (h >>> 16);
		b[22] = (byte) (h >>> 8);
		b[23] = (byte) h;
		return b;
	}

	@Test
	void readsPngSize() {
		assertEquals(new PngInfo.Size(64, 32), PngInfo.read(header(64, 32)));
		assertEquals(new PngInfo.Size(1024, 512), PngInfo.read(header(1024, 512)));
	}

	@Test
	void rejectsNonPng() {
		assertNull(PngInfo.read("GIF89a this is not a png!".getBytes()));
		assertNull(PngInfo.read(new byte[5]));
		byte[] zero = header(0, 32);
		assertNull(PngInfo.read(zero));
	}

	@Test
	void readsFromFile() throws IOException {
		Path p = tmp.resolve("x.png");
		Files.write(p, header(128, 64));
		assertEquals(new PngInfo.Size(128, 64), PngInfo.read(p));
	}

	@Test
	void capeSizes() {
		assertEquals(PngInfo.CapeLayout.STANDARD, PngInfo.capeLayout(64, 32));
		assertEquals(PngInfo.CapeLayout.STANDARD, PngInfo.capeLayout(2048, 1024));
		assertEquals(PngInfo.CapeLayout.LEGACY, PngInfo.capeLayout(22, 17));
		assertEquals(PngInfo.CapeLayout.LEGACY, PngInfo.capeLayout(44, 34));
		assertNull(PngInfo.capeLayout(64, 64));
		assertNull(PngInfo.capeLayout(100, 50));
		assertNull(PngInfo.capeLayout(4096, 2048));
		assertNull(PngInfo.capeProblem(new PngInfo.Size(64, 32)));
		assertTrue(PngInfo.capeProblem(new PngInfo.Size(64, 64)).contains("64x64"));
		assertTrue(PngInfo.capeProblem(null).contains("isn't a PNG"));
	}

	@Test
	void safeFileNames() {
		assertEquals("My Pack.zip", FileNames.safeFileName("My Pack.zip"));
		assertEquals("a_b_c", FileNames.safeFileName("a<b>c"));
		assertEquals("pack_CON", FileNames.safeFileName("CON"));
		assertEquals("pack_nul.zip", FileNames.safeFileName("nul.zip"));
		assertEquals("dots", FileNames.safeFileName("dots..."));
		assertTrue(FileNames.safeFileName("x".repeat(200)).length() <= 80);
		assertEquals("pack_", FileNames.safeFileName("  "));
	}

	@Test
	void textureIds() {
		assertEquals("my_cool_cape.png", FileNames.textureId("My Cool Cape.png"));
		assertEquals("a_b", FileNames.textureId("a!!!b"));
		assertEquals("cape", FileNames.textureId(""));
	}

	@Test
	void uniqueNames() throws IOException {
		Files.writeString(tmp.resolve("a.zip"), "");
		Files.writeString(tmp.resolve("a (2).zip"), "");
		assertEquals(tmp.resolve("a (3).zip"), FileNames.unique(tmp, "a.zip"));
		Files.createDirectory(tmp.resolve("folder.v2"));
		assertEquals(tmp.resolve("folder.v2 (2)"), FileNames.unique(tmp, "folder.v2"));
		assertEquals(tmp.resolve("new.zip"), FileNames.unique(tmp, "new.zip"));
	}
}
