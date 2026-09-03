package de.minecraft.rival.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.resources.ResourceLocation;

/** One consistent loading/error surface with a single centered project logo. */
public final class RivalScreenStyle {
    private static final ResourceLocation LOGO = new ResourceLocation("minecraft_rival", "icon.png");
    private static final int LOGO_TEXTURE_SIZE = 1254;
    private static Screen animatedScreen;
    private static long animationStarted;

    private RivalScreenStyle() {}

    public static boolean applies(Screen screen) {
        return screen instanceof ConnectScreen || screen instanceof DisconnectedScreen
            || screen instanceof ReceivingLevelScreen || screen instanceof GenericDirtMessageScreen
            || screen instanceof ProgressScreen;
    }

    public static boolean isModernVanillaMenu(Screen screen) {
        return screen instanceof JoinMultiplayerScreen || screen instanceof OptionsScreen;
    }

    public static void renderModernMenuBackground(Screen screen, GuiGraphics graphics) {
        int width = screen.width;
        int height = screen.height;
        graphics.fill(0, 0, width, height, 0xF2070B12);
        graphics.fillGradient(0, 0, width / 2, height, 0xD70B2948, 0xF305090F);
        graphics.fillGradient(width / 2, 0, width, height, 0xF305090F, 0xD7470B16);
        graphics.fillGradient(0, 0, width, Math.max(58, height / 5), 0x4400A8FF, 0x00000000);
        graphics.fill(18, 17, width - 18, 19, 0x8051C7FF);
        graphics.fill(width / 2, 17, width - 18, 19, 0xA0FF4D57);
    }

    public static void renderCornerBranding(Screen screen, GuiGraphics graphics) {
        if (screen == null || screen instanceof RivalTitleScreen
            || screen instanceof ChatScreen || applies(screen)) return;
        if (isModernVanillaMenu(screen)) {
            String section = screen instanceof JoinMultiplayerScreen ? "RIVAL SERVERBROWSER"
                : screen instanceof OptionsScreen ? "RIVAL EINSTELLUNGEN" : "RIVAL MENÜ";
            graphics.fill(8, 6, 8 + screen.getMinecraft().font.width(section) + 10, 19, 0xB20A111C);
            graphics.drawString(screen.getMinecraft().font, section, 13, 8, 0xFFEAF3FF, false);
        }
        int size = 22;
        int x = 8;
        int y = screen.height - size - 8;
        RenderSystem.enableBlend();
        graphics.blit(LOGO, x, y, size, size, 0, 0, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE,
            LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE);
        if (!isModernVanillaMenu(screen)) renderTransition(screen, graphics);
    }

    public static void renderPauseOverlay(Screen screen, GuiGraphics graphics) {
        graphics.fill(0, 0, screen.width, screen.height, 0x30020509);
        graphics.fillGradient(0, 0, screen.width, screen.height, 0x240C4670, 0x281C050A);
        graphics.fill(8, 6, 78, 19, 0x920A111C);
        graphics.drawString(screen.getMinecraft().font, "RIVAL MENÜ", 13, 8, 0xFFEAF3FF, false);
        int size = 22;
        graphics.blit(LOGO, 8, screen.height - size - 8, size, size, 0, 0,
            LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE);
        renderTransition(screen, graphics);
    }

    public static void renderModernTransition(Screen screen, GuiGraphics graphics) {
        renderTransition(screen, graphics);
    }

    private static void renderTransition(Screen screen, GuiGraphics graphics) {
        if (animatedScreen != screen) {
            animatedScreen = screen;
            animationStarted = System.currentTimeMillis();
        }
        float progress = Math.min(1.0f, (System.currentTimeMillis() - animationStarted) / 240.0f);
        if (progress >= 1.0f) return;
        float eased = 1.0f - (1.0f - progress) * (1.0f - progress);
        int alpha = Math.round(190.0f * (1.0f - eased));
        graphics.fill(0, 0, screen.width, screen.height, alpha << 24 | 0x030811);
        int sweep = Math.round((screen.width + 100) * eased) - 100;
        graphics.fill(sweep, 0, Math.min(screen.width, sweep + 54), screen.height, 0x2451C7FF);
    }

