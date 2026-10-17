package dev.telmuad.hollowclient.skyblock;

import com.google.gson.JsonObject;
import dev.telmuad.hollowclient.skyblock.data.Contests;
import dev.telmuad.hollowclient.skyblock.features.garden.Garden;
import dev.telmuad.hollowclient.skyblock.parse.ChatParser;
import dev.telmuad.hollowclient.skyblock.parse.TabListParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

class GardenTest {
	@Test
	void visitorsFromTheTabList() {
		assertEquals(List.of("Jerry", "Liam", "Farmer Jon"), Garden.visitors(TabListParser.parse(Fixtures.tab("garden"))));
		assertTrue(Garden.visitors(TabListParser.parse(Fixtures.tab("hub"))).isEmpty());
	}

	@Test
	void visitorOffer() {
		Garden.Offer o = Garden.offer(List.of(
				"§7Items Required:", " §aEnchanted Wheat §8x10", " §9Enchanted Hay Bale §8x2", "",
				"§7Rewards:", " §8+§c30 Copper", " §8+§315,000 §7Farming XP", " §8+§230 Garden Experience", "",
				"§eClick to give!"));
		assertEquals(Map.of("Enchanted Wheat", 10L, "Enchanted Hay Bale", 2L), o.items());
		assertEquals(30, o.copper());
		assertEquals(3, o.rewards().size());
		Map<String, Double> prices = Map.of("Enchanted Wheat", 160.0, "Enchanted Hay Bale", 25_600.0);
		assertEquals(10 * 160 + 2 * 25_600, o.cost(n -> prices.containsKey(n) ? OptionalDouble.of(prices.get(n)) : OptionalDouble.empty()).getAsDouble(), 1e-9);
		assertTrue(o.cost(n -> OptionalDouble.empty()).isEmpty());
	}

	@Test
	void pests() {
		Garden.Pests p = new Garden.Pests();
		for (String line : List.of("§2§lGROSS! §7A §6ൠ Pest §7has appeared in §aPlot §7- §b4§7!",
				"§6§lYUCK! §e3 §6ൠ Pests §7have spawned in §aPlot §7- §b7§7!")) {
			ChatParser.ChatMatch m = ChatParser.parse(line).getFirst();
			assertEquals("pest_spawn", m.name(), line);
			p.spawned(m.get("plot"), m.get("count").isEmpty() ? 1 : Integer.parseInt(m.get("count")), 1);
		}
		assertEquals(4, p.alive());
		assertEquals(Map.of("4", 1, "7", 3), p.plots());
		p.killedAll();
		p.offline("2, 5 and 9", 2);
		assertEquals(3, p.alive());
		p.fromTab(TabListParser.parse(List.of("§b§lPests:", " Alive: §c0")));
		assertEquals(0, p.alive(), "the tab list says none are left");
	}

	@Test
	void speedAndToolCounter() {
		Garden.Speed s = new Garden.Speed(1000);
		for (int i = 0; i < 20; i++) s.broke(i * 50L);
		assertEquals(20, s.perSecond(999), 1e-9);
		assertEquals(0, s.perSecond(5_000), 1e-9);
		JsonObject hoe = new JsonObject();
		hoe.addProperty("mined_crops", 12_345_678L);
		assertEquals(12_345_678L, Garden.toolCounter(hoe));
		assertEquals(-1, Garden.toolCounter(new JsonObject()));
	}

	@Test
	void contestsFromElite() {
		Contests c = new Contests();
		c.load("{\"year\":520,\"contests\":{\"1792196100\":[\"Melon\",\"Nether Wart\",\"Wheat\"],\"1792199700\":[\"Potato\",\"Pumpkin\",\"Moonflower\"]}}");
		assertTrue(c.loaded());
		List<Contests.Contest> next = c.next(1792195033L, 3);
		assertEquals(1792196100L, next.get(0).start());
		assertEquals(List.of("Melon", "Nether Wart", "Wheat"), next.get(0).crops());
		assertEquals(List.of("Potato", "Pumpkin", "Moonflower"), next.get(1).crops());
		assertTrue(next.get(2).crops().isEmpty(), "unknown crops stay empty");
		c.load("not json");
		assertTrue(c.loaded(), "a bad answer keeps the old data");
	}
}
