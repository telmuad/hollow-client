package dev.telmuad.hollowclient.skyblock;

import dev.telmuad.hollowclient.skyblock.features.events.Events;
import dev.telmuad.hollowclient.skyblock.parse.ChatParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventsTest {
	@Test
	void burrowChainsOfAnyLength() {
		ChatParser.ChatMatch m = ChatParser.parse("§eYou dug out a Griffin Burrow! §7(5/8)").getFirst();
		assertEquals("griffin_burrow", m.name());
		assertEquals("5", m.get("n"));
		assertEquals("8", m.get("total"));
		assertEquals("10", ChatParser.parse("You dug out a Griffin Burrow! (10/10)").getFirst().get("n"));
	}

	@Test
	void elusiveCreatures() {
		assertEquals("Minos Inquisitor", Events.elusive("§8[§7Lv750§8] §2Empyrean §cMinos Inquisitor§r §a40M§f/§a40M§c❤"));
		assertEquals("King Minos", Events.elusive("[Lv900] King Minos 100M/100M❤"));
		assertNull(Events.elusive("[Lv100] Minos Hunter 1M/1M❤"));
		assertNull(Events.elusive("Sphinx"), "no level: not a mob tag");
	}

	@Test
	void chocolateFactoryBestUpgrade() {
		assertTrue(Events.chocolateFactory("Chocolate Factory"));
		assertFalse(Events.chocolateFactory("Chocolate Shop"));
		List<Events.MenuItem> items = List.of(
				new Events.MenuItem(28, "§aRabbit Bro §8- §7[50] §aBoss", List.of("§7Produces §6+50 Chocolate §7per second.", "", "§7Cost", "§61,200 Chocolate")),
				new Events.MenuItem(29, "§aRabbit Cousin §8- §7[30]", List.of("§7Cost", "§62,000 Chocolate")),
				new Events.MenuItem(30, "§aRabbit Sis §8- §7[20]", List.of("§7Cost:", "§69,000 Chocolate")),
				new Events.MenuItem(31, "§aHand-Baked Chocolate", List.of("§7Cost", "§6500 Chocolate")),
				new Events.MenuItem(32, "§aRabbit Dog", List.of("§cRequires Rabbit Uncle level 20")));
		// Bro 1/1200 = 0.00083, Cousin 2/2000 = 0.001, Sis 3/9000 = 0.00033; hand-baked isn't an employee, Dog has no cost
		Events.Upgrade best = Events.bestUpgrade(items);
		assertNotNull(best);
		assertEquals(29, best.slot());
		assertEquals("Rabbit Cousin", best.employee());
		assertEquals(2000, best.cost());
		assertEquals(-1, Events.cost(List.of("Produces +50 Chocolate per second.")), "a chocolate line without Cost above it isn't a cost");
	}
}