    public static void renderBackground(Screen screen, GuiGraphics graphics) {
        int width = screen.width;
        int height = screen.height;
        graphics.fill(0, 0, width, height, 0xFF04070D);
        graphics.fillGradient(0, 0, width / 2, height, 0xF20A2244, 0xFC03070E);
        graphics.fillGradient(width / 2, 0, width, height, 0xFC03070E, 0xF23E0710);
        graphics.fillGradient(0, 0, width, Math.max(60, height / 3), 0x2800A8FF, 0x00000000);

        int logoSize = Math.max(88, Math.min(154, Math.min(width, height) / 3));
        int logoX = (width - logoSize) / 2;
        int logoY = Math.max(14, height / 16);
        RenderSystem.enableBlend();
        graphics.blit(LOGO, logoX, logoY, logoSize, logoSize,
            0, 0, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE);

        boolean error = screen instanceof DisconnectedScreen;
        String status = error ? cleanReason(screen.getNarrationMessage().getString(), screen.getTitle().getString()) : status(screen);
        int color = error ? 0xFFFF5555 : 0xFFE9F3FF;
        int textY = Math.min(height - 70, logoY + logoSize + 14);
        for (var line : screen.getMinecraft().font.split(net.minecraft.network.chat.Component.literal(status), Math.max(180, width - 80))) {
            graphics.drawCenteredString(screen.getMinecraft().font, line, width / 2, textY, color);
            textY += 11;
        }
        if (!error) drawLoadingDots(screen, graphics, textY + 7);
    }

    public static void renderLoadingOverlay(Minecraft minecraft, GuiGraphics graphics) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        graphics.fill(0, 0, width, height, 0xFF04070D);
        graphics.fillGradient(0, 0, width / 2, height, 0xF20A2244, 0xFC03070E);
        graphics.fillGradient(width / 2, 0, width, height, 0xFC03070E, 0xF23E0710);
        int logoSize = Math.max(88, Math.min(154, Math.min(width, height) / 3));
        graphics.blit(LOGO, (width - logoSize) / 2, Math.max(14, height / 16), logoSize, logoSize,
            0, 0, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE);
        int textY = Math.max(14, height / 16) + logoSize + 14;
        graphics.drawCenteredString(minecraft.font, "Minecraft Rival wird geladen ...", width / 2, textY, 0xFFE9F3FF);
        int active = (int) (System.currentTimeMillis() / 280L % 3L);
        for (int index = 0; index < 3; index++) {
            int color = index == active ? 0xFFFF4D57 : index < active ? 0xFF51C7FF : 0xFF526273;
            int x = width / 2 - 15 + index * 12;
            graphics.fill(x, textY + 18, x + 6, textY + 24, color);
        }
    }

    private static String status(Screen screen) {
        if (screen instanceof ConnectScreen) return "Verbindung zum Rival-Server wird hergestellt ...";
        if (screen instanceof ReceivingLevelScreen) return "Projektwelt wird geladen ...";
        if (screen instanceof ProgressScreen) return "Minecraft Rival wird vorbereitet ...";
        return "Daten werden geladen ...";
    }

    static String cleanReason(String narration, String title) {
        String cleaned = narration.strip();
        if (title != null && !title.isBlank() && cleaned.regionMatches(true, 0, title, 0, Math.min(cleaned.length(), title.length())))
            cleaned = cleaned.substring(Math.min(cleaned.length(), title.length())).replaceFirst("^[,.: ]+", "");
        cleaned = cleaned.replaceFirst("(?i)^verbindung unterbrochen[,.: ]*", "")
            .replaceFirst("(?i)^connection lost[,.: ]*", "")
            .replaceFirst("(?i)^disconnected[,.: ]*", "").strip();
        return cleaned.isBlank() ? "Verbindung konnte nicht hergestellt werden." : cleaned;
    }

    private static void drawLoadingDots(Screen screen, GuiGraphics graphics, int y) {
        int active = (int) (System.currentTimeMillis() / 280L % 3L);
        int center = screen.width / 2;
        for (int index = 0; index < 3; index++) {
            int color = index == active ? 0xFFFF4D57 : index < active ? 0xFF51C7FF : 0xFF526273;
            int x = center - 15 + index * 12;
            graphics.fill(x, y, x + 6, y + 6, color);
        }
    }
}
