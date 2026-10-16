package dev.telmuad.hollowclient.modules.visual;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.ColorSetting;
import dev.telmuad.hollowclient.setting.EnumSetting;
import dev.telmuad.hollowclient.setting.Labeled;
import dev.telmuad.hollowclient.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.level.GameType;

/** Replaces the vanilla crosshair with a shape of your choice. Vanilla's attack indicator is kept. */
public final class CrosshairModule extends Module {
	public enum Shape implements Labeled {
		CROSS("Cross"), DOT("Dot"), CIRCLE("Circle"), X("X"), BOX("Box"), T("T-shape");
		private final String label;

		Shape(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private static final Identifier INDICATOR_FULL = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_full");
	private static final Identifier INDICATOR_BACKGROUND = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_background");
	private static final Identifier INDICATOR_PROGRESS = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_progress");

	private final EnumSetting<Shape> shape = add(new EnumSetting<>("shape", "Shape", Shape.CROSS));
	private final ColorSetting color = add(new ColorSetting("color", "Color", 0xFFFFFFFF));
	private final NumberSetting size = add(new NumberSetting("size", "Size", 4, 1, 20, 1));
	private final NumberSetting thickness = add(new NumberSetting("thickness", "Thickness", 1, 1, 5, 1));
	private final NumberSetting gap = add(new NumberSetting("gap", "Gap", 2, 0, 10, 1));
	private final BooleanSetting centerDot = add(new BooleanSetting("centerDot", "Center dot", false));
	private final BooleanSetting outline = add(new BooleanSetting("outline", "Outline", true));
	private final ColorSetting outlineColor = add(new ColorSetting("outlineColor", "Outline color", 0xC0000000));

	public CrosshairModule() {
		super("crosshair", "Custom Crosshair", "Pick your own crosshair shape, color and size.", Category.VISUAL, false);
		outlineColor.visibleWhen(outline::get);
		gap.visibleWhen(() -> shape.get() == Shape.CROSS || shape.get() == Shape.X || shape.get() == Shape.T);
	}

	/** Wraps the vanilla crosshair element (see {@code Modules.registerHooks}). */
	public HudElement wrap(HudElement vanilla) {
		return (g, dt) -> render(g, dt, vanilla);
	}

	private void render(GuiGraphicsExtractor g, DeltaTracker dt, HudElement vanilla) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.player == null || mc.gameMode == null
				|| mc.gameMode.getPlayerMode() == GameType.SPECTATOR
				|| mc.debugEntries.isCurrentlyEnabled(DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR)) {
			vanilla.extractRenderState(g, dt);
			return;
		}
		if (!mc.options.getCameraType().isFirstPerson()) return;
		g.nextStratum();
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		if (outline.on()) draw(g, cx, cy, outlineColor.argb(), 1);
		draw(g, cx, cy, color.argb(), 0);
		attackIndicator(g, mc);
	}

	/** Draw the shape; {@code grow} > 0 draws every piece that many pixels bigger (for the outline pass). */
	private void draw(GuiGraphicsExtractor g, int cx, int cy, int c, int grow) {
		int s = size.getInt();
		int t = thickness.getInt();
		int gp = gap.getInt();
		int half = (t - 1) / 2;
		switch (shape.get()) {
			case CROSS, T -> {
				// left, right, (top), bottom arms; the center pixel is (cx, cy)
				rect(g, cx - gp - s, cy - half, cx - gp, cy - half + t, c, grow);
				rect(g, cx + 1 + gp, cy - half, cx + 1 + gp + s, cy - half + t, c, grow);
				if (shape.get() == Shape.CROSS) rect(g, cx - half, cy - gp - s, cx - half + t, cy - gp, c, grow);
				rect(g, cx - half, cy + 1 + gp, cx - half + t, cy + 1 + gp + s, c, grow);
			}
			case DOT -> {
				int d = Math.max(1, s / 2);
				ring(g, cx, cy, 0, d, c, grow, true);
			}
			case CIRCLE -> ring(g, cx, cy, s, t, c, grow, false);
			case X -> {
				for (int i = gp + 1; i <= gp + s; i++) {
					rect(g, cx - i - half, cy - i - half, cx - i - half + t, cy - i - half + t, c, grow);
					rect(g, cx + i - half, cy - i - half, cx + i - half + t, cy - i - half + t, c, grow);
					rect(g, cx - i - half, cy + i - half, cx - i - half + t, cy + i - half + t, c, grow);
					rect(g, cx + i - half, cy + i - half, cx + i - half + t, cy + i - half + t, c, grow);
				}
			}
			case BOX -> {
				int x0 = cx - s;
				int y0 = cy - s;
				int x1 = cx + s + 1;
				int y1 = cy + s + 1;
				rect(g, x0, y0, x1, y0 + t, c, grow);
				rect(g, x0, y1 - t, x1, y1, c, grow);
				rect(g, x0, y0 + t, x0 + t, y1 - t, c, grow);
				rect(g, x1 - t, y0 + t, x1, y1 - t, c, grow);
			}
		}
		if (centerDot.on() && shape.get() != Shape.DOT) rect(g, cx - half, cy - half, cx - half + t, cy - half + t, c, grow);
	}

