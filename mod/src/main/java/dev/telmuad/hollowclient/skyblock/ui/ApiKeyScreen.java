package dev.telmuad.hollowclient.skyblock.ui;

import dev.telmuad.hollowclient.gui.Draw;
import dev.telmuad.hollowclient.gui.Rect;
import dev.telmuad.hollowclient.gui.Text;
import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.gui.Ui;
import dev.telmuad.hollowclient.gui.widget.UiButton;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.data.ApiKeyStore;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.io.IOException;

/** The optional Hypixel API key: paste it, save it (encrypted for your Windows account), or remove it. */
public final class ApiKeyScreen extends SbScreen {
	private final UiButton save = new UiButton("Save", this::saveKey).style(UiButton.Style.PRIMARY);
	private final UiButton clear = new UiButton("Remove key", this::clearKey).style(UiButton.Style.DANGER);
	private EditBox field;
	private Rect box = Rect.EMPTY;
	private String status = "";

	public ApiKeyScreen(Screen parent) {
		super("Hypixel API key", parent);
	}

	@Override
	protected void init() {
		super.init();
		box = new Rect(body.x(), body.y() + 70, Math.min(320, body.w() - 90), 16);
		field = new EditBox(font, box.x() + 5, box.y() + 4, box.w() - 8, 10, Component.literal("API key"));
		field.setBordered(false);
		field.setMaxLength(40);
		field.setTextColor(Theme.TEXT);
		field.setHint(Ui.text("Paste your key").withStyle(ChatFormatting.DARK_GRAY));
		addWidget(field);
		save.bounds(new Rect(box.right() + 6, box.y(), save.preferredWidth(font) + 8, 16));
		clear.bounds(new Rect(body.x(), box.bottom() + 10, clear.preferredWidth(font) + 8, 16));
	}

	@Override
	protected String subtitle() {
		String key = SkyBlock.apiKeys().read();
		return key == null ? "Optional · none saved" : "Saved: " + ApiKeyStore.masked(key);
	}

	@Override
	protected String footer() {
		return status.isEmpty() ? "The key is never logged, sent anywhere except Hypixel's API, or shown in full." : status;
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float a) {
		drawFrame(g, mx, my, a);
		String text = "You don't need a key: every SkyBlock Mode feature works with Hypixel's public data. A key is only for future "
				+ "extras that say they need one. "
				+ (SkyBlock.apiKeys().secure() ? "It's stored encrypted for your Windows account (Windows DPAPI), so other accounts and other computers can't read it."
				: "This system can't encrypt it, so it would be stored as a plain file in Hollow's config folder.");
		Text.wrapped(g, font, text, body.x(), body.y() + 4, body.w(), 6, Theme.fade(Theme.TEXT_DIM, a));
		Draw.roundRect(g, box, Theme.radius(4), Theme.fade(field.isFocused() ? Theme.CARD_HOVER : Theme.CARD, a));
		Draw.roundOutline(g, box, Theme.radius(4), Theme.fade(field.isFocused() ? Theme.accent() : Theme.STROKE, a));
		if (a > 0.5f) field.extractRenderState(g, mx, my, 0);
		save.render(g, font, mx, my, dt, a);
		clear.setEnabled(SkyBlock.apiKeys().present());
		clear.render(g, font, mx, my, dt, a);
	}

	private void saveKey() {
		try {
			SkyBlock.apiKeys().save(field.getValue());
			field.setValue("");
			status = "Saved.";
		} catch (IOException e) {
			status = e.getMessage();
		}
	}

	private void clearKey() {
		try {
			SkyBlock.apiKeys().clear();
			status = "Removed.";
		} catch (IOException e) {
			status = "Couldn't remove it: " + e.getMessage();
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing()) return true;
		double mx = event.x(), my = event.y();
		if (event.button() == 0) {
			if (save.click(mx, my) || clear.click(mx, my)) return true;
			field.setFocused(box.contains(mx, my));
			if (field.isFocused()) setFocused(field);
		}
		return super.mouseClicked(event, doubleClick);
	}
}
