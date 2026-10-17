package dev.telmuad.hollowclient.skyblock.features.economy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.data.Prices;
import dev.telmuad.hollowclient.skyblock.event.SbEvent;
import dev.telmuad.hollowclient.skyblock.features.items.ItemCache;
import dev.telmuad.hollowclient.skyblock.parse.ItemParser;
import dev.telmuad.hollowclient.skyblock.parse.Patterns;
import dev.telmuad.hollowclient.skyblock.text.SbText;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockHudModule;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockModule;
import dev.telmuad.hollowclient.util.AtomicFiles;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.component.ItemLore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/** The Economy area's modules. */
public final class EconomyModules {
	private EconomyModules() {
	}

	/** Bazaar: your orders (HUD), filled-order alerts, and price history + flip margin in bazaar item tooltips. */
	public static final class BazaarHud extends SkyBlockHudModule {
		private final BooleanSetting tooltip = add(new BooleanSetting("tooltip", "Price history and flip margin in tooltips", true));
		private final Economy.Orders orders = new Economy.Orders();
		private final Economy.History history = new Economy.History();
		private String profileKey = "";
		private boolean historyLoaded;

		public BazaarHud() {
			super("sb_bazaar", "Bazaar Orders", "Your bazaar orders and which are filled; price history and flip margin in bazaar tooltips.",
					"Economy", false, at(Anchor.START, 2, Anchor.START, 130));
		}

		private Path historyFile() {
			return SkyBlock.dir().resolve("cache").resolve("bazaar_history.json");
		}

