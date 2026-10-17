package dev.telmuad.hollowclient.skyblock.mc;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Locale;
import java.util.Optional;

/**
 * Text components to and from the old "§" format. SkyBlock Mode works on strings (so parsers, the debug recorder and
 * the simulator don't need the game), and Hypixel still styles everything with the 16 legacy colors, so nothing that
 * matters is lost. Hex colors (rare on Hypixel) are dropped.
 */
public final class LegacyText {
	private LegacyText() {
	}

	public static String of(Component c) {
		if (c == null) return "";
		StringBuilder sb = new StringBuilder();
		String[] last = {""};
		c.visit((style, text) -> {
			if (text.isEmpty()) return Optional.empty();
			String codes = codes(style);
			if (!codes.equals(last[0])) {
				sb.append(codes);
				last[0] = codes;
			}
			sb.append(text);
			return Optional.empty();
		}, Style.EMPTY);
		return sb.toString();
	}

	private static String codes(Style s) {
		StringBuilder b = new StringBuilder();
		TextColor color = s.getColor();
		ChatFormatting f = color == null ? null : legacy(color);
		b.append(f != null ? f.toString() : "§r");
		if (s.isObfuscated()) b.append("§k");
		if (s.isBold()) b.append("§l");
		if (s.isStrikethrough()) b.append("§m");
		if (s.isUnderlined()) b.append("§n");
		if (s.isItalic()) b.append("§o");
		return b.toString();
	}

	private static ChatFormatting legacy(TextColor color) {
		String name = color.serialize();
		if (name.startsWith("#")) return null;
		try {
			return ChatFormatting.valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** "§6§lLEGENDARY" -> a component with those styles (italic off, as item names would otherwise be italic). */
	public static MutableComponent parse(String legacy) {
		MutableComponent out = Component.empty();
		Style base = Style.EMPTY.withItalic(false);
		Style style = base;
		StringBuilder run = new StringBuilder();
		for (int i = 0; i < legacy.length(); i++) {
			char ch = legacy.charAt(i);
			if (ch == '§' && i + 1 < legacy.length()) {
				ChatFormatting f = ChatFormatting.getByCode(legacy.charAt(i + 1));
				if (f != null) {
					if (!run.isEmpty()) {
						out.append(Component.literal(run.toString()).withStyle(style));
						run.setLength(0);
					}
					style = f == ChatFormatting.RESET ? base : style.applyLegacyFormat(f);
					i++;
					continue;
				}
			}
			run.append(ch);
		}
		if (!run.isEmpty()) out.append(Component.literal(run.toString()).withStyle(style));
		return out;
	}
}
