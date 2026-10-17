package dev.telmuad.hollowclient.skyblock.features.general;

import dev.telmuad.hollowclient.skyblock.parse.Patterns;
import dev.telmuad.hollowclient.skyblock.parse.TabListParser;
import dev.telmuad.hollowclient.skyblock.text.SbText;

import java.util.List;
import java.util.regex.Matcher;

/** Text reading for the General area: deployables, damage numbers, the pet widget, effective HP. Plain Java. */
public final class GeneralParse {
	/** A power orb, flare or totem floating near you, with the seconds it has left. */
	public record Deployable(String name, int seconds) {
	}

	/** The pet widget: name, level, and either the XP progress or "MAX LEVEL". */
	public record PetStatus(String name, int level, String xp, double percent, boolean max) {
	}

	private GeneralParse() {
	}

	/** A deployable's name tag ("Overflux 45s"), or null. */
	public static Deployable deployable(String rawName) {
		Matcher m = Patterns.of("entities").find("deployable", SbText.clean(rawName));
		if (m == null) return null;
		long ms = SbText.parseDuration(m.group("time"));
		return ms < 0 ? null : new Deployable(m.group("name"), (int) (ms / 1000));
	}

	/**
	 * A damage number's tag ("✧1,234,567✧" or "123,456") as a short one ("✧1.23M✧"), keeping the symbols; null when
	 * it isn't a damage number (or is already short).
	 */
	public static String compactDamage(String rawName) {
		String clean = SbText.clean(rawName);
		Matcher m = Patterns.of("entities").matches("damage", clean);
		if (m == null) return null;
		long n = SbText.longOr(m.group("num"), -1);
		if (n < 10_000) return null;
		return m.group("pre") + SbText.shortNumber(n) + m.group("post");
	}

	/** Effective health: what it takes to kill you, counting defense (health × (1 + defense / 100)). */
	public static long effectiveHealth(int health, int defense) {
		if (health < 0) return -1;
		return Math.round(health * (1 + Math.max(0, defense) / 100.0));
	}

	/** The pet from the tab list's Pet widget (name and level line, then the XP line). */
	public static PetStatus pet(TabListParser.TabInfo tab) {
		if (tab.pet().isEmpty()) return null;
		List<String> lines = tab.widget("Pet");
		Patterns p = Patterns.of("tab");
		String xp = "";
		double percent = -1;
		boolean max = false;
		for (String line : lines) {
			Matcher m = p.find("pet_xp", line);
			if (m != null) {
				xp = m.group("current") + "/" + m.group("needed");
				percent = Double.parseDouble(m.group("percent"));
			}
			if (p.find("pet_max", line) != null) max = true;
		}
		return new PetStatus(tab.pet(), tab.petLevel(), xp, max ? 100 : percent, max);
	}
}
