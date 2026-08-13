package de.minecraft.rival.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.glfw.GLFW;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.zip.InflaterInputStream;

/** Smooth, zoomable project overview opened through the configurable map key. */
public final class RivalMapScreen extends Screen {
    private static final ResourceLocation FALLBACK_MAP = new ResourceLocation("minecraft_rival", "textures/gui/project_map.png");
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
    private static volatile MapTransfer transfer;
    private static volatile MapData mapData;
    private static DynamicTexture mapTexture;
    private static ResourceLocation mapLocation;

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
        uploadPendingTexture();
        int textureWidth = mapData == null ? TEXTURE_SIZE : mapData.width;
        int textureHeight = mapData == null ? TEXTURE_SIZE : mapData.height;
        float fitted = Math.min((width - 52.0f) / textureWidth, (height - 68.0f) / textureHeight);
        float scale = fitted * zoom * (0.94f + 0.06f * entrance);
        int renderedWidth = Math.max(1, Math.round(textureWidth * scale));
        int renderedHeight = Math.max(1, Math.round(textureHeight * scale));
        int x = Math.round((width - renderedWidth) / 2.0f + (float) offsetX);
        int y = Math.round((height - renderedHeight) / 2.0f + 9.0f + (float) offsetY);

        graphics.fill(x - 6, y - 6, x + renderedWidth + 6, y + renderedHeight + 6, 0x94000000);
        RenderSystem.enableBlend();
        ResourceLocation texture = mapLocation == null ? FALLBACK_MAP : mapLocation;
        graphics.blit(texture, x, y, renderedWidth, renderedHeight, 0, 0, textureWidth, textureHeight, textureWidth, textureHeight);

        renderPlayerMarker(graphics, x, y, renderedWidth, renderedHeight);

        int logoSize = Math.max(32, Math.min(58, width / 12));
        graphics.blit(LOGO, 15, height - logoSize - 15,
            logoSize, logoSize, 0, 0, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);

        graphics.drawCenteredString(font, mapData == null ? "PROJEKTKARTE • WARTE AUF ADMIN-SNAPSHOT" : "PROJEKTKARTE • EINGEFRORENER SERVERSTAND",
            width / 2, 10, 0xFFF2F5FA);
        graphics.drawCenteredString(font, "Mausrad: Zoom  •  Ziehen: Verschieben  •  "
                + RivalClient.mapKeyLabel() + "/Esc: Schließen",
            width / 2, height - 15, 0xFF9AA8BA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPlayerMarker(GuiGraphics graphics, int mapX, int mapY, int mapWidth, int mapHeight) {
        MapData data = mapData;
        Minecraft client = Minecraft.getInstance();
        if (data == null || client.player == null || data.maxX == data.minX || data.maxZ == data.minZ) return;
        double normalizedX = (client.player.getX() - data.minX) / (double) (data.maxX - data.minX);
        double normalizedZ = (client.player.getZ() - data.minZ) / (double) (data.maxZ - data.minZ);
        if (normalizedX < 0.0 || normalizedX > 1.0 || normalizedZ < 0.0 || normalizedZ > 1.0) return;
        int markerX = mapX + (int) Math.round(normalizedX * mapWidth);
        int markerY = mapY + (int) Math.round(normalizedZ * mapHeight);
        graphics.fill(markerX - 4, markerY - 4, markerX + 5, markerY + 5, 0xC0000000);
        graphics.fill(markerX - 2, markerY - 2, markerX + 3, markerY + 3, 0xFFFFFFFF);
        graphics.fill(markerX - 1, markerY - 1, markerX + 2, markerY + 2, 0xFFFF3547);
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

    public static synchronized void receive(byte[] raw) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));
            if (in.readUnsignedByte() != 2) return;
            int type = in.readUnsignedByte();
            long version = in.readLong();
            if (type == 1) {
                int width = in.readInt(), height = in.readInt();
                int minX = in.readInt(), minZ = in.readInt(), maxX = in.readInt(), maxZ = in.readInt();
                int parts = in.readInt(), total = in.readInt();
                int expectedParts = (total + 27_999) / 28_000;
                if (width < 1 || height < 1 || width > 1024 || height > 1024 || parts < 1 || parts > 1024
                    || total < 1 || total > 8_000_000 || parts != expectedParts) return;
                transfer = new MapTransfer(version, width, height, minX, minZ, maxX, maxZ, parts, new byte[total]);
                return;
            }
            if (type != 2 || transfer == null || transfer.version != version) return;
            int index = in.readInt(), length = in.readInt();
            if (index < 0 || index >= transfer.parts || length < 0 || length > 28_000 || in.available() != length) return;
            int offset = index * 28_000;
            int expectedLength = Math.min(28_000, transfer.compressed.length - offset);
            if (length != expectedLength || offset + length > transfer.compressed.length) return;
            in.readFully(transfer.compressed, offset, length);
            if (!transfer.received[index]) { transfer.received[index] = true; transfer.receivedCount++; }
            if (transfer.receivedCount == transfer.parts) decodeTransfer();
        } catch (IOException ignored) { }
    }

    private static void decodeTransfer() throws IOException {
        MapTransfer done = transfer;
        transfer = null;
        int expected = done.width * done.height * 3;
        byte[] rgb;
        try (InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(done.compressed))) {
            rgb = inflater.readNBytes(expected + 1);
        }
        if (rgb.length != expected) return;
        mapData = new MapData(done.version, done.width, done.height, done.minX, done.minZ, done.maxX, done.maxZ, rgb, false);
    }

    private static void uploadPendingTexture() {
        MapData data = mapData;
        if (data == null || data.uploaded) return;
        NativeImage image = new NativeImage(data.width, data.height, false);
        for (int y = 0; y < data.height; y++) for (int x = 0; x < data.width; x++) {
            int offset = (y * data.width + x) * 3;
            int r = data.rgb[offset] & 255, g = data.rgb[offset + 1] & 255, b = data.rgb[offset + 2] & 255;
            image.setPixelRGBA(x, y, 0xFF000000 | b << 16 | g << 8 | r);
        }
        if (mapTexture != null) mapTexture.close();
        mapTexture = new DynamicTexture(image);
        mapLocation = Minecraft.getInstance().getTextureManager().register("rival_world_map", mapTexture);
        mapData = new MapData(data.version, data.width, data.height, data.minX, data.minZ, data.maxX, data.maxZ, data.rgb, true);
    }

    private static final class MapTransfer {
        final long version; final int width, height, minX, minZ, maxX, maxZ, parts; final byte[] compressed; final boolean[] received;
        int receivedCount;
        MapTransfer(long version, int width, int height, int minX, int minZ, int maxX, int maxZ, int parts, byte[] compressed) {
            this.version = version; this.width = width; this.height = height; this.minX = minX; this.minZ = minZ;
            this.maxX = maxX; this.maxZ = maxZ; this.parts = parts; this.compressed = compressed; this.received = new boolean[parts];
        }
    }
    private record MapData(long version, int width, int height, int minX, int minZ, int maxX, int maxZ, byte[] rgb, boolean uploaded) {}
}
