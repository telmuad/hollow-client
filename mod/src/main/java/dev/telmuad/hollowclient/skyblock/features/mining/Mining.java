package dev.telmuad.hollowclient.skyblock.features.mining;

import dev.telmuad.hollowclient.skyblock.parse.Patterns;
import dev.telmuad.hollowclient.skyblock.parse.SidebarParser;
import dev.telmuad.hollowclient.skyblock.parse.TabListParser;
import dev.telmuad.hollowclient.skyblock.text.SbText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

/** Mining bookkeeping read from the tab list, scoreboard, chat and action bar. Plain Java. */
public final class Mining {
	private Mining() {
	}

	private static Patterns p() {
		return Patterns.of("mining");
	}

	// ------------------------------------------------------------------------------------------------ commissions

	/** One commission from the Commissions widget: progress 0–100, or done (claim it at the Emissary or Royal Pigeon). */
	public record Commission(String name, double percent, boolean done) {
	}

	public static List<Commission> commissions(TabListParser.TabInfo tab) {
		List<Commission> out = new ArrayList<>();
		Patterns tp = Patterns.of("tab");
		for (String line : tab.widget("Commissions")) {
			Matcher m = tp.matches("commission", SbText.clean(line).trim());
			if (m == null) continue;
			String progress = m.group("progress");
			boolean done = progress.equals("DONE");
			out.add(new Commission(m.group("name"), done ? 100 : SbText.number(progress.replace("%", "")), done));
		}
		return out;
	}

	// ------------------------------------------------------------------------------------------------ powder

	public enum Powder {
		MITHRIL("Mithril", 0xFF4FD1A1), GEMSTONE("Gemstone", 0xFFE070E0), GLACITE("Glacite", 0xFF7FD6F5);

		public final String label;
		public final int color;

		Powder(String label, int color) {
			this.label = label;
			this.color = color;
		}
	}

	/** Powder amounts from the Powders widget (missing types are left out). */
	public static Map<Powder, Long> powders(TabListParser.TabInfo tab) {
		Map<Powder, Long> out = new LinkedHashMap<>();
		for (String line : tab.widget("Powders")) {
			Matcher m = p().find("powder", SbText.clean(line));
			if (m == null) continue;
			for (Powder pw : Powder.values()) {
				if (pw.label.equalsIgnoreCase(m.group("type"))) out.put(pw, SbText.longOr(m.group("amount"), 0));
			}
		}
		return out;
	}

	/** Counts what you gain of something that's only ever shown as a total (powder): spending doesn't count against it. */
	public static final class Gain {
		private long last = -1;
		private long gained;
		private long startedAt = -1;

		public void update(long now, long value) {
			if (value < 0) return;
			if (last >= 0 && value > last) {
				if (startedAt < 0) startedAt = now;
				gained += value - last;
			}
			last = value;
		}

		public long gained() {
			return gained;
		}

		/** Per hour, once there's been a minute of gains. */
		public double perHour(long now) {
			if (startedAt < 0 || now - startedAt < 60_000) return Double.NaN;
			return gained * 3_600_000.0 / (now - startedAt);
		}

		public void reset() {
			last = -1;
			gained = 0;
			startedAt = -1;
		}
	}

	// ------------------------------------------------------------------------------------------------ pickaxe abilities

	/** Base cooldowns (seconds, level 1) from the wiki's Heart of the Mountain page. */
	public static final Map<String, Integer> COOLDOWNS = Map.of(
			"Mining Speed Boost", 120, "Pickobulus", 60, "Tunnel Vision", 120, "Maniac Miner", 120,
			"Gemstone Infusion", 120, "Sheer Force", 120);

	/**
	 * When your pickaxe ability is ready again. Cooldown perks (Sky Mall, HotM levels) shorten it, so the time from
	 * "used" is an estimate until Hypixel says it's available.
	 */
	public static final class Ability {
		private String name = "";
		private long readyAt = -1;
		private boolean estimated;

