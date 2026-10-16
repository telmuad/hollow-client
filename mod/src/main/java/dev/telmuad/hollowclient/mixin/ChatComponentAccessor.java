package dev.telmuad.hollowclient.mixin;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** Chat+: read the chat's messages (copy, stacking) and refresh the wrapped lines after removing one. */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	@Accessor("allMessages")
	List<GuiMessage> hollowclient$allMessages();

	@Accessor("trimmedMessages")
	List<GuiMessage.Line> hollowclient$trimmedMessages();

	@Accessor("chatScrollbarPos")
	int hollowclient$scroll();

	@Invoker("refreshTrimmedMessages")
	void hollowclient$refreshTrimmedMessages();
}
