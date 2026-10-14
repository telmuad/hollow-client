package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.telmuad.hollowclient.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Capes: only the local player's skin gets Hollow's cape (and, if chosen, the matching elytra texture). */
@Mixin(AbstractClientPlayer.class)
abstract class AbstractClientPlayerMixin {
	/** Plain vanilla elytra, so "cape on elytra" off really shows the normal elytra (vanilla would fall back to the cape). */
	@Unique
	private static final ClientAsset.Texture HOLLOWCLIENT$VANILLA_ELYTRA =
			new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace("entity/equipment/wings/elytra"));
	@Unique
	private PlayerSkin hollowclient$lastIn;
	@Unique
	private Identifier hollowclient$lastCape;
	@Unique
	private boolean hollowclient$lastElytra;
	@Unique
	private PlayerSkin hollowclient$lastOut;

	@ModifyReturnValue(method = "getSkin", at = @At("RETURN"))
	private PlayerSkin hollowclient$cape(PlayerSkin skin) {
		if ((Object) this != Minecraft.getInstance().player || Modules.CAPE == null) return skin;
		Identifier cape = Modules.CAPE.texture();
		if (cape == null) return skin;
		boolean elytra = Modules.CAPE.onElytra();
		if (skin != hollowclient$lastIn || !cape.equals(hollowclient$lastCape) || elytra != hollowclient$lastElytra) {
			ClientAsset.Texture texture = new ClientAsset.ResourceTexture(cape, cape);
			hollowclient$lastOut = new PlayerSkin(skin.body(), texture, elytra ? texture : (skin.elytra() != null ? skin.elytra() : HOLLOWCLIENT$VANILLA_ELYTRA), skin.model(), skin.secure());
			hollowclient$lastIn = skin;
			hollowclient$lastCape = cape;
			hollowclient$lastElytra = elytra;
		}
		return hollowclient$lastOut;
	}
}
