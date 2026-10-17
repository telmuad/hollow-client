package dev.telmuad.hollowclient.skyblock;

import dev.telmuad.hollowclient.skyblock.features.fishing.Fishing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FishingTest {
	@Test
	void rareCatchesFromTheWikiMessages() {
		assertEquals("Lord Jawbus", Fishing.rareCatch("§c§lYou have angered a legendary creature... Lord Jawbus has arrived."));
		assertEquals("Thunder", Fishing.rareCatch("§bYou hear a massive rumble as Thunder emerges."));
		assertEquals("Water Hydra", Fishing.rareCatch("The Water Hydra has come to test your strength."));
		assertEquals("Puddle Jumper", Fishing.rareCatch("A Puddle Jumper is preparing for liftoff—cast your rod into it and hold on tight!"));
		assertEquals("Wiki Tiki", Fishing.rareCatch("The water bubbles and froths. A massive form emerges- you have disturbed the Wiki Tiki! You shall pay the price."));
		assertNull(Fishing.rareCatch("A Squid appeared."));
		assertNull(Fishing.rareCatch("[MVP+] Someone: You hear a massive rumble as Thunder emerges."), "someone quoting it isn't a catch");
		assertTrue(Fishing.mythic("Lord Jawbus"));
		assertFalse(Fishing.mythic("Thunder"));
	}

	@Test
	void capDoubleHookAndBite() {
		assertTrue(Fishing.capReached("§cThere is not enough space for another Sea Creature! Kill some to make space for new ones!"));
		assertTrue(Fishing.doubleHook("§a§lIt's a Double Hook! Woot woot!"));
		assertFalse(Fishing.doubleHook("It's a fish."));
		assertTrue(Fishing.bite("§c§l!!!"));
		assertFalse(Fishing.bite("§e1.5"));
	}

	@Test
	void seaCreatureNameTags() {
		assertEquals("Sea Walker", Fishing.seaCreature("§8[§7Lv45§8] §cSea Walker§r §a1,000§f/§a1,000§c❤"));
		assertEquals("Lord Jawbus", Fishing.seaCreature("§8[§7Lv600§8] §c§lLord Jawbus§r §a100M§f/§a100M§c❤"));
		assertEquals("Poisoned Water Worm", Fishing.seaCreature("[Lv20] Poisoned Water Worm 500/500❤"), "the longer name wins");
		assertEquals("Ent", Fishing.seaCreature("[Lv120] ⚓ Ent 40,000/40,000❤"), "a category icon before the name");
		assertNull(Fishing.seaCreature("[Lv10] Zombie 100/100❤"));
		assertNull(Fishing.seaCreature("Sea Walker"), "a name tag without a level isn't a mob");
		assertNull(Fishing.seaCreature("[Lv50] Entangled Horror 1/1❤"), "Ent must be a whole word");
	}

	@Test
	void trophyFish() {
		Fishing.Trophy t = Fishing.trophy("§6§lTROPHY FISH! §bYou caught a §9Blobfish §f§lBRONZE§b.");
		assertEquals(new Fishing.Trophy("Blobfish", Fishing.Tier.BRONZE), t);
		assertEquals(new Fishing.Trophy("Obfuscated 1", Fishing.Tier.DIAMOND), Fishing.trophy("TROPHY FISH! You caught an Obfuscated 1 DIAMOND."));
		assertNull(Fishing.trophy("You caught a Blobfish."));

		Fishing.Trophies tr = new Fishing.Trophies();
		tr.add(t);
		tr.add(t);
		tr.add(new Fishing.Trophy("Lavahorse", Fishing.Tier.GOLD));
		assertEquals(3, tr.total());
		assertEquals(2, tr.total(Fishing.Tier.BRONZE));
		assertEquals(1, tr.count("Lavahorse", Fishing.Tier.GOLD));
		assertEquals(0, tr.count("Lavahorse", Fishing.Tier.BRONZE));
	}

	@Test
	void catchesNewestFirst() {
		Fishing.Catches c = new Fishing.Catches();
		c.caught("Thunder", 1000);
		c.caught("Lord Jawbus", 2000);
		c.caught("Thunder", 3000);
		assertEquals(List.of("Thunder", "Lord Jawbus"), c.recent());
		assertEquals(2, c.counts().get("Thunder"));
		assertEquals(3000, c.lastAt("Thunder"));
		assertEquals(-1, c.lastAt("Nessie"));
	}
}
