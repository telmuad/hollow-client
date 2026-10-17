package dev.telmuad.hollowclient.mixin;

import dev.telmuad.hollowclient.skyblock.features.items.ItemProtectionModule;
import dev.telmuad.hollowclient.skyblock.features.items.MenuToolsModule;
import dev.telmuad.hollowclient.skyblock.features.items.SlotOverlaysModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SkyBlock Mode in menus: rarity backgrounds and labels on slots, the search dimming, the lock icon, and item
 * protection (a click that would throw, drop or sell an item you locked is not sent).
 */
@Mixin(AbstractContainerScreen.class)
abstract class ContainerScreenSkyBlockMixin {
	@Inject(method = "extractSlot", at = @At("HEAD"))
	private void hollowclient$slotBackground(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		try {
			SlotOverlaysModule.background(g, slot);
		} catch (RuntimeException e) {
			dev.telmuad.hollowclient.HollowClient.LOG.warn("Slot background failed", e);
		}
	}

	/** After every slot is drawn, on a layer above the items: search dimming, labels and lock icons. */
	@Inject(method = "extractSlots", at = @At("TAIL"))
	private void hollowclient$slotOverlays(GuiGraphicsExtractor g, int mouseX, int mouseY, CallbackInfo ci) {
		try {
			if (!dev.telmuad.hollowclient.skyblock.SkyBlock.active()) return;
			AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
			g.nextStratum();
			for (Slot slot : self.getMenu().slots) {
				if (!slot.isActive() || !slot.hasItem()) continue;
				MenuToolsModule.dim(self, g, slot);
				SlotOverlaysModule.label(g, slot);
				ItemProtectionModule.lockIcon(g, slot);
				dev.telmuad.hollowclient.skyblock.features.economy.EconomyModules.AuctionsHud.tag(g, slot);
			}
			for (Slot slot : self.getMenu().slots) if (slot.isActive()) dev.telmuad.hollowclient.skyblock.features.items.ExperimentsModule.overlay(self, g, slot);
		} catch (RuntimeException e) {
			dev.telmuad.hollowclient.HollowClient.LOG.warn("Slot overlay failed", e);
		}
	}

	@Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
	private void hollowclient$protect(Slot slot, int slotId, int buttonNum, ContainerInput input, CallbackInfo ci) {
		try {
			dev.telmuad.hollowclient.skyblock.features.items.ExperimentsModule.clicked((AbstractContainerScreen<?>) (Object) this, slot);
			if (ItemProtectionModule.blockClick((AbstractContainerScreen<?>) (Object) this, slot, slotId, input)) ci.cancel();
		} catch (RuntimeException e) {
			dev.telmuad.hollowclient.HollowClient.LOG.warn("Item protection check failed", e);
		}
	}
}
