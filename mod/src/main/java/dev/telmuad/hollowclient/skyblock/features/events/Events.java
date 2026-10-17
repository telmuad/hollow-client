package dev.telmuad.hollowclient.skyblock.features.events;

import dev.telmuad.hollowclient.skyblock.parse.Patterns;
import dev.telmuad.hollowclient.skyblock.text.SbText;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/** Event helpers: Diana's elusive creatures and the Chocolate Factory's best upgrade. Plain Java. */
public final class Events {
	private Events() {
	}

	private static Patterns p() {
		return Patterns.of("events");
	}

	/** The elusive mythological creature a name tag belongs to (Minos Inquisitor, Sphinx, Manticore, King Minos), or null. */
	public static String elusive(String nameTag) {
		Matcher m = p().find("elusive_tag", SbText.clean(nameTag));
		return m == null ? null : m.group("name");
	}

	// ------------------------------------------------------------------------------------------------ chocolate factory

	/** Chocolate per second each level adds, from the wiki's Chocolate Factory page. */
	public static final Map<String, Integer> EMPLOYEES = Map.of("Rabbit Bro", 1, "Rabbit Cousin", 2, "Rabbit Sis", 3, "Rabbit Daddy", 4,
			"Rabbit Granny", 5, "Rabbit Uncle", 6, "Rabbit Dog", 7);

	/** A menu item as plain text. */
	public record MenuItem(int slot, String name, List<String> lore) {
	}

	/** An employee upgrade: chocolate per second gained per chocolate spent. */
	public record Upgrade(int slot, String employee, long cost, double perChocolate) {
	}

	public static boolean chocolateFactory(String title) {
		return p().matches("cf_title", SbText.clean(title).trim()) != null;
	}

	/** The cost from an item's lore: the first "N Chocolate" line after a "Cost" line (-1 if none). */
	public static long cost(List<String> lore) {
		boolean afterHeader = false;
		for (String raw : lore) {
			String line = SbText.clean(raw).trim();
			if (p().matches("cf_cost_header", line) != null) {
				afterHeader = true;
				continue;
			}
			if (afterHeader) {
				Matcher m = p().matches("cf_cost", line);
				if (m != null) return SbText.longOr(m.group("amount"), -1);
				if (!line.isEmpty()) afterHeader = false;
			}
		}
		return -1;
	}

	/** The employee upgrade giving the most chocolate per second for its cost, or null. */
	public static Upgrade bestUpgrade(List<MenuItem> items) {
		Upgrade best = null;
		for (MenuItem i : items) {
			Matcher m = p().matches("cf_employee", SbText.clean(i.name()).trim());
			if (m == null) continue;
			Integer cps = EMPLOYEES.get(m.group("name"));
			long cost = cost(i.lore());
			if (cps == null || cost <= 0) continue;
			Upgrade u = new Upgrade(i.slot(), m.group("name"), cost, (double) cps / cost);
			if (best == null || u.perChocolate() > best.perChocolate()) best = u;
		}
		return best;
	}
}
