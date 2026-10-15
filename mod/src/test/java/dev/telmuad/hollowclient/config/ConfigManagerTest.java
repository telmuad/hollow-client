package dev.telmuad.hollowclient.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.module.ModuleRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {
	@TempDir
	Path dir;

	private record Setup(TestModules.Hud hud, TestModules.Plain plain, ModuleRegistry registry, StylesConfig styles,
						 ConfigManager manager) {
	}

	private Setup setup() {
		var hud = new TestModules.Hud();
		var plain = new TestModules.Plain();
		var registry = TestModules.registry(hud, plain);
		var styles = new StylesConfig();
		var manager = new ConfigManager(dir.resolve("hollowclient.json"), registry, styles);
		registry.setChangeListener(m -> manager.markDirty());
		return new Setup(hud, plain, registry, styles, manager);
	}

	@Test
	void missingFileGivesDefaultsAndWritesOne() {
		Setup s = setup();
		assertFalse(s.manager.load());
		assertTrue(Files.exists(dir.resolve("hollowclient.json")));
		assertTrue(s.hud.isEnabled());
		assertFalse(s.plain.isEnabled());
		assertEquals(4.0, s.plain.zoom.get());
	}

	@Test
	void roundTripKeepsEverything() {
		Setup a = setup();
		a.manager.load();
		a.hud.setEnabled(false);
		a.hud.color.set(0x80FF0000);
		a.hud.background.set(false);
		a.plain.setEnabled(true);
		a.plain.zoom.set(12.5);
		a.plain.shape.set(TestModules.Shape.CIRCLE);
		a.hud.pos.anchorX = Anchor.END;
		a.hud.pos.offsetX = 0.05;
		a.hud.pos.scale = 1.5f;
		a.styles.packs.add("Cool Pack.zip");
		a.styles.activePack = "Cool Pack.zip";
		a.styles.putPreset(new Preset("PvP", new JsonObject(), "Cool Pack.zip"));
		a.manager.saveNow();

		Setup b = setup();
		assertTrue(b.manager.load());
		assertFalse(b.hud.isEnabled());
		assertEquals(0x80FF0000, b.hud.color.get());
		assertFalse(b.hud.background.get());
		assertTrue(b.plain.isEnabled());
		assertEquals(12.5, b.plain.zoom.get());
		assertEquals(TestModules.Shape.CIRCLE, b.plain.shape.get());
		assertEquals(Anchor.END, b.hud.pos.anchorX);
		assertEquals(0.05, b.hud.pos.offsetX, 1e-9);
		assertEquals(1.5f, b.hud.pos.scale);
		assertEquals("Cool Pack.zip", b.styles.activePack);
		assertNotNull(b.styles.findPreset("pvp"));
	}

	@Test
	void loadingDoesNotFireEnableCallbacks() throws IOException {
		Files.writeString(dir.resolve("hollowclient.json"), "{\"modules\":{\"fps\":{\"enabled\":false}}}");
		Setup s = setup();
		s.manager.load();
		assertFalse(s.hud.isEnabled());
		assertEquals(0, s.hud.enables);
		assertEquals(0, s.hud.disables);
	}

	@Test
	void corruptFileIsBackedUpAndDefaultsUsed() throws IOException {
		Path file = dir.resolve("hollowclient.json");
		Files.writeString(file, "{ this is not json", StandardCharsets.UTF_8);
		Setup s = setup();
		assertFalse(s.manager.load());
		assertTrue(s.hud.isEnabled());
		try (Stream<Path> files = Files.list(dir)) {
			assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("hollowclient.json.corrupt-")));
		}
		// A fresh, valid file was written.
		assertTrue(JsonParser.parseString(Files.readString(file)).isJsonObject());
	}

	@Test
	void nonObjectRootIsTreatedAsCorrupt() throws IOException {
		Files.writeString(dir.resolve("hollowclient.json"), "[1,2,3]");
		Setup s = setup();
		assertFalse(s.manager.load());
		assertTrue(s.hud.isEnabled());
	}

	@Test
	void wrongTypesAndOutOfRangeValuesFallBackOrClamp() throws IOException {
		Files.writeString(dir.resolve("hollowclient.json"), """
				{
				  "version": 1,
				  "modules": {
				    "fps": { "enabled": "yes", "settings": { "textColor": "not a color", "background": 3, "opacity": 7 },
				             "hud": { "anchorX": "sideways", "x": "far", "scale": 99 } },
				    "zoom": { "enabled": true, "settings": { "zoom": -5, "shape": "triangle" } },
				    "unknown_module": { "enabled": true }
				  },
				  "styles": { "packs": [1, "A.zip", "a.zip"], "activePack": "Missing.zip", "presets": [{"nope": 1}] },
				  "extra": "ignored"
				}
				""");
		Setup s = setup();
		assertTrue(s.manager.load());
		assertTrue(s.hud.isEnabled(), "non-boolean enabled keeps the default");
		assertEquals(0xFFFFFFFF, s.hud.color.get());
		assertTrue(s.hud.background.get());
		assertEquals(1.0, s.hud.scale.get(), "clamped to max");
		assertEquals(Anchor.START, s.hud.pos.anchorX);
		assertEquals(0.01, s.hud.pos.offsetX, 1e-9);
		assertEquals(3.0f, s.hud.pos.scale, "scale clamped to 3");
		assertTrue(s.plain.isEnabled());
		assertEquals(2.0, s.plain.zoom.get(), "clamped to min");
		assertEquals(TestModules.Shape.CROSS, s.plain.shape.get());
		assertEquals(1, s.styles.packs.size(), "non-strings and case duplicates dropped");
		assertEquals("", s.styles.activePack, "active pack must be one of the imported packs");
		assertTrue(s.styles.presets.isEmpty());
	}

	@Test
	void markDirtyWritesAfterTheDelay() throws Exception {
		Setup s = setup();
		s.manager.load();
		s.plain.zoom.set(20);
		// generous deadline: the save runs on a background timer and the build machine may be busy
		long deadline = System.currentTimeMillis() + 15000;
		Path file = dir.resolve("hollowclient.json");
		while (System.currentTimeMillis() < deadline) {
			try {
				if (Files.readString(file).contains("20.0")) return;
			} catch (IOException e) {
				// the file is being replaced right now; try again
			}
			Thread.sleep(50);
		}
		fail("debounced save never happened");
	}

	@Test
	void presetsAreUniqueByNameIgnoringCase() {
		StylesConfig styles = new StylesConfig();
		styles.putPreset(new Preset("Night", new JsonObject(), ""));
		styles.putPreset(new Preset("NIGHT", new JsonObject(), "x.zip"));
		assertEquals(1, styles.presets.size());
		assertEquals("x.zip", styles.findPreset("night").pack());
		assertTrue(styles.removePreset("night"));
		assertTrue(styles.presets.isEmpty());
	}

	@Test
	void presetNamesAreCleaned() {
		Preset p = new Preset("  §cRed\u0007 one that is far too long for the limit  ", null, null);
		assertFalse(p.name().contains("§"));
		assertTrue(p.name().length() <= Preset.MAX_NAME);
		assertEquals("Preset", new Preset("   ", null, null).name());
	}
}
