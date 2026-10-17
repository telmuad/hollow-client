package dev.telmuad.hollowclient.skyblock.ui;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.skyblock.state.Island;

import java.util.EnumSet;
import java.util.Set;

/** Base for SkyBlock features that aren't HUD elements. */
public abstract class SkyBlockModule extends Module implements SkyBlockFeature {
	private final String group;
	private final Set<Island> islands;

	protected SkyBlockModule(String id, String name, String description, String group, boolean enabledByDefault, Island... islands) {
		super(id, name, description, Category.SKYBLOCK, enabledByDefault);
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

	/** Switched on, on SkyBlock and on one of this feature's islands. */
	public final boolean live() {
		return SkyBlockFeature.live(this, islands);
	}
}
