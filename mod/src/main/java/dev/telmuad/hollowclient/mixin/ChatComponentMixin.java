package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.telmuad.hollowclient.gui.ChatStyle;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat Style: vanilla's first pass over the visible lines only paints a black bar behind each one. Hollow runs the
 * same pass but collects the lines instead, then paints one rounded, tinted panel. The text pass is untouched.
 */
@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {
	@WrapOperation(method = "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V",
			at = @At(value = "INVOKE", ordinal = 0,
					target = "Lnet/minecraft/client/gui/components/ChatComponent;forEachLine(Lnet/minecraft/client/gui/components/ChatComponent$AlphaCalculator;Lnet/minecraft/client/gui/components/ChatComponent$LineConsumer;)I"))
	private int hollowclient$background(ChatComponent self, ChatComponent.AlphaCalculator alpha, ChatComponent.LineConsumer painter,
										Operation<Integer> original, @Local(argsOnly = true) ChatComponent.ChatGraphicsAccess access,
										@Local(argsOnly = true, ordinal = 0) int screenHeight) {
		if (!ChatStyle.backgroundOn()) return original.call(self, alpha, painter);
		List<float[]> rows = new ArrayList<>();
		ChatComponent.LineConsumer collect = (line, index, a) -> rows.add(new float[]{index, a});
		int count = original.call(self, alpha, collect);
		ChatStyle.drawBackground(self, access, rows, screenHeight);
		return count;
	}
}
