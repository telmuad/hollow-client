package dev.telmuad.hollowclient.skyblock.features.items;

import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.mc.GameReader;
import dev.telmuad.hollowclient.skyblock.parse.ItemParser;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.WeakHashMap;

/**
 * Parsed SkyBlock items per item stack (stacks in an open menu are drawn every frame; parsing once is enough), plus the
 * NEU lookups the item features share.
 */
public final class ItemCache {
	private static final Map<ItemStack, ItemParser.SbItem> PARSED = Collections.synchronizedMap(new WeakHashMap<>());
	private static Map<String, String> reforgeStones;
	private static com.google.gson.JsonObject essence;

	private ItemCache() {
	}

	public static ItemParser.SbItem of(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		return PARSED.computeIfAbsent(stack, s -> GameReader.raw(-1, s).parse());
	}

	/** Bazaar insta-buy, else lowest BIN: what an upgrade or ingredient costs to get today. */
	public static OptionalDouble buyPrice(String id) {
		var p = SkyBlock.data().prices();
		var b = p.bazaar(id);
		if (b != null && b.buy() > 0) return OptionalDouble.of(b.buy());
		return p.lowestBin(id);
	}

	public static ItemValue.Neu neu() {
		var repo = SkyBlock.data().neu();
		if (!repo.available()) return null;
		if (reforgeStones == null) reforgeStones = ItemData.reforgeStones(repo.constant("reforgestones"));
		if (essence == null) essence = repo.constant("essencecosts");
		return new ItemValue.Neu() {
			@Override
			public String reforgeStone(String reforge) {
				return reforgeStones.get(reforge.toLowerCase(java.util.Locale.ROOT));
			}

			@Override
			public ItemValue.EssenceCost essence(String itemId) {
				return ItemData.essence(essence, itemId);
			}
		};
	}
}
