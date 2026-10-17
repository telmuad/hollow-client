package dev.telmuad.hollowclient.skyblock.ui;

import dev.telmuad.hollowclient.hud.HudPosition;
import dev.telmuad.hollowclient.hud.TextHudModule;
import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.skyblock.state.Island;
import net.minecraft.client.Minecraft;

import java.util.EnumSet;
import java.util.Set;

/**
 * Base for SkyBlock HUD elements: listed in the SkyBlock menu category, placed with the normal HUD editor, and shown
 * only on SkyBlock (on their islands). In the HUD editor they show sample values.
 */
public abstract class SkyBlockHudModule extends TextHudModule implements SkyBlockFeature {
	private final String group;
	private final Set<Island> islands;

	protected SkyBlockHudModule(String id, String name, String description, String group, boolean enabledByDefault,
								HudPosition defaults, Island... islands) {
		super(id, name, description, Category.SKYBLOCK, enabledByDefault, defaults);
		this.group = group;
		this.islands = islands.length == 0 ? Set.of() : Set.copyOf(EnumSet.of(islands[0], islands));
	}

	@Override
	public final String group() {
		return group;
	}

	@Override
	public final Set<Island> islands() {
		return islands;
	}

	public final boolean live() {
		return SkyBlockFeature.live(this, islands);
	}

	@Override
	public boolean visibleInGame(Minecraft mc) {
		return mc.player != null && live();
	}
}
