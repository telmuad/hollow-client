package dev.telmuad.hollowclient.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.telmuad.hollowclient.gui.Toasts;
import dev.telmuad.hollowclient.gui.UiSounds;
import dev.telmuad.hollowclient.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.io.File;
import java.util.function.Consumer;

/** Screenshots: a shutter sound and a notification when a screenshot is saved (the chat message stays as it is). */
@Mixin(Screenshot.class)
abstract class ScreenshotMixin {
	@ModifyVariable(method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V",
			at = @At("HEAD"), argsOnly = true)
	private static Consumer<Component> hollowclient$notify(Consumer<Component> original, File dir, String name, RenderTarget target, int scale) {
		var m = Modules.SCREENSHOTS;
		if (m == null || !m.isEnabled()) return original;
		if (m.shutter()) UiSounds.play(UiSounds.SHUTTER, 1f, 0.8f);
		return message -> {
			original.accept(message);
			if (!m.notifyOnCapture()) return;
			String file = null;
			if (message.getContents() instanceof TranslatableContents t && t.getKey().equals("screenshot.success")) {
				Object[] args = t.getArgs();
				file = args.length > 0 && args[0] instanceof Component c ? c.getString() : "Saved";
			}
			String saved = file;
			Minecraft.getInstance().execute(() -> {
				if (saved != null) Toasts.success("Screenshot saved", saved);
				else Toasts.error("Screenshot failed", message.getString());
			});
		};
	}
}
