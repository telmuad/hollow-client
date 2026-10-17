package dev.telmuad.hollowclient.skyblock.features.dungeons;

import dev.telmuad.hollowclient.skyblock.parse.Patterns;
import dev.telmuad.hollowclient.skyblock.parse.TabListParser;
import dev.telmuad.hollowclient.skyblock.text.SbText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/** Dungeon bookkeeping: the score estimate, run splits, keys, terminals and the blaze puzzle order. Plain Java. */
public final class Dungeons {
	private Dungeons() {
	}

	// ------------------------------------------------------------------------------------------------ score

	/** What the score needs, from the sidebar, tab list and chat. */
	public record ScoreInput(String floor, int clearedPercent, double secretsPercent, int crypts, int deaths, int failedPuzzles,
							 long elapsedMs, boolean ezpz, boolean mimic) {
	}

	public record Score(int skill, int explore, int speed, int bonus, int total, String rank) {
	}

	/** Secrets needed (fraction of all secrets) for full exploration points, by floor (wiki: Dungeon Score). */
	static double secretRequirement(String floor) {
		if (floor.startsWith("M")) return 1.0;
		return switch (floor) {
			case "F1" -> 0.3;
			case "F2" -> 0.4;
			case "F3" -> 0.5;
			case "F4" -> 0.6;
			case "F5" -> 0.7;
			case "F6" -> 0.85;
			default -> 1.0;
		};
	}

	/** Seconds taken off the run time before speed points start to fall (wiki: Dungeon Score). */
	static int speedOffset(String floor) {
		return switch (floor) {
			case "F4", "F6", "M6" -> floor.equals("M6") ? 120 : 240;
			case "F7", "M7" -> 360;
			case "M1", "M2", "M3", "M4", "M5" -> 0;
			default -> 120;
		};
	}

	static int speedPoints(long seconds) {
		if (seconds < 480) return 100;
		if (seconds < 600) return (int) Math.ceil(140 - seconds / 12.0);
		if (seconds < 840) return (int) Math.ceil(115 - seconds / 24.0);
		if (seconds < 1140) return (int) Math.ceil(108 - seconds / 30.0);
		if (seconds < 3940) return (int) Math.ceil(98.5 - seconds / 40.0);
		return 0;
	}

	/** The wiki's formula: skill + explore + speed + bonus, and the rank (S+ at 300, S from 269.5). */
	public static Score score(ScoreInput in) {
		int skill = Math.max(0, 100 - 2 * in.deaths() - 14 * in.failedPuzzles());
		int rooms = (int) Math.floor(60 * Math.min(100, in.clearedPercent()) / 100.0);
		double needed = secretRequirement(in.floor()) * 100;
		int secrets = (int) Math.min(40, Math.floor(40 * in.secretsPercent() / needed));
		int explore = rooms + secrets;
		int speed = in.elapsedMs() < 0 ? 100 : speedPoints(Math.max(0, in.elapsedMs() / 1000 - speedOffset(in.floor())));
		int bonus = Math.min(5, in.crypts()) + (in.mimic() && floorNumber(in.floor()) >= 6 ? 2 : 0) + (in.ezpz() ? 10 : 0);
		int total = skill + explore + speed + bonus;
		String rank = total >= 300 ? "S+" : total >= 270 ? "S" : total >= 230 ? "A" : total >= 160 ? "B" : total >= 100 ? "C" : "D";
		return new Score(skill, explore, speed, bonus, total, rank);
	}

	static int floorNumber(String floor) {
		return floor.length() >= 2 && Character.isDigit(floor.charAt(1)) ? floor.charAt(1) - '0' : 0;
	}

	/** Crypts, deaths, secrets and failed puzzles from the dungeon tab list. */
	public record TabStats(int crypts, int deaths, double secretsPercent, int failedPuzzles, int puzzlesLeft) {
	}

