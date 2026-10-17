package dev.telmuad.hollowclient.skyblock;

import dev.telmuad.hollowclient.skyblock.features.chat.ChatModules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatTest {
	@Test
	void cleanupCategories() {
		assertEquals("ability_damage", ChatModules.category("§7Your §r§6Implosion §r§7hit §r§c3 §r§7enemies for §r§c1,234,567.8 §r§7damage."));
		assertEquals("ability_damage", ChatModules.category("Your Wither Impact hit 1 enemy for 50,000 damage."));
		assertEquals("blocks_in_way", ChatModules.category("§cThere are blocks in the way!"));
		assertEquals("no_mana", ChatModules.category("§cYou do not have enough mana to do this!"));
		assertEquals("cooldown", ChatModules.category("§cThis ability is on cooldown for 3s."));
		assertEquals("watchdog", ChatModules.category("§4[WATCHDOG ANNOUNCEMENT]"));
		assertEquals("watchdog", ChatModules.category("§fWatchdog has banned §c§l5,123 §fplayers in the last 7 days."));
		assertNull(ChatModules.category("§6§lRARE DROP! §5Shard of the Shredded"));
	}

	@Test
	void otherPlayersAreNeverHidden() {
		assertNull(ChatModules.category("§7[302] §b[MVP§c+§b] Someone§f: There are blocks in the way!"));
		assertNull(ChatModules.category("Party > [MVP+] Someone: Your Implosion hit 3 enemies for 1 damage."));
		assertNull(ChatModules.category("From [VIP] Someone: You do not have enough mana to do this!"));
	}

	@Test
	void shareMessage() {
		assertEquals("x: 12, y: 70, z: -41 (Village)", ChatModules.shareMessage("Village", 12.7, 70.0, -40.2));
		assertEquals("x: 0, y: 64, z: 0", ChatModules.shareMessage("", 0.4, 64.9, 0.1));
	}
}
