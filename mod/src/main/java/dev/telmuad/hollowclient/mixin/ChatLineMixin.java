package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.telmuad.hollowclient.gui.ChatStyle;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Chat Style: new lines slide in from the left and fade in. Targets vanilla's per-line text pass (an inner class). */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$1")
abstract class ChatLineMixin {
	@WrapOperation(method = "accept", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z"))
	private boolean hollowclient$slideIn(ChatComponent.ChatGraphicsAccess access, int y, float alpha, FormattedCharSequence text,
										 Operation<Boolean> original, @Local(argsOnly = true) GuiMessage.Line line) {
		float offset = ChatStyle.slideOffset(line);
		if (offset == 0f) return original.call(access, y, alpha, text);
		float fade = ChatStyle.slideFade(line);
		access.updatePose(m -> m.translate(offset, 0f));
		boolean result = original.call(access, y, alpha * fade, text);
		access.updatePose(m -> m.translate(-offset, 0f));
		return result;
	}
}
