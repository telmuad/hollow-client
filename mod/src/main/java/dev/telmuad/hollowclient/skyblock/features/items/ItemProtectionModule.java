package dev.telmuad.hollowclient.skyblock.features.items;

import com.google.gson.JsonObject;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.gui.Draw;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.event.SbEvent;
import dev.telmuad.hollowclient.skyblock.parse.ItemParser;
import dev.telmuad.hollowclient.skyblock.parse.Patterns;
import dev.telmuad.hollowclient.skyblock.text.SbText;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;

/**
 * Item protection: lock items (by their SkyBlock uuid, or by inventory slot for items without one) so they can't be
 * dropped, sold, salvaged, auctioned or traded by mistake. Blocking those clicks is the one vanilla action SkyBlock
 * Mode ever stops, and only for items you locked yourself.
 */
public final class ItemProtectionModule extends SkyBlockModule {
	private static ItemProtectionModule instance;
	private final ItemLocks locks = new ItemLocks();
	private String profileKey = "";

	public ItemProtectionModule() {
		super("sb_item_protection", "Item Protection", "Lock items (Controls → Hollow Client → lock key) so they can't be dropped, sold or salvaged by mistake.",
				"Items", true);
		instance = this;
	}

	@Override
	public void onLoaded() {
		SkyBlock.bus().on(SbEvent.ProfileChanged.class, e -> profileKey = "");
	}

	private ItemLocks locks() {
		String key = SkyBlock.state().profileKey();
		if (!key.equals(profileKey)) {
			profileKey = key;
			JsonObject o = SkyBlock.store().read(key, "item_locks");
			locks.load(o);
		}
		return locks;
	}

	private void save() {
		try {
			SkyBlock.store().write(SkyBlock.state().profileKey(), "item_locks", locks.toJson());
		} catch (IOException e) {
			HollowClient.LOG.warn("Couldn't save item locks: {}", e.toString());
		}
	}

	/** The player-inventory index of a slot, or -1 for a menu's own slots. */
	private static int inventoryIndex(Slot slot) {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && slot != null && slot.container == mc.player.getInventory() ? slot.getContainerSlot() : -1;
	}

	private boolean locked(ItemStack stack, int inventoryIndex) {
		if (stack == null || stack.isEmpty()) return false;
		ItemParser.SbItem item = ItemCache.of(stack);
		return locks().locked(item, inventoryIndex);
	}

	/** The lock key on a slot in a menu (or on the held item in game). */
	public static void toggle(ItemStack stack, int inventoryIndex) {
		ItemProtectionModule m = instance;
		Minecraft mc = Minecraft.getInstance();
		if (m == null || !m.live() || stack == null || stack.isEmpty()) return;
		boolean now = m.locks().toggle(ItemCache.of(stack), inventoryIndex);
		m.save();
		SkyBlock.showOverlay(mc, Component.literal(now ? "Locked " : "Unlocked ").withStyle(now ? ChatFormatting.GOLD : ChatFormatting.GRAY)
				.append(stack.getHoverName()));
	}

	public static void toggle(Slot slot) {
		if (slot != null && slot.hasItem()) toggle(slot.getItem(), inventoryIndex(slot));
	}

	/** Pressing the drop key with a locked item in hand. True = don't drop. */
	public static boolean blockDrop() {
		ItemProtectionModule m = instance;
		Minecraft mc = Minecraft.getInstance();
		if (m == null || !m.live() || mc.player == null) return false;
		int sel = mc.player.getInventory().getSelectedSlot();
		if (!m.locked(mc.player.getMainHandItem(), sel)) return false;
		warn(mc);
		return true;
	}

	/** A click in a menu. True = blocked (only for locked items: throwing, dropping outside, or into a selling menu). */
	public static boolean blockClick(AbstractContainerScreen<?> screen, Slot slot, int slotId, ContainerInput input) {
		ItemProtectionModule m = instance;
		Minecraft mc = Minecraft.getInstance();
		if (m == null || !m.live() || mc.player == null) return false;
		ItemStack carried = screen.getMenu().getCarried();
		if (slotId == -999 && m.locked(carried, -1)) return warn(mc);
		if (slot == null || !slot.hasItem()) return false;
		boolean lockedItem = m.locked(slot.getItem(), inventoryIndex(slot));
		if (!lockedItem) return false;
		if (input == ContainerInput.THROW) return warn(mc);
		if (inventoryIndex(slot) >= 0 && protectedMenu(screen)) return warn(mc);
		return false;
	}

	private static boolean protectedMenu(AbstractContainerScreen<?> screen) {
		Patterns p = Patterns.of("menus");
		if (p.matches("protected_title", SbText.clean(screen.getTitle().getString())) != null) return true;
		for (Slot s : screen.getMenu().slots) {
			if (s.hasItem() && s.container != Minecraft.getInstance().player.getInventory()
					&& p.matches("shop_button", SbText.clean(s.getItem().getHoverName().getString())) != null) return true;
		}
		return false;
	}

	private static boolean warn(Minecraft mc) {
		SkyBlock.showOverlay(mc, Component.literal("That item is locked (Hollow item protection)").withStyle(ChatFormatting.RED));
		mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.6f);
		return true;
	}

	/** A small lock in the corner of locked items. */
	public static void lockIcon(GuiGraphicsExtractor g, Slot slot) {
		ItemProtectionModule m = instance;
		if (m == null || !slot.hasItem() || !m.live()) return;
		if (!m.locked(slot.getItem(), inventoryIndex(slot))) return;
		int x = slot.x + 10, y = slot.y + 9;
		Draw.roundRect(g, x, y + 2, 6, 5, 1, 0xFFF5C542);
		Draw.roundOutline(g, x + 1, y, 4, 4, 2, 0xFFF5C542);
	}

	@Override
	public Health health() {
		Health h = super.health();
		if (!h.ok()) return h;
		return Health.ok(locks().size() + " item" + (locks().size() == 1 ? "" : "s") + " locked on this profile");
	}
}
