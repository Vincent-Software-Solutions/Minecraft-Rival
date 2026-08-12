package de.minecraft.rival.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Smooth, zoomable project overview opened through the configurable map key. */
public final class RivalMapScreen extends Screen {
    private static final ResourceLocation MAP = new ResourceLocation("minecraft_rival", "textures/gui/project_map.png");
    private static final ResourceLocation LOGO = new ResourceLocation("minecraft_rival", "icon.png");
    private static final int TEXTURE_SIZE = 1254;
    private static final float MIN_ZOOM = 0.55f;
    private static final float MAX_ZOOM = 4.0f;

    private final long openedAt = System.currentTimeMillis();
    private float targetZoom = 1.0f;
    private float zoom = 1.0f;
    private double offsetX;
    private double offsetY;
    private boolean dragging;

    public RivalMapScreen() {
        super(Component.literal("Projektkarte"));
    }

    @Override
    public void tick() {
        zoom += (targetZoom - zoom) * 0.24f;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF4060A10);
        graphics.fillGradient(0, 0, width, height, 0xBE06172B, 0xE0150508);

        float entrance = Math.min(1.0f, (System.currentTimeMillis() - openedAt) / 220.0f);
        entrance = 1.0f - (1.0f - entrance) * (1.0f - entrance);
        float fitted = Math.min((width - 52.0f) / TEXTURE_SIZE, (height - 68.0f) / TEXTURE_SIZE);
        float scale = fitted * zoom * (0.94f + 0.06f * entrance);
        int size = Math.max(1, Math.round(TEXTURE_SIZE * scale));
        int x = Math.round((width - size) / 2.0f + (float) offsetX);
        int y = Math.round((height - size) / 2.0f + 9.0f + (float) offsetY);

        graphics.fill(x - 6, y - 6, x + size + 6, y + size + 6, 0x94000000);
        RenderSystem.enableBlend();
        graphics.blit(MAP, x, y, size, size, 0, 0, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);

        int logoSize = Math.max(32, Math.min(58, width / 12));
        graphics.blit(LOGO, width - logoSize - 15, height - logoSize - 15,
            logoSize, logoSize, 0, 0, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);

        graphics.drawCenteredString(font, "PROJEKTKARTE", width / 2, 10, 0xFFF2F5FA);
        graphics.drawCenteredString(font, "Mausrad: Zoom  •  Ziehen: Verschieben  •  "
                + RivalClient.mapKeyLabel() + "/Esc: Schließen",
            width / 2, height - 15, 0xFF9AA8BA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0) return false;
        float old = targetZoom;
        targetZoom = clamp(targetZoom * (delta > 0 ? 1.18f : 1.0f / 1.18f), MIN_ZOOM, MAX_ZOOM);
        if (old != 0) {
            double factor = targetZoom / old;
            offsetX = (offsetX + width / 2.0 - mouseX) * factor - (width / 2.0 - mouseX);
            offsetY = (offsetY + height / 2.0 - mouseY) * factor - (height / 2.0 - mouseY);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            offsetX += dragX;
            offsetY += dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || RivalClient.matchesMapKey(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_R) {
            targetZoom = zoom = 1.0f;
            offsetX = offsetY = 0;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
