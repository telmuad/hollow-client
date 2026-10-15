package dev.telmuad.hollowclient.chat;

import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.gui.Toasts;
import dev.telmuad.hollowclient.gui.UiSounds;
import dev.telmuad.hollowclient.mixin.ChatComponentAccessor;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.qol.AutoGgModule;
import dev.telmuad.hollowclient.modules.qol.ChatMacrosModule;
import dev.telmuad.hollowclient.modules.qol.ChatPlusModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Chat+ / Auto GG / Chat Macros. Incoming lines pass through {@link #process} before vanilla shows them; that is
 * where timestamps, highlights and "x3" stacking happen and where the end-of-game phrases are noticed. Outgoing
 * messages (Auto GG, macros) are ordinary chat messages or commands, rate limited.
 */
public final class ChatFx {
	/** One remembered chat line, for search. */
	public record Entry(LocalTime time, String text) {
	}

	public record Result(Component content, GuiMessageTag tag) {
	}

	private static final int HISTORY = 1000;
	private static final long STACK_WINDOW_MS = 60_000;
	private static final Deque<Entry> HISTORY_LINES = new ArrayDeque<>();

	private static String lastKey;
	private static int lastCount;
	private static long lastAt;
	private static GuiMessage lastShown;
	private static long ggAt;
	private static long lastGgSent;
	private static long lastMacroAt;

	private ChatFx() {
	}

	private static ChatPlusModule plus() {
		ChatPlusModule m = Modules.CHAT_PLUS;
		return m != null && m.isEnabled() ? m : null;
	}

	/** Newest first. */
	public static List<Entry> history() {
		return new ArrayList<>(HISTORY_LINES);
	}

	// ------------------------------------------------------------------------------------------------ incoming

	public static Result process(ChatComponent chat, Component content, GuiMessageTag tag) {
		String plain = content.getString();
		HISTORY_LINES.addFirst(new Entry(LocalTime.now(), plain));
		while (HISTORY_LINES.size() > HISTORY) HISTORY_LINES.removeLast();
		checkAutoGg(plain);

		ChatPlusModule m = plus();
		if (m == null) return new Result(content, tag);
		Component out = content;
		GuiMessageTag outTag = tag;

		if (m.highlight()) {
			List<String> words = new ArrayList<>(m.words());
			Minecraft mc = Minecraft.getInstance();
			String name = mc.getUser() != null ? mc.getUser().getName() : null;
			boolean mine = name != null && plain.startsWith("<" + name + ">");
			if (m.myName() && name != null && name.length() >= 2 && !mine) words.add(name.toLowerCase(Locale.ROOT));
			if (!words.isEmpty() && containsAny(plain.toLowerCase(Locale.ROOT), words)) {
				out = highlight(out, words);
				outTag = new GuiMessageTag(Theme.accent() & 0xFFFFFF, null, Component.literal("Highlighted by Hollow"), "Hollow highlight");
				if (m.sound()) UiSounds.play(UiSounds.NOTIFY, 1.2f, 0.7f);
			}
		}

		if (m.stack()) {
			long now = Util.getMillis();
			if (plain.equals(lastKey) && now - lastAt < STACK_WINDOW_MS && lastShown != null) {
				lastCount++;
				removeLast(chat);
				out = Component.empty().append(out).append(Component.literal(" (x" + lastCount + ")").withStyle(ChatFormatting.GRAY));
			} else {
				lastKey = plain;
				lastCount = 1;
			}
			lastAt = now;
		}

		if (m.timestamps()) {
			String pattern = (m.twentyFour() ? "HH:mm" : "h:mm") + (m.seconds() ? ":ss" : "") + (m.twentyFour() ? "" : " a");
			String stamp = LocalTime.now().format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
			out = Component.literal("[" + stamp + "] ").withStyle(ChatFormatting.DARK_GRAY).append(out);
		}
		return new Result(out, outTag);
	}

	/** Remember which message vanilla just added (for stacking the next one onto it). */
	public static void afterAdd(ChatComponent chat) {
		List<GuiMessage> all = ((ChatComponentAccessor) chat).hollowclient$allMessages();
		lastShown = all.isEmpty() ? null : all.get(0);
	}

	private static void removeLast(ChatComponent chat) {
		ChatComponentAccessor access = (ChatComponentAccessor) chat;
		List<GuiMessage> all = access.hollowclient$allMessages();
		if (!all.isEmpty() && all.get(0) == lastShown) {
			all.remove(0);
			access.hollowclient$refreshTrimmedMessages();
		}
	}

	private static boolean containsAny(String lower, List<String> words) {
		for (String w : words) if (lower.contains(w)) return true;
		return false;
	}

	/** Rebuild the message with every match of the words in the accent color, underlined. Keeps other styling. */
	private static Component highlight(Component content, List<String> words) {
		MutableComponent out = Component.empty();
		int accent = Theme.accent() & 0xFFFFFF;
		content.visit((Style style, String text) -> {
			String lower = text.toLowerCase(Locale.ROOT);
			int i = 0;
			while (i < text.length()) {
				int best = -1;
				int bestLen = 0;
				for (String w : words) {
					int at = lower.indexOf(w, i);
					if (at >= 0 && (best < 0 || at < best)) {
						best = at;
						bestLen = w.length();
					}
				}
				if (best < 0) {
					out.append(Component.literal(text.substring(i)).withStyle(style));
					break;
				}
				if (best > i) out.append(Component.literal(text.substring(i, best)).withStyle(style));
				out.append(Component.literal(text.substring(best, best + bestLen)).withStyle(style.withColor(accent).withUnderlined(true)));
				i = best + bestLen;
			}
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	// ------------------------------------------------------------------------------------------------ outgoing

	private static void checkAutoGg(String plain) {
		AutoGgModule m = Modules.AUTO_GG;
		if (m == null || !m.isEnabled() || m.message().isEmpty()) return;
		long now = Util.getMillis();
		if (ggAt != 0 || now - lastGgSent < m.cooldownMs()) return;
		String lower = plain.toLowerCase(Locale.ROOT);
		// never react to our own gg
		if (lower.contains(m.message().toLowerCase(Locale.ROOT)) && lower.length() < m.message().length() + 40) return;
		for (String phrase : m.phrases()) {
			if (lower.contains(phrase)) {
				ggAt = now + m.delayMs();
				return;
			}
		}
	}

	/** Every client tick: send a pending gg. */
	public static void tick(Minecraft mc) {
		if (ggAt == 0 || Util.getMillis() < ggAt) return;
		ggAt = 0;
		AutoGgModule m = Modules.AUTO_GG;
		if (m == null || !m.isEnabled() || mc.player == null) return;
		lastGgSent = Util.getMillis();
		send(mc, m.message());
	}

	/** A chat macro key was pressed. Returns false when nothing was sent (empty slot or too soon). */
	public static boolean macro(Minecraft mc, int slot) {
		ChatMacrosModule m = Modules.CHAT_MACROS;
		if (m == null || !m.isEnabled() || mc.player == null) return false;
		String text = m.text(slot);
		long now = Util.getMillis();
		if (text.isEmpty()) {
			Toasts.info("Chat macro " + (slot + 1) + " is empty", "Set its text in Hollow menu → Quality of Life → Chat Macros.");
			return false;
		}
		if (now - lastMacroAt < 1000) return false;
		lastMacroAt = now;
		send(mc, text);
		return true;
	}

	/** Send like typing it: a command if it starts with "/", otherwise a chat message. */
	private static void send(Minecraft mc, String text) {
		if (mc.player == null || mc.getConnection() == null) return;
		if (text.startsWith("/")) mc.getConnection().sendCommand(text.substring(1));
		else mc.getConnection().sendChat(text);
	}

	// ------------------------------------------------------------------------------------------------ copy

	/**
	 * The whole message under a point of the open chat (GUI coordinates), or null. Lines of one message are joined.
	 */
	private static final java.util.regex.Pattern OWN_STAMP = java.util.regex.Pattern.compile("^\\[\\d{1,2}:\\d{2}(:\\d{2})?( [AP]M)?] ");
	private static final java.util.regex.Pattern OWN_STACK = java.util.regex.Pattern.compile(" \\(x\\d+\\)$");

	/** A chat line without the timestamp and "(xN)" that Hollow added, for copying. */
	public static String withoutHollowParts(String text) {
		return OWN_STACK.matcher(OWN_STAMP.matcher(text).replaceFirst("")).replaceFirst("");
	}

	public static String messageAt(ChatComponent chat, double mouseX, double mouseY) {
		Minecraft mc = Minecraft.getInstance();
		ChatComponentAccessor access = (ChatComponentAccessor) chat;
		List<GuiMessage.Line> lines = access.hollowclient$trimmedMessages();
		if (lines.isEmpty()) return null;
		double scale = mc.options.chatScale().get();
		if (scale <= 0) return null;
		int bottom = (int) Math.floor((mc.getWindow().getGuiScaledHeight() - 40) / scale);
		int lineH = (int) (9 * (mc.options.chatLineSpacing().get() + 1.0));
		double y = mouseY / scale;
		double x = mouseX / scale - 4;
		if (x < -4 || x > ChatComponent.getWidth(mc.options.chatWidth().get()) / scale + 8) return null;
		int k = (int) Math.floor((bottom - y) / lineH);
		if (k < 0 || k >= chat.getLinesPerPage()) return null;
		int index = k + access.hollowclient$scroll();
		if (index < 0 || index >= lines.size()) return null;
		int low = index;
		while (low > 0 && !lines.get(low).endOfEntry()) low--;
		int high = index;
		while (high + 1 < lines.size() && !lines.get(high + 1).endOfEntry()) high++;
		StringBuilder sb = new StringBuilder();
		for (int i = high; i >= low; i--) {
			if (sb.length() > 0) sb.append(' ');
			lines.get(i).content().accept((pos, style, cp) -> {
				sb.appendCodePoint(cp);
				return true;
			});
		}
		return sb.toString().replaceAll("\\s+", " ").trim();
	}
}
