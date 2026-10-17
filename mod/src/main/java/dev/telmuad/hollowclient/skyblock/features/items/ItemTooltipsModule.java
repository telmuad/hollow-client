package dev.telmuad.hollowclient.skyblock.features.items;

import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.data.Prices;
import dev.telmuad.hollowclient.skyblock.parse.ItemParser;
import dev.telmuad.hollowclient.skyblock.text.SbText;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockModule;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Price lines under SkyBlock item tooltips: lowest BIN, bazaar buy/sell, NPC price, craft cost, an estimated value of
 * the item with its upgrades (hold Shift for the breakdown), museum status and age.
 */
public final class ItemTooltipsModule extends SkyBlockModule {
	private final BooleanSetting lbin = add(new BooleanSetting("lbin", "Lowest BIN", true));
	private final BooleanSetting bazaar = add(new BooleanSetting("bazaar", "Bazaar buy/sell", true));
	private final BooleanSetting npc = add(new BooleanSetting("npc", "NPC sell price", true));
	private final BooleanSetting craft = add(new BooleanSetting("craft", "Craft cost", true));
	private final BooleanSetting value = add(new BooleanSetting("value", "Estimated value (Shift: breakdown)", true));
	private final BooleanSetting museum = add(new BooleanSetting("museum", "Museum status", true));
	private final BooleanSetting age = add(new BooleanSetting("age", "Item age", false));

	public ItemTooltipsModule() {
		super("sb_tooltips", "Price Tooltips", "Lowest BIN, bazaar, NPC and craft prices and an estimated value under SkyBlock item tooltips.",
				"Items", true);
	}

	@Override
	public void onLoaded() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!live()) return;
			try {
				add(ItemCache.of(stack), stack.getCount(), lines);
			} catch (RuntimeException e) {
				dev.telmuad.hollowclient.HollowClient.LOG.warn("SkyBlock tooltip failed", e);
			}
		});
	}

	private static MutableComponent line(String label, String value) {
		return Component.literal(label + ": ").withStyle(ChatFormatting.GOLD).append(Component.literal(value).withStyle(ChatFormatting.YELLOW));
	}

	private static String coins(double v, int count) {
		return count > 1 ? SbText.shortNumber(v) + " (×" + count + " = " + SbText.shortNumber(v * count) + ")" : SbText.shortNumber(v);
	}

	private void add(ItemParser.SbItem item, int count, List<Component> lines) {
		if (item == null || !item.isSkyBlock()) return;
		Prices p = SkyBlock.data().prices();
		int start = lines.size();
		String id = item.priceId();
		if (bazaar.on()) {
			Prices.Bazaar b = p.bazaar(id);
			if (b != null) lines.add(line("Bazaar", "buy " + coins(b.buy(), count) + " · sell " + coins(b.sell(), count)));
		}
		if (lbin.on()) {
			OptionalDouble l = item.pet() != null ? p.petLowestBin(item.pet().type(), item.pet().tier(), Math.max(1, ItemParser.petLevel(item)))
					: p.lowestBin(item.id());
			if (l.isPresent()) lines.add(line("Lowest BIN", coins(l.getAsDouble(), count)));
		}
		if (npc.on()) {
			OptionalDouble n = p.npc(item.id());
			if (n.isPresent()) lines.add(line("NPC sell", coins(n.getAsDouble(), count)));
		}
		if (craft.on() && SkyBlock.data().neu().available()) {
			OptionalDouble c = ItemData.craftCost(SkyBlock.data().neu().item(item.id()), ItemCache::buyPrice);
			if (c.isPresent()) lines.add(line("Craft cost", coins(c.getAsDouble(), count)));
		}
		if (value.on() && item.pet() == null) {
			double base = p.lowestBin(item.id()).orElse(p.bazaar(id) != null ? p.bazaar(id).sell() : 0);
			ItemValue.Estimate est = ItemValue.estimate(item, base, ItemCache::buyPrice, ItemCache.neu());
			if (est.parts().size() > 1) {
				lines.add(line("Estimated value", SbText.shortNumber(est.total())));
				if (Minecraft.getInstance().hasShiftDown()) {
					for (ItemValue.Part part : est.parts()) {
						lines.add(Component.literal("  " + part.label() + ": ").withStyle(ChatFormatting.GRAY)
								.append(Component.literal(SbText.shortNumber(part.value())).withStyle(ChatFormatting.WHITE)));
					}
				} else {
					lines.add(Component.literal("  Shift for the breakdown").withStyle(ChatFormatting.DARK_GRAY));
				}
			}
		}
		if (museum.on() && item.museum()) lines.add(Component.literal("Donated to the Museum ✔").withStyle(ChatFormatting.GREEN));
		if (age.on() && item.timestamp() > 0) {
			lines.add(Component.literal("Obtained " + SbText.duration(System.currentTimeMillis() - item.timestamp()) + " ago")
					.withStyle(ChatFormatting.DARK_GRAY));
		}
		if (lines.size() > start) lines.add(start, Component.literal("Hollow").withStyle(ChatFormatting.DARK_PURPLE));
	}

	@Override
	public Health health() {
		Health h = super.health();
		if (!h.ok()) return h;
		return SkyBlock.data().prices().loaded() ? Health.ok("Prices loaded") : Health.missing("Prices aren't loaded yet");
	}
}
