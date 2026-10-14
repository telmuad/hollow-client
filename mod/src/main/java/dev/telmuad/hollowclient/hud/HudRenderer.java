package dev.telmuad.hollowclient.hud;

import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.gui.HudEditorScreen;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.modules.hud.PotionHud;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** Draws every enabled HUD module through Fabric's HUD registry (no mixin needed). */
public final class HudRenderer {
	private HudRenderer() {
	}

	public static void register() {
		HudElementRegistry.addLast(HollowClient.id("hud"), HudRenderer::render);
		HudElementRegistry.replaceElement(VanillaHudElements.STATUS_EFFECTS, vanilla -> (g, dt) -> {
			boolean hide = HollowClient.modules().get(PotionHud.class).map(PotionHud::hidesVanilla).orElse(false);
			if (!hide) vanilla.render(g, dt);
		});
	}

	public static List<HudModule> hudModules() {
		List<HudModule> out = new ArrayList<>();
		for (Module m : HollowClient.modules().all()) if (m instanceof HudModule h) out.add(h);
		return out;
	}

	private static void render(GuiGraphics g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.player == null) return;
		if (mc.screen instanceof HudEditorScreen) return;
		if (mc.getDebugOverlay().showDebugScreen()) return;
		for (HudModule m : hudModules()) {
			if (!m.isEnabled() || !m.visibleInGame(mc)) continue;
			try {
				drawModule(g, mc, m, false);
			} catch (RuntimeException e) {
				HollowClient.LOG.error("HUD element {} failed to draw", m.id(), e);
			}
		}
	}

	/** Measure, place and draw one element. Returns where it was drawn (scaled box), or null if it is empty. */
	public static HudLayout.Box drawModule(GuiGraphics g, Minecraft mc, HudModule m, boolean preview) {
		HudModule.Size size = m.measure(mc, mc.font, preview);
		if (size.w() <= 0 || size.h() <= 0) return null;
		float scale = m.hudPosition().scale;
		HudLayout.Box box = HudLayout.place(m.hudPosition(), g.guiWidth(), g.guiHeight(), size.w() * scale, size.h() * scale);
		float x = Math.round(box.x());
		float y = Math.round(box.y());
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		m.draw(g, mc, mc.font, preview);
		g.pose().popMatrix();
		return new HudLayout.Box(x, y, box.w(), box.h());
	}
}
