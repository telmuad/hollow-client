package dev.telmuad.hollowclient.modules.hud;

import dev.telmuad.hollowclient.hud.HudModule;
import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.EnumSetting;
import dev.telmuad.hollowclient.setting.Labeled;
import dev.telmuad.hollowclient.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Armor pieces and the held item, each with its durability. */
public final class ArmorHud extends HudModule {
	public enum Mode implements Labeled {
		NUMBER("Number"), PERCENT("Percent"), BOTH("Number and percent"), NONE("Icons only");
		private final String label;

		Mode(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private static final int ROW = 17;

	private final EnumSetting<Mode> mode = add(new EnumSetting<>("mode", "Durability as", Mode.NUMBER));
	private final BooleanSetting heldItem = add(new BooleanSetting("heldItem", "Show held item", true));
	private final BooleanSetting offhand = add(new BooleanSetting("offhand", "Show offhand item", true));
	private final BooleanSetting colorByDamage = add(new BooleanSetting("colorByDamage", "Color by durability", true));

	private record Entry(ItemStack stack, String text, int color) {
	}

	private final List<Entry> entries = new ArrayList<>();
	private int textWidth;

	public ArmorHud() {
		super("armor", "Armor & Held Item", "Your armor and held item with their durability.", true,
				at(Anchor.END, 2, Anchor.END, 2));
	}

	@Override
	public Size measure(Minecraft mc, Font font, boolean preview) {
		entries.clear();
		Player p = mc.player;
		List<ItemStack> stacks = new ArrayList<>();
		if (p == null) {
			stacks.add(sample(Items.DIAMOND_HELMET, 0.1f));
			stacks.add(sample(Items.DIAMOND_CHESTPLATE, 0.35f));
			stacks.add(sample(Items.DIAMOND_LEGGINGS, 0.6f));
			stacks.add(sample(Items.DIAMOND_BOOTS, 0.85f));
			if (heldItem.on()) stacks.add(sample(Items.DIAMOND_SWORD, 0.2f));
		} else {
			for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				stacks.add(p.getItemBySlot(slot));
			}
			if (heldItem.on()) stacks.add(p.getMainHandItem());
			if (offhand.on()) stacks.add(p.getOffhandItem());
		}
		textWidth = 0;
		for (ItemStack s : stacks) {
			if (s.isEmpty()) continue;
			String text = describe(s);
			int color = textColor.argb();
			if (s.isDamageableItem() && colorByDamage.on()) {
				float left = 1f - s.getDamageValue() / (float) Math.max(1, s.getMaxDamage());
				color = ColorUtil.withAlpha(ColorUtil.fromHsv(left / 3f, 0.7f, 1f), ColorUtil.alpha(textColor.argb()));
			}
			entries.add(new Entry(s, text, color));
			textWidth = Math.max(textWidth, width(font, text));
		}
		if (entries.isEmpty() && preview) {
			entries.add(new Entry(sample(Items.DIAMOND_CHESTPLATE, 0.3f), "No armor", textColor.argb()));
			textWidth = width(font, "No armor");
		}
		if (entries.isEmpty()) return new Size(0, 0);
		int w = 2 * PAD + 16 + (textWidth > 0 ? 3 + textWidth : 0);
		return new Size(w, 2 * PAD + entries.size() * ROW - 1);
	}

	private String describe(ItemStack s) {
		if (s.isDamageableItem()) {
			int left = s.getMaxDamage() - s.getDamageValue();
			int pct = Math.round(100f * left / Math.max(1, s.getMaxDamage()));
			return switch (mode.get()) {
				case NUMBER -> Integer.toString(left);
				case PERCENT -> pct + "%";
				case BOTH -> left + " (" + pct + "%)";
				case NONE -> "";
			};
		}
		return s.getCount() > 1 && mode.get() != Mode.NONE ? Integer.toString(s.getCount()) : "";
	}

	private static ItemStack sample(net.minecraft.world.item.Item item, float damage) {
		ItemStack s = new ItemStack(item);
		if (s.isDamageableItem()) s.setDamageValue(Math.round(s.getMaxDamage() * damage));
		return s;
	}

	@Override
	public void draw(GuiGraphics g, Minecraft mc, Font font, boolean preview) {
		if (entries.isEmpty()) return;
		int w = 2 * PAD + 16 + (textWidth > 0 ? 3 + textWidth : 0);
		drawBackground(g, w, 2 * PAD + entries.size() * ROW - 1);
		boolean right = alignRight();
		int y = PAD;
		for (Entry e : entries) {
			int iconX = right ? w - PAD - 16 : PAD;
			g.renderItem(e.stack, iconX, y);
			if (!e.text.isEmpty()) {
				int tx = right ? iconX - 3 - width(font, e.text) : PAD + 16 + 3;
				text(g, font, e.text, tx, y + 4, e.color);
			}
			y += ROW;
		}
	}
}
