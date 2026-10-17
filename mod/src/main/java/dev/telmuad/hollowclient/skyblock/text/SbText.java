package dev.telmuad.hollowclient.skyblock.text;

import java.util.Locale;

/**
 * Text helpers for what Hypixel sends: formatting codes, the invisible "uniqueness" characters it puts into scoreboard
 * lines, and numbers written as {@code 12,345}, {@code 1.2k}, {@code 3.4M} or roman numerals. Plain Java.
 */
public final class SbText {
	private SbText() {
	}

	/**
	 * Removes formatting codes ({@code §a}, {@code §l}, ...), characters outside the Basic Multilingual Plane (Hypixel
	 * puts emoji into team prefixes so every scoreboard line is unique; real SkyBlock symbols such as ⏣ ф ♲ ✎ ❤ are all
	 * in the BMP), zero-width characters and non-breaking spaces.
	 */
	public static String strip(String s) {
		if (s == null || s.isEmpty()) return "";
		StringBuilder out = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); ) {
			int cp = s.codePointAt(i);
			int n = Character.charCount(cp);
			if (cp == '§') {
				i += n + (i + n < s.length() ? 1 : 0); // skip the code character too
				continue;
			}
			i += n;
			if (cp > 0xFFFF) continue;
			if (cp >= 0x200B && cp <= 0x200F || cp == 0xFEFF || cp == 0x2060) continue;
			if (cp >= 0xE000 && cp <= 0xF8FF) continue; // private use (resource pack icons)
			if (cp == 0xA0 || cp == 0x202F) cp = ' ';
			out.appendCodePoint(cp);
		}
		return out.toString();
	}

	/** {@link #strip} and trimmed. */
	public static String clean(String s) {
		return strip(s).strip();
	}

	/** True if the (stripped) text is empty or only spaces. */
	public static boolean blank(String s) {
		return clean(s).isEmpty();
	}

	/**
	 * A number as Hypixel writes it: {@code 12,345}, {@code 1,234.5}, {@code 1.2k}, {@code 3.4M}, {@code 5B}, with an
	 * optional sign. Returns NaN when there is no number.
	 */
	public static double number(String s) {
		if (s == null) return Double.NaN;
		String t = s.strip().replace(",", "").replace("_", "");
		if (t.isEmpty()) return Double.NaN;
		double mult = 1;
		char last = Character.toLowerCase(t.charAt(t.length() - 1));
		if (last == 'k') mult = 1e3;
		else if (last == 'm') mult = 1e6;
		else if (last == 'b') mult = 1e9;
		else if (last == 't') mult = 1e12;
		if (mult != 1) t = t.substring(0, t.length() - 1);
		try {
			return Double.parseDouble(t) * mult;
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}

	/** {@link #number} rounded down to a long; {@code fallback} when there is no number. */
	public static long longOr(String s, long fallback) {
		double d = number(s);
		return Double.isNaN(d) ? fallback : (long) Math.floor(d + 1e-9);
	}

	/** Roman numerals (I..MMMCMXCIX) or plain digits. 0 when it's neither. */
	public static int roman(String s) {
		if (s == null) return 0;
		String t = s.strip().toUpperCase(Locale.ROOT);
		if (t.isEmpty()) return 0;
		if (t.chars().allMatch(Character::isDigit)) {
			try {
				return Integer.parseInt(t);
			} catch (NumberFormatException e) {
				return 0;
			}
		}
		int total = 0;
		int prev = 0;
		for (int i = t.length() - 1; i >= 0; i--) {
			int v = switch (t.charAt(i)) {
				case 'I' -> 1;
				case 'V' -> 5;
				case 'X' -> 10;
				case 'L' -> 50;
				case 'C' -> 100;
				case 'D' -> 500;
				case 'M' -> 1000;
				default -> -1;
			};
			if (v < 0) return 0;
			total += v < prev ? -v : v;
			prev = Math.max(prev, v);
		}
		return total;
	}

	private static final int[] ROMAN_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
	private static final String[] ROMAN_SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

	public static String toRoman(int n) {
		if (n <= 0 || n >= 4000) return Integer.toString(n);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < ROMAN_VALUES.length; i++) {
			while (n >= ROMAN_VALUES[i]) {
				n -= ROMAN_VALUES[i];
				sb.append(ROMAN_SYMBOLS[i]);
			}
		}
		return sb.toString();
	}

	/** {@code 1234} -> "1,234"; {@code 1234567} -> "1.23M" (short) for big numbers. */
	public static String shortNumber(double v) {
		double a = Math.abs(v);
		if (a >= 1e12) return trim(v / 1e12) + "T";
		if (a >= 1e9) return trim(v / 1e9) + "B";
		if (a >= 1e6) return trim(v / 1e6) + "M";
		if (a >= 1e4) return trim1(v / 1e3) + "k";
		return grouped(Math.round(v));
	}

	private static String trim1(double v) {
		String s = String.format(Locale.ROOT, "%.1f", v);
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}

	private static String trim(double v) {
		String s = String.format(Locale.ROOT, Math.abs(v) >= 100 ? "%.1f" : "%.2f", v);
		if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
		return s;
	}

	/** {@code 1234567} -> "1,234,567". */
	public static String grouped(long v) {
		return String.format(Locale.ROOT, "%,d", v);
	}

	/** "1h 2m", "4m 05s", "12s". */
	public static String duration(long ms) {
		long s = Math.max(0, ms / 1000);
		if (s >= 3600) return (s / 3600) + "h " + (s % 3600) / 60 + "m";
		if (s >= 60) return (s / 60) + "m " + String.format(Locale.ROOT, "%02ds", s % 60);
		return s + "s";
	}

	/** Parses "05m 20s", "1h 2m", "45s", "1:05" into milliseconds; -1 when it isn't a duration. */
	public static long parseDuration(String s) {
		if (s == null) return -1;
		String t = s.strip().toLowerCase(Locale.ROOT);
		if (t.matches("\\d+:\\d{2}(:\\d{2})?")) {
			String[] p = t.split(":");
			long total = 0;
			for (String part : p) total = total * 60 + Long.parseLong(part);
			return total * 1000;
		}
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*([dhms])").matcher(t);
		long total = 0;
		boolean any = false;
		while (m.find()) {
			any = true;
			long v = Long.parseLong(m.group(1));
			total += switch (m.group(2)) {
				case "d" -> v * 86_400_000L;
				case "h" -> v * 3_600_000L;
				case "m" -> v * 60_000L;
				default -> v * 1000L;
			};
		}
		return any ? total : -1;
	}
}
