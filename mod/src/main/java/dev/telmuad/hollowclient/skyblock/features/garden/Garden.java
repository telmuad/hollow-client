package dev.telmuad.hollowclient.skyblock.features.garden;

import com.google.gson.JsonObject;
import dev.telmuad.hollowclient.skyblock.parse.Patterns;
import dev.telmuad.hollowclient.skyblock.parse.TabListParser;
import dev.telmuad.hollowclient.skyblock.text.SbText;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.regex.Matcher;

/** Garden bookkeeping: visitors, a visitor's offer, pests, farming speed and the tool's crop counter. Plain Java. */
public final class Garden {
	private Garden() {
	}

	// ------------------------------------------------------------------------------------------------ visitors

	/** Visitor names from the tab list's Visitors widget (its first line is "Visitors: (n)"). */
	public static List<String> visitors(TabListParser.TabInfo tab) {
		List<String> lines = tab.widget("Visitors");
		List<String> out = new ArrayList<>();
		for (int i = 1; i < lines.size(); i++) {
			String l = lines.get(i).strip();
			if (!l.isEmpty() && !l.contains(":")) out.add(l);
		}
		return out;
	}

	/** What a visitor asks for and gives, read from the "Accept Offer" item's lore. */
	public record Offer(Map<String, Long> items, long copper, List<String> rewards) {
		/** What the items cost on the bazaar (insta-buy); empty when one has no price. */
		public OptionalDouble cost(Function<String, OptionalDouble> priceByName) {
			double total = 0;
			for (Map.Entry<String, Long> e : items.entrySet()) {
				OptionalDouble p = priceByName.apply(e.getKey());
				if (p.isEmpty()) return OptionalDouble.empty();
				total += p.getAsDouble() * e.getValue();
			}
			return OptionalDouble.of(total);
		}
	}

	public static Offer offer(List<String> loreLines) {
		Patterns p = Patterns.of("menus");
		Map<String, Long> items = new LinkedHashMap<>();
		List<String> rewards = new ArrayList<>();
		long copper = 0;
		int section = 0; // 1 = items, 2 = rewards
		for (String raw : loreLines) {
			String l = SbText.clean(raw);
			if (l.isEmpty()) {
				section = 0;
				continue;
			}
			if (p.matches("visitor_items_header", l) != null) {
				section = 1;
				continue;
			}
			if (p.matches("visitor_rewards_header", l) != null) {
				section = 2;
				continue;
			}
			if (section == 1) {
				Matcher m = p.matches("visitor_item", l);
				if (m != null) items.merge(m.group("name").strip(), Math.max(1, SbText.longOr(Patterns.group(m, "count"), 1)), Long::sum);
			} else if (section == 2) {
				Matcher c = p.find("visitor_copper", l);
				if (c != null) copper += SbText.longOr(c.group("amount"), 0);
				rewards.add(l);
			}
		}
		return new Offer(items, copper, rewards);
	}

	// ------------------------------------------------------------------------------------------------ pests

	/** Pests on the Garden: how many and in which plots, from chat (and cleared when the tab says none). */
	public static final class Pests {
		private final Map<String, Integer> byPlot = new LinkedHashMap<>();
		private long lastSpawn;

		public void spawned(String plot, int count, long now) {
			byPlot.merge(plot.strip(), Math.max(1, count), Integer::sum);
			lastSpawn = now;
		}

		public void offline(String plots, long now) {
			for (String p : plots.replace(" and ", ", ").split(",")) if (!p.isBlank()) spawned(p, 1, now);
		}

		/** The tab list's pests widget, when shown, is the truth: "Alive: n" and "Plots: a, b". */
		public void fromTab(TabListParser.TabInfo tab) {
			String alive = tab.value("alive");
			if (alive.isEmpty()) return;
			int n = (int) SbText.longOr(alive, -1);
			if (n == 0) {
				byPlot.clear();
				return;
			}
			String plots = tab.value("plots");
			if (!plots.isEmpty() && n > 0) {
				byPlot.clear();
				String[] list = plots.split(",");
				for (String p : list) byPlot.put(p.strip(), 0);
				// spread the count over the plots we know (the widget doesn't say how many per plot)
				int i = 0;
				for (String key : byPlot.keySet()) byPlot.put(key, n / list.length + (i++ < n % list.length ? 1 : 0));
			}
		}

		public void killedAll() {
			byPlot.clear();
		}

		public int alive() {
			return byPlot.values().stream().mapToInt(Integer::intValue).sum();
		}

		public Map<String, Integer> plots() {
			return Map.copyOf(byPlot);
		}

		public long lastSpawn() {
			return lastSpawn;
		}
	}

	// ------------------------------------------------------------------------------------------------ farming

	/** Blocks broken per second over the last few seconds. */
	public static final class Speed {
		private final Deque<Long> breaks = new ArrayDeque<>();
		private final long window;

		public Speed(long windowMs) {
			this.window = windowMs;
		}

		public void broke(long now) {
			breaks.addLast(now);
			trim(now);
		}

		private void trim(long now) {
			while (!breaks.isEmpty() && now - breaks.peekFirst() > window) breaks.removeFirst();
		}

		public double perSecond(long now) {
			trim(now);
			return breaks.size() * 1000.0 / window;
		}
	}

	/** A farming tool's own counter (custom data): crops it has broken, and cultivating. -1 if it has none. */
	public static long toolCounter(JsonObject customData) {
		if (customData == null) return -1;
		for (String key : new String[]{"mined_crops", "farmed_cultivating"}) {
			if (customData.has(key) && customData.get(key).isJsonPrimitive()) {
				try {
					return customData.get(key).getAsLong();
				} catch (RuntimeException ignored) {
				}
			}
		}
		return -1;
	}
}
