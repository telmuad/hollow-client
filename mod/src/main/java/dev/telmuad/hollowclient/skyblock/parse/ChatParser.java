package dev.telmuad.hollowclient.skyblock.parse;

import dev.telmuad.hollowclient.skyblock.text.SbText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/** Matches a chat line against every chat pattern. Plain Java. */
public final class ChatParser {
	/** Patterns that only classify other players' messages; they never become SkyBlock events. */
	private static final List<String> PRIVATE = List.of("player_message", "private_message", "channel_message");

	/** One pattern that matched, with its named groups. */
	public record ChatMatch(String name, Map<String, String> groups) {
		public String get(String group) {
			String v = groups.get(group);
			return v == null ? "" : v;
		}
	}

	private ChatParser() {
	}

	/** Every SkyBlock chat pattern that matches the whole (cleaned) line, in file order. Player messages give none. */
	public static List<ChatMatch> parse(String raw) {
		String line = SbText.clean(raw);
		List<ChatMatch> out = new ArrayList<>();
		if (line.isEmpty() || isFromAPlayer(line)) return out;
		Patterns p = Patterns.of("chat");
		for (String name : p.names()) {
			if (PRIVATE.contains(name)) continue;
			Matcher m = p.matches(name, line);
			if (m != null) out.add(new ChatMatch(name, groups(m)));
		}
		return out;
	}

	/** True for messages written by players (public, private, party/guild/co-op chat), which Hollow never records. */
	public static boolean isFromAPlayer(String cleanLine) {
		Patterns p = Patterns.of("chat");
		for (String name : PRIVATE) if (p.matches(name, cleanLine) != null) return true;
		return false;
	}

	private static Map<String, String> groups(Matcher m) {
		Map<String, String> out = new LinkedHashMap<>();
		for (String g : m.namedGroups().keySet()) {
			String v = m.group(g);
			if (v != null) out.put(g, v);
		}
		return out;
	}
}