	private static void rect(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int c, int grow) {
		g.fill(x0 - grow, y0 - grow, x1 + grow, y1 + grow, c);
	}

	/**
	 * A ring of radius {@code r} and thickness {@code t} (or a filled disc of radius {@code t} when {@code filled}),
	 * drawn as horizontal runs, one or two per row.
	 */
	private static void ring(GuiGraphicsExtractor g, int cx, int cy, int r, int t, int c, int grow, boolean filled) {
		double outer = (filled ? t : r + t / 2.0) + grow + 0.5;
		double inner = filled ? -1 : Math.max(-1, r - t / 2.0 - grow - 0.5 + (t % 2 == 0 ? 0.5 : 0));
		int ext = (int) Math.ceil(outer);
		for (int dy = -ext; dy <= ext; dy++) {
			double yy = dy * dy;
			if (yy > outer * outer) continue;
			int xo = (int) Math.floor(Math.sqrt(outer * outer - yy));
			if (inner < 0 || yy >= inner * inner) {
				g.fill(cx - xo, cy + dy, cx + xo + 1, cy + dy + 1, c);
			} else {
				int xi = (int) Math.ceil(Math.sqrt(inner * inner - yy));
				if (xi > xo) continue;
				g.fill(cx - xo, cy + dy, cx - xi + 1, cy + dy + 1, c);
				g.fill(cx + xi, cy + dy, cx + xo + 1, cy + dy + 1, c);
			}
		}
	}

	/** Same logic as the vanilla attack indicator under the crosshair. */
	private static void attackIndicator(GuiGraphicsExtractor g, Minecraft mc) {
		if (mc.options.attackIndicator().get() != AttackIndicatorStatus.CROSSHAIR) return;
		float strength = mc.player.getAttackStrengthScale(0.0F);
		boolean full = false;
		if (mc.crosshairPickEntity instanceof LivingEntity && strength >= 1.0F) {
			full = mc.player.getCurrentItemAttackStrengthDelay() > 5.0F;
			full &= mc.crosshairPickEntity.isAlive();
			AttackRange range = mc.player.getActiveItem().get(DataComponents.ATTACK_RANGE);
			full &= range == null || mc.hitResult == null || range.isInRange(mc.player, mc.hitResult.getLocation());
		}
		int y = g.guiHeight() / 2 - 7 + 16;
		int x = g.guiWidth() / 2 - 8;
		if (full) {
			g.blitSprite(RenderPipelines.CROSSHAIR, INDICATOR_FULL, x, y, 16, 16);
		} else if (strength < 1.0F) {
			int w = (int) (strength * 17.0F);
			g.blitSprite(RenderPipelines.CROSSHAIR, INDICATOR_BACKGROUND, x, y, 16, 4);
			g.blitSprite(RenderPipelines.CROSSHAIR, INDICATOR_PROGRESS, 16, 4, 0, 0, x, y, w, 4);
		}
	}
}
