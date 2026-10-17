package dev.telmuad.hollowclient.skyblock.features.slayer;

import dev.telmuad.hollowclient.skyblock.parse.Patterns;
import dev.telmuad.hollowclient.skyblock.text.SbText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/** Slayer bookkeeping: kill times, bosses per hour, drops and their value, and finding your own boss. Plain Java. */
public final class Slayer {
	private Slayer() {
	}

	/** Kill times and rate since the first boss of the session. */
	public static final class Stats {
		private long spawnedAt = -1;
		private long firstAt = -1;
		private final List<Long> killMs = new ArrayList<>();

		public void spawned(long now) {
			spawnedAt = now;
			if (firstAt < 0) firstAt = now;
		}

		public void killed(long now) {
			if (spawnedAt >= 0) killMs.add(now - spawnedAt);
			spawnedAt = -1;
		}

		public void failed() {
			spawnedAt = -1;
		}

		/** How long the current boss has been up (-1 when none is). */
		public long fightMs(long now) {
			return spawnedAt < 0 ? -1 : now - spawnedAt;
		}

		public int kills() {
			return killMs.size();
		}

		public long averageKillMs() {
			return killMs.isEmpty() ? -1 : (long) killMs.stream().mapToLong(Long::longValue).average().orElse(-1);
		}

		public long bestKillMs() {
			return killMs.stream().mapToLong(Long::longValue).min().orElse(-1);
		}

		public double perHour(long now) {
			if (firstAt < 0 || now - firstAt < 60_000) return Double.NaN;
			return kills() * 3_600_000.0 / (now - firstAt);
		}

		public long since() {
			return firstAt;
		}
	}

	/** Drops during the session, with what they're worth. */
	public static final class Drops {
		private final Map<String, Integer> counts = new LinkedHashMap<>();
		private final Map<String, Double> values = new LinkedHashMap<>();

		public void add(String item, int count, double eachValue) {
			counts.merge(item, count, Integer::sum);
			values.merge(item, eachValue * count, Double::sum);
		}

		public double total() {
			return values.values().stream().mapToDouble(Double::doubleValue).sum();
		}

		public Map<String, Integer> counts() {
			return Map.copyOf(counts);
		}

		public List<String> byValue() {
			List<String> out = new ArrayList<>(counts.keySet());
			out.sort(Comparator.comparingDouble((String k) -> values.getOrDefault(k, 0.0)).reversed());
			return out;
		}

		public double value(String item) {
			return values.getOrDefault(item, 0.0);
		}
	}

	/** An armor stand label or a mob, as plain positions. */
	public record Thing(int id, double x, double y, double z, String name) {
	}

	/** Your boss: the labels above it (top first) and the mob it belongs to (-1 if not found). */
	public record Boss(List<String> labels, int entityId, double x, double y, double z, String hp) {
	}

	/**
	 * Finds your boss from the labels floating around: the "Spawned by: you" tag sits over your boss, with the boss's
	 * name/health tag and any extra lines just above it, and the boss itself just below.
	 */
	public static Boss findBoss(List<Thing> labels, List<Thing> mobs, String playerName) {
		Patterns p = Patterns.of("entities");
		Thing mine = null;
		for (Thing t : labels) {
			Matcher m = p.find("spawned_by", SbText.clean(t.name()));
			if (m != null && m.group("name").equalsIgnoreCase(playerName)) {
				mine = t;
				break;
			}
		}
		if (mine == null) return null;
		List<Thing> above = new ArrayList<>();
		for (Thing t : labels) {
			if (t == mine) continue;
			double dx = t.x() - mine.x(), dz = t.z() - mine.z(), dy = t.y() - mine.y();
			if (dx * dx + dz * dz <= 2.25 && dy > -0.1 && dy < 3.5 && !SbText.clean(t.name()).isEmpty()) above.add(t);
		}
		above.sort(Comparator.comparingDouble(Thing::y).reversed());
		List<String> names = new ArrayList<>();
		String hp = "";
		for (Thing t : above) {
			String clean = SbText.clean(t.name());
			names.add(clean);
			Matcher m = p.find("boss_hp", clean);
			if (m != null && hp.isEmpty()) hp = m.group("hp");
		}
		Thing best = null;
		double bestD = 9;
		for (Thing mob : mobs) {
			double dx = mob.x() - mine.x(), dz = mob.z() - mine.z(), dy = mine.y() - mob.y();
			double d = dx * dx + dz * dz + Math.max(0, dy - 3) * Math.max(0, dy - 3);
			if (dy > -1 && dy < 6 && d < bestD) {
				bestD = d;
				best = mob;
			}
		}
		return new Boss(names, best == null ? -1 : best.id(), mine.x(), mine.y(), mine.z(), hp);
	}
}
