package dev.telmuad.hollowclient.skyblock.features.items;

import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.StringSetting;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.text.SbText;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockModule;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Two menu helpers: a search box under menus that dims every item whose name and lore don't match, and quick-nav
 * buttons above your inventory (each button sends one command you chose, like /pets or /storage, when you click it).
 */
public final class MenuToolsModule extends SkyBlockModule {
	private static MenuToolsModule instance;
	private static final Map<AbstractContainerScreen<?>, EditBox> SEARCH = new WeakHashMap<>();

	private final BooleanSetting search = add(new BooleanSetting("search", "Search box under menus", true));
	private final BooleanSetting nav = add(new BooleanSetting("nav", "Quick-nav buttons above your inventory", true));
	private final StringSetting commands = add(new StringSetting("commands", "Buttons (commands)", "pets, storage, wardrobe, ah, bz, craft", 120));

	public MenuToolsModule() {
		super("sb_menu_tools", "Menu Tools", "A search box that highlights matching items in menus, and quick-nav buttons (one click = one command).",
				"Items", true);
		commands.describe("Commands for the buttons, separated by commas, without the slash.");
		instance = this;
	}

	/** Adds the search box and buttons when a menu opens (SkyBlockUi). */
	public static void onScreen(AbstractContainerScreen<?> screen) {
		MenuToolsModule m = instance;
		Minecraft mc = Minecraft.getInstance();
		if (m == null || !m.live() || mc.player == null) return;
		var widgets = Screens.getWidgets(screen);
		if (m.search.on()) {
			int w = Math.min(160, screen.imageWidth);
			EditBox box = new EditBox(mc.font, screen.leftPos + (screen.imageWidth - w) / 2, screen.topPos + screen.imageHeight + 4, w, 14,
					Component.literal("Search"));
			box.setHint(Component.literal("Search items").withStyle(ChatFormatting.DARK_GRAY));
			box.setMaxLength(40);
			widgets.add(box);
			SEARCH.put(screen, box);
		}
		if (m.nav.on() && screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
			String[] cmds = m.commands.get().split(",");
			int x = screen.leftPos;
			for (String raw : cmds) {
				String cmd = raw.strip().replaceFirst("^/", "");
				if (cmd.isEmpty() || !cmd.matches("[A-Za-z0-9_ ]{1,32}")) continue;
				int bw = Math.max(30, mc.font.width(cmd) + 10);
				widgets.add(Button.builder(Component.literal(cmd), b -> {
					if (mc.player != null && SkyBlock.active()) mc.player.connection.sendCommand(cmd);
				}).bounds(x, screen.topPos - 22, bw, 18).build());
				x += bw + 2;
				if (x > screen.leftPos + screen.imageWidth + 120) break;
			}
		}
	}

	/** After a slot is drawn: dims items that don't match the search. */
	public static void dim(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g, Slot slot) {
		EditBox box = SEARCH.get(screen);
		if (box == null || box.getValue().isBlank() || !slot.hasItem()) return;
		if (!matches(slot.getItem(), box.getValue())) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xC0101015);
	}

	static boolean matches(ItemStack stack, String query) {
		String q = query.strip().toLowerCase(Locale.ROOT);
		if (SbText.clean(stack.getHoverName().getString()).toLowerCase(Locale.ROOT).contains(q)) return true;
		ItemLore lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
		if (lore != null) for (Component c : lore.lines()) if (SbText.clean(c.getString()).toLowerCase(Locale.ROOT).contains(q)) return true;
		var item = ItemCache.of(stack);
		return item != null && item.id().toLowerCase(Locale.ROOT).contains(q.replace(' ', '_'));
	}
}
