package dev.telmuad.hollowclient.theme;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.telmuad.hollowclient.util.AutoQuality;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.*;

class ThemeTest {
	@TempDir
	Path dir;

	@Test
	void valuesAreClampedAndOpaque() {
		HollowTheme t = new HollowTheme("  x  ", 0x112233, 0x00445566, 0x80778899, 5f, -1f, 40, 9f, false);
		assertEquals("x", t.name());
		assertEquals(0xFF112233, t.accent());
		assertEquals(0xFF445566, t.gradientStart());
		assertEquals(0xFF778899, t.gradientEnd());
		assertEquals(1f, t.glow());
		assertEquals(0f, t.blur());
		assertEquals(8, t.roundness());
		assertEquals(3f, t.animationSpeed());
		assertEquals(0.25f, t.withAnimationSpeed(0f).animationSpeed());
		assertEquals(0, t.withRoundness(-3).roundness());
	}

	@Test
	void sevenPresetsWithUniqueNames() {
		assertEquals(7, HollowTheme.PRESETS.size());
		assertEquals(7, HollowTheme.PRESETS.stream().map(t -> t.name().toLowerCase()).distinct().count());
		assertSame(HollowTheme.FROST, HollowTheme.preset("frost"));
		assertNull(HollowTheme.preset("nope"));
		assertTrue(HollowTheme.VOID.isPreset());
		assertFalse(HollowTheme.VOID.withGlow(0.1f).isPreset(), "a tweaked preset is no longer the preset");
		assertTrue(HollowTheme.RAINBOW.rainbow());
	}

	@Test
	void jsonRoundTripAndFallbacks() {
		HollowTheme t = HollowTheme.INFERNO.withName("Mine").withGlow(0.3f).withRainbow(true);
		assertEquals(t, HollowTheme.fromJson(t.toJson(), HollowTheme.VOID));

		JsonObject broken = JsonParser.parseString("{\"name\":7,\"accent\":\"#zz\",\"glow\":\"bright\",\"roundness\":3}").getAsJsonObject();
		HollowTheme f = HollowTheme.fromJson(broken, HollowTheme.VOID);
		assertEquals("Void", f.name());
		assertEquals(HollowTheme.VOID.accent(), f.accent());
		assertEquals(HollowTheme.VOID.glow(), f.glow());
		assertEquals(3, f.roundness());
		assertSame(HollowTheme.TOXIC, HollowTheme.fromJson(JsonParser.parseString("[1]"), HollowTheme.TOXIC));
	}

	@Test
	void gradientEndsAndRainbowMoves() {
		HollowTheme t = HollowTheme.VOID;
		assertEquals(t.gradientStart(), t.gradientAt(0f, 0));
		assertEquals(t.gradientEnd(), t.gradientAt(1f, 0));
		assertEquals(t.accent(), t.accentAt(123456));
		HollowTheme r = HollowTheme.RAINBOW;
		assertNotEquals(r.accentAt(0), r.accentAt(4000), "rainbow accent cycles over time");
		assertEquals(r.accentAt(0), r.accentAt(8000), "one full turn every 8 s at speed 1");
	}

	@Test
	void storeCreatesDefaultsSavesAndReloads() throws IOException {
		Path file = dir.resolve("hollowclient/theme.json");
		ThemeStore store = new ThemeStore(file);
		store.load();
		assertTrue(Files.isRegularFile(file), "missing file is created with defaults");
		assertEquals(HollowTheme.VOID, store.current());

		store.setCurrent(HollowTheme.FROST);
		HollowTheme saved = store.saveCustom(HollowTheme.TOXIC.withName("Slime").withBlur(0.1f));
		assertEquals("Slime", saved.name());
		assertEquals(saved, store.current());
		assertEquals(8, store.all().size());

		ThemeStore other = new ThemeStore(file);
		other.load();
		assertEquals(saved, other.current());
		assertEquals(1, other.custom().size());

		// Same name (any case) replaces; a preset name gets " (custom)"
		store.saveCustom(HollowTheme.TOXIC.withName("SLIME"));
		assertEquals(1, store.custom().size());
		assertEquals("Void (custom)", store.saveCustom(HollowTheme.VOID.withGlow(0.2f)).name());
		assertTrue(store.deleteCustom("slime"));
		assertFalse(store.deleteCustom("slime"));
		assertEquals(1, store.custom().size());
	}

	@Test
	void unsavedEditsStayInMemory() throws IOException {
		Path file = dir.resolve("theme.json");
		ThemeStore store = new ThemeStore(file);
		store.load();
		String before = Files.readString(file);
		store.setCurrent(HollowTheme.VOID.withGlow(0.1f), false);
		assertEquals(before, Files.readString(file));
		store.save();
		assertNotEquals(before, Files.readString(file));
	}

	@Test
	void reloadsWhenTheLauncherChangesTheFile() throws IOException {
		Path file = dir.resolve("theme.json");
		ThemeStore store = new ThemeStore(file);
		store.load();
		assertFalse(store.reloadIfChanged(), "nothing changed");

		ThemeStore launcher = new ThemeStore(file);
		launcher.load();
		launcher.setCurrent(HollowTheme.BLOODMOON);
		// make sure the timestamp moves even on file systems with coarse time stamps
		Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 2000));
		assertTrue(store.reloadIfChanged());
		assertEquals(HollowTheme.BLOODMOON, store.current());
	}

	@Test
	void brokenFileFallsBackToDefaults() throws IOException {
		Path file = dir.resolve("theme.json");
		Files.writeString(file, "{not json", StandardCharsets.UTF_8);
		ThemeStore store = new ThemeStore(file);
		store.load();
		assertEquals(HollowTheme.VOID, store.current());
		assertTrue(store.custom().isEmpty());

		Files.writeString(file, "{\"current\":{\"name\":\"A\"},\"custom\":[{\"name\":\"B\"},{\"name\":\"b\"},5]}", StandardCharsets.UTF_8);
		store.load();
		assertEquals("A", store.current().name());
		assertEquals(1, store.custom().size(), "duplicates and non-objects are skipped");
	}

	@Test
	void autoQualityStepsDownAndBackUp() {
		AutoQuality q = new AutoQuality();
		long t = 0;
		assertEquals(AutoQuality.Level.HIGH, q.update(40, 60, t));
		assertEquals(AutoQuality.Level.HIGH, q.update(40, 60, t += 2000), "not yet: 2 s below target");
		assertEquals(AutoQuality.Level.MEDIUM, q.update(40, 60, t += 1000));
		assertEquals(AutoQuality.Level.MEDIUM, q.update(40, 60, t += 1000));
		assertEquals(AutoQuality.Level.LOW, q.update(40, 60, t += 2000));
		assertEquals(AutoQuality.Level.LOW, q.update(10, 60, t += 5000), "never below LOW");

		// just above target is not enough to step up (hysteresis)
		for (int i = 0; i < 20; i++) q.update(65, 60, t += 1000);
		assertEquals(AutoQuality.Level.LOW, q.level());
		q.update(90, 60, t += 1000);
		assertEquals(AutoQuality.Level.LOW, q.update(90, 60, t += 9000));
		assertEquals(AutoQuality.Level.MEDIUM, q.update(90, 60, t += 1000));
		q.reset();
		assertEquals(AutoQuality.Level.HIGH, q.level());
		assertTrue(AutoQuality.scale(AutoQuality.Level.LOW) < AutoQuality.scale(AutoQuality.Level.MEDIUM));
		assertEquals(1f, AutoQuality.scale(AutoQuality.Level.HIGH));
	}
}
