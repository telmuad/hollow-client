package dev.telmuad.hollowclient.skyblock.features.items;

import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.NumberSetting;
import dev.telmuad.hollowclient.skyblock.parse.ItemParser;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.Slot;

/** In-slot info in menus: a rarity-colored background, and a short label (pet level, stars, book, minion tier). */
public final class SlotOverlaysModule extends SkyBlockModule {
	private static SlotOverlaysModule instance;

	private final BooleanSetting rarity = add(new BooleanSetting("rarity", "Rarity backgrounds", true));
	private final NumberSetting rarityAlpha = add(new NumberSetting("rarityAlpha", "Background strength", 35, 10, 80, 5).format(0, "%"));
	private final BooleanSetting labels = add(new BooleanSetting("labels", "Pet levels, stars, books, minion tiers", true));

	public SlotOverlaysModule() {
		super("sb_slot_overlays", "Slot Info", "Rarity backgrounds and short labels (pet level, stars, enchanted book, minion tier) on items in menus.",
				"Items", true);
		rarityAlpha.visibleWhen(rarity::on);
		instance = this;
	}

	public static int rarityColor(ItemParser.Rarity r) {
		return switch (r) {
			case COMMON -> 0xFFFFFF;
			case UNCOMMON -> 0x55FF55;
			case RARE -> 0x5555FF;
			case EPIC -> 0xAA00AA;
			case LEGENDARY -> 0xFFAA00;
			case MYTHIC -> 0xFF55FF;
			case DIVINE -> 0x55FFFF;
			case SPECIAL, VERY_SPECIAL -> 0xFF5555;
			case ULTIMATE, ADMIN -> 0xAA0000;
			case UNKNOWN -> -1;
		};
	}

	/** Before vanilla draws the slot's item. */
	public static void background(GuiGraphicsExtractor g, Slot slot) {
		SlotOverlaysModule m = instance;
		if (m == null || !m.rarity.on() || !slot.hasItem() || !m.live()) return;
		ItemParser.SbItem item = ItemCache.of(slot.getItem());
		if (item == null || !item.isSkyBlock()) return;
		int rgb = rarityColor(item.rarity());
		if (rgb < 0) return;
		int a = Math.round(m.rarityAlpha.get().floatValue() / 100f * 255f);
		g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, (a << 24) | rgb);
	}

	/** After vanilla drew the slot: the short label in the top-left corner. */
	public static void label(GuiGraphicsExtractor g, Slot slot) {
		SlotOverlaysModule m = instance;
		if (m == null || !m.labels.on() || !slot.hasItem() || !m.live()) return;
		ItemParser.SbItem item = ItemCache.of(slot.getItem());
		if (item == null) return;
		String text = ItemData.slotLabel(item);
		if (text == null) return;
		var font = Minecraft.getInstance().font;
		g.pose().pushMatrix();
		g.pose().translate(slot.x, slot.y);
		g.pose().scale(0.6f, 0.6f);
		g.text(font, text, 0, 0, 0xFFFFE066, true);
		g.pose().popMatrix();
	}
}
