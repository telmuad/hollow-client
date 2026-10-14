package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.module.Module;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Placeholder until milestone 4. */
public final class HudEditorScreen extends HollowScreen {
	public HudEditorScreen(Screen parent) {
		this(parent, null);
	}

	public HudEditorScreen(Screen parent, Module focus) {
		super(Component.literal("HUD editor"), parent);
	}

	@Override
	protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float alpha) {
	}
}
