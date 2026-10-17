package dev.telmuad.hollowclient.skyblock.modules;

import dev.telmuad.hollowclient.gui.Toasts;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.EnumSetting;
import dev.telmuad.hollowclient.setting.Labeled;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.event.SbEvent;
import dev.telmuad.hollowclient.skyblock.state.Island;
import dev.telmuad.hollowclient.skyblock.ui.HudLayouts;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockModule;

/**
 * The SkyBlock Mode switch. With it on, Hollow notices when you join Hypixel SkyBlock: a toast with the SkyBlock
 * badge, your SkyBlock HUD layout, and every SkyBlock feature for the island you're on. Off, every SkyBlock feature
 * stays quiet (the data layer still reads along, so switching it on works at once).
 */
public final class SkyBlockModeModule extends SkyBlockModule {
	public enum Layout implements Labeled {
		OFF("Same as everywhere"), SHARED("One SkyBlock layout"), PER_ISLAND("One layout per island");

		private final String label;

		Layout(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final BooleanSetting joinToast = add(new BooleanSetting("joinToast", "Toast when joining SkyBlock", true));
	private final EnumSetting<Layout> layout = add(new EnumSetting<>("hudLayout", "SkyBlock HUD layout", Layout.SHARED));
	private final BooleanSetting downloads = add(new BooleanSetting("downloads", "Download prices and item data", true));
	private HudLayouts layouts;

	public SkyBlockModeModule() {
		super("skyblock_mode", "SkyBlock Mode", "Notices Hypixel SkyBlock and turns on the SkyBlock features for each island. Display only.",
				"General", true);
		layout.describe("While on SkyBlock, Hollow's HUD can have its own layout (or one per island). Edit it with the HUD editor while on SkyBlock.");
		downloads.describe("Bazaar, lowest BIN and NPC prices, and the NEU item repository, from public sources. Only while on SkyBlock.");
	}

	public boolean downloads() {
		return downloads.on();
	}

	/** The HUD layout in use ("outside", "skyblock" or "skyblock:<island>"). */
	public String activeLayout() {
		return layouts == null ? "" : layouts.active();
	}

	@Override
	public boolean toggleable() {
		return true;
	}

	@Override
	public void onLoaded() {
		layouts = new HudLayouts(SkyBlock.dir().resolve("hud_layouts.json"));
		layouts.startup();
		SkyBlock.bus().on(SbEvent.JoinedSkyBlock.class, e -> {
			if (!isEnabled()) return;
			if (joinToast.on()) Toasts.showBadge("SkyBlock Mode", "SkyBlock features are on.");
			switchLayout();
		});
		SkyBlock.bus().on(SbEvent.IslandChanged.class, e -> {
			if (isEnabled() && SkyBlock.state().onSkyBlock()) switchLayout();
		});
		SkyBlock.bus().on(SbEvent.LeftSkyBlock.class, e -> layouts.enter(HudLayouts.OUTSIDE));
	}

	@Override
	protected void onDisable() {
		if (layouts != null) layouts.enter(HudLayouts.OUTSIDE);
	}

	@Override
	protected void onEnable() {
		if (layouts != null && SkyBlock.state() != null && SkyBlock.state().onSkyBlock()) switchLayout();
	}

	private void switchLayout() {
		Island island = SkyBlock.state().island();
		String key = switch (layout.get()) {
			case OFF -> HudLayouts.OUTSIDE;
			case SHARED -> HudLayouts.SKYBLOCK;
			case PER_ISLAND -> island == Island.UNKNOWN ? HudLayouts.SKYBLOCK : HudLayouts.SKYBLOCK + ":" + island.mode();
		};
		layouts.enter(key);
	}

	@Override
	public Health health() {
		if (!SkyBlock.active()) return Health.missing("Not on SkyBlock");
		if (SkyBlock.simulator().running()) return Health.ok("Location from the simulator");
		return Health.ok(SkyBlock.state().fromModApi() ? "Location from the Hypixel Mod API" : "Location from the scoreboard and tab list");
	}
}