		public void used(String ability, long now) {
			name = ability;
			readyAt = now + COOLDOWNS.getOrDefault(ability, 120) * 1000L;
			estimated = true;
		}

		public void available(String ability, long now) {
			name = ability;
			readyAt = now;
			estimated = false;
		}

		public String name() {
			return name;
		}

		/** Milliseconds until ready (0 when ready, -1 when no ability has been seen). */
		public long left(long now) {
			if (readyAt < 0) return -1;
			return Math.max(0, readyAt - now);
		}

		public boolean estimated() {
			return estimated;
		}
	}

	// ------------------------------------------------------------------------------------------------ forge

	/** A forge slot: what's in it and when it's done (absolute time; 0 = ready, -1 = empty). */
	public record ForgeSlot(int slot, String item, long readyAt) {
		public boolean empty() {
			return readyAt < 0;
		}
	}

	/** The Forges widget, with each time left turned into a finish time from {@code now}. */
	public static List<ForgeSlot> forge(TabListParser.TabInfo tab, long now) {
		List<ForgeSlot> out = new ArrayList<>();
		for (String line : tab.widget("Forges")) {
			String clean = SbText.clean(line).trim();
			Matcher m = p().matches("forge_slot", clean);
			if (m == null) continue;
			int slot = Integer.parseInt(m.group("slot"));
			String item = m.group("item").trim();
			String time = m.group("time") == null ? "" : m.group("time").trim();
			if (item.equalsIgnoreCase("EMPTY") || item.equalsIgnoreCase("Locked")) {
				out.add(new ForgeSlot(slot, item, -1));
			} else if (time.isEmpty() || p().matches("forge_ready", time) != null) {
				out.add(new ForgeSlot(slot, item, 0));
			} else {
				long ms = SbText.parseDuration(time);
				out.add(new ForgeSlot(slot, item, ms < 0 ? 0 : now + ms));
			}
		}
		return out;
	}

	// ------------------------------------------------------------------------------------------------ events, cold, crystals

	/** The mining event running on this server, from the tab list (empty name if none). */
	public record Event(String name, String endsIn) {
	}

	public static Event event(TabListParser.TabInfo tab) {
		String name = tab.value("event");
		if (name.isEmpty()) return new Event("", "");
		return new Event(SbText.clean(name).trim(), SbText.clean(tab.value("ends in")).trim());
	}

	/** Cold in the Glacite Tunnels and Mineshafts (0 down to -100, where you're sent back to camp); 1 if not shown. */
	public static int cold(SidebarParser.Sidebar sidebar) {
		for (String line : sidebar.lines()) {
			Matcher m = p().find("cold", SbText.clean(line));
			if (m != null) return Integer.parseInt(m.group("cold"));
		}
		return 1;
	}

	/** Heat in the Crystal Hollows' Magma Fields (0–100); -1 if not shown. */
	public static int heat(SidebarParser.Sidebar sidebar) {
		for (String line : sidebar.lines()) {
			Matcher m = p().find("heat", SbText.clean(line));
			if (m != null) return Integer.parseInt(m.group("heat"));
		}
		return -1;
	}

	/** Crystal Hollows crystals for the Nucleus: name -> state as the widget writes it (Found, Not Found, Placed). */
	public static Map<String, String> crystals(TabListParser.TabInfo tab) {
		Map<String, String> out = new LinkedHashMap<>();
		for (String line : tab.widget("Crystals")) {
			Matcher m = p().find("crystal", SbText.clean(line));
			if (m != null) out.put(m.group("name"), m.group("state").trim());
		}
		return out;
	}

	public static boolean crystalFound(String state) {
		String s = state.toLowerCase(Locale.ROOT);
		return (s.contains("found") && !s.contains("not")) || s.contains("placed") || s.contains("✔");
	}

	// ------------------------------------------------------------------------------------------------ action bar

	/** The metal detector's distance reading from the action bar, -1 if not there. */
	public static double treasureDistance(String actionBar) {
		Matcher m = p().find("treasure", SbText.clean(actionBar));
		return m == null ? -1 : SbText.number(m.group("dist"));
	}
}
