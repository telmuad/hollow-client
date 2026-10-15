package dev.telmuad.hollowclient.mixin;

import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.render.CosmeticsLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tags the local player's render state with the cosmetics to draw (every other player gets nothing), and makes sure
 * the cape layer shows Hollow's cape even if the skin's cape part is switched off.
 */
@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void hollowclient$tagLocalPlayer(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		if (avatar != Minecraft.getInstance().player || Modules.HAT == null) {
			state.setData(CosmeticsLayer.LOOK, null);
			state.setData(CosmeticsLayer.CAPE_FX, null);
			state.setData(dev.telmuad.hollowclient.styles.Emotes.KEY, null);
			return;
		}
		boolean hat = Modules.HAT.isEnabled();
		boolean elytra = state.chestEquipment.is(Items.ELYTRA);
		boolean wings = Modules.WINGS.isEnabled() && !(elytra && Modules.WINGS.hideWithElytra());
		var halo = Modules.HALO;
		var horns = Modules.HORNS;
		var pet = Modules.PET;
		CosmeticsLayer.Look look = new CosmeticsLayer.Look(
				hat ? Modules.HAT.hat() : null, Modules.HAT.tint(),
				wings ? Modules.WINGS.wings() : null, Modules.WINGS.tint(), Modules.WINGS.flapSpeed(),
				halo.isEnabled() ? halo.style() : null, halo.color(), halo.glow(), halo.spin(),
				horns.isEnabled() ? horns.style() : null, horns.tint(),
				pet.isEnabled() ? pet.style() : null, pet.tint());
		state.setData(CosmeticsLayer.LOOK, look.any() ? look : null);
		state.setData(CosmeticsLayer.CAPE_FX, new CosmeticsLayer.CapeFx(Modules.CAPE.glow(), Modules.CAPE.shimmer()));
		dev.telmuad.hollowclient.styles.Emotes.apply(state);
		if (Modules.CAPE.texture() != null) state.showCape = true;
	}
}
