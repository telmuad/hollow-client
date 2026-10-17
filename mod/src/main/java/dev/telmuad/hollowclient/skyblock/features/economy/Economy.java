package dev.telmuad.hollowclient.skyblock.features.economy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.telmuad.hollowclient.skyblock.parse.ChatParser;
import dev.telmuad.hollowclient.skyblock.text.SbText;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The Economy area's bookkeeping: bazaar orders, bazaar price history, your auctions, coins per hour. Plain Java. */
public final class Economy {
	private Economy() {
	}

	/** Bazaar tax on sales (1.25%; the Bazaar Flipper perk lowers it). */
	public static final double BAZAAR_TAX = 0.0125;

	// ------------------------------------------------------------------------------------------------ bazaar orders

	public record Order(String kind, String item, long amount, double coins, boolean filled, long since) {
		public boolean buy() {
			return kind.equals("Buy Order");
		}
	}

	/** Your bazaar orders, from the bazaar's chat lines: set up → filled → claimed. */
	public static final class Orders {
		private final List<Order> orders = new ArrayList<>();

		public List<Order> list() {
			return List.copyOf(orders);
		}

		/** Returns the order a "filled" line refers to (for the alert), or null. */
		public Order onChat(ChatParser.ChatMatch m, long now) {
			switch (m.name()) {
				case "bazaar_setup" -> {
					orders.add(new Order(m.get("kind"), m.get("item"), SbText.longOr(m.get("amount"), 0), SbText.number(m.get("coins")), false, now));
					while (orders.size() > 28) orders.removeFirst(); // Hypixel's maximum with Bazaar Flipper
				}
				case "bazaar_filled" -> {
					for (int i = 0; i < orders.size(); i++) {
						Order o = orders.get(i);
						if (!o.filled() && o.kind().equals(m.get("kind")) && o.item().equals(m.get("item"))) {
							Order f = new Order(o.kind(), o.item(), o.amount(), o.coins(), true, o.since());
							orders.set(i, f);
							return f;
						}
					}
					Order f = new Order(m.get("kind"), m.get("item"), SbText.longOr(m.get("amount"), 0), 0, true, now);
					orders.add(f);
					return f;
				}
				case "bazaar_claimed" -> {
					for (Iterator<Order> it = orders.iterator(); it.hasNext(); ) {
						Order o = it.next();
						if (o.filled() && o.item().equals(m.get("item"))) {
							it.remove();
							break;
						}
					}
				}
				default -> {
				}
			}
			return null;
		}

		public void clear() {
			orders.clear();
		}

		public JsonArray toJson() {
			JsonArray a = new JsonArray();
			for (Order o : orders) {
				JsonObject j = new JsonObject();
				j.addProperty("kind", o.kind());
				j.addProperty("item", o.item());
				j.addProperty("amount", o.amount());
				j.addProperty("coins", o.coins());
				j.addProperty("filled", o.filled());
				j.addProperty("since", o.since());
				a.add(j);
			}
			return a;
		}

		public void load(JsonArray a) {
			orders.clear();
			if (a == null) return;
			for (JsonElement e : a) {
				if (!e.isJsonObject()) continue;
				JsonObject j = e.getAsJsonObject();
				orders.add(new Order(j.get("kind").getAsString(), j.get("item").getAsString(), j.get("amount").getAsLong(),
						j.get("coins").getAsDouble(), j.get("filled").getAsBoolean(), j.get("since").getAsLong()));
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ price history

	/** Hourly bazaar insta-buy prices per product (about two days), for sparklines and 24-hour changes. */
	public static final class History {
		public static final long STEP = 3_600_000L;
		public static final int KEEP = 48;
		private final Map<String, Deque<double[]>> byProduct = new HashMap<>();
		private long lastAt;

		/** Adds a snapshot if an hour passed since the last one. Returns true if it was added. */
		public boolean add(long now, Map<String, Double> buyPrices) {
			if (lastAt != 0 && now - lastAt < STEP) return false;
			lastAt = now;
			for (Map.Entry<String, Double> e : buyPrices.entrySet()) {
				Deque<double[]> d = byProduct.computeIfAbsent(e.getKey(), k -> new ArrayDeque<>());
				d.addLast(new double[]{now, e.getValue()});
				while (d.size() > KEEP) d.removeFirst();
			}
			return true;
		}

		public List<Double> prices(String id) {
			List<Double> out = new ArrayList<>();
			Deque<double[]> d = byProduct.get(id);
			if (d != null) for (double[] p : d) out.add(p[1]);
			return out;
		}

		/** Change since about 24 hours ago (NaN if unknown). */
		public double change24h(String id, long now) {
			Deque<double[]> d = byProduct.get(id);
			if (d == null || d.size() < 2) return Double.NaN;
			double[] last = d.peekLast();
			double[] best = null;
			for (double[] p : d) if (best == null || Math.abs((now - p[0]) - 86_400_000L) < Math.abs((now - best[0]) - 86_400_000L)) best = p;
			if (best == last || best[1] <= 0) return Double.NaN;
			return (last[1] - best[1]) / best[1];
		}

		public JsonObject toJson() {
			JsonObject o = new JsonObject();
			o.addProperty("lastAt", lastAt);
			JsonObject p = new JsonObject();
			byProduct.forEach((id, d) -> {
				JsonArray a = new JsonArray();
				for (double[] v : d) {
					JsonArray pt = new JsonArray();
					pt.add((long) v[0]);
					pt.add(v[1]);
					a.add(pt);
				}
				p.add(id, a);
			});
			o.add("products", p);
			return o;
		}

		public void load(JsonObject o) {
			byProduct.clear();
			if (o == null) return;
			lastAt = o.has("lastAt") ? o.get("lastAt").getAsLong() : 0;
			if (!o.has("products")) return;
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("products").entrySet()) {
				Deque<double[]> d = new ArrayDeque<>();
				for (JsonElement pt : e.getValue().getAsJsonArray()) {
					JsonArray a = pt.getAsJsonArray();
					d.addLast(new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble()});
				}
				byProduct.put(e.getKey(), d);
			}
		}
	}

