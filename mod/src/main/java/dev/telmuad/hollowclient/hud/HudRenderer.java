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

import dev.telmuad.hollowclient.gui.Anim;
import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.modules.hud.HudStyleModule;
import dev.telmuad.hollowclient.render.Quality;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

	/** Appear progress per element: below 0 = still waiting (staggered), 1 = fully shown. */
	private static final Map<HudModule, Float> APPEAR = new HashMap<>();
	private static long lastNanos;

	private static void render(GuiGraphics g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.player == null || mc.getDebugOverlay().showDebugScreen()) {
			APPEAR.clear(); // elements fade in again when the HUD comes back
			return;
		}
		dev.telmuad.hollowclient.render.EffectsFx.renderFlash(g);
		if (mc.screen instanceof HudEditorScreen) return;
		long now = System.nanoTime();
		float frame = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;
		HudStyleModule style = HudStyle.module();
		float slide = style == null || Quality.reducedMotion() ? 0f : style.appear();
		int index = 0;
		for (HudModule m : hudModules()) {
			if (!m.isEnabled() || !m.visibleInGame(mc)) {
				APPEAR.remove(m);
				continue;
			}
			float p = APPEAR.getOrDefault(m, -0.15f * index);
			p = Math.min(1f, p + frame * 3.4f * Theme.speed());
			APPEAR.put(m, p);
			index++;
			try {
				drawModule(g, mc, m, false, slide > 0f ? Anim.clamp01(p) : 1f, slide);
			} catch (RuntimeException e) {
				HollowClient.LOG.error("HUD element {} failed to draw", m.id(), e);
			}
		}
	}

	/** Measure, place and draw one element. Returns where it was drawn (scaled box), or null if it is empty. */
	public static HudLayout.Box drawModule(GuiGraphics g, Minecraft mc, HudModule m, boolean preview) {
		return drawModule(g, mc, m, preview, 1f, 0f);
	}

	/**
	 * Like {@link #drawModule(GuiGraphics, Minecraft, HudModule, boolean)}, while appearing: {@code appear} 0..1 fades
	 * the element in and slides it in from its screen edge by up to {@code slide} x 12 pixels.
	 */
	public static HudLayout.Box drawModule(GuiGraphics g, Minecraft mc, HudModule m, boolean preview, float appear, float slide) {
		HudModule.Size size = m.measure(mc, mc.font, preview);
		if (size.w() <= 0 || size.h() <= 0) return null;
		float scale = m.hudPosition().scale;
		HudLayout.Box box = HudLayout.place(m.hudPosition(), g.guiWidth(), g.guiHeight(), size.w() * scale, size.h() * scale);
		float x = Math.round(box.x());
		float y = Math.round(box.y());
		float e = Anim.easeOutCubic(appear);
		float offset = (1f - e) * 12f * slide;
		float ox = switch (m.hudPosition().anchorX) {
			case START -> -offset;
			case END -> offset;
			case CENTER -> 0f;
		};
		float oy = m.hudPosition().anchorX == HudPosition.Anchor.CENTER
				? (m.hudPosition().anchorY == HudPosition.Anchor.END ? offset : -offset) : 0f;
		m.setAlpha(e);
		g.pose().pushMatrix();
		g.pose().translate(x + ox, y + oy);
		g.pose().scale(scale, scale);
		try {
			if (e > 0.01f) m.draw(g, mc, mc.font, preview);
		} finally {
			g.pose().popMatrix();
			m.setAlpha(1f);
		}
		return new HudLayout.Box(x, y, box.w(), box.h());
	}
}
