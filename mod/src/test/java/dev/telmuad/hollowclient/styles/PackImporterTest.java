package dev.telmuad.hollowclient.styles;

import dev.telmuad.hollowclient.styles.PackImporter.ImportException;
import dev.telmuad.hollowclient.styles.PackImporter.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PackImporterTest {
	private static final String META = "{\"pack\":{\"pack_format\":75,\"description\":\"test\"}}";

	@TempDir
	Path tmp;

	private Path zip(String name, Map<String, String> entries) throws IOException {
		Path p = tmp.resolve("src").resolve(name);
		Files.createDirectories(p.getParent());
		try (OutputStream os = Files.newOutputStream(p); ZipOutputStream zos = new ZipOutputStream(os)) {
			for (Map.Entry<String, String> e : entries.entrySet()) {
				zos.putNextEntry(new ZipEntry(e.getKey()));
				zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
				zos.closeEntry();
			}
		}
		return p;
	}

	private static Map<String, String> entries(String... kv) {
		Map<String, String> m = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
		return m;
	}

	private Path packs() throws IOException {
		return Files.createDirectories(tmp.resolve("resourcepacks"));
	}

	@Test
	void plainZipIsCopiedAsIs() throws Exception {
		Path z = zip("Cool Pack.zip", entries("pack.mcmeta", META, "assets/minecraft/textures/block/stone.png", "x"));
		assertEquals(Kind.ZIP, PackImporter.check(z).kind());
		Path out = PackImporter.importPack(z, packs());
		assertEquals("Cool Pack.zip", out.getFileName().toString());
		assertEquals(Files.size(z), Files.size(out));
	}

	@Test
	void secondImportGetsANumberedName() throws Exception {
		Path z = zip("Cool Pack.zip", entries("pack.mcmeta", META));
		PackImporter.importPack(z, packs());
		Path second = PackImporter.importPack(z, packs());
		assertEquals("Cool Pack (2).zip", second.getFileName().toString());
	}

	@Test
	void wrappedZipIsExtractedAsAFolder() throws Exception {
		Path z = zip("Wrapped.zip", entries(
				"Inner Pack/pack.mcmeta", META,
				"Inner Pack/assets/minecraft/textures/item/apple.png", "png",
				"__MACOSX/Inner Pack/._pack.mcmeta", "junk"));
		PackImporter.Check check = PackImporter.check(z);
		assertEquals(Kind.ZIP_NESTED, check.kind());
		assertEquals("Inner Pack", check.inner());
		Path out = PackImporter.importPack(z, packs());
		assertTrue(Files.isDirectory(out));
		assertEquals("Wrapped", out.getFileName().toString());
		assertTrue(Files.isRegularFile(out.resolve("pack.mcmeta")));
		assertTrue(Files.isRegularFile(out.resolve("assets/minecraft/textures/item/apple.png")));
		try (Stream<Path> s = Files.list(packs())) {
			assertEquals(1, s.count(), "no temp folders left behind");
		}
	}

	@Test
	void entriesThatEscapeTheFolderAreSkipped() throws Exception {
		Path z = zip("Evil.zip", entries(
				"P/pack.mcmeta", META,
				"P/../../escaped.txt", "nope",
				"P/ok.txt", "fine"));
		Path out = PackImporter.importPack(z, packs());
		assertTrue(Files.isRegularFile(out.resolve("ok.txt")));
		assertFalse(Files.exists(tmp.resolve("escaped.txt")));
		assertFalse(Files.exists(packs().resolve("escaped.txt")));
	}

	@Test
	void missingMcmetaIsAFriendlyError() throws Exception {
		Path z = zip("NoMeta.zip", entries("assets/minecraft/textures/block/stone.png", "x"));
		ImportException e = assertThrows(ImportException.class, () -> PackImporter.check(z));
		assertTrue(e.getMessage().contains("no pack.mcmeta"), e.getMessage());
	}

	@Test
	void brokenMcmetaIsAFriendlyError() throws Exception {
		Path z = zip("Broken.zip", entries("pack.mcmeta", "{ nope"));
		assertTrue(assertThrows(ImportException.class, () -> PackImporter.check(z)).getMessage().contains("isn't valid JSON"));
		Path z2 = zip("NoSection.zip", entries("pack.mcmeta", "{\"other\":1}"));
		assertTrue(assertThrows(ImportException.class, () -> PackImporter.check(z2)).getMessage().contains("\"pack\" section"));
	}

	@Test
	void notAZipIsAFriendlyError() throws Exception {
		Path fake = tmp.resolve("fake.zip");
		Files.writeString(fake, "this is just text");
		assertTrue(assertThrows(ImportException.class, () -> PackImporter.check(fake)).getMessage().contains("isn't a valid .zip"));
	}

	@Test
	void deeplyBuriedMcmetaIsExplained() throws Exception {
		Path z = zip("Deep.zip", entries("a/b/pack.mcmeta", META));
		assertTrue(assertThrows(ImportException.class, () -> PackImporter.check(z)).getMessage().contains("too deep"));
	}

	@Test
	void severalPacksInOneZipAreExplained() throws Exception {
		Path z = zip("Two.zip", entries("A/pack.mcmeta", META, "B/pack.mcmeta", META));
		assertTrue(assertThrows(ImportException.class, () -> PackImporter.check(z)).getMessage().contains("holds 2 packs"));
	}

	@Test
	void folderPacksAreCopied() throws Exception {
		Path folder = Files.createDirectories(tmp.resolve("src/My Folder Pack"));
		Files.writeString(folder.resolve("pack.mcmeta"), META);
		Files.createDirectories(folder.resolve("assets/minecraft"));
		Files.writeString(folder.resolve("assets/minecraft/x.txt"), "x");
		assertEquals(Kind.FOLDER, PackImporter.check(folder).kind());
		Path out = PackImporter.importPack(folder, packs());
		assertEquals("My Folder Pack", out.getFileName().toString());
		assertTrue(Files.isRegularFile(out.resolve("assets/minecraft/x.txt")));
		assertTrue(Files.isRegularFile(folder.resolve("pack.mcmeta")), "original left alone");
	}

	@Test
	void wrappedFolderImportsTheInnerFolder() throws Exception {
		Path outer = Files.createDirectories(tmp.resolve("src/Download"));
		Path inner = Files.createDirectories(outer.resolve("Real Pack"));
		Files.writeString(inner.resolve("pack.mcmeta"), META);
		PackImporter.Check c = PackImporter.check(outer);
		assertEquals(Kind.FOLDER_NESTED, c.kind());
		Path out = PackImporter.importPack(outer, packs());
		assertEquals("Real Pack", out.getFileName().toString());
	}

	@Test
	void folderWithoutMcmetaIsAFriendlyError() throws Exception {
		Path folder = Files.createDirectories(tmp.resolve("src/Empty"));
		assertTrue(assertThrows(ImportException.class, () -> PackImporter.check(folder)).getMessage().contains("no pack.mcmeta"));
	}

	@Test
	void packAlreadyInTheFolderIsUsedInPlace() throws Exception {
		Path packs = packs();
		Path z = packs.resolve("Here.zip");
		try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(z))) {
			zos.putNextEntry(new ZipEntry("pack.mcmeta"));
			zos.write(META.getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
		}
		assertEquals(z.toAbsolutePath().normalize(), PackImporter.importPack(z, packs));
		try (Stream<Path> s = Files.list(packs)) {
			assertEquals(1, s.count());
		}
	}

	@Test
	void unsafeNamesAreRecognised() {
		assertFalse(PackImporter.safeEntryName("../x"));
		assertFalse(PackImporter.safeEntryName("a/../../x"));
		assertFalse(PackImporter.safeEntryName("/abs"));
		assertFalse(PackImporter.safeEntryName("C:/x"));
		assertFalse(PackImporter.safeEntryName("a//b"));
		assertTrue(PackImporter.safeEntryName("assets/minecraft/x.png"));
	}
}
