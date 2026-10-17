package dev.telmuad.hollowclient.skyblock;

import dev.telmuad.hollowclient.skyblock.features.progress.Progress;
import dev.telmuad.hollowclient.skyblock.features.progress.ProgressModules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProgressTest {
	@Test
	void levelProgressReadings() {
		Progress.Level l = Progress.level("12,048/50,000");
		assertTrue(l.exact());
		assertEquals(12_048, l.current(), 1e-9);
		assertEquals(50_000, l.needed(), 1e-9);
		assertEquals(24.096, l.percent(), 1e-9);
		Progress.Level k = Progress.level("1.2M/3.4M");
		assertEquals(1_200_000, k.current(), 1e-6);
		Progress.Level p = Progress.level("45.2%");
		assertFalse(p.exact());
		assertEquals(45.2, p.percent(), 1e-9);
		assertNull(Progress.level("MAX"));
	}

	@Test
	void skillRateAndTimeToLevel() {
		Progress.Skills s = new Progress.Skills();
		s.gained("Farming", 100, "10,000/50,000", 0);
		assertTrue(Double.isNaN(s.perHour("Farming", 30_000)), "under a minute");
		s.gained("Farming", 1_900, "12,000/50,000", 120_000);
		assertEquals("Farming", s.last());
		assertEquals(2_000, s.gained("Farming"), 1e-9);
		double ph = s.perHour("Farming", 120_000);
		assertEquals(60_000, ph, 1e-6, "2,000 XP in two minutes");
		assertEquals((long) (38_000 / 60_000.0 * 3_600_000), s.toNextLevel("Farming", 120_000));
		s.gained("Mining", 5, "45.2%", 130_000);
		assertEquals("Mining", s.last());
		assertEquals(-1, s.toNextLevel("Mining", 300_000), "a percent alone can't give a time");
	}

	@Test
	void remindersFireInOrderAndSurviveASave() {
		Progress.Reminders r = new Progress.Reminders();
		r.add("later", 5_000);
		r.add("sooner", 1_000);
		assertEquals(List.of("sooner", "later"), r.upcoming().stream().map(Progress.Reminder::text).toList());
		assertTrue(r.due(500).isEmpty());
		assertEquals("sooner", r.due(1_000).getFirst().text());
		assertEquals(1, r.upcoming().size());
		Progress.Reminders copy = new Progress.Reminders();
		copy.load(r.toJson());
		assertEquals(r.upcoming(), copy.upcoming());
	}

	@Test
	void reminderDelays() {
		assertEquals(150 * 60_000L, ProgressModules.parseDelay("2h30m"));
		assertEquals(45 * 60_000L, ProgressModules.parseDelay("45"), "a plain number is minutes");
		assertEquals(86_400_000L, ProgressModules.parseDelay("1d"));
		assertEquals(-1, ProgressModules.parseDelay("soon"));
	}

	@Test
	void cookieBuffWidget() {
		assertTrue(ProgressModules.CookieReminder.inactive(List.of("Cookie Buff:", "Not active! Obtain booster cookies from the community shop in the hub.")));
		assertFalse(ProgressModules.CookieReminder.inactive(List.of("Cookie Buff:", "3d 4h 12m")));
		assertNull(ProgressModules.CookieReminder.inactive(List.of()), "no widget: can't tell");
	}
}
