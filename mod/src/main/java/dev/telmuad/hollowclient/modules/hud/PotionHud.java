package dev.telmuad.hollowclient.modules.hud;

import dev.telmuad.hollowclient.hud.HudModule;
import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.List;

/** Active potion effects with icon, name, level and time left. */
public final class PotionHud extends HudModule {
	private static final int ROW = 20;

	private final BooleanSetting icons = add(new BooleanSetting("icons", "Show icons", true));
	private final BooleanSetting effectColors = add(new BooleanSetting("effectColors", "Color names by effect", false));
	private final BooleanSetting hideVanilla = add(new BooleanSetting("hideVanilla", "Hide vanilla effect icons", false));

	private record Entry(Holder<MobEffect> effect, String name, String time, int color, boolean blink) {
	}

	private final List<Entry> entries = new ArrayList<>();
	private int width;

	public PotionHud() {
		super("potions", "Potion Effects", "Your active effects with time left.", true, at(Anchor.END, 2, Anchor.CENTER, 0));
		hideVanilla.describe("Hides the small effect icons Minecraft draws in the top-right corner.");
	}

	/** Whether the vanilla effect icons should be hidden (only while this module is on). */
	public boolean hidesVanilla() {
		return isEnabled() && hideVanilla.on();
	}

	@Override
	public boolean visibleInGame(Minecraft mc) {
		return mc.player != null && !mc.player.getActiveEffects().isEmpty();
	}

	@Override
	public Size measure(Minecraft mc, Font font, boolean preview) {
		entries.clear();
		width = 0;
		if (mc.player != null && !mc.player.getActiveEffects().isEmpty()) {
			float tickRate = mc.level != null ? mc.level.tickRateManager().tickrate() : 20f;
			List<MobEffectInstance> list = new ArrayList<>(mc.player.getActiveEffects());
			list.sort((a, b) -> Integer.compare(b.getDuration(), a.getDuration()));
			for (MobEffectInstance e : list) {
				String name = e.getEffect().value().getDisplayName().getString() + level(e.getAmplifier());
				String time = MobEffectUtil.formatDuration(e, 1.0f, tickRate).getString();
				int color = effectColors.on() ? ColorUtil.withAlpha(e.getEffect().value().getColor(), 255) : textColor.argb();
				entries.add(new Entry(e.getEffect(), name, time, color, e.endsWithin(200) && !e.isInfiniteDuration()));
			}
		} else if (preview) {
			entries.add(new Entry(MobEffects.SPEED, "Speed II", "1:30", textColor.argb(), false));
			entries.add(new Entry(MobEffects.FIRE_RESISTANCE, "Fire Resistance", "7:45", textColor.argb(), false));
		}
		for (Entry e : entries) width = Math.max(width, Math.max(width(font, e.name), width(font, e.time)));
		if (entries.isEmpty()) return new Size(0, 0);
		return new Size(2 * PAD + (icons.on() ? 18 + 4 : 0) + width, 2 * PAD + entries.size() * ROW - 2);
	}

	private static String level(int amplifier) {
		if (amplifier <= 0) return "";
		String key = "enchantment.level." + (amplifier + 1);
		Component c = Component.translatable(key);
		String s = c.getString();
		return " " + (s.equals(key) ? Integer.toString(amplifier + 1) : s);
	}

	@Override
	public void draw(GuiGraphicsExtractor g, Minecraft mc, Font font, boolean preview) {
		if (entries.isEmpty()) return;
		int w = 2 * PAD + (icons.on() ? 18 + 4 : 0) + width;
		drawBackground(g, w, 2 * PAD + entries.size() * ROW - 2);
		boolean right = alignRight();
		boolean blinkOff = (System.currentTimeMillis() / 400) % 2 == 0;
		int y = PAD;
		int slot = 0;
		for (Entry e : entries) {
			int iconX = right ? w - PAD - 18 : PAD;
			if (icons.on()) g.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(e.effect), iconX, y, 18, 18);
			int textLeft = icons.on() ? (right ? PAD : PAD + 22) : PAD;
			int textRight = icons.on() ? (right ? iconX - 4 : w - PAD) : w - PAD;
			int nx = right ? textRight - width(font, e.name) : textLeft;
			int tx = right ? textRight - width(font, e.time) : textLeft;
			text(g, font, e.name, nx, y, e.color);
			int timeColor = e.blink && blinkOff ? ColorUtil.fade(0xFFB0B3BD, 0.4f) : 0xFFB0B3BD;
			rollingText(g, font, e.time, tx, y + 10, timeColor, slot++);
			y += ROW;
		}
	}
}
