package dev.telmuad.hollowclient.mixin;

import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.effects.OwnNametagModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Own Nametag: your own name in the theme gradient (optionally bold, with a star). Only on your screen. */
@Mixin(EntityRenderer.class)
abstract class EntityRendererNameMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("TAIL"))
	private void hollowclient$styleOwnName(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
		OwnNametagModule m = Modules.OWN_NAMETAG;
		if (m == null || !m.isEnabled() || state.nameTag == null || entity != Minecraft.getInstance().player) return;
		String name = state.nameTag.getString();
		MutableComponent out = Component.empty();
		if (m.mark()) out.append(Component.literal("✦ ").withStyle(s -> s.withColor(Theme.accent() & 0xFFFFFF)));
		int n = name.length();
		for (int i = 0; i < n; i++) {
			float t = n <= 1 ? 0f : i / (float) (n - 1);
			int color = m.gradient() ? Theme.gradient(t) & 0xFFFFFF : 0xFFFFFF;
			out.append(Component.literal(String.valueOf(name.charAt(i))).withStyle(s -> s.withColor(color).withBold(m.bold())));
		}
		state.nameTag = out;
	}
}
