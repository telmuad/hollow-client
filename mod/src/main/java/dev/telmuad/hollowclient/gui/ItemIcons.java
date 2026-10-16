package dev.telmuad.hollowclient.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Item icons that also work on the title screen. Since 26.1 an {@link ItemStack} can't be made before a world has
 * loaded (the item components aren't bound yet), so before that the item's texture file is drawn instead.
 */
public final class ItemIcons {
	/** An icon: the item, plus the vanilla texture (textures/item/NAME.png) used before a world is loaded. */
	public record Icon(Item item, String texture) {
	}

	private ItemIcons() {
	}

	/** Whether item stacks can be made right now (a world has been loaded). */
	public static boolean stacksAvailable(Item item) {
		return item.builtInRegistryHolder().areComponentsBound();
	}

	/** A stack of the item, or null before a world has loaded. */
	public static ItemStack stackOrNull(Item item) {
		return stacksAvailable(item) ? new ItemStack(item) : null;
	}

	/** Draw a 16x16 icon at (x, y). */
	public static void draw(GuiGraphicsExtractor g, Icon icon, int x, int y) {
		ItemStack stack = stackOrNull(icon.item());
		if (stack != null) {
			g.item(stack, x, y);
			return;
		}
		Identifier texture = Identifier.withDefaultNamespace("textures/item/" + icon.texture() + ".png");
		g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, 16, 16, 16, 16);
	}
}
