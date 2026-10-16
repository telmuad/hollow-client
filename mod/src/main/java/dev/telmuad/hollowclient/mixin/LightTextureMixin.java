package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.telmuad.hollowclient.modules.Modules;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Fullbright: replaces the Brightness value the lightmap reads (the saved option itself is not changed). In 26.x the
 * lightmap values are worked out in {@link LightmapRenderStateExtractor#extract}.
 */
@Mixin(LightmapRenderStateExtractor.class)
abstract class LightTextureMixin {
	@ModifyExpressionValue(method = "extract",
			slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;gamma()Lnet/minecraft/client/OptionInstance;")),
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0))
	private Object hollowclient$fullbright(Object original) {
		if (Modules.FULLBRIGHT == null || !(original instanceof Double gamma)) return original;
		return Modules.FULLBRIGHT.gamma(gamma);
	}
}
