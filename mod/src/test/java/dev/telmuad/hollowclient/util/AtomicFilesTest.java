package dev.telmuad.hollowclient.util;

import dev.telmuad.hollowclient.styles.AnimatedCapes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class AtomicFilesTest {
	@TempDir
	Path dir;

	@Test
	void writesAndReplaces() throws Exception {
		Path f = dir.resolve("sub/a.json");
		AtomicFiles.write(f, "one");
		assertEquals("one", Files.readString(f));
		AtomicFiles.write(f, "two");
		assertEquals("two", Files.readString(f));
		assertFalse(Files.exists(dir.resolve("sub/a.json.tmp")), "no temp file left behind");
	}

	@Test
	void survivesAReaderHammeringTheFile() throws Exception {
		Path f = dir.resolve("b.json");
		AtomicFiles.write(f, "start");
		AtomicBoolean stop = new AtomicBoolean();
		Thread reader = new Thread(() -> {
			while (!stop.get()) {
				try {
					Files.readString(f);
					Thread.sleep(2); // a busy reader, but one that does close the file now and then
				} catch (Exception ignored) {
					// being replaced right now
				}
			}
		});
		reader.start();
		try {
			for (int i = 0; i < 200; i++) AtomicFiles.write(f, "value " + i);
		} finally {
			stop.set(true);
			reader.join();
		}
		assertEquals("value 199", Files.readString(f));
	}

	@Test
	void capeStripsNeedThreeFrames() {
		assertTrue(AnimatedCapes.isStrip(64, 96));
		assertTrue(AnimatedCapes.isStrip(128, 640));
		assertFalse(AnimatedCapes.isStrip(64, 64), "a square is a skin, not a 2-frame strip");
		assertFalse(AnimatedCapes.isStrip(64, 32));
		assertFalse(AnimatedCapes.isStrip(64, 100));
	}
}