	public static TabStats tabStats(TabListParser.TabInfo tab) {
		int crypts = (int) SbText.longOr(tab.value("crypts"), 0);
		String d = tab.value("deaths").replaceAll("[()]", "");
		int deaths = (int) SbText.longOr(d, 0);
		double secrets = SbText.number(tab.value("secrets found").replace("%", ""));
		int failed = 0, left = 0;
		Patterns p = Patterns.of("tab");
		for (String line : tab.lines()) {
			Matcher m = p.find("dungeon_puzzle", line.strip());
			if (m == null) continue;
			if (m.group("state").equals("✖")) failed++;
			else if (m.group("state").equals("✦")) left++;
		}
		return new TabStats(crypts, deaths, Double.isNaN(secrets) ? 0 : secrets, failed, left);
	}

	// ------------------------------------------------------------------------------------------------ splits and keys

	/** Run splits from chat: start → blood opened → Watcher done → boss → end. Times are ms since the start. */
	public static final class Splits {
		private long start = -1;
		private final Map<String, Long> marks = new LinkedHashMap<>();

		public void onChat(String patternName, String bossName, long now) {
			switch (patternName) {
				case "dungeon_run_start", "dungeon_starting" -> {
					if (start < 0 || marks.containsKey("End")) {
						start = now;
						marks.clear();
					}
				}
				case "dungeon_blood_opened" -> mark("Blood open", now);
				case "dungeon_watcher_done" -> mark("Watcher", now);
				case "boss_message" -> {
					if (bossName != null && !bossName.equals("The Watcher") && !marks.containsKey("Boss")) mark("Boss", now);
				}
				case "dungeon_score" -> mark("End", now);
				default -> {
				}
			}
		}

		private void mark(String name, long now) {
			if (start >= 0) marks.putIfAbsent(name, now - start);
		}

		public long elapsed(long now) {
			return start < 0 ? -1 : marks.getOrDefault("End", now - start);
		}

		public Map<String, Long> marks() {
			return Map.copyOf(marks);
		}

		public List<String> order() {
			return new ArrayList<>(marks.keySet());
		}

		public boolean running() {
			return start >= 0 && !marks.containsKey("End");
		}

		public void reset() {
			start = -1;
			marks.clear();
		}
	}

	/** Keys picked up and not used yet. */
	public static final class Keys {
		private int wither;
		private boolean blood;
		private String lastBy = "";

		public void onChat(String patternName, String key, String who) {
			switch (patternName) {
				case "dungeon_key_obtained", "dungeon_key_picked" -> {
					if ("Wither".equals(key)) wither++;
					else blood = true;
					if (who != null && !who.isEmpty()) lastBy = who;
				}
				case "dungeon_door_opened" -> {
					if ("WITHER".equals(key)) wither = Math.max(0, wither - 1);
					else blood = false;
				}
				default -> {
				}
			}
		}

		public int wither() {
			return wither;
		}

		public boolean blood() {
			return blood;
		}

		public String lastBy() {
			return lastBy;
		}

		public void reset() {
			wither = 0;
			blood = false;
			lastBy = "";
		}
	}

	// ------------------------------------------------------------------------------------------------ terminals

	/** One slot of a terminal menu. {@code done}: already clicked (glint, or a green/lime pane). */
	public record Slot(int index, String material, int count, String name, boolean glint) {
		String color() {
			String m = material.substring(material.indexOf(':') + 1);
			for (String s : new String[]{"_stained_glass_pane", "_stained_glass", "_terracotta", "_concrete", "_wool", "_dye", "_carpet", "_banner"}) {
				if (m.endsWith(s)) return m.substring(0, m.length() - s.length());
			}
			return "";
		}
	}

	public enum Terminal { ORDER, COLOR, NAME, PANES, RUBIX }

	public record Solution(List<Integer> highlight, Map<Integer, Integer> clicks) {
	}

	private static final List<String> RUBIX_CYCLE = List.of("red", "orange", "yellow", "green", "blue");

