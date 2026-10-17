package dev.telmuad.hollowclient.skyblock.features.events;

import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.alerts.SkyBlockAlerts;
import dev.telmuad.hollowclient.skyblock.event.SbEvent;
import dev.telmuad.hollowclient.skyblock.mc.LegacyText;
import dev.telmuad.hollowclient.skyblock.state.Island;
import dev.telmuad.hollowclient.skyblock.text.SbText;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockHudModule;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The Events area's modules (Diana, Hoppity). */
public final class EventModules {
	private EventModules() {
	}

	/** Diana: the burrow chain you're on and an alert when an elusive mythological creature's name tag shows near you. */
	public static final class DianaHud extends SkyBlockHudModule {
		private final Set<Integer> seen = new HashSet<>();
		private int burrow = -1, chain = -1;
		private long burrowAt;
		private String lastElusive = "";
		private long elusiveAt;
		private int elusives;

		public DianaHud() {
			super("sb_diana", "Diana", "During Diana's Mythological Ritual: your burrow chain progress and an alert when a Minos Inquisitor, Sphinx, "
					+ "Manticore or King Minos appears near you.", "Events", true, at(Anchor.START, 2, Anchor.START, 150), Island.HUB);
		}

		public int elusives() {
			return elusives;
		}

		public int burrow() {
			return burrow;
		}

		@Override
		public void onLoaded() {
			SkyBlock.bus().on(SbEvent.ChatMatched.class, e -> {
				if (!e.match().name().equals("griffin_burrow")) return;
				burrow = (int) SbText.longOr(e.match().get("n"), -1);
				chain = (int) SbText.longOr(e.match().get("total"), -1);
				burrowAt = System.currentTimeMillis();
			});
		}

		@Override
		public void onTick() {
			Minecraft mc = Minecraft.getInstance();
			if (!live() || mc.player == null || mc.level == null || mc.player.tickCount % 10 != 0) return;
			for (ArmorStand a : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(48))) {
				if (a.getCustomName() == null) continue;
				String name = Events.elusive(LegacyText.of(a.getCustomName()));
				if (name != null && seen.add(a.getId())) {
					elusives++;
					lastElusive = name;
					elusiveAt = System.currentTimeMillis();
					SkyBlockAlerts.notify("diana_elusive", "§6§l" + name, "§7nearby", "LEVEL_UP", true);
				}
			}
			if (seen.size() > 64) seen.clear();
		}

		@Override
		public boolean visibleInGame(Minecraft mc) {
			long now = System.currentTimeMillis();
			return super.visibleInGame(mc) && (now - burrowAt < 600_000 || now - elusiveAt < 600_000);
		}

		@Override
		protected void lines(Minecraft mc, boolean preview, List<Line> out) {
			long now = System.currentTimeMillis();
			if (!SkyBlock.active()) {
				if (!preview) return;
				out.add(new Line("Burrow 3/6", 0xFFF5C542));
				out.add(new Line("Minos Inquisitor 2m ago", 0xFFE08850));
				return;
			}
			if (burrow > 0) out.add(new Line("Burrow " + burrow + (chain > 0 ? "/" + chain : ""), 0xFFF5C542));
			if (!lastElusive.isEmpty()) out.add(new Line(lastElusive + " " + SbText.duration(now - elusiveAt) + " ago", 0xFFE08850));
		}
	}

	/** Chocolate Factory: outlines the employee upgrade that gives the most chocolate per second for its cost. You click. */
	public static final class ChocolateFactoryModule extends SkyBlockModule {
		private static ChocolateFactoryModule instance;
		private AbstractContainerScreen<?> screen;
		private Events.Upgrade best;

		public ChocolateFactoryModule() {
			super("sb_chocolate_factory", "Chocolate Factory Helper", "Outlines the rabbit employee upgrade with the most chocolate per second for its "
					+ "cost. Display only; you click.", "Events", true);
			instance = this;
		}

		public Events.Upgrade best() {
			return best;
		}

		@Override
		public void onTick() {
			Minecraft mc = Minecraft.getInstance();
			if (!live() || mc.player == null || !(mc.gui.screen() instanceof AbstractContainerScreen<?> s)
					|| !Events.chocolateFactory(s.getTitle().getString())) {
				best = null;
				return;
			}
			List<Events.MenuItem> items = new ArrayList<>();
			for (Slot sl : s.getMenu().slots) {
				if (sl.container == mc.player.getInventory() || !sl.hasItem()) continue;
				ItemStack st = sl.getItem();
				List<String> lore = new ArrayList<>();
				ItemLore l = st.get(DataComponents.LORE);
				if (l != null) for (Component c : l.lines()) lore.add(LegacyText.of(c));
				items.add(new Events.MenuItem(sl.index, LegacyText.of(st.getHoverName()), lore));
			}
			screen = s;
			best = Events.bestUpgrade(items);
		}

		public static void overlay(AbstractContainerScreen<?> s, GuiGraphicsExtractor g, Slot slot) {
			ChocolateFactoryModule m = instance;
			if (m == null || m.best == null || s != m.screen || slot.index != m.best.slot()) return;
			int c = 0xFF5BD18B;
			g.fill(slot.x - 1, slot.y - 1, slot.x + 17, slot.y, c);
			g.fill(slot.x - 1, slot.y + 16, slot.x + 17, slot.y + 17, c);
			g.fill(slot.x - 1, slot.y, slot.x, slot.y + 16, c);
			g.fill(slot.x + 16, slot.y, slot.x + 17, slot.y + 16, c);
		}
	}
}
