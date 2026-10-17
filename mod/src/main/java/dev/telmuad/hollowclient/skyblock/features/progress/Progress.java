package dev.telmuad.hollowclient.skyblock.features.progress;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.telmuad.hollowclient.skyblock.text.SbText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Progression helpers: skill XP rates and your own reminders. Plain Java. */
public final class Progress {
	private Progress() {
	}

	// ------------------------------------------------------------------------------------------------ skills

	/** The action bar's progress part: "1,234/5,000" (XP into the level and needed) or "45.2%" (or MAX). */
	public record Level(double current, double needed, double percent) {
		public boolean exact() {
			return needed > 0;
		}
	}

	private static final Pattern FRACTION = Pattern.compile("(?<cur>[\\d,.]+[kMB]?)/(?<need>[\\d,.]+[kMB]?)");
	private static final Pattern PERCENT = Pattern.compile("(?<pct>[\\d.]+)%");

	public static Level level(String progress) {
		if (progress == null) return null;
		Matcher m = FRACTION.matcher(progress);
		if (m.find()) {
			double cur = SbText.number(m.group("cur")), need = SbText.number(m.group("need"));
			return new Level(cur, need, need > 0 ? 100 * cur / need : 0);
		}
		m = PERCENT.matcher(progress);
		if (m.find()) return new Level(-1, -1, Double.parseDouble(m.group("pct")));
		return null;
	}

	/** XP gained per skill this session, with the latest progress reading. */
	public static final class Skills {
		private final Map<String, Double> gained = new LinkedHashMap<>();
		private final Map<String, Long> firstAt = new LinkedHashMap<>();
		private final Map<String, Level> level = new LinkedHashMap<>();
		private String last = "";
		private long lastAt = -1;

		public void gained(String skill, double amount, String progress, long now) {
			gained.merge(skill, amount, Double::sum);
			firstAt.putIfAbsent(skill, now);
			Level l = Progress.level(progress);
			if (l != null) level.put(skill, l);
			last = skill;
			lastAt = now;
		}

		public String last() {
			return last;
		}

		public long lastAt() {
			return lastAt;
		}

		public double gained(String skill) {
			return gained.getOrDefault(skill, 0.0);
		}

		public Level level(String skill) {
			return level.get(skill);
		}

		/** Per hour, once a minute has passed since the first gain. */
		public double perHour(String skill, long now) {
			Long first = firstAt.get(skill);
			if (first == null || now - first < 60_000) return Double.NaN;
			return gained(skill) * 3_600_000.0 / (now - first);
		}

		/** Time to the next level at the current rate (-1 if unknown). */
		public long toNextLevel(String skill, long now) {
			Level l = level.get(skill);
			double ph = perHour(skill, now);
			if (l == null || !l.exact() || Double.isNaN(ph) || ph <= 0) return -1;
			return (long) ((l.needed() - l.current()) / ph * 3_600_000.0);
		}

		public void reset() {
			gained.clear();
			firstAt.clear();
			level.clear();
			last = "";
			lastAt = -1;
		}
	}

	// ------------------------------------------------------------------------------------------------ reminders

	public record Reminder(String text, long dueAt) {
	}

	/** Your reminders ("/hollow remind 2h30m check the forge"), soonest first. */
	public static final class Reminders {
		private final List<Reminder> list = new ArrayList<>();

		public Reminder add(String text, long dueAt) {
			Reminder r = new Reminder(text, dueAt);
			list.add(r);
			list.sort(Comparator.comparingLong(Reminder::dueAt));
			return r;
		}

		/** Removes and returns the reminders that are due. */
		public List<Reminder> due(long now) {
			List<Reminder> out = new ArrayList<>();
			list.removeIf(r -> {
				if (r.dueAt() <= now) {
					out.add(r);
					return true;
				}
				return false;
			});
			return out;
		}

		public List<Reminder> upcoming() {
			return List.copyOf(list);
		}

		public void clear() {
			list.clear();
		}

		public JsonObject toJson() {
			JsonObject o = new JsonObject();
			JsonArray a = new JsonArray();
			for (Reminder r : list) {
				JsonObject e = new JsonObject();
				e.addProperty("text", r.text());
				e.addProperty("dueAt", r.dueAt());
				a.add(e);
			}
			o.add("reminders", a);
			return o;
		}

		public void load(JsonObject o) {
			list.clear();
			if (o.get("reminders") instanceof JsonArray a) {
				for (JsonElement e : a) {
					if (e instanceof JsonObject r && r.has("text") && r.has("dueAt")) add(r.get("text").getAsString(), r.get("dueAt").getAsLong());
				}
			}
		}
	}
}
