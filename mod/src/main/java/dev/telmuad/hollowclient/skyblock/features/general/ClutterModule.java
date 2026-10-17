package dev.telmuad.hollowclient.skyblock.features.general;

import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.EnumSetting;
import dev.telmuad.hollowclient.setting.Labeled;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.state.Island;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockModule;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;

/**
 * Visual clutter hiders: other players in busy places and lightning. Hidden things are only not drawn on your screen;
 * nothing about them changes and nothing hidden is revealed. NPCs (which Hypixel makes from player models) and your
 * party members stay visible.
 */
public final class ClutterModule extends SkyBlockModule {
	public enum Players implements Labeled {
		OFF("Show everyone"), HUBS("Hide in the Hub and Dungeon Hub"), EVERYWHERE("Hide everywhere on SkyBlock");

		private final String label;

		Players(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private static ClutterModule instance;

	private final EnumSetting<Players> players = add(new EnumSetting<>("players", "Other players", Players.HUBS));
	private final BooleanSetting lightning = add(new BooleanSetting("lightning", "Hide lightning", true));

	public ClutterModule() {
		super("sb_clutter", "Clutter Hider", "Hides other players in busy hubs and lightning, on your screen only.", "General", false);
		instance = this;
	}

	/** Called before each entity is drawn (EntityRenderDispatcherMixin): true = don't draw it. */
	public static boolean hide(Entity e) {
		ClutterModule m = instance;
		if (m == null || !m.live()) return false;
		if (e instanceof LightningBolt) return m.lightning.on();
		if (e instanceof RemotePlayer p) {
			Players mode = m.players.get();
			if (mode == Players.OFF) return false;
			Island island = SkyBlock.state().island();
			if (mode == Players.HUBS && island != Island.HUB && island != Island.DUNGEON_HUB) return false;
			// real players have random (version 4) UUIDs; Hypixel's NPCs don't
			if (p.getUUID().version() != 4) return false;
			return !SkyBlock.party().contains(p.getUUID());
		}
		return false;
	}
}
