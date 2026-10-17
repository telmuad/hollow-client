package dev.telmuad.hollowclient.skyblock;

import dev.telmuad.hollowclient.skyblock.features.items.Experiments;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentsTest {
	private static Experiments.Tile pane(int slot) {
		return new Experiments.Tile(slot, "minecraft:gray_stained_glass_pane", 1, " ", false, "gray");
	}

	@Test
	void ultrasequencerRemembersNumbersAndPointsAtTheNext() {
		Experiments e = new Experiments();
		e.reset(Experiments.Game.ULTRASEQUENCER);
		List<Experiments.Tile> shown = List.of(
				new Experiments.Tile(10, "minecraft:lime_dye", 2, "2", false, "lime"),
				new Experiments.Tile(14, "minecraft:white_dye", 1, "1", false, "white"),
				new Experiments.Tile(20, "minecraft:light_blue_dye", 3, "3", false, "light_blue"));
		e.observe(shown, true);
		e.observe(List.of(pane(10), pane(14), pane(20)), false);
		assertEquals(2, e.number(10));
		assertEquals(14, e.nextSlot(), "number 1 first");
		e.clicked(14);
		assertEquals(10, e.nextSlot());
		e.clicked(10);
		e.clicked(20);
		assertEquals(-1, e.nextSlot(), "done");
		// next round: a new pattern replaces the old one
		e.observe(List.of(new Experiments.Tile(30, "minecraft:white_dye", 1, "1", false, "white")), true);
		e.observe(List.of(pane(30)), false);
		assertEquals(30, e.nextSlot());
		assertEquals(0, e.number(10));
	}

	@Test
	void chronomatronFollowsTheChainEachRound() {
		Experiments e = new Experiments();
		e.reset(Experiments.Game.CHRONOMATRON);
		Experiments.Tile red = new Experiments.Tile(10, "minecraft:red_stained_glass_pane", 1, "", false, "red");
		Experiments.Tile redLit = new Experiments.Tile(10, "minecraft:red_terracotta", 1, "", true, "red");
		Experiments.Tile blue = new Experiments.Tile(12, "minecraft:blue_stained_glass_pane", 1, "", false, "blue");
		Experiments.Tile blueLit = new Experiments.Tile(12, "minecraft:blue_stained_glass_pane", 1, "", true, "blue");
		// round 1: red lights up
		e.observe(List.of(red, blue), true);
		e.observe(List.of(redLit, blue), true);
		e.observe(List.of(red, blue), false);
		assertEquals("red", e.nextColor());
		e.clicked(10);
		assertEquals("", e.nextColor());
		// round 2: red, then blue
		e.observe(List.of(redLit, blue), true);
		e.observe(List.of(red, blue), true);
		e.observe(List.of(red, blueLit), true);
		e.observe(List.of(red, blue), false);
		assertEquals(List.of("red", "blue"), e.chain());
		assertEquals("red", e.nextColor());
		e.clicked(10);
		assertEquals("blue", e.nextColor());
	}

	@Test
	void superpairsRemembersRevealedTiles() {
		Experiments e = new Experiments();
		e.reset(Experiments.Game.SUPERPAIRS);
		List<Experiments.Tile> board = new ArrayList<>(List.of(pane(10), pane(11)));
		e.observe(board, false);
		assertNull(e.remembered(10));
		board.set(0, new Experiments.Tile(10, "minecraft:enchanted_book", 1, "Enchanted Book", false, ""));
		e.observe(board, false);
		board.set(0, pane(10));
		e.observe(board, false);
		assertEquals("Enchanted Book", e.remembered(10).name());
		assertNull(e.remembered(11));
	}
}
