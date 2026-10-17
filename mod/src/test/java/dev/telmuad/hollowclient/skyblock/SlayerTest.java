package dev.telmuad.hollowclient.skyblock;

import dev.telmuad.hollowclient.skyblock.features.slayer.Slayer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SlayerTest {
	private static final List<Slayer.Thing> LABELS = List.of(
			// your boss: name and health on top, the timer, then "Spawned by" lowest
			new Slayer.Thing(1, 10, 65.9, 20, "§c☠ §bSven Packmaster §a1.2M§c❤"),
			new Slayer.Thing(2, 10, 65.6, 20, "§c02:58"),
			new Slayer.Thing(3, 10, 65.3, 20, "§7Spawned by: §bMe"),
			// someone else's boss a few blocks away
			new Slayer.Thing(4, 2, 65.9, 20, "§c☠ §bSven Packmaster §a2.0M§c❤"),
			new Slayer.Thing(5, 2, 65.3, 20, "§7Spawned by: §bSomeoneElse"),
			// a far-away label that isn't part of either
			new Slayer.Thing(6, 30, 66, 30, "§aOverflux §e45s"));
	private static final List<Slayer.Thing> MOBS = List.of(
			new Slayer.Thing(10, 10, 64, 20, ""),
			new Slayer.Thing(11, 2, 64, 20, ""),
			new Slayer.Thing(12, 12, 64, 25, ""));

	@Test
	void findsYourBossAndItsLabels() {
		Slayer.Boss b = Slayer.findBoss(LABELS, MOBS, "Me");
		assertNotNull(b);
		assertEquals(10, b.entityId(), "the mob right under your Spawned by tag");
		assertEquals(List.of("☠ Sven Packmaster 1.2M❤", "02:58"), b.labels(), "labels above your tag, top first");
		assertEquals("1.2M", b.hp());
	}

	@Test
	void namesMatchIgnoringCaseAndOthersAreNeverPicked() {
		assertEquals(10, Slayer.findBoss(LABELS, MOBS, "me").entityId());
		assertEquals(11, Slayer.findBoss(LABELS, MOBS, "SomeoneElse").entityId());
		assertNull(Slayer.findBoss(LABELS, MOBS, "Nobody"), "no boss of yours, nothing found");
		assertNull(Slayer.findBoss(List.of(), MOBS, "Me"));
	}

	@Test
	void bossWithoutAMobStillShowsItsLabels() {
		Slayer.Boss b = Slayer.findBoss(LABELS.subList(0, 3), List.of(), "Me");
		assertNotNull(b);
		assertEquals(-1, b.entityId());
		assertEquals("1.2M", b.hp());
	}

	@Test
	void killTimesAndRate() {
		Slayer.Stats s = new Slayer.Stats();
		assertEquals(-1, s.fightMs(0));
		assertTrue(Double.isNaN(s.perHour(0)));
		s.spawned(0);
		assertEquals(5_000, s.fightMs(5_000));
		s.killed(60_000);
		s.spawned(120_000);
		s.failed();
		assertEquals(-1, s.fightMs(130_000), "a failed quest ends the fight");
		s.spawned(180_000);
		s.killed(210_000);
		assertEquals(2, s.kills(), "failed quests aren't kills");
		assertEquals(45_000, s.averageKillMs());
		assertEquals(30_000, s.bestKillMs());
		assertEquals(0, s.since());
		assertEquals(2 * 3_600_000.0 / 360_000, s.perHour(360_000), 1e-9);
		s.killed(400_000);
		assertEquals(2, s.kills(), "a kill without a spawn isn't counted");
	}

	@Test
	void dropsAddUpAndSortByValue() {
		Slayer.Drops d = new Slayer.Drops();
		d.add("Grizzly Bait", 2, 150_000);
		d.add("Red Claw Egg", 1, 2_000_000);
		d.add("Grizzly Bait", 1, 150_000);
		d.add("Unknown Thing", 1, 0);
		assertEquals(450_000 + 2_000_000, d.total(), 1e-6);
		assertEquals(3, d.counts().get("Grizzly Bait"));
		assertEquals(List.of("Red Claw Egg", "Grizzly Bait", "Unknown Thing"), d.byValue());
		assertEquals(450_000, d.value("Grizzly Bait"), 1e-6);
	}
}
