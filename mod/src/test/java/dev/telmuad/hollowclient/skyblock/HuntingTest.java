package dev.telmuad.hollowclient.skyblock;

import dev.telmuad.hollowclient.skyblock.features.hunting.Hunting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HuntingTest {
	private static final long H = 3_600_000L;

	@Test
	void trapsFromTheWikiLines() {
		Hunting.Traps t = new Hunting.Traps();
		assertTrue(t.chat("§aYou placed a Trap near a Shard location! §7(1/5 traps placed)", 0));
		assertTrue(t.chat("You placed a Trap near a Shard location! (2/5 traps placed)", H));
		assertEquals(2, t.count());
		assertEquals(5, t.max());
		assertEquals(0, t.oldest());
		assertTrue(t.chat("§6§lHUNT §e- §a2 §eof your Hunting Traps caught something! §b[Click here to collect them.]", 9 * H));
		assertEquals(2, t.caught());
		assertEquals(9 * H, t.caughtAt());
		assertTrue(t.chat("You also picked up the trap and it got sent to your Hunting Toolkit!", 10 * H));
		assertEquals(1, t.count());
		assertEquals(1, t.caught());
		assertEquals(H, t.oldest());
		assertFalse(t.chat("You caught a fish.", 0));
	}

	@Test
	void placementCountCorrectsMissedTraps() {
		Hunting.Traps t = new Hunting.Traps();
		t.chat("You placed a Trap near a Shard location! (3/5 traps placed)", 5 * H);
		assertEquals(List.of(-1L, -1L, 5 * H), t.placedAt(), "two placed before Hollow saw them");
		assertEquals(5 * H, t.oldest(), "unknown times don't count as the oldest");
		t.chat("You placed a Trap near a Shard location! (1/5 traps placed)", 6 * H);
		assertEquals(List.of(6 * H), t.placedAt(), "the others were collected without a message");
	}
}
