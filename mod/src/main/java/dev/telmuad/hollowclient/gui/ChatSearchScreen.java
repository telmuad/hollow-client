package dev.telmuad.hollowclient.gui;

import dev.telmuad.hollowclient.chat.ChatFx;
import dev.telmuad.hollowclient.gui.widget.UiButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Chat+ search (Ctrl+F in chat): the last 1000 chat lines, filtered as you type. Click a line to copy it. */
public final class ChatSearchScreen extends HollowScreen {
	private static final int ROW = 13;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

	private Rect panel = Rect.EMPTY;
	private Rect list = Rect.EMPTY;
	private Rect searchBox = Rect.EMPTY;
	private EditBox search;
	private final UiButton back = new UiButton("< Back", this::onClose);
	private List<ChatFx.Entry> results = new ArrayList<>();
	private String lastQuery;
	private float scroll;
	private float scrollTarget;

	public ChatSearchScreen(Screen parent) {
		super(Component.literal("Chat search"), parent);
	}

	@Override
	protected void init() {
		super.init();
		int pw = Math.min(width - 24, 560);
		int ph = Math.min(height - 24, 320);
		panel = new Rect((width - pw) / 2, (height - ph) / 2, pw, ph);
		back.bounds(new Rect(panel.x() + 8, panel.y() + 8, back.preferredWidth(font), 16));
		searchBox = new Rect(back.bounds().right() + 8, panel.y() + 8, pw - back.bounds().w() - 24, 16);
		list = new Rect(panel.x() + 8, panel.y() + 32, pw - 16, ph - 32 - 20);
		search = new EditBox(font, searchBox.x() + 14, searchBox.y() + 4, searchBox.w() - 18, 10, Component.literal("Search"));
		search.setBordered(false);
		search.setMaxLength(80);
		search.setTextColor(Theme.TEXT);
		search.setHint(Ui.text("Type to search the chat").withStyle(ChatFormatting.DARK_GRAY));
		addWidget(search);
		setInitialFocus(search);
		search.setFocused(true);
		refresh();
	}

	private void refresh() {
		String q = search.getValue().trim().toLowerCase(Locale.ROOT);
		if (q.equals(lastQuery)) return;
		lastQuery = q;
		List<ChatFx.Entry> out = new ArrayList<>();
		for (ChatFx.Entry e : ChatFx.history()) {
			if (q.isEmpty() || e.text().toLowerCase(Locale.ROOT).contains(q)) out.add(e);
		}
		results = out;
		scroll = scrollTarget = 0;
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float a) {
		refresh();
		drawPanel(g, panel, 8, a);
		back.render(g, font, mx, my, dt, a);
		boolean focused = search.isFocused();
		Draw.roundRect(g, searchBox, Theme.radius(4), Theme.fade(focused ? Theme.CARD_HOVER : Theme.CARD, a));
		Draw.roundOutline(g, searchBox, Theme.radius(4), Theme.fade(focused ? Theme.accent() : Theme.STROKE, a));
		HollowMenuScreen.drawMagnifier(g, searchBox.x() + 4, searchBox.y() + 4, Theme.fade(focused ? Theme.accent() : Theme.TEXT_DIM, a));
		if (a > 0.5f) search.extractRenderState(g, mx, my, 0);

		scroll = Anim.approach(scroll, scrollTarget, dt, 18f);
		g.enableScissor(list.x(), list.y(), list.right(), list.bottom());
		int y = list.y() - Math.round(scroll);
		String q = lastQuery == null ? "" : lastQuery;
		for (ChatFx.Entry e : results) {
			if (y + ROW >= list.y() && y <= list.bottom()) {
				Rect r = new Rect(list.x(), y, list.w() - 6, ROW - 1);
				boolean hover = r.contains(mx, my) && list.contains(mx, my);
				if (hover) Draw.roundRect(g, r, 2, Theme.fade(Theme.CARD_HOVER, a));
				String time = e.time().format(TIME);
				Ui.draw(g, font, time, r.x() + 3, y + 2, Theme.fade(Theme.TEXT_FAINT, a));
				int tx = r.x() + 8 + Ui.width(font, time);
				drawWithMatch(g, Ui.fit(font, e.text(), r.right() - tx - 4), q, tx, y + 2, a);
			}
			y += ROW;
		}
		g.disableScissor();
		String count = results.isEmpty() ? (q.isEmpty() ? "No chat yet" : "No matches") : results.size() + (q.isEmpty() ? (results.size() == 1 ? " line" : " lines") : (results.size() == 1 ? " match" : " matches")) + " · click a line to copy it";
		Ui.draw(g, font, count, panel.x() + 10, panel.bottom() - 14, Theme.fade(Theme.TEXT_FAINT, a));
	}

	private void drawWithMatch(GuiGraphicsExtractor g, String text, String q, int x, int y, float a) {
		int at = q.isEmpty() ? -1 : text.toLowerCase(Locale.ROOT).indexOf(q);
		if (at < 0) {
			Ui.draw(g, font, text, x, y, Theme.fade(Theme.TEXT, a));
			return;
		}
		int end = Math.min(text.length(), at + q.length());
		String before = text.substring(0, at);
		String match = text.substring(at, end);
		Ui.draw(g, font, before, x, y, Theme.fade(Theme.TEXT, a));
		int mxp = x + Ui.width(font, before);
		int mw = Ui.width(font, match);
		Draw.roundRect(g, mxp - 1, y - 1, mw + 2, 10, 2, Theme.fade(Theme.accent(), a * 0.3f));
		Ui.draw(g, font, match, mxp, y, Theme.fade(Theme.mix(Theme.accent(), 0xFFFFFFFF, 0.4f), a));
		Ui.draw(g, font, text.substring(end), mxp + mw, y, Theme.fade(Theme.TEXT, a));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && back.click(event.x(), event.y())) return true;
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && list.contains(event.x(), event.y())) {
			int i = (int) ((event.y() - list.y() + scroll) / ROW);
			if (i >= 0 && i < results.size()) {
				String text = results.get(i).text();
				if (minecraft != null) minecraft.keyboardHandler.setClipboard(text);
				UiSounds.click();
				ripple(event.x(), event.y(), new Rect(list.x(), list.y() + i * ROW - Math.round(scroll), list.w() - 6, ROW - 1));
				Toasts.success("Copied", text.length() > 60 ? text.substring(0, 57) + "..." : text);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		float max = Math.max(0, results.size() * ROW - list.h());
		scrollTarget = (float) Math.max(0, Math.min(max, scrollTarget - sy * 26));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}
}
