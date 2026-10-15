package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.telmuad.hollowclient.gui.ChatStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Chat Style: the typing bar becomes a rounded glass bar with an accent border. */
@Mixin(ChatScreen.class)
abstract class ChatScreenMixin {
	@WrapOperation(method = "render", at = @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
	private void hollowclient$inputBar(GuiGraphics g, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
		if (!ChatStyle.inputBar()) {
			original.call(g, x0, y0, x1, y1, color);
			return;
		}
		ChatStyle.drawInputBar(g, x0, y0, x1, y1);
	}
}
