package dev.telmuad.hollowclient.skyblock.features.items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The memory games at the Experimentation Table, followed from what the menu shows each tick. They only remember what
 * was on screen and say what comes next; the player clicks everything. Plain Java.
 * <ul>
 *   <li><b>Ultrasequencer</b>: numbered items show briefly (the number is the stack size), then hide. Remembered: number → slot.</li>
 *   <li><b>Chronomatron</b>: colored panes light up one after another (lit = glint or terracotta); each round replays the
 *   chain with one more note. Remembered: the chain of colors.</li>
 *   <li><b>Superpairs</b>: clicked tiles show their item, then cover again. Remembered: slot → item.</li>
 * </ul>
 */
public final class Experiments {
	public enum Game { ULTRASEQUENCER, CHRONOMATRON, SUPERPAIRS }

	/** One board slot as seen this tick. {@code color} is the pane/dye color ("red", "lime", ...), or "". */
	public record Tile(int slot, String material, int count, String name, boolean lit, String color) {
		public boolean covered() {
			return material.endsWith("_stained_glass_pane") || material.equals("minecraft:glass_pane") || material.equals("minecraft:air");
		}
	}

	private Game game;
	private boolean remembering;
	private final Map<Integer, Integer> numberToSlot = new TreeMap<>();
	private final List<String> chain = new ArrayList<>();
	private final Map<Integer, Boolean> wasLit = new HashMap<>();
	private final Map<Integer, Tile> revealed = new HashMap<>();
	private int clicks;

	public void reset(Game g) {
		game = g;
		remembering = false;
		numberToSlot.clear();
		chain.clear();
		wasLit.clear();
		revealed.clear();
		clicks = 0;
	}

	public Game game() {
		return game;
	}

	/**
	 * One tick of the menu. {@code rememberPhase} is true while the game shows the pattern (its indicator says
	 * "Remember the pattern!") and false on the player's turn (a timer).
	 */
	public void observe(List<Tile> board, boolean rememberPhase) {
		if (game == null) return;
		boolean newRound = rememberPhase && !remembering;
		remembering = rememberPhase;
		switch (game) {
			case ULTRASEQUENCER -> {
				if (rememberPhase) {
					if (newRound) {
						numberToSlot.clear();
						clicks = 0;
					}
					for (Tile t : board) if (!t.covered() && t.material().endsWith("_dye") && t.count() > 0) numberToSlot.put(t.count(), t.slot());
				}
			}
			case CHRONOMATRON -> {
				if (newRound) {
					chain.clear();
					clicks = 0;
				}
				for (Tile t : board) {
					boolean before = wasLit.getOrDefault(t.slot(), false);
					if (rememberPhase && t.lit() && !before && !t.color().isEmpty()) chain.add(t.color());
					wasLit.put(t.slot(), t.lit());
				}
			}
			case SUPERPAIRS -> {
				for (Tile t : board) if (!t.covered() && !t.name().isBlank()) revealed.put(t.slot(), t);
			}
		}
	}

	/** The player clicked a board slot (counted only; nothing is sent by Hollow). */
	public void clicked(int slot) {
		if (game == Game.ULTRASEQUENCER || game == Game.CHRONOMATRON) {
			if (!remembering) clicks++;
		}
	}

	/** Ultrasequencer: the remembered number for a slot (0 = none). */
	public int number(int slot) {
		for (Map.Entry<Integer, Integer> e : numberToSlot.entrySet()) if (e.getValue() == slot) return e.getKey();
		return 0;
	}

	/** Ultrasequencer: the slot to click next on the player's turn (-1 = unknown or finished). */
	public int nextSlot() {
		if (game != Game.ULTRASEQUENCER || remembering) return -1;
		int i = 0;
		for (int slot : numberToSlot.values()) if (i++ == clicks) return slot;
		return -1;
	}

	/** Chronomatron: the color to click next on the player's turn ("" = unknown or finished). */
	public String nextColor() {
		if (game != Game.CHRONOMATRON || remembering || clicks >= chain.size()) return "";
		return chain.get(clicks);
	}

	public List<String> chain() {
		return List.copyOf(chain);
	}

	/** Superpairs: what a covered slot showed before, or null. */
	public Tile remembered(int slot) {
		return revealed.get(slot);
	}

	public boolean remembering() {
		return remembering;
	}
}
