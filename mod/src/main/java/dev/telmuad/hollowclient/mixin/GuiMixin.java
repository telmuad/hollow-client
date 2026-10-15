package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.telmuad.hollowclient.hud.HotbarFx;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Animated hotbar: a gliding, glowing selection frame and a popping selected item. Vanilla still draws everything. */
@Mixin(Gui.class)
abstract class GuiMixin {
	/** The second sprite vanilla draws in the hotbar is the selection frame. */
	@WrapOperation(method = "renderItemHotbar", at = @At(value = "INVOKE", ordinal = 1,
			target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private void hollowclient$selection(GuiGraphics g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h,
										Operation<Void> original) {
		if (HotbarFx.amount() <= 0f) {
			original.call(g, pipeline, sprite, x, y, w, h);
			return;
		}
		int slot = Math.max(0, Math.round((x - (g.guiWidth() / 2f - 92)) / 20f));
		float ax = HotbarFx.selectionX(x, slot);
		HotbarFx.glow(g, ax, y, w, h);
		g.pose().pushMatrix();
		g.pose().translate(ax - x, 0f);
		original.call(g, pipeline, sprite, x, y, w, h);
		g.pose().popMatrix();
	}

	@WrapOperation(method = "renderItemHotbar", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/Gui;renderSlot(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/client/DeltaTracker;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V"))
	private void hollowclient$slot(Gui gui, GuiGraphics g, int x, int y, DeltaTracker delta, Player player, ItemStack stack, int seed,
								   Operation<Void> original) {
		// seeds 1..9 are the hotbar slots in order; 10 is the offhand
		float s = seed <= 9 ? HotbarFx.itemScale(seed - 1, player.getInventory().getSelectedSlot()) : 1f;
		if (s == 1f) {
			original.call(gui, g, x, y, delta, player, stack, seed);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x + 8f, y + 8f);
		g.pose().scale(s, s);
		g.pose().translate(-(x + 8f), -(y + 8f));
		original.call(gui, g, x, y, delta, player, stack, seed);
		g.pose().popMatrix();
	}
}