	private static final char[] BARS = {'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};

	/** A text sparkline of the last {@code width} values. */
	public static String sparkline(List<Double> values, int width) {
		if (values.size() < 2) return "";
		List<Double> v = values.subList(Math.max(0, values.size() - width), values.size());
		double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
		for (double x : v) {
			min = Math.min(min, x);
			max = Math.max(max, x);
		}
		StringBuilder sb = new StringBuilder();
		for (double x : v) {
			int i = max - min < 1e-9 ? 3 : (int) Math.round((x - min) / (max - min) * (BARS.length - 1));
			sb.append(BARS[i]);
		}
		return sb.toString();
	}

	/** Flipping one item: buy at the top buy order, sell at the lowest sell offer, minus tax. */
	public static double flipProfit(double instaBuy, double instaSell) {
		return instaBuy * (1 - BAZAAR_TAX) - instaSell;
	}

	// ------------------------------------------------------------------------------------------------ auctions

	public record Sale(String item, String buyer, double coins, boolean collected, long at) {
	}

	/** Your auctions that sold (from "[Auction] X bought Y for Z coins") and whether you collected the coins. */
	public static final class Auctions {
		private final Deque<Sale> sales = new ArrayDeque<>();

		public Sale onChat(ChatParser.ChatMatch m, long now) {
			if (m.name().equals("auction_sold")) {
				Sale s = new Sale(m.get("item"), m.get("buyer"), SbText.number(m.get("coins")), false, now);
				sales.addFirst(s);
				while (sales.size() > 50) sales.removeLast();
				return s;
			}
			if (m.name().equals("auction_collected")) {
				List<Sale> list = new ArrayList<>(sales);
				for (int i = 0; i < list.size(); i++) {
					Sale s = list.get(i);
					if (!s.collected() && s.item().equals(m.get("item"))) {
						list.set(i, new Sale(s.item(), s.buyer(), s.coins(), true, s.at()));
						sales.clear();
						sales.addAll(list);
						break;
					}
				}
			}
			return null;
		}

		public List<Sale> list() {
			return List.copyOf(sales);
		}

		public double uncollected() {
			double t = 0;
			for (Sale s : sales) if (!s.collected()) t += s.coins();
			return t;
		}
	}

	/** An auction listing against the lowest BIN: "cheap" under 90%, "pricey" over 110%, else "fair". */
	public static String verdict(double listed, double lowestBin) {
		if (lowestBin <= 0 || listed <= 0) return "";
		double r = listed / lowestBin;
		return r <= 0.9 ? "cheap" : r >= 1.1 ? "pricey" : "fair";
	}

	// ------------------------------------------------------------------------------------------------ coins per hour

	/** Purse over time since you started tracking: coins per hour. */
	public static final class Rate {
		private final LinkedHashMap<Long, Long> samples = new LinkedHashMap<>();
		private long startAt;
		private boolean started;
		private long startPurse = -1;
		private long lastPurse = -1;

		public void reset(long now, long purse) {
			samples.clear();
			started = true;
			startAt = now;
			startPurse = purse;
			lastPurse = purse;
		}

		/** Starts timing now; the purse is filled in by the first reading if it isn't known yet. */
		public void start(long now, long purse) {
			reset(now, purse);
		}

		/** A purse reading; {@code delta} is how much it just changed (so the first change counts too). */
		public void purse(long now, long purse, long delta) {
			if (purse < 0) return;
			if (startPurse < 0) reset(started ? startAt : now, purse - delta);
			lastPurse = purse;
			samples.put(now, purse);
		}

		public long gained() {
			return startPurse < 0 ? 0 : lastPurse - startPurse;
		}

		public double perHour(long now) {
			long ms = now - startAt;
			if (startPurse < 0 || ms < 60_000) return Double.NaN;
			return gained() * 3_600_000.0 / ms;
		}

		public long since() {
			return startAt;
		}
	}
}
