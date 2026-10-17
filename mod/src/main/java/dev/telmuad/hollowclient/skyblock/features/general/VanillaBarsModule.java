package dev.telmuad.hollowclient.skyblock.features.general;

import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockModule;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

/** Hides vanilla's hearts, armor and food bars on SkyBlock (SkyBlock shows its own numbers). Only drawing changes. */
public final class VanillaBarsModule extends SkyBlockModule {
	private static VanillaBarsModule instance;

	private final BooleanSetting hearts = add(new BooleanSetting("hearts", "Hide hearts", true));
	private final BooleanSetting armor = add(new BooleanSetting("armor", "Hide armor", true));
	private final BooleanSetting food = add(new BooleanSetting("food", "Hide food", true));

	public VanillaBarsModule() {
		super("sb_vanilla_bars", "Hide Vanilla Bars", "Hides the vanilla hearts, armor and food bars while on SkyBlock.", "General", false);
		instance = this;
	}

	/** Wraps the three vanilla HUD elements once at startup. */
	public static void registerHooks() {
		wrap(VanillaHudElements.HEALTH_BAR, 0);
		wrap(VanillaHudElements.ARMOR_BAR, 1);
		wrap(VanillaHudElements.FOOD_BAR, 2);
	}

	private static void wrap(Identifier id, int which) {
		HudElementRegistry.replaceElement(id, vanilla -> (HudElement) (g, delta) -> {
			if (!hidden(which)) vanilla.extractRenderState(g, delta);
		});
	}

	private static boolean hidden(int which) {
		VanillaBarsModule m = instance;
		if (m == null || !m.live()) return false;
		return switch (which) {
			case 0 -> m.hearts.on();
			case 1 -> m.armor.on();
			default -> m.food.on();
		};
	}
}
