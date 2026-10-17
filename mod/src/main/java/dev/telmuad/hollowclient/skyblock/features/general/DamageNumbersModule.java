package dev.telmuad.hollowclient.skyblock.features.general;

import dev.telmuad.hollowclient.skyblock.mc.LegacyText;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockModule;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

/** Short damage numbers on SkyBlock ("✧1.23M✧" instead of "✧1,234,567✧"). Only how the number is drawn changes. */
public final class DamageNumbersModule extends SkyBlockModule {
	private static DamageNumbersModule instance;

	public DamageNumbersModule() {
		super("sb_damage_numbers", "Compact Damage", "Shows damage numbers short: 1.23M instead of 1,234,567.", "General", false);
		instance = this;
	}

	/** Called for every entity's name tag (EntityRendererNameMixin). */
	public static void restyle(Entity entity, EntityRenderState state) {
		DamageNumbersModule m = instance;
		if (m == null || state.nameTag == null || !(entity instanceof ArmorStand) || !m.live()) return;
		String legacy = LegacyText.of(state.nameTag);
		String compact = GeneralParse.compactDamage(legacy);
		if (compact == null) return;
		// keep the first color of the original so crits stay colorful-ish
		String color = legacy.length() >= 2 && legacy.charAt(0) == '§' ? legacy.substring(0, 2) : "";
		state.nameTag = LegacyText.parse(color + compact);
	}
}
