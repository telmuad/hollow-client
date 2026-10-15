package dev.telmuad.hollowclient.mixin;

import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TitleScreen.class)
public interface TitleScreenAccessor {
	/** True when the title screen fades in after the game finished loading. */
	@Accessor("fading")
	boolean hollowclient$fading();
}
