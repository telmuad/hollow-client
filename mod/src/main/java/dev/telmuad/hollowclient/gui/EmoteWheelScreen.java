package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.input.Keybinds;
import dev.telmuad.hollowclient.styles.Emotes;
import dev.telmuad.hollowclient.styles.Emotes.Emote;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * The emote wheel: hold B, point at an emote, let go to play it. A quick tap keeps the wheel open so you can click
 * instead. The game keeps running behind it (it doesn't pause and isn't blurred).
 */
public final class EmoteWheelScreen extends HollowScreen {
	private static final long TAP_MS = 250;

	private final long openedMillis = Util.getMillis();
	private final float[] hover = new float[Emote.values().length];
	private int selected = -1;
	private boolean sticky;

	public EmoteWheelScreen() {
		super(Component.literal("Emotes"), null);
	}

	@Override
	public int blurRadius(int vanilla) {
		return 0;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		float p = openProgress();
		g.fillGradient(0, 0, width, height, Theme.fade(0x30000000, p), Theme.fade(0x60000000, p));
	}

	private float radius() {
		return Math.max(62f, Math.min(116f, Math.min(width, height) * 0.32f));
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Emote[] emotes = Emote.values();
		float cx = width / 2f;
		float cy = height / 2f;
		float r = radius();
		float dx = mouseX - cx;
		float dy = mouseY - cy;
		selected = -1;
		if (dx * dx + dy * dy > 18 * 18) {
			double angle = Math.atan2(dy, dx) + Math.PI / 2;
			if (angle < 0) angle += Math.PI * 2;
			double sector = Math.PI * 2 / emotes.length;
			selected = (int) Math.floor((angle + sector / 2) / sector) % emotes.length;
		}
		// the ring
		Fx.disc(g, cx, cy, r + 34, Theme.fade(0xB80B0C13, a));
		Fx.disc(g, cx, cy, r - 30, Theme.fade(0xD0090A10, a));
		if (Theme.glow() > 0f) Fx.dot(g, cx, cy, r + 46, Theme.fade(Theme.accent(), a * 0.18f * Theme.glow()));
		for (int i = 0; i < emotes.length; i++) {
			hover[i] = Anim.approach(hover[i], i == selected ? 1f : 0f, dt, 16f);
			double ang = i * Math.PI * 2 / emotes.length - Math.PI / 2;
			float ex = cx + (float) Math.cos(ang) * r;
			float ey = cy + (float) Math.sin(ang) * r;
			float h = hover[i];
			float size = 26f + 3f * h;
			if (h > 0.01f) Fx.dot(g, ex, ey, size * 2.2f, Theme.fade(Theme.accent(), a * h * 0.45f));
			Fx.disc(g, ex, ey, size, Theme.fade(Theme.mix(0xFF1A1C27, Theme.accentSoft(), h), a));
			String label = Ui.fit(font, emotes[i].label(), Math.round(size * 2) - 6);
			Ui.center(g, font, label, Math.round(ex), Math.round(ey) - 4, Theme.fade(Theme.mix(Theme.TEXT_DIM, 0xFFFFFFFF, h), a));
		}
		// center: what you're pointing at
		Fx.sigil(g, cx - 9, cy - 22, 18, a * 0.8f, 1f, Theme.glow());
		String title = selected >= 0 ? emotes[selected].label() : "Emotes";
		Ui.center(g, font, Ui.bold(title), Math.round(cx), Math.round(cy), Theme.fade(Theme.TEXT, a));
		String hint = sticky ? "Click one to play" : "Let go of the key to play";
		Ui.center(g, font, hint, Math.round(cx), Math.round(cy) + 12, Theme.fade(Theme.TEXT_FAINT, a));
		Emote playing = Emotes.current();
		if (playing != null) {
			String now = "Playing: " + playing.label();
			Ui.center(g, font, now, Math.round(cx), Math.round(cy + r + 38), Theme.fade(ColorUtil.mix(Theme.TEXT_DIM, Theme.accent(), 0.5f), a));
		}
	}

	private void playSelected() {
		if (selected >= 0) {
			UiSounds.click();
			Emotes.play(Emote.values()[selected]);
		}
		if (minecraft != null) minecraft.gui.setScreen(null);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (Keybinds.emoteKey() != null && Keybinds.emoteKey().matches(event)) {
			if (Util.getMillis() - openedMillis < TAP_MS && selected < 0) {
				sticky = true; // a quick tap: stay open and wait for a click
				return true;
			}
			playSelected();
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			playSelected();
			return true;
		}
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			Emotes.stop();
			if (minecraft != null) minecraft.gui.setScreen(null);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** For tests: point at an emote. */
	public void select(int index) {
		selected = index;
	}
}
