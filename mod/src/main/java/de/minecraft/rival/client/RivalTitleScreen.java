package de.minecraft.rival.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Reduzierter Projekt-Startbildschirm ohne Singleplayer- oder Realms-Zugriff. */
public final class RivalTitleScreen extends Screen {
    private static final ResourceLocation BACKGROUND = new ResourceLocation(
        "minecraft_rival", "textures/gui/title_background.png");
    private static final int BACKGROUND_WIDTH = 1672;
    private static final int BACKGROUND_HEIGHT = 941;
    private static final int BUTTON_WIDTH = 216;
    private static final int BUTTON_HEIGHT = 26;
    private static final int BUTTON_GAP = 7;

    public RivalTitleScreen() {
        super(Component.literal("Minecraft Rival"));
    }

    @Override
    protected void init() {
        int x = (width - BUTTON_WIDTH) / 2;
        int stackHeight = BUTTON_HEIGHT * 3 + BUTTON_GAP * 2;
        int preferredY = Math.round(height * 0.62f);
        int y = Math.min(preferredY, height - stackHeight - 23);
        y = Math.max(y, Math.round(height * 0.48f));

        addRenderableWidget(new RivalButton(x, y, Component.translatable("menu.multiplayer"), 0xFF4BB8FF,
            () -> minecraft.setScreen(new JoinMultiplayerScreen(this))));
        addRenderableWidget(new RivalButton(x, y + BUTTON_HEIGHT + BUTTON_GAP,
            Component.translatable("menu.options"), 0xFFE9B955,
            () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))));
        addRenderableWidget(new RivalButton(x, y + (BUTTON_HEIGHT + BUTTON_GAP) * 2,
            Component.translatable("menu.quit"), 0xFFFF5757, minecraft::stop));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderFittedBackground(graphics);

        int stackHeight = BUTTON_HEIGHT * 3 + BUTTON_GAP * 2;
        int preferredY = Math.round(height * 0.62f);
        int panelY = Math.min(preferredY, height - stackHeight - 23);
        panelY = Math.max(panelY, Math.round(height * 0.48f));
        fillRounded(graphics, (width - BUTTON_WIDTH) / 2 - 10, panelY - 20,
            (width + BUTTON_WIDTH) / 2 + 10, panelY + stackHeight + 10, 7, 0xA50A101B);
        graphics.drawCenteredString(font, "OFFIZIELLER PROJEKT-CLIENT", width / 2, panelY - 13, 0xFFBAC8D8);

        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "Minecraft Rival  •  pluginsmc.com", width / 2, height - 12, 0xFF8B96A5);
    }

    private void renderFittedBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0xFF02060D);
        float scale = Math.min(width / (float) BACKGROUND_WIDTH, height / (float) BACKGROUND_HEIGHT);
        int renderedWidth = Math.round(BACKGROUND_WIDTH * scale);
        int renderedHeight = Math.round(BACKGROUND_HEIGHT * scale);
        float x = (width - renderedWidth) / 2.0f;
        float y = (height - renderedHeight) / 2.0f;

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.blit(BACKGROUND, 0, 0, 0, 0,
            BACKGROUND_WIDTH, BACKGROUND_HEIGHT, BACKGROUND_WIDTH, BACKGROUND_HEIGHT);
        graphics.pose().popPose();

        graphics.fill(0, 0, width, height, 0x16000000);
        graphics.fillGradient(0, height / 2, width, height, 0x00000000, 0x88000000);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void fillRounded(GuiGraphics graphics, int left, int top, int right, int bottom, int radius, int color) {
        graphics.fill(left + radius, top, right - radius, bottom, color);
        graphics.fill(left, top + radius, right, bottom - radius, color);
        graphics.fill(left + 2, top + 2, right - 2, bottom - 2, color);
    }

    private static final class RivalButton extends AbstractButton {
        private final int accent;
        private final Runnable action;

        private RivalButton(int x, int y, Component label, int accent, Runnable action) {
            super(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, label);
            this.accent = accent;
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean highlighted = isHoveredOrFocused();
            int background = highlighted ? 0xE5263446 : 0xD9141D2A;
            int border = highlighted ? accent : 0x80566475;
            int right = getX() + getWidth();
            int bottom = getY() + getHeight();
            fillRounded(graphics, getX(), getY(), right, bottom, 5, 0x74000000);
            fillRounded(graphics, getX() + 1, getY(), right - 1, bottom - 2, 5, background);
            graphics.fill(getX() + 5, getY(), right - 5, getY() + 1, border);
            graphics.fill(getX() + 1, getY() + 5, getX() + 3, bottom - 6, accent);
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2,
                highlighted ? 0xFFFFFFFF : 0xFFE2E8F0);
        }
    }
}