		@Override
		public void onLoaded() {
			SkyBlock.bus().on(SbEvent.ChatMatched.class, e -> {
				if (!e.match().name().startsWith("bazaar_")) return;
				ensureProfile();
				orders.onChat(e.match(), System.currentTimeMillis());
				saveOrders();
			});
			SkyBlock.data().prices().setBazaarListener(this::onBazaar);
			ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
				if (!live() || !tooltip.on()) return;
				ItemParser.SbItem item = ItemCache.of(stack);
				if (item == null || !item.isSkyBlock()) return;
				Prices.Bazaar b = SkyBlock.data().prices().bazaar(item.priceId());
				if (b == null) return;
				loadHistory();
				String spark = Economy.sparkline(history.prices(item.priceId()), 24);
				double change = history.change24h(item.priceId(), System.currentTimeMillis());
				if (!spark.isEmpty()) {
					lines.add(Component.literal("24h: ").withStyle(ChatFormatting.GOLD).append(Component.literal(spark).withStyle(ChatFormatting.AQUA))
							.append(Double.isNaN(change) ? Component.empty() : Component.literal(String.format(java.util.Locale.ROOT, " %+.1f%%", change * 100))
									.withStyle(change >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED)));
				}
				double flip = Economy.flipProfit(b.buy(), b.sell());
				lines.add(Component.literal("Flip margin: ").withStyle(ChatFormatting.GOLD)
						.append(Component.literal(SbText.shortNumber(flip) + " each after tax").withStyle(flip > 0 ? ChatFormatting.GREEN : ChatFormatting.RED)));
			});
		}

		private void onBazaar(Map<String, Prices.Bazaar> bazaar, long at) {
			loadHistory();
			Map<String, Double> buy = new HashMap<>();
			bazaar.forEach((id, b) -> {
				if (b.buy() > 0) buy.put(id, b.buy());
			});
			if (history.add(at, buy)) {
				try {
					Files.createDirectories(historyFile().getParent());
					AtomicFiles.write(historyFile(), history.toJson().toString());
				} catch (IOException e) {
					HollowClient.LOG.warn("Couldn't save the bazaar history: {}", e.toString());
				}
			}
		}

		private void loadHistory() {
			if (historyLoaded) return;
			historyLoaded = true;
			try {
				if (Files.isRegularFile(historyFile())) {
					history.load(JsonParser.parseString(Files.readString(historyFile(), StandardCharsets.UTF_8)).getAsJsonObject());
				}
			} catch (IOException | RuntimeException e) {
				HollowClient.LOG.warn("Couldn't read the bazaar history: {}", e.toString());
			}
		}

		private void ensureProfile() {
			String key = SkyBlock.state().profileKey();
			if (key.equals(profileKey)) return;
			profileKey = key;
			JsonObject o = SkyBlock.store().read(key, "bazaar_orders");
			orders.load(o.has("orders") && o.get("orders").isJsonArray() ? o.getAsJsonArray("orders") : null);
		}

		private void saveOrders() {
			JsonObject o = new JsonObject();
			o.add("orders", orders.toJson());
			try {
				SkyBlock.store().write(SkyBlock.state().profileKey(), "bazaar_orders", o);
			} catch (IOException e) {
				HollowClient.LOG.warn("Couldn't save bazaar orders: {}", e.toString());
			}
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			List<Economy.Order> list;
			if (SkyBlock.active()) {
				ensureProfile();
				list = orders.list();
			} else {
				list = preview ? List.of(new Economy.Order("Buy Order", "Enchanted Diamond", 64, 9600, false, 0),
						new Economy.Order("Sell Offer", "Enchanted Gold", 160, 45000, true, 0)) : List.of();
			}
			if (list.isEmpty()) return;
			out.add(new Line("Bazaar orders", 0xFFF5C542));
			for (Economy.Order o : list) {
				String text = (o.buy() ? "Buy " : "Sell ") + o.amount() + "x " + o.item() + (o.filled() ? "  ✔ filled" : "");
				out.add(new Line(text, o.filled() ? 0xFF5BD18B : 0));
			}
		}
	}

	/** Auctions: listings tagged against the lowest BIN in the auction browser, and your sold auctions. */
	public static final class AuctionsHud extends SkyBlockHudModule {
		private static AuctionsHud instance;
		private final BooleanSetting tags = add(new BooleanSetting("tags", "Tag listings (cheap / pricey vs lowest BIN)", true));
		private final Economy.Auctions auctions = new Economy.Auctions();

		public AuctionsHud() {
			super("sb_auctions", "Auctions", "Tags auction listings cheap or pricey against the lowest BIN, and lists your sold auctions.",
					"Economy", false, at(Anchor.START, 2, Anchor.START, 185));
			instance = this;
		}

		@Override
		public void onLoaded() {
			SkyBlock.bus().on(SbEvent.ChatMatched.class, e -> auctions.onChat(e.match(), System.currentTimeMillis()));
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			List<Economy.Sale> list = auctions.list();
			if (list.isEmpty() && preview && !SkyBlock.active()) list = List.of(new Economy.Sale("Hyperion", "Player1", 456_000_000, false, 0));
			if (list.isEmpty()) return;
			double unclaimed = list.stream().filter(s -> !s.collected()).mapToDouble(Economy.Sale::coins).sum();
			out.add(new Line("Auctions sold" + (unclaimed > 0 ? " · " + SbText.shortNumber(unclaimed) + " to collect" : ""), 0xFFF5C542));
			int n = 0;
			for (Economy.Sale s : list) {
				if (n++ >= 5) break;
				out.add(new Line(s.item() + "  " + SbText.shortNumber(s.coins()) + (s.collected() ? "" : "  •"), s.collected() ? 0xFF8A8F9C : 0));
			}
		}

		/** Drawn over auction listings in any menu: a green or red corner against the lowest BIN. */
		public static void tag(GuiGraphicsExtractor g, Slot slot) {
			AuctionsHud m = instance;
			if (m == null || !m.isEnabled() || !m.tags.on() || !SkyBlock.active() || !slot.hasItem()) return;
			ItemLore lore = slot.getItem().get(DataComponents.LORE);
			if (lore == null) return;
			double listed = -1;
			Patterns p = Patterns.of("menus");
			for (Component c : lore.lines()) {
				Matcher mm = p.find("ah_bin", SbText.clean(c.getString()));
				if (mm != null) {
					listed = SbText.number(mm.group("coins"));
					break;
				}
			}
			if (listed <= 0) return;
			ItemParser.SbItem item = ItemCache.of(slot.getItem());
			if (item == null || !item.isSkyBlock()) return;
			var lbin = item.pet() != null ? SkyBlock.data().prices().petLowestBin(item.pet().type(), item.pet().tier(), Math.max(1, ItemParser.petLevel(item)))
					: SkyBlock.data().prices().lowestBin(item.id());
			if (lbin.isEmpty()) return;
			String v = Economy.verdict(listed, lbin.getAsDouble());
			int color = v.equals("cheap") ? 0xFF5BD18B : v.equals("pricey") ? 0xFFF2706B : 0;
			if (color == 0) return;
			g.fill(slot.x, slot.y, slot.x + 5, slot.y + 2, color);
			g.fill(slot.x, slot.y, slot.x + 2, slot.y + 5, color);
		}
	}

	/** Coins per hour from your purse, since you joined SkyBlock (or reset it with /hollow sbprofit reset). */
	public static final class ProfitHud extends SkyBlockHudModule {
		private static ProfitHud instance;
		private final Economy.Rate rate = new Economy.Rate();

		public ProfitHud() {
			super("sb_profit", "Coins per Hour", "How fast your purse grows while you play (coins per hour), since you joined SkyBlock.",
					"Economy", false, at(Anchor.END, 2, Anchor.START, 100));
			instance = this;
		}

		@Override
		public void onLoaded() {
			SkyBlock.bus().on(SbEvent.JoinedSkyBlock.class, e -> rate.reset(System.currentTimeMillis(), SkyBlock.state().purse()));
			SkyBlock.bus().on(SbEvent.PurseChanged.class, e -> rate.purse(System.currentTimeMillis(), e.purse(), e.delta()));
		}

		public static void reset() {
			if (instance != null) instance.rate.reset(System.currentTimeMillis(), SkyBlock.state().purse());
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			double ph = rate.perHour(System.currentTimeMillis());
			if (!SkyBlock.active()) {
				if (preview) out.add(new Line("Coins/h  +2.4M", 0xFFF5C542));
				return;
			}
			out.add(new Line("Coins/h  " + (Double.isNaN(ph) ? "…" : (ph >= 0 ? "+" : "") + SbText.shortNumber(ph)), 0xFFF5C542));
			out.add(new Line("Since " + SbText.duration(System.currentTimeMillis() - rate.since()) + ": " + (rate.gained() >= 0 ? "+" : "")
					+ SbText.shortNumber(rate.gained()), 0));
		}
	}
}
