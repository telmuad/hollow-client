package dev.telmuad.hollowclient.skyblock.features.chat;

import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.parse.ChatParser;
import dev.telmuad.hollowclient.skyblock.parse.Patterns;
import dev.telmuad.hollowclient.skyblock.text.SbText;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockHudModule;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** The Chat & Party area's modules. */
public final class ChatModules {
	private ChatModules() {
	}

	/** Which cleanup category a chat line belongs to (null if none); other players' messages never match. */
	public static String category(String line) {
		String clean = SbText.clean(line).trim();
		if (ChatParser.isFromAPlayer(clean)) return null;
		Patterns p = Patterns.of("chatclean");
		for (String name : p.names()) {
			if (!name.startsWith("_") && p.find(name, clean) != null) return name;
		}
		return null;
	}

	/** Hides SkyBlock's noisy system lines, by category. Only what's drawn in chat changes: SkyBlock Mode still reads them. */
	public static final class ChatCleanup extends SkyBlockModule {
		private static ChatCleanup instance;
		private final BooleanSetting abilityDamage = add(new BooleanSetting("ability_damage", "Ability damage lines", true));
		private final BooleanSetting blocks = add(new BooleanSetting("blocks_in_way", "\"There are blocks in the way!\"", true));
		private final BooleanSetting mana = add(new BooleanSetting("no_mana", "Not enough mana", true));
		private final BooleanSetting cooldown = add(new BooleanSetting("cooldown", "Ability cooldown", true));
		private final BooleanSetting watchdog = add(new BooleanSetting("watchdog", "Watchdog announcements", true));
		private int hidden;

		public ChatCleanup() {
			super("sb_chat_cleanup", "Chat Cleanup", "Hides SkyBlock's noisy system lines (ability damage, blocks in the way, mana, cooldowns, Watchdog). "
					+ "Other players' messages are never hidden.", "Chat", false);
			instance = this;
		}

		public int hidden() {
			return hidden;
		}

		/** Called for every chat line before it's shown: true hides it. */
		public static boolean hide(Component message) {
			ChatCleanup m = instance;
			if (m == null || !m.live()) return false;
			String cat = ChatModules.category(message.getString());
			if (cat == null) return false;
			boolean on = switch (cat) {
				case "ability_damage" -> m.abilityDamage.get();
				case "blocks_in_way" -> m.blocks.get();
				case "no_mana" -> m.mana.get();
				case "cooldown" -> m.cooldown.get();
				case "watchdog" -> m.watchdog.get();
				default -> false;
			};
			if (on) m.hidden++;
			return on;
		}
	}

	/** Your party, from the Hypixel Mod API: who's in it, by name. */
	public static final class PartyHud extends SkyBlockHudModule {
		public PartyHud() {
			super("sb_party", "Party", "Your party members (from the Hypixel Mod API, so it needs that mod).", "Chat", true,
					at(Anchor.END, 2, Anchor.END, 180));
		}

		public static List<String> names(Minecraft mc, List<UUID> party) {
			List<String> out = new ArrayList<>();
			for (UUID id : party) {
				if (mc.player != null && id.equals(mc.player.getUUID())) continue;
				PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(id);
				out.add(info != null ? info.getProfile().name() : id.toString().substring(0, 8) + "…");
			}
			return out;
		}

		@Override
		public boolean visibleInGame(Minecraft mc) {
			return super.visibleInGame(mc) && SkyBlock.party().size() > 1;
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			List<String> names = SkyBlock.active() ? names(mc, SkyBlock.party()) : List.of();
			if (names.isEmpty()) {
				if (!preview) return;
				names = List.of("Player1", "Player2", "Player3");
			}
			out.add(new Line("Party (" + (names.size() + 1) + ")", 0xFF7FB2F0));
			for (String n : names) out.add(new Line(n, 0));
		}
	}

	/** The party chat line for "share my location" (one key press sends it once). */
	public static String shareMessage(String area, double x, double y, double z) {
		String where = String.format(Locale.ROOT, "x: %d, y: %d, z: %d", (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
		return area == null || area.isBlank() ? where : where + " (" + area + ")";
	}

	private static long sharedAt;

	/** Sends your location to party chat; at most once every 3 seconds. Returns false if it didn't. */
	public static boolean shareLocation(Minecraft mc) {
		if (mc.player == null || mc.getConnection() == null || !SkyBlock.active()) return false;
		long now = System.currentTimeMillis();
		if (now - sharedAt < 3_000) return false;
		sharedAt = now;
		String area = SkyBlock.state().area().isEmpty() ? SkyBlock.state().island().displayName() : SkyBlock.state().area();
		mc.getConnection().sendCommand("pc " + shareMessage(area, mc.player.getX(), mc.player.getY(), mc.player.getZ()));
		return true;
	}
}
