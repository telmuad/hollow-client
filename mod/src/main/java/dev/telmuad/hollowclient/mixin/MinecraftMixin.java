package dev.telmuad.hollowclient.mixin;

import dev.telmuad.hollowclient.gui.HollowTitleScreen;
import dev.telmuad.hollowclient.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Swap vanilla's title screen for Hollow's when the Title Screen module is on. Only the exact vanilla class is
 * swapped (not other mods' subclasses), and never in the demo, whose title screen has different buttons.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen hollowclient$titleScreen(Screen screen) {
		if (screen == null || screen.getClass() != TitleScreen.class) return screen;
		if (Modules.TITLE_SCREEN == null || !Modules.TITLE_SCREEN.isEnabled()) return screen;
		if (((Minecraft) (Object) this).isDemo()) return screen;
		return new HollowTitleScreen(((TitleScreenAccessor) screen).hollowclient$fading());
	}
}
