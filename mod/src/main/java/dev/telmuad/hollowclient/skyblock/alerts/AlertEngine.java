package dev.telmuad.hollowclient.skyblock.alerts;

import dev.telmuad.hollowclient.skyblock.event.EventBus;
import dev.telmuad.hollowclient.skyblock.event.SbEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches SkyBlock events and chat lines against the alert rules and hands each alert that fires (with its
 * placeholders filled in) to a sink, which shows it. Each rule has a cooldown so a burst of lines fires once.
 * Plain Java.
 */
public final class AlertEngine {
	/** The event keys rules can use, with the placeholders each one fills. */
	public static final Map<String, String> EVENT_KEYS = new LinkedHashMap<>();

	static {
		EVENT_KEYS.put("rare_drop", "{kind} {item} {count} {mf}");
		EVENT_KEYS.put("pet_drop", "{item} {mf}");
		EVENT_KEYS.put("slayer_boss_spawned", "{boss} {tier}");
		EVENT_KEYS.put("slayer_quest_complete", "{boss} {tier}");
		EVENT_KEYS.put("slayer_quest_failed", "");
		EVENT_KEYS.put("visitor_arrived", "{name}");
		EVENT_KEYS.put("skill_level_up", "{skill} {level}");
		EVENT_KEYS.put("joined_skyblock", "");
	}

	/** An alert that fired: the rule and its finished texts. */
	public record Fired(AlertRule rule, String title, String subtitle) {
	}

	private final LongSupplier clock;
	private final Supplier<String> islandMode;
	private final Consumer<Fired> sink;
	private final Map<String, Long> lastFired = new HashMap<>();
	private List<AlertRule> rules = List.of();
	private final Map<String, Pattern> regexCache = new HashMap<>();

	public AlertEngine(LongSupplier clock, Supplier<String> islandMode, Consumer<Fired> sink) {
		this.clock = clock;
		this.islandMode = islandMode;
		this.sink = sink;
	}

	public void setRules(List<AlertRule> rules) {
		this.rules = List.copyOf(rules);
		regexCache.clear();
	}

	public List<AlertRule> rules() {
		return rules;
	}

	public void attach(EventBus bus) {
		bus.onAny(this::onEvent);
	}

	public void onEvent(SbEvent e) {
		switch (e) {
			case SbEvent.RareDrop d -> {
				Map<String, String> v = Map.of("kind", d.kind(), "item", d.item(), "count", Integer.toString(d.count()),
						"mf", d.magicFind() > 0 ? d.magicFind() + "%" : "");
				event(d.kind().equals("PET DROP") ? "pet_drop" : "rare_drop", v);
			}
			case SbEvent.SlayerBossSpawned s -> event("slayer_boss_spawned", Map.of("boss", s.boss(), "tier", Integer.toString(s.tier())));
			case SbEvent.SlayerQuestCompleted s -> event("slayer_quest_complete", Map.of("boss", s.boss(), "tier", Integer.toString(s.tier())));
			case SbEvent.SlayerQuestFailed s -> event("slayer_quest_failed", Map.of());
			case SbEvent.VisitorArrived v -> event("visitor_arrived", Map.of("name", v.name()));
			case SbEvent.SkillLevelUp l -> event("skill_level_up", Map.of("skill", l.skill(), "level", Integer.toString(l.level())));
			case SbEvent.JoinedSkyBlock j -> event("joined_skyblock", Map.of());
			case SbEvent.ChatMatched m -> event("pattern:" + m.match().name(), m.match().groups());
			case SbEvent.ChatLine c -> chat(c.line());
			default -> {
			}
		}
	}

	private void event(String key, Map<String, String> values) {
		for (AlertRule r : rules) {
			if (r.trigger() == AlertRule.Trigger.EVENT && r.match().equals(key)) fire(r, values);
		}
	}

	private void chat(String line) {
		for (AlertRule r : rules) {
			if (r.trigger() != AlertRule.Trigger.CHAT || !r.enabled()) continue;
			Pattern p = regexCache.computeIfAbsent(r.id() + "\u0000" + r.match(), k -> r.regex());
			if (p == null) continue;
			Matcher m = p.matcher(line);
			if (!m.find()) continue;
			Map<String, String> values = new HashMap<>();
			for (int i = 1; i <= m.groupCount(); i++) if (m.group(i) != null) values.put(Integer.toString(i), m.group(i));
			for (String g : m.namedGroups().keySet()) if (m.group(g) != null) values.put(g, m.group(g));
			values.put("line", line);
			fire(r, values);
		}
	}

	private void fire(AlertRule r, Map<String, String> values) {
		if (!r.enabled()) return;
		if (!r.islands().isEmpty() && !r.islands().contains(islandMode.get())) return;
		long now = clock.getAsLong();
		Long last = lastFired.get(r.id());
		if (last != null && now - last < r.cooldownMs()) return;
		lastFired.put(r.id(), now);
		sink.accept(new Fired(r, fill(r.title(), values), fill(r.subtitle(), values)));
	}

	/** Replaces {key} with the value (unknown placeholders become empty) and tidies the spaces. */
	public static String fill(String template, Map<String, String> values) {
		if (template == null || template.isEmpty()) return "";
		StringBuilder out = new StringBuilder();
		Matcher m = Pattern.compile("\\{([A-Za-z0-9_]+)}").matcher(template);
		while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(values.getOrDefault(m.group(1), "")));
		m.appendTail(out);
		return out.toString().replaceAll("\\s{2,}", " ").replaceAll("\\(\\s*\\)", "").strip();
	}

	/** Every rule key the engine understands, for the editor. */
	public static List<String> eventKeys() {
		return new ArrayList<>(EVENT_KEYS.keySet());
	}
}
