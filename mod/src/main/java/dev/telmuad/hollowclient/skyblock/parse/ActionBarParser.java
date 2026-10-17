package dev.telmuad.hollowclient.skyblock.parse;

import dev.telmuad.hollowclient.skyblock.text.SbText;

import java.util.regex.Matcher;

/** Reads the action bar: health, defense, mana, skill XP gains, Rift time, dungeon secrets, drill fuel. Plain Java. */
public final class ActionBarParser {
	/** -1 (or null) for anything the action bar didn't show this time. */
	public record ActionBar(int health, int maxHealth, int defense, int mana, int maxMana, int overflowMana,
							SkillGain skill, long riftTimeMs, int secretsFound, int secretsTotal, int drillFuel,
							String text) {
	}

	/** "+12.5 Farming (1,234/5,000)" or "+3 Mining (45.2%)". */
	public record SkillGain(String skill, double gained, String progress) {
	}

	private ActionBarParser() {
	}

	public static ActionBar parse(String raw) {
		Patterns p = Patterns.of("actionbar");
		String t = SbText.clean(raw);
		int health = -1, maxHealth = -1, defense = -1, mana = -1, maxMana = -1, overflow = -1, sf = -1, st = -1, fuel = -1;
		long rift = -1;
		SkillGain skill = null;
		Matcher m;
		if ((m = p.find("health", t)) != null) {
			health = (int) SbText.longOr(m.group("health"), -1);
			maxHealth = (int) SbText.longOr(m.group("max"), -1);
		}
		if ((m = p.find("defense", t)) != null) defense = (int) SbText.longOr(m.group("defense"), -1);
		if ((m = p.find("mana", t)) != null) {
			mana = (int) SbText.longOr(m.group("mana"), -1);
			maxMana = (int) SbText.longOr(m.group("max"), -1);
		}
		if ((m = p.find("overflow_mana", t)) != null) overflow = (int) SbText.longOr(m.group("overflow"), -1);
		if ((m = p.find("skill", t)) != null) {
			skill = new SkillGain(m.group("skill"), SbText.number(m.group("gain")), m.group("progress"));
		}
		if ((m = p.find("rift_time", t)) != null) rift = SbText.parseDuration(m.group("time"));
		if ((m = p.find("secrets", t)) != null) {
			sf = Integer.parseInt(m.group("found"));
			st = Integer.parseInt(m.group("total"));
		}
		if ((m = p.find("drill_fuel", t)) != null) fuel = (int) SbText.longOr(m.group("fuel"), -1);
		return new ActionBar(health, maxHealth, defense, mana, maxMana, overflow, skill, rift, sf, st, fuel, t);
	}
}
