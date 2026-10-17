package dev.telmuad.hollowclient.skyblock;

import dev.telmuad.hollowclient.skyblock.features.dungeons.Dungeons;
import dev.telmuad.hollowclient.skyblock.parse.TabListParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DungeonsTest {
	@Test
	void perfectF7IsSPlus() {
		Dungeons.Score s = Dungeons.score(new Dungeons.ScoreInput("F7", 100, 100, 5, 0, 0, 10 * 60_000, false, false));
		assertEquals(100, s.skill());
		assertEquals(100, s.explore());
		assertEquals(100, s.speed(), "10 minutes minus 6 is under 8 minutes");
		assertEquals(5, s.bonus());
		assertEquals(305, s.total());
		assertEquals("S+", s.rank());
	}

	@Test
	void penaltiesAndSecretRequirement() {
		// F3 needs 50% secrets: 25% found gives half the 40 secret points
		Dungeons.Score s = Dungeons.score(new Dungeons.ScoreInput("F3", 100, 25, 2, 1, 1, -1, false, false));
		assertEquals(100 - 2 - 14, s.skill(), "a death costs 2, a failed puzzle 14");
		assertEquals(60 + 20, s.explore());
		assertEquals(100, s.speed());
		assertEquals(2, s.bonus());
		assertEquals(266, s.total());
		assertEquals("A", s.rank());
		assertEquals(10 + 5, Dungeons.score(new Dungeons.ScoreInput("M7", 0, 0, 9, 0, 0, -1, true, false)).bonus(), "EZPZ +10, crypts max 5");
		assertEquals(7, Dungeons.score(new Dungeons.ScoreInput("F6", 0, 0, 5, 0, 0, -1, false, true)).bonus(), "Mimic +2 on F6");
	}

	@Test
	void speedPointsFallAfterTheLimit() {
		// F1 offset 120 s: 12 minutes is T = 600 -> ceil(115 - 600/24) = 90
		assertEquals(90, Dungeons.score(new Dungeons.ScoreInput("F1", 100, 100, 0, 0, 0, 12 * 60_000, false, false)).speed());
		// M5 has no offset: 9 minutes is T = 540 -> ceil(140 - 45) = 95
		assertEquals(95, Dungeons.score(new Dungeons.ScoreInput("M5", 100, 100, 0, 0, 0, 9 * 60_000, false, false)).speed());
		assertEquals(0, Dungeons.score(new Dungeons.ScoreInput("F1", 100, 100, 0, 0, 0, 80 * 60_000, false, false)).speed());
	}

	@Test
	void tabStats() {
		Dungeons.TabStats t = Dungeons.tabStats(TabListParser.parse(Fixtures.tab("dungeon")));
		assertEquals(3, t.crypts());
		assertEquals(1, t.deaths());
		assertEquals(45, t.secretsPercent(), 1e-9);
		assertEquals(1, t.failedPuzzles());
		assertEquals(1, t.puzzlesLeft());
	}

	@Test
	void splitsAndKeys() {
		Dungeons.Splits s = new Dungeons.Splits();
		s.onChat("dungeon_starting", null, 1_000);
		s.onChat("dungeon_blood_opened", null, 61_000);
		s.onChat("boss_message", "The Watcher", 70_000);
		s.onChat("dungeon_watcher_done", null, 120_000);
		s.onChat("boss_message", "Necron", 200_000);
		s.onChat("dungeon_score", null, 300_000);
		assertEquals(List.of("Blood open", "Watcher", "Boss", "End"), s.order());
		assertEquals(60_000, s.marks().get("Blood open"));
		assertEquals(299_000, s.elapsed(999_999));
		assertFalse(s.running());

		Dungeons.Keys k = new Dungeons.Keys();
		k.onChat("dungeon_key_obtained", "Wither", "PlayerTwo");
		k.onChat("dungeon_key_picked", "Blood", "");
		assertEquals(1, k.wither());
		assertTrue(k.blood());
		k.onChat("dungeon_door_opened", "WITHER", "PlayerTwo");
		assertEquals(0, k.wither());
		assertEquals("PlayerTwo", k.lastBy());
	}

	private static Dungeons.Slot slot(int i, String material, int count, String name, boolean glint) {
		return new Dungeons.Slot(i, material, count, name, glint);
	}

	@Test
	void terminals() {
		Dungeons.Solution order = Dungeons.solve("Click in order!", List.of(slot(10, "minecraft:lime_stained_glass_pane", 1, "", false),
				slot(11, "minecraft:red_stained_glass_pane", 3, "", false), slot(12, "minecraft:red_stained_glass_pane", 2, "", false)));
		assertEquals(List.of(12), order.highlight());

		Dungeons.Solution color = Dungeons.solve("Select all the LIGHT BLUE items!", List.of(slot(1, "minecraft:light_blue_wool", 1, "Wool", false),
				slot(2, "minecraft:light_blue_dye", 1, "Dye", true), slot(3, "minecraft:blue_wool", 1, "Wool", false)));
		assertEquals(List.of(1), color.highlight(), "already clicked (glint) and other colors are left out");

		Dungeons.Solution name = Dungeons.solve("What starts with: 'S'?", List.of(slot(1, "minecraft:stone", 1, "Stone", false),
				slot(2, "minecraft:dirt", 1, "Dirt", false), slot(3, "minecraft:sand", 1, "Sand", true)));
		assertEquals(List.of(1), name.highlight());

		Dungeons.Solution panes = Dungeons.solve("Correct all the panes!", List.of(slot(1, "minecraft:red_stained_glass_pane", 1, "", false),
				slot(2, "minecraft:lime_stained_glass_pane", 1, "", false)));
		assertEquals(List.of(1), panes.highlight());

		Dungeons.Solution rubix = Dungeons.solve("Change all to same color!", List.of(slot(1, "minecraft:red_stained_glass_pane", 1, "", false),
				slot(2, "minecraft:orange_stained_glass_pane", 1, "", false), slot(3, "minecraft:orange_stained_glass_pane", 1, "", false)));
		assertEquals(Map.of(1, 1), rubix.clicks(), "one click turns the red pane orange");

		assertTrue(Dungeons.solve("Some chest", List.of()).highlight().isEmpty());
	}

	@Test
	void blazeOrder() {
		assertEquals(List.of(3, 1, 2), Dungeons.blazeOrder(Map.of(1, 500, 2, 900, 3, 100)));
	}
}
