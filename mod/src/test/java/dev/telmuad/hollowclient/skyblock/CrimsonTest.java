package dev.telmuad.hollowclient.skyblock;

import dev.telmuad.hollowclient.skyblock.features.crimson.CrimsonModules;
import dev.telmuad.hollowclient.skyblock.parse.Patterns;
import dev.telmuad.hollowclient.skyblock.parse.TabListParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CrimsonTest {
	@Test
	void vanquisherNameTags() {
		assertTrue(CrimsonModules.vanquisher("§8[§7Lv235§8] §cVanquisher§r §a1,000,000§f/§a1,000,000§c❤"));
		assertTrue(CrimsonModules.vanquisher("[Lv100] Vanquisher 600k/600k❤"), "the old level still matches");
		assertFalse(CrimsonModules.vanquisher("Vanquisher"), "no level: not a mob tag");
		assertFalse(CrimsonModules.vanquisher("[Lv50] Vanquishers' Friend 1/1❤"));
		assertFalse(CrimsonModules.vanquisher("[Lv235] Blaze 1/1❤"));
	}

	@Test
	void tabWidgetLinesByTitle() {
		TabListParser.TabInfo tab = TabListParser.parse(Fixtures.tab("mines_full"));
		assertEquals(List.of("Powders:", "Mithril: 1,234,567", "Gemstone: 845,210", "Glacite: 230,000"),
				CrimsonModules.TabWidgetHud.widgetLines(tab, List.of("Powders")));
		assertEquals(4 + 4, CrimsonModules.TabWidgetHud.widgetLines(tab, List.of("powders", "Forges")).size(), "titles ignore case");
		assertTrue(CrimsonModules.TabWidgetHud.widgetLines(tab, List.of("Nope")).isEmpty());
	}

	@Test
	void kuudraChestsAreRewardChests() {
		Patterns p = Patterns.of("menus");
		assertNotNull(p.find("reward_chest", "Paid Chest"));
		assertNotNull(p.find("reward_chest", "Bedrock Chest"));
		assertEquals("Infernal Kuudra Key", p.find("reward_key", "Infernal Kuudra Key").group("key"));
		assertEquals("Kuudra Key", p.find("reward_key", "Kuudra Key").group("key"));
	}
}
