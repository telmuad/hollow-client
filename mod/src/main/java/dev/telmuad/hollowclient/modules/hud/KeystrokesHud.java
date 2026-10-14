package dev.telmuad.hollowclient.modules.hud;

import dev.telmuad.hollowclient.gui.Anim;
import dev.telmuad.hollowclient.hud.HudModule;
import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.input.Clicks;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.ColorSetting;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** W A S D, both mouse buttons (with CPS) and the space bar; held keys light up. */
public final class KeystrokesHud extends HudModule {
	private static final int KEY = 18;
	private static final int GAP = 2;
	private static final int WIDTH = KEY * 3 + GAP * 2;

	private final BooleanSetting showMouse = add(new BooleanSetting("showMouse", "Show mouse buttons", true));
	private final BooleanSetting showCps = add(new BooleanSetting("showCps", "Show CPS under mouse buttons", true));
	private final BooleanSetting showSpace = add(new BooleanSetting("showSpace", "Show space bar", true));
	private final ColorSetting pressedColor = add(new ColorSetting("pressedColor", "Pressed key color", 0xD0F0F0F5));
	private final ColorSetting pressedText = add(new ColorSetting("pressedTextColor", "Pressed text color", 0xFF101015));
	private final float[] glow = new float[7];
	private long lastNanos;

	public KeystrokesHud() {
		super("keystrokes", "Keystrokes", "Shows W A S D, mouse buttons and space as you press them.", false,
				at(Anchor.START, 2, Anchor.CENTER, 30));
		showCps.visibleWhen(showMouse::get);
	}

	@Override
	public Size measure(Minecraft mc, Font font, boolean preview) {
		int h = KEY * 2 + GAP;
		if (showMouse.on()) h += GAP + KEY;
		if (showSpace.on()) h += GAP + 10;
		return new Size(WIDTH, h);
	}

	@Override
	public void draw(GuiGraphics g, Minecraft mc, Font font, boolean preview) {
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0 : Math.min(0.1f, (now - lastNanos) / 1e9f);
		lastNanos = now;
		Options o = mc.options;
		boolean demo = preview && mc.player == null;
		int x1 = KEY + GAP;
		int x2 = 2 * (KEY + GAP);
		key(g, font, 0, o.keyUp, x1, 0, KEY, KEY, dt);
		key(g, font, 1, o.keyLeft, 0, KEY + GAP, KEY, KEY, dt);
		key(g, font, 2, o.keyDown, x1, KEY + GAP, KEY, KEY, dt);
		key(g, font, 3, o.keyRight, x2, KEY + GAP, KEY, KEY, dt);
		int y = 2 * (KEY + GAP);
		if (showMouse.on()) {
			int mw = (WIDTH - GAP) / 2;
			String l = showCps.on() ? (demo ? 9 : Clicks.leftCps()) + " CPS" : null;
			String r = showCps.on() ? (demo ? 4 : Clicks.rightCps()) + " CPS" : null;
			mouseKey(g, font, 4, o.keyAttack, "LMB", l, 0, y, mw, dt);
			mouseKey(g, font, 5, o.keyUse, "RMB", r, mw + GAP, y, WIDTH - mw - GAP, dt);
			y += KEY + GAP;
		}
		if (showSpace.on()) {
			boolean down = o.keyJump.isDown();
			glow[6] = Anim.approach(glow[6], down ? 1f : 0f, dt, 25f);
			box(g, 0, y, WIDTH, 10, glow[6]);
			int c = ColorUtil.mix(textColor.argb(), pressedText.argb(), glow[6]);
			int bar = WIDTH / 3;
			g.fill(bar, y + 4, WIDTH - bar, y + 5, c);
		}
	}

	private void key(GuiGraphics g, Font font, int i, KeyMapping mapping, int x, int y, int w, int h, float dt) {
		glow[i] = Anim.approach(glow[i], mapping.isDown() ? 1f : 0f, dt, 25f);
		box(g, x, y, w, h, glow[i]);
		String label = label(mapping);
		int c = ColorUtil.mix(textColor.argb(), pressedText.argb(), glow[i]);
		text(g, font, label, x + (w - font.width(label)) / 2 + 1, y + (h - 8) / 2 + 1, c);
	}

	private void mouseKey(GuiGraphics g, Font font, int i, KeyMapping mapping, String name, String cps, int x, int y, int w, float dt) {
		glow[i] = Anim.approach(glow[i], mapping.isDown() ? 1f : 0f, dt, 25f);
		box(g, x, y, w, KEY, glow[i]);
		int c = ColorUtil.mix(textColor.argb(), pressedText.argb(), glow[i]);
		if (cps == null) {
			text(g, font, name, x + (w - font.width(name)) / 2 + 1, y + 6, c);
		} else {
			g.pose().pushMatrix();
			g.pose().translate(x + w / 2f, y + 2);
			g.pose().scale(0.75f, 0.75f);
			text(g, font, name, -font.width(name) / 2, 0, c);
			text(g, font, cps, -font.width(cps) / 2, 11, c);
			g.pose().popMatrix();
		}
	}

	private void box(GuiGraphics g, int x, int y, int w, int h, float t) {
		int base = background.on() ? backgroundColor.argb() : 0;
		int color = ColorUtil.mix(base, pressedColor.argb(), t);
		if ((color >>> 24) != 0) g.fill(x, y, x + w, y + h, color);
	}

	private static String label(KeyMapping mapping) {
		String s = mapping.getTranslatedKeyMessage().getString();
		if (s.length() > 3) s = s.substring(0, 3);
		return s.toUpperCase(java.util.Locale.ROOT);
	}
}
