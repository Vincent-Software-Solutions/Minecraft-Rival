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
    private static final int BUTTON_WIDTH = 244;
    private static final int BUTTON_HEIGHT = 30;
    private static final int BUTTON_GAP = 8;
    private static final int PANEL_PADDING = 13;

    public RivalTitleScreen() {
        super(Component.literal("Minecraft Rival by pluginsmc.com"));
    }

    @Override
    protected void init() {
        int x = (width - BUTTON_WIDTH) / 2;
        int stackHeight = BUTTON_HEIGHT * 3 + BUTTON_GAP * 2;
        int preferredY = Math.round(height * 0.59f);
        int y = Math.min(preferredY, height - stackHeight - 30);
        y = Math.max(y, Math.round(height * 0.46f));

        addRenderableWidget(new RivalButton(x, y, "01", Component.translatable("menu.multiplayer"), 0xFF51C7FF,
            () -> minecraft.setScreen(new JoinMultiplayerScreen(this))));
        addRenderableWidget(new RivalButton(x, y + BUTTON_HEIGHT + BUTTON_GAP,
            "02", Component.translatable("menu.options"), 0xFFFFC857,
            () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))));
        addRenderableWidget(new RivalButton(x, y + (BUTTON_HEIGHT + BUTTON_GAP) * 2,
            "03", Component.translatable("menu.quit"), 0xFFFF626E, minecraft::stop));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderFittedBackground(graphics);

        int stackHeight = BUTTON_HEIGHT * 3 + BUTTON_GAP * 2;
        int preferredY = Math.round(height * 0.59f);
        int panelY = Math.min(preferredY, height - stackHeight - 23);
        panelY = Math.max(panelY, Math.round(height * 0.46f));
        int panelLeft = (width - BUTTON_WIDTH) / 2 - PANEL_PADDING;
        int panelRight = (width + BUTTON_WIDTH) / 2 + PANEL_PADDING;
        int panelTop = panelY - 39;
        int panelBottom = panelY + stackHeight + 14;

        fillRounded(graphics, panelLeft + 4, panelTop + 5, panelRight + 4, panelBottom + 5, 10, 0x78000000);
        fillRounded(graphics, panelLeft, panelTop, panelRight, panelBottom, 10, 0xD00A111D);
        fillRounded(graphics, panelLeft + 1, panelTop + 1, panelRight - 1, panelBottom - 1, 9, 0xB5162130);
        graphics.fill(panelLeft + 14, panelTop, width / 2, panelTop + 2, 0xFF3DBDFF);
        graphics.fill(width / 2, panelTop, panelRight - 14, panelTop + 2, 0xFFFF4D57);
        graphics.drawCenteredString(font, "RIVAL PROJECT CLIENT", width / 2, panelTop + 10, 0xFFF4F7FB);

        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "Minecraft Rival 1.0 • by pluginsmc.com",
            width / 2, height - 13, 0xFF9AA8BA);
    }

    private void renderFittedBackground(GuiGraphics graphics) {
        float scale = Math.max(width / (float) BACKGROUND_WIDTH, height / (float) BACKGROUND_HEIGHT);
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

        graphics.fill(0, 0, width, height, 0x10000000);
        graphics.fillGradient(0, height / 3, width, height, 0x00000000, 0x9A02050A);
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
        private final String index;
        private final Runnable action;

        private RivalButton(int x, int y, String index, Component label, int accent, Runnable action) {
            super(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, label);
            this.index = index;
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
            int background = highlighted ? 0xF02A384B : 0xE0182331;
            int border = highlighted ? accent : 0x80566778;
            int right = getX() + getWidth();
            int bottom = getY() + getHeight();
            fillRounded(graphics, getX() + 2, getY() + 3, right + 2, bottom + 3, 7, 0x66000000);
            fillRounded(graphics, getX(), getY(), right, bottom, 7, border);
            fillRounded(graphics, getX() + 1, getY() + 1, right - 1, bottom - 1, 6, background);
            graphics.fill(getX() + 1, getY() + 7, getX() + 4, bottom - 7, accent);
            graphics.drawString(Minecraft.getInstance().font, index, getX() + 13,
                getY() + (getHeight() - 8) / 2, highlighted ? accent : 0xFF718096, false);
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2,
                highlighted ? 0xFFFFFFFF : 0xFFE2E8F0);
        }
    }
}
