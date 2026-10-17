package dev.telmuad.hollowclient.skyblock;

import dev.telmuad.hollowclient.skyblock.features.mining.MetalDetector;
import dev.telmuad.hollowclient.skyblock.features.mining.Mining;
import dev.telmuad.hollowclient.skyblock.parse.ChatParser;
import dev.telmuad.hollowclient.skyblock.parse.SidebarParser;
import dev.telmuad.hollowclient.skyblock.parse.TabListParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MiningTest {
	private static TabListParser.TabInfo tab(String key) {
		return TabListParser.parse(Fixtures.tab(key));
	}

	private static SidebarParser.Sidebar sidebar(String key) {
		return SidebarParser.parse(Fixtures.sidebarTitle(key), Fixtures.sidebarLines(key));
	}

	@Test
	void commissionsFromTheWidget() {
		List<Mining.Commission> c = Mining.commissions(tab("mines_full"));
		assertEquals(3, c.size());
		assertEquals(new Mining.Commission("Royal Mines Mithril", 45, false), c.get(0));
		assertEquals(new Mining.Commission("Goblin Slayer", 100, true), c.get(1));
		assertEquals(80.5, c.get(2).percent(), 1e-9);
		assertTrue(Mining.commissions(tab("hub")).isEmpty());
	}

	@Test
	void powdersEventAndForge() {
		Map<Mining.Powder, Long> p = Mining.powders(tab("mines_full"));
		assertEquals(Map.of(Mining.Powder.MITHRIL, 1_234_567L, Mining.Powder.GEMSTONE, 845_210L, Mining.Powder.GLACITE, 230_000L), p);
		assertEquals(new Mining.Event("2X POWDER", "12m 30s"), Mining.event(tab("mines_full")));
		assertEquals("", Mining.event(tab("hub")).name());

		long now = 1_000_000;
		List<Mining.ForgeSlot> f = Mining.forge(tab("mines_full"), now);
		assertEquals(3, f.size());
		assertEquals(new Mining.ForgeSlot(1, "Refined Diamond", 0), f.get(0), "ready");
		assertEquals(new Mining.ForgeSlot(2, "Gemstone Mixture", now + 3 * 3_600_000L + 10 * 60_000L), f.get(1));
		assertTrue(f.get(2).empty());
	}

	@Test
	void coldHeatAndCrystals() {
		assertEquals(-78, Mining.cold(sidebar("glacite")));
		assertEquals(1, Mining.cold(sidebar("hub")), "no Cold line");
		assertEquals(45, Mining.heat(sidebar("crystal_hollows")));
		assertEquals(-1, Mining.heat(sidebar("glacite")));
		Map<String, String> c = Mining.crystals(tab("crystal_hollows"));
		assertEquals(List.of("Jade", "Amber", "Amethyst", "Sapphire", "Topaz"), List.copyOf(c.keySet()));
		assertEquals(3, c.values().stream().filter(Mining::crystalFound).count(), "found and placed count, not found doesn't");
	}

	@Test
	void treasureReading() {
		assertEquals(8.5, Mining.treasureDistance("§c1,234/1,500❤     §e§lTREASURE: §b8.5m     §b300/400✎ Mana"), 1e-9);
		assertEquals(-1, Mining.treasureDistance("§c1,234/1,500❤"));
	}

	@Test
	void powderGainIgnoresSpending() {
		Mining.Gain g = new Mining.Gain();
		g.update(0, 1000);
		assertEquals(0, g.gained(), "the first reading is the start");
		g.update(10_000, 1500);
		g.update(20_000, 200); // spent on the Heart of the Mountain
		g.update(70_000, 700);
		assertEquals(1000, g.gained());
		assertEquals(1000 * 3_600_000.0 / 60_000, g.perHour(70_000), 1e-6, "counted from the first gain");
		assertTrue(Double.isNaN(g.perHour(30_000)), "under a minute");
	}

	@Test
	void pickaxeAbility() {
		ChatParser.ChatMatch used = ChatParser.parse("§aYou used your §r§6Mining Speed Boost §r§aPickaxe Ability!").getFirst();
		assertEquals("pickaxe_ability_used", used.name());
		assertEquals("Mining Speed Boost", used.get("ability"));
		ChatParser.ChatMatch ready = ChatParser.parse("§a§r§6Pickobulus §r§ais now available!").getFirst();
		assertEquals("pickaxe_ability_ready", ready.name());
		assertEquals("Pickobulus", ready.get("ability"));
		assertEquals("Gain +100⸕ Mining Speed", ChatParser.parse("§8§oNew buff: §r§fGain §r§6+100⸕ Mining Speed§r§f.").getFirst().get("buff"));

		Mining.Ability a = new Mining.Ability();
		assertEquals(-1, a.left(0));
		a.used("Pickobulus", 0);
		assertEquals(60_000, a.left(0));
		assertTrue(a.estimated());
		a.available("Pickobulus", 45_000);
		assertEquals(0, a.left(45_000), "Hypixel says when it's ready, whatever the estimate was");
		assertFalse(a.estimated());
	}

	private static double dist(double[] t, double x, double y, double z) {
		// the detector shows one decimal
		return Math.round(Math.sqrt(Math.pow(t[0] - x, 2) + Math.pow(t[1] - y, 2) + Math.pow(t[2] - z, 2)) * 10) / 10.0;
	}

	@Test
	void metalDetectorFindsTheTreasureBelowYou() {
		double[] treasure = {512.5, 37.5, 488.5};
		MetalDetector d = new MetalDetector();
		double[][] spots = {{500, 40, 480}, {503, 40, 480}, {503, 40, 484}, {498, 40, 486}, {506, 40, 490}, {510, 40, 482}};
		for (int i = 0; i < spots.length; i++) {
			double[] s = spots[i];
			d.add(new MetalDetector.Reading(s[0], s[1], s[2], dist(treasure, s[0], s[1], s[2])));
			if (i < 3) assertNull(d.guess(), "not enough readings yet");
		}
		MetalDetector.Guess g = d.guess();
		assertNotNull(g);
		assertEquals(treasure[0], g.x(), 0.6);
		assertEquals(treasure[2], g.z(), 0.6);
		assertEquals(treasure[1], g.y(), 1.0, "readings at one height: the guess goes under you");
		assertTrue(g.error() < 0.2, "exact readings fit well: " + g.error());
	}

	@Test
	void metalDetectorNeedsSpreadAndStartsOverForANewTreasure() {
		MetalDetector d = new MetalDetector();
		double[] treasure = {10, 60, 10};
		for (int i = 0; i < 6; i++) d.add(new MetalDetector.Reading(i, 64, 0, dist(treasure, i, 64, 0)));
		assertNull(d.guess(), "all in a line: can't tell which side");
		d.add(new MetalDetector.Reading(5, 64, 0.2, dist(treasure, 5, 64, 0.2)));
		assertEquals(6, d.count(), "a reading on the same spot replaces the last one");
		d.add(new MetalDetector.Reading(3, 64, 6, dist(treasure, 3, 64, 6)));
		assertEquals(7, d.count(), "a reading off the line is kept even though the line alone can't be solved");
		assertNotNull(d.guess());
		assertEquals(10, d.guess().x(), 0.6);

		d.clear();
		double[][] spots = {{0, 64, 0}, {4, 64, 0}, {4, 64, 4}, {0, 64, 5}, {2, 64, 8}};
		for (double[] s : spots) d.add(new MetalDetector.Reading(s[0], s[1], s[2], dist(treasure, s[0], s[1], s[2])));
		assertEquals(5, d.count());
		double[] next = {-20, 55, -20};
		d.add(new MetalDetector.Reading(8, 64, 2, dist(next, 8, 64, 2)));
		assertEquals(1, d.count(), "a reading that fits nowhere means the treasure moved");
	}
}
