package dev.telmuad.hollowclient.render;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 26.2 can run on Vulkan, so all of Hollow's drawing must go through the Blaze3D API. This fails the build if raw
 * OpenGL (LWJGL's GL classes or GlStateManager) shows up anywhere in the mod's code.
 */
class NoRawOpenGlTest {
	private static final Pattern RAW_GL = Pattern.compile("org\\.lwjgl\\.opengl|\\bGlStateManager\\b|\\bGL[0-9]{2}[A-Z]?\\.|\\bgl[A-Z]\\w+\\(");

	@Test
	void noRawOpenGlInModCode() throws IOException {
		List<String> hits = new ArrayList<>();
		try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
			for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
				List<String> lines = Files.readAllLines(p);
				for (int i = 0; i < lines.size(); i++) {
					String line = lines.get(i);
					if (line.trim().startsWith("//") || line.trim().startsWith("*")) continue;
					if (RAW_GL.matcher(line).find()) hits.add(p + ":" + (i + 1) + ": " + line.trim());
				}
			}
		}
		assertTrue(hits.isEmpty(), "Raw OpenGL calls found (use Blaze3D instead):\n" + String.join("\n", hits));
	}
}
