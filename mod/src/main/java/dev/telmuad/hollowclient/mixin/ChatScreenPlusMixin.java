package dev.telmuad.hollowclient.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.telmuad.hollowclient.chat.ChatFx;
import dev.telmuad.hollowclient.gui.ChatSearchScreen;
import dev.telmuad.hollowclient.gui.Toasts;
import dev.telmuad.hollowclient.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chat+: right-click a message to copy it; Ctrl+F opens the chat search. */
@Mixin(ChatScreen.class)
abstract class ChatScreenPlusMixin {
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void hollowclient$copy(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		var m = Modules.CHAT_PLUS;
		if (m == null || !m.isEnabled() || !m.copy() || event.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;
		Minecraft mc = Minecraft.getInstance();
		String text = ChatFx.messageAt(mc.gui.getChat(), event.x(), event.y());
		if (text == null || text.isEmpty()) return;
		text = ChatFx.withoutHollowParts(text);
		mc.keyboardHandler.setClipboard(text);
		Toasts.success("Copied", text.length() > 60 ? text.substring(0, 57) + "..." : text);
		cir.setReturnValue(true);
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void hollowclient$search(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		var m = Modules.CHAT_PLUS;
		if (m == null || !m.isEnabled() || !m.search()) return;
		Minecraft mc = Minecraft.getInstance();
		boolean ctrl = event.hasControlDown() || InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
				|| InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
		if (event.key() == GLFW.GLFW_KEY_F && ctrl) {
			mc.setScreen(new ChatSearchScreen(mc.screen));
			cir.setReturnValue(true);
		}
	}
}
