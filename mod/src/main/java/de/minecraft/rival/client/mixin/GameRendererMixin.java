package de.minecraft.rival.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import de.minecraft.rival.client.RivalClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Zeichnet das Projekt-Credit unmittelbar vor dem finalen GUI-Flush jedes Frames. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Redirect(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V")
    )
    private void rival$renderCredit(GuiGraphics graphics) {
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RivalClient.renderCredit(graphics);
        graphics.flush();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }
}
