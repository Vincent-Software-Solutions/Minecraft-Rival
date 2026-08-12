package de.minecraft.rival.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import de.minecraft.rival.client.RivalClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Zeichnet das Projekt-Credit unmittelbar vor dem finalen GUI-Flush jedes Frames. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final private Minecraft minecraft;

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

    @Redirect(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Overlay;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V")
    )
    private void rival$renderLoadingOverlay(net.minecraft.client.gui.screens.Overlay overlay,
                                            GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // LoadingOverlay.render() also advances and completes the reload. Always invoke it,
        // then paint the Rival surface opaquely over the vanilla artwork.
        overlay.render(graphics, mouseX, mouseY, partialTick);
        if (overlay instanceof net.minecraft.client.gui.screens.LoadingOverlay)
            de.minecraft.rival.client.RivalScreenStyle.renderLoadingOverlay(minecraft, graphics);
    }
}
