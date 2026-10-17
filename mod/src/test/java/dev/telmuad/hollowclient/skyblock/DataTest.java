package dev.telmuad.hollowclient.skyblock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.telmuad.hollowclient.skyblock.data.Http;
import dev.telmuad.hollowclient.skyblock.data.NeuRepo;
import dev.telmuad.hollowclient.skyblock.data.Prices;
import dev.telmuad.hollowclient.skyblock.parse.ItemParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class DataTest {
	private final long[] now = {10_000_000};

	private static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	// ------------------------------------------------------------------------------------------------ http cache

	@Test
	void cacheThenStaleOnOutage(@TempDir Path dir) {
		List<String> calls = new ArrayList<>();
		boolean[] down = {false};
		Http http = new Http(dir, url -> {
			calls.add(url);
			if (down[0]) throw new IOException("HTTP 525");
			return ("{\"n\":" + calls.size() + "}").getBytes(StandardCharsets.UTF_8);
		}, () -> now[0]);
		Http.Result a = http.getNow("x", "https://example/x", 60_000, false);
		assertTrue(a.ok() && !a.stale());
		assertEquals(1, calls.size());
		now[0] += 30_000;
		Http.Result b = http.getNow("x", "https://example/x", 60_000, false);
		assertEquals(1, calls.size(), "still fresh");
		assertArrayEquals(a.body(), b.body());
		now[0] += 60_000;
		down[0] = true;
		Http.Result c = http.getNow("x", "https://example/x", 60_000, false);
		assertTrue(c.ok() && c.stale());
		assertEquals("HTTP 525", c.error());
		assertTrue(http.status("x").stale());
		assertFalse(http.getNow("never", "https://example/y", 60_000, false).ok(), "nothing cached, nothing to show");
		http.shutdown();
	}

	// ------------------------------------------------------------------------------------------------ prices

	private static final String BAZAAR = """
			{"success":true,"products":{
			 "ENCHANTED_DIAMOND":{"quick_status":{"buyPrice":160.5,"sellPrice":150.2,"buyMovingWeek":900000,"sellMovingWeek":800000}},
			 "ENCHANTMENT_ULTIMATE_WISE_5":{"quick_status":{"buyPrice":2100000,"sellPrice":1900000,"buyMovingWeek":100,"sellMovingWeek":90}},
			 "BROKEN":"nope"}}""";
	private static final String ELITE = """
			{"items":{
			 "HYPERION":[{"skyblockId":"HYPERION","variantKey":"ex:scrolls=WITHER_SHIELD_SCROLL","variedBy":{"extra":{}},"lowest":810000000},
			             {"skyblockId":"HYPERION","variantKey":"","variedBy":{},"lowest":456900000,"lowest3Day":440000000}],
			 "SOLD_OUT":[{"variantKey":"","variedBy":{},"lowest":-1,"lowest3Day":12345}],
			 "GONE":[{"variantKey":"","variedBy":{},"lowest":-1,"lowest3Day":-1}],
			 "PET":[{"variantKey":"r:LEGENDARY|pet:GOLDEN_DRAGON|pet_group:100","variedBy":{"rarity":"LEGENDARY","pet":"GOLDEN_DRAGON","petLevel":{"key":"LVL_100","min":100,"max":100}},"lowest":600000000},
			        {"variantKey":"r:LEGENDARY|pet:GOLDEN_DRAGON|pet_group:1","variedBy":{"rarity":"LEGENDARY","pet":"GOLDEN_DRAGON","petLevel":{"key":"LVL_1","min":1,"max":99}},"lowest":400000000}]}}""";
	private static final String ITEMS = """
			{"success":true,"items":[{"id":"HYPERION","name":"Hyperion","npc_sell_price":100000},{"id":"TITANIUM_DRILL_4","name":"Titanium Drill DR-X655"}]}""";

	private Prices prices() {
		Prices p = new Prices();
		p.loadBazaar(json(BAZAAR), 1);
		p.loadLowestBin(json(ELITE), 2);
		p.loadItems(json(ITEMS), 3);
		return p;
	}

	@Test
	void pricesFromEachSource() {
		Prices p = prices();
		assertTrue(p.loaded());
		assertEquals(150.2, p.bazaar("ENCHANTED_DIAMOND").sell(), 1e-9);
		assertEquals(456_900_000, p.lowestBin("HYPERION").getAsDouble(), "the base listing, not the scroll variants");
		assertEquals(12_345, p.lowestBin("SOLD_OUT").getAsDouble(), "falls back to the 3-day lowest");
		assertTrue(p.lowestBin("GONE").isEmpty());
		assertEquals(600_000_000, p.petLowestBin("GOLDEN_DRAGON", ItemParser.Rarity.LEGENDARY, 100).getAsDouble());
		assertEquals(400_000_000, p.petLowestBin("GOLDEN_DRAGON", ItemParser.Rarity.LEGENDARY, 42).getAsDouble());
		assertTrue(p.petLowestBin("GOLDEN_DRAGON", ItemParser.Rarity.EPIC, 100).isEmpty());
		assertEquals(100_000, p.npc("HYPERION").getAsDouble());
		assertEquals("Titanium Drill DR-X655", p.name("TITANIUM_DRILL_4"));
	}

	@Test
	void valueOfParsedItems() {
		Prices p = prices();
		assertEquals(new Prices.Estimate(456_900_000, Prices.Source.LOWEST_BIN), p.value(Fixtures.item("hyperion").parse(), -1));
		assertEquals(new Prices.Estimate(1_900_000, Prices.Source.BAZAAR), p.value(Fixtures.item("book").parse(), -1));
		ItemParser.SbItem pet = Fixtures.item("pet").parse();
		assertEquals(100, ItemParser.petLevel(pet));
		assertEquals(600_000_000, p.value(pet, ItemParser.petLevel(pet)).value());
		assertEquals(Prices.Estimate.NONE, p.value(Fixtures.item("menu_glass").parse(), -1));
		assertEquals(Prices.Estimate.NONE, p.value(Fixtures.item("soulbound").parse(), -1));
	}

	@Test
	void junkDoesntWipeGoodData() {
		Prices p = prices();
		p.loadBazaar(json("{\"success\":false}"), 9);
		p.loadLowestBin(json("{}"), 9);
		assertEquals(150.2, p.bazaar("ENCHANTED_DIAMOND").sell(), 1e-9);
		assertEquals(1, p.bazaarAt());
	}

	// ------------------------------------------------------------------------------------------------ NEU repo

	private static void zip(Path file, String... entries) throws IOException {
		try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(file))) {
			for (int i = 0; i < entries.length; i += 2) {
				z.putNextEntry(new ZipEntry(entries[i]));
				z.write(entries[i + 1].getBytes(StandardCharsets.UTF_8));
				z.closeEntry();
			}
			for (int i = 0; i < 12; i++) {
				z.putNextEntry(new ZipEntry("NotEnoughUpdates-REPO-master/items/FILLER_" + i + ".json"));
				z.write("{}".getBytes(StandardCharsets.UTF_8));
				z.closeEntry();
			}
		}
	}

	@Test
	void neuRepoDownloadsOnceAndReadsFromTheZip(@TempDir Path dir) throws Exception {
		NeuRepo repo = new NeuRepo(dir, () -> now[0]);
		int[] downloads = {0};
		String[] sha = {"a".repeat(40)};
		NeuRepo.Downloader download = (url, target) -> {
			downloads[0]++;
			zip(target, "NotEnoughUpdates-REPO-master/items/HYPERION.json", "{\"displayname\":\"§dHyperion\"}",
					"NotEnoughUpdates-REPO-master/constants/pets.json", "{\"pet_levels\":[100,110]}",
					"NotEnoughUpdates-REPO-master/../../evil.json", "{}");
		};
		repo.updateIfDue(url -> sha[0].getBytes(StandardCharsets.UTF_8), download, false);
		assertTrue(repo.available(), repo.error());
		assertEquals("§dHyperion", repo.item("HYPERION").get("displayname").getAsString());
		assertEquals(2, repo.constant("pets").getAsJsonArray("pet_levels").size());
		assertNull(repo.item("NOPE"));
		assertNull(repo.item("../../evil"), "names are checked");
		assertFalse(Files.exists(dir.getParent().resolve("evil.json")), "nothing is extracted");

		repo.updateIfDue(url -> sha[0].getBytes(StandardCharsets.UTF_8), download, false);
		assertEquals(1, downloads[0], "checked less than a day ago");
		now[0] += NeuRepo.CHECK_EVERY + 1;
		repo.updateIfDue(url -> sha[0].getBytes(StandardCharsets.UTF_8), download, false);
		assertEquals(1, downloads[0], "same commit, no download");
		now[0] += NeuRepo.CHECK_EVERY + 1;
		sha[0] = "b".repeat(40);
		repo.updateIfDue(url -> sha[0].getBytes(StandardCharsets.UTF_8), download, false);
		assertEquals(2, downloads[0], "new commit, new download");
		repo.close();
	}

	@Test
	void neuRepoSurvivesOutagesAndBadDownloads(@TempDir Path dir) {
		NeuRepo repo = new NeuRepo(dir, () -> now[0]);
		repo.updateIfDue(url -> {
			throw new IOException("offline");
		}, (u, t) -> fail(), false);
		assertFalse(repo.available());
		assertEquals("offline", repo.error());
		repo.updateIfDue(url -> "c".repeat(40).getBytes(StandardCharsets.UTF_8), (u, t) -> {
			try (OutputStream o = Files.newOutputStream(t)) {
				o.write("not a zip".getBytes(StandardCharsets.UTF_8));
			}
		}, false);
		assertFalse(repo.available());
		assertFalse(repo.error().isEmpty());
	}
}
