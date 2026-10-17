package dev.telmuad.hollowclient.skyblock.parse;

import dev.telmuad.hollowclient.skyblock.text.SbText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Reads Hypixel's tab list widgets. The tab list is a grid of fake players whose names are the widget lines, in
 * columns of 20. A widget starts with a "Title:" line (sometimes with a value, like "Pet:" or "Visitors: (3)") and
 * goes on with indented lines. This keeps every widget as a list of lines, plus the plain "Key: value" pairs, plus the
 * values SkyBlock Mode uses everywhere (area, profile, game mode, ...). Plain Java.
 */
public final class TabListParser {
	public enum GameMode {
		NORMAL(""), IRONMAN("♲"), STRANDED("☀"), BINGO("Ⓑ");

		public final String symbol;

		GameMode(String symbol) {
			this.symbol = symbol;
		}

		static GameMode fromSymbol(String s) {
			if (s != null) for (GameMode g : values()) if (!g.symbol.isEmpty() && g.symbol.equals(s)) return g;
			return NORMAL;
		}
	}

	/**
	 * @param values   "Key: value" pairs (key lower-cased), first one wins
	 * @param widgets  widget title (as shown, without the colon) -> its lines (including the title line)
	 * @param players  how many players the "Players (n)" header says, -1 if none
	 */
	public record TabInfo(String area, String server, String profile, GameMode gameMode, boolean coop, int sbLevel,
						  String bank, String pet, int petLevel, Map<String, String> values, Map<String, List<String>> widgets,
						  int players, List<String> lines) {
		public static final TabInfo EMPTY = new TabInfo("", "", "", GameMode.NORMAL, false, -1, "", "", -1, Map.of(),
				Map.of(), -1, List.of());

		public String value(String key) {
			return values.getOrDefault(key.toLowerCase(Locale.ROOT), "");
		}

		public List<String> widget(String title) {
			for (Map.Entry<String, List<String>> e : widgets.entrySet()) {
				if (e.getKey().equalsIgnoreCase(title)) return e.getValue();
			}
			return List.of();
		}
	}

	private TabListParser() {
	}

	public static TabInfo parse(List<String> rawLines) {
		Patterns p = Patterns.of("tab");
		List<String> lines = new ArrayList<>();
		for (String raw : rawLines) lines.add(SbText.strip(raw).stripTrailing());

		Map<String, String> values = new LinkedHashMap<>();
		Map<String, List<String>> widgets = new LinkedHashMap<>();
		List<String> current = null;
		int players = -1;
		String area = "", server = "", profile = "", bank = "", pet = "";
		GameMode mode = GameMode.NORMAL;
		boolean coop = false;
		int sbLevel = -1, petLevel = -1;
		boolean wantPet = false;

		for (String line : lines) {
			String c = line.strip();
			if (c.isEmpty()) {
				current = null;
				continue;
			}
			boolean indented = line.startsWith(" ");
			Matcher m;
			if ((m = p.matches("players_header", c)) != null) {
				players = Integer.parseInt(m.group("count"));
				current = null;
				continue;
			}
			if (!indented && p.matches("column_header", c) != null) {
				current = null;
				continue;
			}
			Matcher kv = p.matches("key_value", c);
			if (kv != null) {
				String key = kv.group("key").strip();
				String value = kv.group("value") == null ? "" : kv.group("value").strip();
				values.putIfAbsent(key.toLowerCase(Locale.ROOT), value);
				if (!indented) {
					current = new ArrayList<>();
					current.add(c);
					widgets.putIfAbsent(key, current);
				} else if (current != null) {
					current.add(c);
				}
			} else if (current != null) {
				current.add(c);
			}

			if ((m = p.matches("area", c)) != null) area = m.group("area").strip();
			else if ((m = p.matches("server", c)) != null) server = m.group("server");
			else if ((m = p.matches("profile", c)) != null) {
				profile = m.group("name");
				mode = GameMode.fromSymbol(Patterns.group(m, "symbol"));
				coop = "Co-op".equals(Patterns.group(m, "kind"));
			} else if ((m = p.matches("sb_level", c)) != null) sbLevel = Integer.parseInt(m.group("level"));
			else if ((m = p.matches("bank", c)) != null) bank = m.group("amount");

			if (c.equalsIgnoreCase("Pet:")) {
				wantPet = true;
			} else if (wantPet) {
				wantPet = false;
				if ((m = p.matches("pet", c)) != null) {
					pet = m.group("name").strip();
					petLevel = Integer.parseInt(m.group("level"));
				} else {
					pet = c;
				}
			}
		}
		Map<String, List<String>> frozen = new LinkedHashMap<>();
		widgets.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
		return new TabInfo(area, server, profile, mode, coop, sbLevel, bank, pet, petLevel, Map.copyOf(values), frozen,
				players, List.copyOf(lines));
	}
}
