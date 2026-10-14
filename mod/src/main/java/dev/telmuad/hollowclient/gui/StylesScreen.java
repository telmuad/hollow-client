package dev.telmuad.hollowclient.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Placeholder until milestone 6. */
public final class StylesScreen extends HollowScreen {
	public enum Tab {CAPES, COSMETICS, PACKS, PRESETS}

	public StylesScreen(Tab tab, Screen parent) {
		super(Component.literal("Styles"), parent);
	}

	public static String capeSummary() {
		return "Built-in capes or your own PNG";
	}

	public static String cosmeticsSummary() {
		return "Hat and wings on your player";
	}

	public static String packSummary() {
		return "Import and switch resource packs";
	}

	public static String presetSummary() {
		return "Save and switch style combos";
	}

	@Override
	protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float alpha) {
	}
}
