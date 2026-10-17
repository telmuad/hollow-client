package dev.telmuad.hollowclient.skyblock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.telmuad.hollowclient.skyblock.features.items.ItemData;
import dev.telmuad.hollowclient.skyblock.features.items.ItemLocks;
import dev.telmuad.hollowclient.skyblock.features.items.ItemValue;
import dev.telmuad.hollowclient.skyblock.parse.ItemParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

class ItemsAreaTest {
	private static final Map<String, Double> PRICES = Map.of(
			"ENCHANTMENT_ULTIMATE_WISE_5", 2_000_000.0, "ENCHANTMENT_SHARPNESS_6", 1_000_000.0,
			"HOT_POTATO_BOOK", 80_000.0, "FUMING_POTATO_BOOK", 1_200_000.0, "RECOMBOBULATOR_3000", 7_000_000.0,
			"DEEP_SEA_ORB", 1_500_000.0, "PERFECT_JASPER_GEM", 15_000_000.0, "ESSENCE_WITHER", 3_000.0,
			"FIRST_MASTER_STAR", 9_000_000.0);

	private static OptionalDouble price(String id) {
		Double v = PRICES.get(id);
		return v == null ? OptionalDouble.empty() : OptionalDouble.of(v);
	}

	private static final ItemValue.Neu NEU = new ItemValue.Neu() {
		@Override
		public String reforgeStone(String reforge) {
			return reforge.equals("heroic") ? null : reforge.equals("submerged") ? "DEEP_SEA_ORB" : null;
		}

		@Override
		public ItemValue.EssenceCost essence(String itemId) {
			return itemId.equals("HYPERION") ? new ItemValue.EssenceCost("Wither", new int[]{150, 300, 500, 900, 1500}) : null;
		}
	};

	@Test
	void hyperionEstimate() {
		JsonObject d = Fixtures.item("hyperion").customData().deepCopy();
		d.getAsJsonObject("gems").addProperty("COMBAT_0_gem", "JASPER");
		ItemParser.SbItem item = ItemParser.parse(d, "x", Fixtures.item("hyperion").lore(), 1);
		ItemValue.Estimate e = ItemValue.estimate(item, 450_000_000, ItemsAreaTest::price, NEU);
		List<String> labels = e.parts().stream().map(ItemValue.Part::label).toList();
		assertEquals(List.of("Item", "Enchantments", "Potato books (15)", "Recombobulator", "Gemstones",
				"Stars (3350 Wither essence)", "Master stars"), labels);
		double expected = 450e6 + 3e6 + (10 * 80_000 + 5 * 1.2e6) + 7e6 + 15e6 + 3350 * 3000 + 9e6;
		assertEquals(expected, e.total(), 1);
	}

	@Test
	void reforgeStonesAndMissingPrices() {
		JsonObject d = new JsonObject();
		d.addProperty("id", "SOME_ROD");
		d.addProperty("modifier", "submerged");
		ItemParser.SbItem rod = ItemParser.parse(d, "x", List.of(), 1);
		ItemValue.Estimate e = ItemValue.estimate(rod, 0, ItemsAreaTest::price, NEU);
		assertEquals(List.of(new ItemValue.Part("Reforge stone", 1_500_000)), e.parts());
		assertEquals(0, ItemValue.estimate(Fixtures.item("hyperion").parse(), 0, id -> OptionalDouble.empty(), null).total());
	}

	@Test
	void craftCostFromNeuRecipes() {
		JsonObject single = JsonParser.parseString("""
				{"internalname":"ENCHANTED_DIAMOND","recipe":{"A1":"","A2":"DIAMOND:32","A3":"","B1":"DIAMOND:32","B2":"DIAMOND:32",
				 "B3":"DIAMOND:32","C1":"","C2":"DIAMOND:32","C3":""}}""").getAsJsonObject();
		assertEquals(Map.of("DIAMOND", 160), ItemData.ingredients(single));
		assertEquals(160 * 8.5, ItemData.craftCost(single, id -> OptionalDouble.of(8.5)).getAsDouble(), 1e-9);
		JsonObject list = JsonParser.parseString("""
				{"recipes":[{"type":"forge","A1":"X:1"},{"type":"crafting","A1":"GIANT_FRAGMENT_LASER:8","B2":"NECRON_BLADE:1","count":1}]}""")
				.getAsJsonObject();
		assertEquals(Map.of("GIANT_FRAGMENT_LASER", 8, "NECRON_BLADE", 1), ItemData.ingredients(list));
		assertTrue(ItemData.craftCost(list, id -> id.equals("NECRON_BLADE") ? OptionalDouble.empty() : OptionalDouble.of(1)).isEmpty(),
				"an ingredient without a price means no craft cost");
		assertTrue(ItemData.ingredients(JsonParser.parseString("{\"itemid\":\"x\"}").getAsJsonObject()).isEmpty());
	}

	@Test
	void neuConstants() {
		JsonObject stones = JsonParser.parseString("""
				{"WITHER_BLOOD":{"internalName":"WITHER_BLOOD","reforgeName":"Withered"},
				 "DEEP_SEA_ORB":{"internalName":"DEEP_SEA_ORB","reforgeName":"Submerged"}}""").getAsJsonObject();
		Map<String, String> m = ItemData.reforgeStones(stones);
		assertEquals("WITHER_BLOOD", m.get("withered"));
		assertEquals("DEEP_SEA_ORB", m.get("submerged"));
		JsonObject ess = JsonParser.parseString("{\"HYPERION\":{\"type\":\"Wither\",\"1\":150,\"2\":300,\"3\":500,\"4\":900,\"5\":1500}}")
				.getAsJsonObject();
		ItemValue.EssenceCost c = ItemData.essence(ess, "HYPERION");
		assertEquals("Wither", c.type());
		assertArrayEquals(new int[]{150, 300, 500, 900, 1500}, c.perStar());
		assertNull(ItemData.essence(ess, "NOPE"));
	}

	@Test
	void slotLabels() {
		assertEquals("100", ItemData.slotLabel(Fixtures.item("pet").parse()));
		assertEquals("UWV", ItemData.slotLabel(Fixtures.item("book").parse()));
		assertEquals("✪6", ItemData.slotLabel(Fixtures.item("hyperion").parse()));
		JsonObject minion = new JsonObject();
		minion.addProperty("id", "COBBLESTONE_GENERATOR_11");
		assertEquals("XI", ItemData.slotLabel(ItemParser.parse(minion, "Cobblestone Minion XI", List.of(), 1)));
		assertNull(ItemData.slotLabel(Fixtures.item("menu_glass").parse()));
	}

	@Test
	void locks() {
		ItemLocks l = new ItemLocks();
		ItemParser.SbItem hyp = Fixtures.item("hyperion").parse();
		ItemParser.SbItem glass = Fixtures.item("menu_glass").parse();
		assertFalse(l.locked(hyp, 0));
		assertTrue(l.toggle(hyp, 0));
		assertTrue(l.locked(hyp, 5), "locked by uuid wherever it is");
		assertTrue(l.toggle(glass, 7), "no uuid: locked by slot");
		assertTrue(l.locked(glass, 7));
		assertFalse(l.locked(glass, 8));
		assertFalse(l.toggle(glass, -1), "nothing to lock without a uuid or a slot");
		ItemLocks again = new ItemLocks();
		again.load(l.toJson());
		assertTrue(again.locked(hyp, -1));
		assertTrue(again.locked(glass, 7));
		assertFalse(again.toggle(hyp, 0));
		assertFalse(again.locked(hyp, 0));
		assertEquals(1, again.size());
	}
}
