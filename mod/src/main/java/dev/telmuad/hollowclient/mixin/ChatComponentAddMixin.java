package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.telmuad.hollowclient.chat.ChatFx;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;

/** Chat+ / Auto GG / SkyBlock Mode: every incoming chat line passes through Hollow before vanilla shows it. */
@Mixin(ChatComponent.class)
abstract class ChatComponentAddMixin {
	@WrapMethod(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V")
	private void hollowclient$process(Component content, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, Operation<Void> original) {
		ChatComponent self = (ChatComponent) (Object) this;
		if (source != GuiMessageSource.SYSTEM_CLIENT) {
			try {
				dev.telmuad.hollowclient.skyblock.SkyBlock.onChat(content);
			} catch (RuntimeException e) {
				dev.telmuad.hollowclient.HollowClient.LOG.error("SkyBlock Mode couldn't read a chat line", e);
			}
		}
		ChatFx.Result result;
		try {
			result = ChatFx.process(self, content, tag);
		} catch (RuntimeException e) {
			// never lose a chat line because of Hollow
			result = new ChatFx.Result(content, tag);
		}
		original.call(result.content(), signature, source, result.tag());
		ChatFx.afterAdd(self);
	}
}
