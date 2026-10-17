package dev.telmuad.hollowclient.skyblock.features.general;

import dev.telmuad.hollowclient.gui.Draw;
import dev.telmuad.hollowclient.hud.HudModule;
import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.mc.LegacyText;
import dev.telmuad.hollowclient.skyblock.parse.ActionBarParser;
import dev.telmuad.hollowclient.skyblock.parse.Patterns;
import dev.telmuad.hollowclient.skyblock.state.Island;
import dev.telmuad.hollowclient.skyblock.text.SbText;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockFeature;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Health, mana (with overflow) and defense as bars, read from the action bar, plus effective HP. Optionally the
 * same numbers are taken out of the action bar text, so they aren't shown twice (only what you see changes).
 */
public final class StatusBarsHud extends HudModule implements SkyBlockFeature {
	private static final int W = 128, BAR = 6;
	private static final int RED = 0xFFE5484D, GOLD = 0xFFF5C542, BLUE = 0xFF4C8DFF, CYAN = 0xFF55FFFF, GREEN = 0xFF5BD18B;
	private static StatusBarsHud instance;

	private final BooleanSetting hideText = add(new BooleanSetting("hideActionBar", "Take these out of the action bar", true));
	private final BooleanSetting showEhp = add(new BooleanSetting("ehp", "Effective health", true));
	private ActionBarParser.ActionBar shown;
	private int rows;

	public StatusBarsHud() {
		super("sb_status_bars", "Status Bars", "Health, mana and defense as bars (from the action bar), with effective health.",
				Category.SKYBLOCK, false, at(Anchor.CENTER, 0, Anchor.END, 62));
		hideText.describe("Removes health, defense and mana from the action bar text while this HUD is on. Only your screen changes.");
		instance = this;
	}

	@Override
	public String group() {
		return "General";
	}

	@Override
	public Set<Island> islands() {
		return Set.of();
	}

	@Override
	public boolean visibleInGame(Minecraft mc) {
		return mc.player != null && SkyBlockFeature.live(this, Set.of()) && SkyBlock.state().actionBar() != null;
	}

	@Override
	public Health health() {
		Health h = SkyBlockFeature.super.health();
		if (!h.ok()) return h;
		var ab = SkyBlock.state().actionBar();
		return ab != null && ab.health() >= 0 ? Health.ok("Reading the action bar") : Health.missing("No health in the action bar right now");
	}

	@Override
	public Size measure(Minecraft mc, Font font, boolean preview) {
		ActionBarParser.ActionBar ab = SkyBlock.state() == null ? null : SkyBlock.state().actionBar();
		if (ab == null && preview) ab = ActionBarParser.parse("§c1,234/1,500❤     §a512❈ Defense     §b300/400✎ Mana §3200ʬ");
		shown = ab;
		if (ab == null) return new Size(0, 0);
		rows = (ab.health() >= 0 ? 1 : 0) + (ab.mana() >= 0 ? 1 : 0) + (ab.defense() >= 0 || ab.health() >= 0 ? 1 : 0);
		return new Size(W + 2 * PAD, rows * 13 + 2 * PAD - 2);
	}

	@Override
	public void draw(GuiGraphicsExtractor g, Minecraft mc, Font font, boolean preview) {
		ActionBarParser.ActionBar ab = shown;
		if (ab == null || rows == 0) return;
		drawBackground(g, W + 2 * PAD, rows * 13 + 2 * PAD - 2);
		int y = PAD;
		if (ab.health() >= 0) {
			boolean over = ab.maxHealth() > 0 && ab.health() > ab.maxHealth();
			bar(g, font, y, ab.health(), ab.maxHealth(), over ? GOLD : RED, SbText.grouped(ab.health()) + "/" + SbText.grouped(ab.maxHealth()));
			y += 13;
		}
		if (ab.mana() >= 0) {
			String label = SbText.grouped(ab.mana()) + "/" + SbText.grouped(ab.maxMana()) + (ab.overflowMana() > 0 ? " +" + SbText.grouped(ab.overflowMana()) : "");
			bar(g, font, y, ab.mana(), ab.maxMana(), BLUE, label);
			y += 13;
		}
		if (ab.defense() >= 0 || ab.health() >= 0) {
			String d = ab.defense() >= 0 ? "❈ " + SbText.grouped(ab.defense()) : "";
			text(g, font, d, PAD, y + 1, GREEN);
			if (showEhp.on() && ab.health() >= 0) {
				String e = "EHP " + SbText.shortNumber(GeneralParse.effectiveHealth(ab.health(), Math.max(0, ab.defense())));
				text(g, font, e, PAD + W - width(font, e), y + 1, ab.overflowMana() > 0 ? CYAN : 0xFFE8E8F0);
			}
		}
	}

	private void bar(GuiGraphicsExtractor g, Font font, int y, int value, int max, int color, String label) {
		float f = max <= 0 ? 0 : Math.min(1f, value / (float) max);
		Draw.roundRect(g, PAD, y + 7, W, BAR - 2, 2, ColorUtil.fade(0x40FFFFFF, alpha));
		if (f > 0) Draw.roundRect(g, PAD, y + 7, Math.max(2, Math.round(W * f)), BAR - 2, 2, ColorUtil.fade(color, alpha));
		text(g, font, label, PAD, y - 2, color);
	}

	/** What the action bar shows: Hypixel's text without the parts the bars already show (while the HUD is on). */
	public static Component filterActionBar(Component message) {
		StatusBarsHud m = instance;
		if (m == null || !m.isEnabled() || !m.hideText.on() || !SkyBlockFeature.live(m, Set.of())) return message;
		String legacy = LegacyText.of(message);
		String[] parts = legacy.split("(?<=\\S) {3,}(?=\\S)|^ +| +$");
		Patterns p = Patterns.of("actionbar");
		List<String> kept = new ArrayList<>();
		boolean removed = false;
		for (String part : parts) {
			String clean = SbText.clean(part);
			if (clean.isEmpty()) continue;
			if (p.find("health", clean) != null || p.find("defense", clean) != null || p.find("mana", clean) != null) {
				removed = true;
				continue;
			}
			kept.add(part);
		}
		if (!removed) return message;
		return LegacyText.parse(String.join("     ", kept));
	}
}