	public static Terminal terminal(String title) {
		Patterns p = Patterns.of("menus");
		if (p.matches("terminal_order", title) != null) return Terminal.ORDER;
		if (p.matches("terminal_color", title) != null) return Terminal.COLOR;
		if (p.matches("terminal_name", title) != null) return Terminal.NAME;
		if (p.matches("terminal_panes", title) != null) return Terminal.PANES;
		if (p.matches("terminal_rubix", title) != null) return Terminal.RUBIX;
		return null;
	}

	/** What to click (highlighted only; the player clicks). */
	public static Solution solve(String title, List<Slot> slots) {
		Terminal t = terminal(title);
		List<Integer> highlight = new ArrayList<>();
		Map<Integer, Integer> clicks = new LinkedHashMap<>();
		if (t == null) return new Solution(highlight, clicks);
		Patterns p = Patterns.of("menus");
		switch (t) {
			case ORDER -> {
				// the lowest red pane (its stack size is its number) is next; clicked ones turn lime
				slots.stream().filter(s -> s.material().endsWith("red_stained_glass_pane"))
						.min(Comparator.comparingInt(Slot::count)).ifPresent(s -> highlight.add(s.index()));
			}
			case COLOR -> {
				Matcher m = p.matches("terminal_color", title);
				String want = m.group("color").strip().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
				if (want.equals("silver")) want = "light_gray";
				for (Slot s : slots) {
					String c = s.color();
					boolean match = c.equals(want) || (want.equals("white") && s.material().endsWith("bone_meal"))
							|| (want.equals("black") && s.material().endsWith("ink_sac")) || (want.equals("blue") && s.material().endsWith("lapis_lazuli"))
							|| (want.equals("brown") && s.material().endsWith("cocoa_beans"));
					if (match && !s.glint()) highlight.add(s.index());
				}
			}
			case NAME -> {
				Matcher m = p.matches("terminal_name", title);
				String letter = m.group("letter").toLowerCase(java.util.Locale.ROOT);
				for (Slot s : slots) {
					String n = SbText.clean(s.name()).toLowerCase(java.util.Locale.ROOT);
					if (!n.isEmpty() && n.startsWith(letter) && !s.glint() && !s.material().endsWith("glass_pane")) highlight.add(s.index());
				}
			}
			case PANES -> {
				for (Slot s : slots) if (s.material().endsWith("red_stained_glass_pane")) highlight.add(s.index());
			}
			case RUBIX -> {
				// every pane must reach the same color; left-click moves one step along the cycle
				List<Slot> panes = slots.stream().filter(s -> RUBIX_CYCLE.contains(s.color()) && s.material().endsWith("_stained_glass_pane")).toList();
				int best = Integer.MAX_VALUE;
				int target = 0;
				for (int ti = 0; ti < RUBIX_CYCLE.size(); ti++) {
					int sum = 0;
					for (Slot s : panes) sum += Math.floorMod(ti - RUBIX_CYCLE.indexOf(s.color()), RUBIX_CYCLE.size());
					if (sum < best) {
						best = sum;
						target = ti;
					}
				}
				for (Slot s : panes) {
					int n = Math.floorMod(target - RUBIX_CYCLE.indexOf(s.color()), RUBIX_CYCLE.size());
					if (n > 0) {
						clicks.put(s.index(), n);
						highlight.add(s.index());
					}
				}
			}
		}
		return new Solution(highlight, clicks);
	}

	// ------------------------------------------------------------------------------------------------ blaze puzzle

	/** Visible blazes by health (lowest first); the room says whether to go lowest-first or highest-first. */
	public static List<Integer> blazeOrder(Map<Integer, Integer> healthByEntity) {
		List<Integer> ids = new ArrayList<>(healthByEntity.keySet());
		ids.sort(Comparator.comparingInt(healthByEntity::get));
		return ids;
	}
}
