package dev.telmuad.hollowclient.skyblock.features.hunting;

import dev.telmuad.hollowclient.skyblock.parse.Patterns;
import dev.telmuad.hollowclient.skyblock.text.SbText;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/** Hunting bookkeeping: your Huntraps, from their chat lines. Plain Java. */
public final class Hunting {
	private Hunting() {
	}

	/** The shortest catch time the wiki gives (a common shard, 8 hours, before trap tier and location bonuses). */
	public static final long COMMON_CATCH_MS = 8 * 3_600_000L;

	/** Your placed traps (when each was placed, oldest first; -1 for ones placed before Hollow saw it) and catches. */
	public static final class Traps {
		private final List<Long> placedAt = new ArrayList<>();
		private int max = 5;
		private int caught;
		private long caughtAt = -1;

		/** Feeds one chat line; true if it was a trap line. */
		public boolean chat(String line, long now) {
			Patterns p = Patterns.of("hunting");
			String clean = SbText.clean(line).trim();
			Matcher m;
			if ((m = p.matches("trap_placed", clean)) != null) {
				placed(Integer.parseInt(m.group("placed")), Integer.parseInt(m.group("max")), now);
				return true;
			}
			if ((m = p.matches("traps_caught", clean)) != null) {
				caught = Integer.parseInt(m.group("count"));
				caughtAt = now;
				return true;
			}
			if (p.matches("trap_picked_up", clean) != null) {
				if (!placedAt.isEmpty()) placedAt.remove(0);
				if (caught > 0) caught--;
				return true;
			}
			return false;
		}

		/** "(n/max traps placed)": the count after placing, so it also corrects traps collected without a message. */
		public void placed(int count, int max, long now) {
			this.max = max;
			while (placedAt.size() > Math.max(0, count - 1)) placedAt.remove(0);
			while (placedAt.size() < count - 1) placedAt.add(0, -1L);
			placedAt.add(now);
		}

		public int count() {
			return placedAt.size();
		}

		public int max() {
			return max;
		}

		public int caught() {
			return caught;
		}

		public long caughtAt() {
			return caughtAt;
		}

		/** When the oldest trap you've seen placed went down, -1 if none is known. */
		public long oldest() {
			for (long t : placedAt) if (t >= 0) return t;
			return -1;
		}

		public List<Long> placedAt() {
			return List.copyOf(placedAt);
		}

		public void restore(List<Long> placed, int max, int caught, long caughtAt) {
			placedAt.clear();
			placedAt.addAll(placed);
			this.max = max;
			this.caught = caught;
			this.caughtAt = caughtAt;
		}
	}
}
