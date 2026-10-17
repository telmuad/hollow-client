package dev.telmuad.hollowclient.skyblock.parse;

import dev.telmuad.hollowclient.skyblock.text.SbText;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/** Reads the scoreboard sidebar: title and lines as the server sent them (formatting codes allowed). Plain Java. */
public final class SidebarParser {
	/** What the sidebar says. Number fields are -1 when the line isn't there. */
	public record Sidebar(boolean skyblock, boolean guest, boolean coop, String serverId, String skyDate, String time,
						  String area, boolean rift, long purse, long purseChange, boolean piggy, long bits, long motes,
						  long copper, long northStars, String objective, Slayer slayer, Dungeon dungeon, String kuudraTier,
						  List<String> lines) {
		public static final Sidebar EMPTY = new Sidebar(false, false, false, "", "", "", "", false, -1, 0, false, -1, -1,
				-1, -1, "", null, null, "", List.of());
	}

	/** The slayer quest box. {@code current}/{@code needed} are -1 while the boss is up. */
	public record Slayer(String boss, int tier, long current, long needed, String unit, boolean bossUp, boolean done) {
	}

	/** Dungeon run info: floor like "F7"/"M5"/"E", cleared percent, time elapsed (ms, -1 if unknown). */
	public record Dungeon(String floor, int cleared, long elapsedMs) {
	}

	private SidebarParser() {
	}

	public static Sidebar parse(String title, List<String> rawLines) {
		Patterns p = Patterns.of("sidebar");
		String t = SbText.clean(title);
		boolean skyblock = p.find("title", t) != null;
		List<String> lines = new ArrayList<>();
		for (String raw : rawLines) {
			String c = SbText.clean(raw);
			if (!c.isEmpty()) lines.add(c);
		}
		if (!skyblock) return new Sidebar(false, false, false, "", "", "", "", false, -1, 0, false, -1, -1, -1, -1, "",
				null, null, "", List.copyOf(lines));

		String serverId = "", skyDate = "", time = "", area = "", objective = "", kuudra = "";
		boolean rift = false, piggy = false;
		long purse = -1, purseChange = 0, bits = -1, motes = -1, copper = -1, northStars = -1;
		Slayer slayer = null;
		String floor = null;
		int cleared = -1;
		long elapsed = -1;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			Matcher m;
			if ((m = p.matches("server_line", line)) != null) {
				serverId = m.group("server");
			} else if ((m = p.matches("sky_date", line)) != null) {
				skyDate = line;
			} else if ((m = p.matches("time", line)) != null) {
				time = m.group("time");
			} else if ((m = p.matches("area", line)) != null) {
				area = m.group("area").strip();
				rift = "ф".equals(m.group("icon"));
				Matcher d = p.matches("dungeon_area", area);
				if (d != null) floor = d.group("floor");
				Matcher k = p.matches("kuudra_area", area);
				if (k != null) kuudra = k.group("tier");
			} else if ((m = p.matches("purse", line)) != null) {
				purse = SbText.longOr(m.group("amount"), -1);
				purseChange = SbText.longOr(Patterns.group(m, "change"), 0);
				piggy = "Piggy".equals(m.group("kind"));
			} else if ((m = p.matches("bits", line)) != null) {
				bits = SbText.longOr(m.group("amount"), -1);
			} else if ((m = p.matches("motes", line)) != null) {
				motes = SbText.longOr(m.group("amount"), -1);
			} else if ((m = p.matches("copper", line)) != null) {
				copper = SbText.longOr(m.group("amount"), -1);
			} else if ((m = p.matches("north_stars", line)) != null) {
				northStars = SbText.longOr(m.group("amount"), -1);
			} else if (p.matches("objective_header", line) != null && i + 1 < lines.size()) {
				objective = lines.get(++i);
			} else if (p.matches("slayer_header", line) != null && i + 1 < lines.size()) {
				Matcher b = p.matches("slayer_boss", lines.get(i + 1));
				if (b != null) {
					i++;
					long cur = -1, need = -1;
					String unit = "";
					boolean up = false, done = false;
					if (i + 1 < lines.size()) {
						String next = lines.get(i + 1);
						Matcher pr = p.matches("slayer_progress", next);
						if (pr != null) {
							cur = SbText.longOr(pr.group("current"), -1);
							need = SbText.longOr(pr.group("needed"), -1);
							unit = pr.group("unit");
							i++;
						} else if (p.matches("slayer_fight", next) != null) {
							up = true;
							i++;
						} else if (p.matches("slayer_done", next) != null) {
							done = true;
							i++;
						}
					}
					slayer = new Slayer(b.group("boss"), SbText.roman(b.group("tier")), cur, need, unit, up, done);
				}
			} else if ((m = p.matches("dungeon_cleared", line)) != null) {
				cleared = Integer.parseInt(m.group("percent"));
			} else if ((m = p.matches("dungeon_time", line)) != null) {
				elapsed = SbText.parseDuration(m.group("time"));
			}
		}
		Dungeon dungeon = floor != null || cleared >= 0 ? new Dungeon(floor == null ? "" : floor, cleared, elapsed) : null;
		return new Sidebar(true, p.find("title_guest", t) != null, p.find("title_coop", t) != null, serverId, skyDate,
				time, area, rift, purse, purseChange, piggy, bits, motes, copper, northStars, objective, slayer, dungeon,
				kuudra, List.copyOf(lines));
	}
}
