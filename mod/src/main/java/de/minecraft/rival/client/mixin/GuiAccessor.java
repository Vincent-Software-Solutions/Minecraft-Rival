package de.minecraft.rival.client.mixin;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access used to avoid overlapping the vanilla actionbar. */
@Mixin(Gui.class)
public interface GuiAccessor {
    @Accessor("overlayMessageTime")
    int rival$getOverlayMessageTime();
}
