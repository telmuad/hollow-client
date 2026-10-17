package dev.telmuad.hollowclient.skyblock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.telmuad.hollowclient.skyblock.features.general.FairySouls;
import dev.telmuad.hollowclient.skyblock.features.general.GeneralParse;
import dev.telmuad.hollowclient.skyblock.parse.TabListParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeneralAreaTest {
	@Test
	void deployables() {
		assertEquals(new GeneralParse.Deployable("Overflux", 45), GeneralParse.deployable("§aOverflux §e45s"));
		assertEquals(new GeneralParse.Deployable("SOS Flare", 150), GeneralParse.deployable("§cSOS Flare §e2m 30s"));
		assertEquals(new GeneralParse.Deployable("Totem of Corruption", 9), GeneralParse.deployable("§5Totem of Corruption 9s"));
		assertNull(GeneralParse.deployable("§aVillager"));
		assertNull(GeneralParse.deployable("Overflux Power Orb"), "the item name isn't a placed orb");
	}

	@Test
	void compactDamage() {
		assertEquals("✧1.23M✧", GeneralParse.compactDamage("§f✧§e1,234,567§6✧"));
		assertEquals("45.7k", GeneralParse.compactDamage("§745,678"));
		assertEquals("2.5B♞", GeneralParse.compactDamage("2,500,000,000♞"));
		assertNull(GeneralParse.compactDamage("§79,999"), "already short");
		assertNull(GeneralParse.compactDamage("§aZombie Villager"));
		assertNull(GeneralParse.compactDamage("§c[Lv10] Zombie §a100§f/§a100❤"), "mob health tags are left alone");
	}

	@Test
	void effectiveHealth() {
		assertEquals(6_170, GeneralParse.effectiveHealth(1234, 400));
		assertEquals(100, GeneralParse.effectiveHealth(100, 0));
		assertEquals(-1, GeneralParse.effectiveHealth(-1, 100));
	}

	@Test
	void petFromTheTabList() {
		GeneralParse.PetStatus xp = GeneralParse.pet(TabListParser.parse(Fixtures.tab("pet_xp")));
		assertEquals("Ender Dragon", xp.name());
		assertEquals(87, xp.level());
		assertEquals("1.2M/3.4M", xp.xp());
		assertEquals(35.3, xp.percent(), 1e-9);
		assertFalse(xp.max());
		GeneralParse.PetStatus max = GeneralParse.pet(TabListParser.parse(Fixtures.tab("hub")));
		assertTrue(max.max());
		assertEquals("Golden Dragon", max.name());
		assertNull(GeneralParse.pet(TabListParser.parse(Fixtures.tab("mines"))), "no pet widget");
	}

	@Test
	void fairySouls() {
		JsonObject neu = JsonParser.parseString("""
				{"//": "comment", "Max Souls": 289,
				 "hub": ["-233,86,84", "-229,123,84", "10,70,10"],
				 "garden": [],
				 "winter": ["-95,77,20"]}""").getAsJsonObject();
		FairySouls f = FairySouls.fromNeu(neu);
		assertTrue(f.loaded());
		assertEquals(289, f.total());
		assertEquals(3, f.souls("hub").size());
		assertNull(f.markNearest("hub", 0, 70, 0, 8), "too far from any soul");
		FairySouls.Soul s = f.markNearest("hub", -232, 87, 84.4, 8);
		assertEquals(new FairySouls.Soul(-233, 86, 84), s);
		assertEquals(2, f.missing("hub").size());
		assertEquals(1, f.foundCount());

		FairySouls again = FairySouls.fromNeu(neu);
		again.loadFound(f.foundJson());
		assertTrue(again.isFound("hub", s));
		again.markAll("winter", true);
		assertEquals(0, again.missing("winter").size());
		again.markAll("hub", false);
		assertEquals(3, again.missing("hub").size());
		assertTrue(FairySouls.fromNeu(null).souls("hub").isEmpty());
	}
}
