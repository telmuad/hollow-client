package dev.telmuad.hollowclient.skyblock.features.fishing;

import dev.telmuad.hollowclient.skyblock.parse.Patterns;
import dev.telmuad.hollowclient.skyblock.text.SbText;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

/** Fishing bookkeeping: rare sea creature catches, sea creatures nearby, trophy fish. Plain Java. */
public final class Fishing {
	private Fishing() {
	}

	private static Patterns p() {
		return Patterns.of("fishing");
	}

	/** The Legendary or Mythic sea creature a chat line announces you caught, or null. */
	public static String rareCatch(String line) {
		String clean = SbText.clean(line).trim();
		Patterns p = p();
		for (String name : p.names()) {
			if (name.startsWith("catch_") && p.matches(name, clean) != null) return name.substring("catch_".length());
		}
		return null;
	}

	public static boolean mythic(String creature) {
		return p().matches("mythic", creature) != null;
	}

	public static boolean capReached(String line) {
		return p().matches("cap", SbText.clean(line).trim()) != null;
	}

	public static boolean doubleHook(String line) {
		return p().matches("double_hook", SbText.clean(line).trim()) != null;
	}

	/** The sea creature a name tag belongs to ("[Lv45] Sea Walker 1,000/1,000❤"), or null. */
	public static String seaCreature(String nameTag) {
		Matcher m = p().find("sea_creature_tag", SbText.clean(nameTag));
		return m == null ? null : m.group("name");
	}

	/** Whether a floating label is the "!!!" Hypixel puts over your bobber when a fish bites. */
	public static boolean bite(String label) {
		return p().matches("bite", SbText.clean(label).trim()) != null;
	}

	// ------------------------------------------------------------------------------------------------ rare catches

	/** Rare catches this session: how many of each and when the last one was. */
	public static final class Catches {
		private final Map<String, Integer> counts = new LinkedHashMap<>();
		private final Map<String, Long> last = new LinkedHashMap<>();

		public void caught(String creature, long now) {
			counts.merge(creature, 1, Integer::sum);
			last.put(creature, now);
		}

		public Map<String, Integer> counts() {
			return Map.copyOf(counts);
		}

		public long lastAt(String creature) {
			return last.getOrDefault(creature, -1L);
		}

		/** Newest first. */
		public List<String> recent() {
			List<String> out = new ArrayList<>(last.keySet());
			out.sort((a, b) -> Long.compare(last.get(b), last.get(a)));
			return out;
		}
	}

	// ------------------------------------------------------------------------------------------------ trophy fish

	public enum Tier {
		BRONZE(0xFFCD7F32), SILVER(0xFFC0C6CF), GOLD(0xFFF5C542), DIAMOND(0xFF5BE0E8);

		public final int color;

		Tier(int color) {
			this.color = color;
		}
	}

	/** A trophy fish line: "TROPHY FISH! You caught a Blobfish BRONZE." */
	public record Trophy(String fish, Tier tier) {
	}

	public static Trophy trophy(String line) {
		Matcher m = p().matches("trophy_fish", SbText.clean(line).trim());
		return m == null ? null : new Trophy(m.group("fish"), Tier.valueOf(m.group("tier").toUpperCase(Locale.ROOT)));
	}

	/** Trophy fish caught, by fish and tier. */
	public static final class Trophies {
		private final Map<String, EnumMap<Tier, Integer>> counts = new LinkedHashMap<>();

		public void add(Trophy t) {
			counts.computeIfAbsent(t.fish(), k -> new EnumMap<>(Tier.class)).merge(t.tier(), 1, Integer::sum);
		}

		public int count(String fish, Tier tier) {
			EnumMap<Tier, Integer> m = counts.get(fish);
			return m == null ? 0 : m.getOrDefault(tier, 0);
		}

		public int total(Tier tier) {
			return counts.values().stream().mapToInt(m -> m.getOrDefault(tier, 0)).sum();
		}

		public int total() {
			return counts.values().stream().flatMap(m -> m.values().stream()).mapToInt(Integer::intValue).sum();
		}

		public Map<String, Map<Tier, Integer>> all() {
			Map<String, Map<Tier, Integer>> out = new LinkedHashMap<>();
			counts.forEach((k, v) -> out.put(k, Map.copyOf(v)));
			return out;
		}

		public void clear() {
			counts.clear();
		}
	}
}
