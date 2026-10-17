package dev.telmuad.hollowclient.skyblock.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The SkyBlock calendar as plain math (same as the launcher's SkyBlock page): the SkyBlock epoch is Early Spring 1st,
 * Year 1, at unix time 1560275700; a day is 20 minutes, a month 31 days, a year 12 months. Event dates from
 * hypixelskyblock.minecraft.wiki (Time, Calendar), checked 2026-10-16. Times are unix seconds. Plain Java.
 */
public final class SkyCalendar {
	public static final long EPOCH = 1560275700L;
	public static final long DAY = 1200, HOUR = DAY / 24, MONTH = 31 * DAY, YEAR = 12 * MONTH;
	public static final String[] MONTHS = {"Early Spring", "Spring", "Late Spring", "Early Summer", "Summer", "Late Summer",
			"Early Autumn", "Autumn", "Late Autumn", "Early Winter", "Winter", "Late Winter"};

	public record SkyDate(int year, int month, int day, int hour, int minute) {
		@Override
		public String toString() {
			int d = day;
			String suffix = d % 100 >= 10 && d % 100 <= 20 ? "th" : switch (d % 10) {
				case 1 -> "st";
				case 2 -> "nd";
				case 3 -> "rd";
				default -> "th";
			};
			return MONTHS[month] + " " + d + suffix + ", Year " + year;
		}

		public String time() {
			int h12 = hour % 12 == 0 ? 12 : hour % 12;
			return String.format(Locale.ROOT, "%d:%02d%s", h12, minute / 10 * 10, hour < 12 ? "am" : "pm");
		}
	}

	public record Event(String name, long start, long end, String detail) {
		public boolean running(long now) {
			return start <= now && now < end;
		}
	}

	private record Yearly(String name, int m1, int d1, int m2, int d2, String detail) {
	}

	private static final List<Yearly> YEARLY = List.of(
			new Yearly("Hoppity's Hunt", 0, 1, 2, 31, "Chocolate rabbit eggs all over SkyBlock"),
			new Yearly("Election closes", 2, 27, 2, 27, "The new mayor takes office"),
			new Yearly("Traveling Zoo", 3, 1, 3, 3, "Oringo sells pets in the Hub"),
			new Yearly("Election booth opens", 5, 27, 5, 27, "Vote for the next mayor"),
			new Yearly("Harvest Feast", 6, 1, 8, 31, "Autumn farming event"),
			new Yearly("Spooky Festival", 7, 29, 7, 31, "Candy and Trick or Treat mobs"),
			new Yearly("Traveling Zoo", 9, 1, 9, 3, "Oringo sells pets in the Hub"),
			new Yearly("Season of Jerry", 11, 24, 11, 26, "Jerry's Workshop defense"),
			new Yearly("New Year Celebration", 11, 29, 11, 31, "New Year Cake from Baker"));
	private static final int[] CULT_DAYS = {7, 14, 21, 28};

	private SkyCalendar() {
	}

	public static SkyDate date(long unixSeconds) {
		long s = unixSeconds - EPOCH;
		long year = Math.floorDiv(s, YEAR);
		s -= year * YEAR;
		long month = s / MONTH;
		s -= month * MONTH;
		long day = s / DAY;
		s -= day * DAY;
		long hour = s / HOUR;
		s -= hour * HOUR;
		return new SkyDate((int) year + 1, (int) month, (int) day + 1, (int) hour, (int) (s * 60 / HOUR));
	}

	public static long time(int year, int month, int day, int hour) {
		return EPOCH + (year - 1L) * YEAR + month * MONTH + (day - 1L) * DAY + hour * HOUR;
	}

	private static long[] monthsLater(SkyDate d, int n) {
		int m = d.month() + n;
		return new long[]{d.year() + m / 12, m % 12};
	}

	/** Running and upcoming events, soonest first ({@code mayorPerks} adds Marina's Fishing Festivals). */
	public static List<Event> events(long now, Set<String> mayorPerks, int count) {
		List<Event> out = new ArrayList<>();
		SkyDate today = date(now);
		for (int y = today.year(); y <= today.year() + 1; y++) {
			for (Yearly e : YEARLY) {
				long start = time(y, e.m1, e.d1, 0);
				long end = time(y, e.m2, e.d2, 0) + DAY;
				if (end > now) out.add(new Event(e.name, start, end, e.detail));
			}
		}
		boolean fishing = mayorPerks != null && mayorPerks.stream().anyMatch(p -> p.equalsIgnoreCase("Fishing Festival"));
		if (fishing) {
			for (int i = 0; i < 3; i++) {
				long[] ym = monthsLater(today, i);
				long start = time((int) ym[0], (int) ym[1], 1, 0);
				if (start + 3 * DAY > now) out.add(new Event("Fishing Festival", start, start + 3 * DAY, "Marina's sharks"));
			}
		}
		long k = Math.floorDiv(now - EPOCH, 3 * DAY);
		long da = EPOCH + k * 3 * DAY;
		if (da + 300 <= now) da += 3 * DAY;
		out.add(new Event("Dark Auction", da, da + 300, "Sirius in the Hub"));
		outer:
		for (int i = 0; i < 2; i++) {
			long[] ym = monthsLater(today, i);
			for (int d : CULT_DAYS) {
				long start = time((int) ym[0], (int) ym[1], d, 0);
				if (start + 6 * HOUR > now) {
					out.add(new Event("Cult of the Fallen Star", start, start + 6 * HOUR, "Dwarven Mines, 0 AM to 6 AM"));
					break outer;
				}
			}
		}
		Event jacob = nextJacob(now);
		out.add(jacob);
		out.sort(Comparator.comparing((Event e) -> e.start() > now).thenComparingLong(Event::start));
		return out.subList(0, Math.min(count, out.size()));
	}

	/** The running or next Jacob's Farming Contest (every real hour at :15, for 20 minutes). */
	public static Event nextJacob(long now) {
		long k = Math.floorDiv(now - EPOCH - DAY, 3 * DAY);
		long start = EPOCH + DAY + k * 3 * DAY;
		if (start + DAY <= now) start += 3 * DAY;
		return new Event("Jacob's Farming Contest", start, start + DAY, "Garden");
	}
}
