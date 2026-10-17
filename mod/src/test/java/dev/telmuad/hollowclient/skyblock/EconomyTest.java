package dev.telmuad.hollowclient.skyblock;

import dev.telmuad.hollowclient.skyblock.features.economy.Economy;
import dev.telmuad.hollowclient.skyblock.parse.ChatParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EconomyTest {
	private static ChatParser.ChatMatch chat(String line) {
		List<ChatParser.ChatMatch> m = ChatParser.parse(line);
		assertFalse(m.isEmpty(), line);
		return m.getFirst();
	}

	@Test
	void bazaarOrdersFromChat() {
		Economy.Orders o = new Economy.Orders();
		o.onChat(chat("§6[Bazaar] §r§7§7Buy Order Setup! §r§a64§r§7x §r§fEnchanted Diamond §r§7for §r§69,600 coins§r§7."), 1);
		o.onChat(chat("§6[Bazaar] §r§7§7Sell Offer Setup! §r§a160§r§7x §r§aEnchanted Gold §r§7for §r§645,000 coins§r§7."), 2);
		assertEquals(2, o.list().size());
		assertTrue(o.list().getFirst().buy());
		assertEquals(9600, o.list().getFirst().coins(), 1e-9);
		Economy.Order filled = o.onChat(chat("§6[Bazaar] §r§eYour §r§aBuy Order §r§efor §r§a64§r§7x §r§fEnchanted Diamond §r§ewas filled!"), 3);
		assertNotNull(filled);
		assertTrue(o.list().getFirst().filled());
		assertFalse(o.list().get(1).filled());
		o.onChat(chat("§6[Bazaar] §r§7Claimed §r§a64§r§7x §r§fEnchanted Diamond §r§7worth §r§69,600 coins §r§7bought for §r§6150 §r§7each!"), 4);
		assertEquals(List.of("Enchanted Gold"), o.list().stream().map(Economy.Order::item).toList());
		Economy.Orders again = new Economy.Orders();
		again.load(o.toJson());
		assertEquals(o.list(), again.list());
	}

	@Test
	void historyAndSparkline() {
		Economy.History h = new Economy.History();
		long hour = Economy.History.STEP;
		for (int i = 0; i <= 25; i++) assertTrue(h.add(i * hour, Map.of("X", 100.0 + i)));
		assertFalse(h.add(25 * hour + 60_000, Map.of("X", 999.0)), "only one snapshot per hour");
		assertEquals(26, h.prices("X").size());
		assertEquals((125.0 - 101.0) / 101.0, h.change24h("X", 25 * hour), 1e-9);
		assertEquals("▁▂▃▄▅▆▇█", Economy.sparkline(List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0), 8));
		assertEquals("", Economy.sparkline(List.of(1.0), 8));
		Economy.History again = new Economy.History();
		again.load(h.toJson());
		assertEquals(h.prices("X"), again.prices("X"));
		assertTrue(Double.isNaN(again.change24h("Y", 0)));
	}

	@Test
	void auctionsAndVerdicts() {
		Economy.Auctions a = new Economy.Auctions();
		a.onChat(chat("§6[Auction] §b[MVP§c+§b] Player1 §ebought §fHyperion §efor §6456,000,000 coins §lCLICK"), 1);
		assertEquals(456_000_000, a.uncollected(), 1);
		a.onChat(chat("§eYou collected §6456,000,000 coins §efrom selling §fHyperion §eto §b[MVP§c+§b] Player1 §ein an auction!"), 2);
		assertEquals(0, a.uncollected(), 1e-9);
		assertTrue(a.list().getFirst().collected());
		assertEquals("cheap", Economy.verdict(80, 100));
		assertEquals("pricey", Economy.verdict(120, 100));
		assertEquals("fair", Economy.verdict(100, 100));
		assertEquals("", Economy.verdict(100, 0));
	}

	@Test
	void coinsPerHourAndFlips() {
		Economy.Rate r = new Economy.Rate();
		r.start(0, -1); // joined before the purse was known
		r.purse(10_000, 1_100_000, 100_000);
		assertEquals(100_000, r.gained(), "the first change counts");
		assertTrue(Double.isNaN(r.perHour(30_000)), "too early to say");
		r.purse(1_800_000, 2_000_000, 900_000);
		assertEquals(1_000_000, r.gained());
		assertEquals(2_000_000, r.perHour(1_800_000), 1e-6);
		assertEquals(100 * 0.9875 - 90, Economy.flipProfit(100, 90), 1e-9);
	}
}
