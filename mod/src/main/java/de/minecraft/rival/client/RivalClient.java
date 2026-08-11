package de.minecraft.rival.client;

import com.mojang.blaze3d.platform.IconSet;
import de.minecraft.rival.client.net.AuthPayload;
import de.minecraft.rival.client.net.StatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

public final class RivalClient implements ClientModInitializer {
    private static final byte PROTOCOL = 1;
    private static final String DENIED = "Dieser Server ist nicht zugelassen, benutze den offiziellen Projekt Server.";
    private static final Identifier[] HEART_TEXTURES = {
        null,
        Identifier.fromNamespaceAndPath("minecraft_rival", "textures/gui/hearts_1.png"),
        Identifier.fromNamespaceAndPath("minecraft_rival", "textures/gui/hearts_2.png"),
        Identifier.fromNamespaceAndPath("minecraft_rival", "textures/gui/hearts_3.png")
    };
    private static final int[] HEART_WIDTHS = {0, 28, 48, 46};
    private static final int[] HEART_HEIGHTS = {0, 24, 25, 35};
    private static volatile boolean authorized;
    private static volatile long authorizationDeadline;
    private static volatile int hearts = 3;
    private static volatile int combatSeconds;
    private static volatile boolean nemesisRevealed;
    private static volatile String nemesisName = "";
    private static volatile UUID nemesisId = new UUID(0, 0);
    private static volatile String clanName = "";
    private static volatile long playtimeSeconds;
    private static volatile boolean playtimeEnabled;
    private static boolean disconnecting;
    private static boolean customDebug;
    private static boolean iconInstalled;

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playS2C().register(AuthPayload.ID, AuthPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AuthPayload.ID, AuthPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StatePayload.ID, StatePayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(AuthPayload.ID, (payload, context) ->
            context.client().execute(() -> receiveChallenge(payload.data())));
        ClientPlayNetworking.registerGlobalReceiver(StatePayload.ID, (payload, context) ->
            context.client().execute(() -> receiveState(payload.data())));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            authorized = false;
            disconnecting = false;
            authorizationDeadline = System.currentTimeMillis() + 10_000L;
            hearts = 3;
            combatSeconds = 0;
            nemesisRevealed = false;
            nemesisName = "";
            nemesisId = new UUID(0, 0);
            clanName = "";
            playtimeSeconds = 0;
            playtimeEnabled = false;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            authorized = false;
            authorizationDeadline = 0;
            combatSeconds = 0;
        });
        ClientTickEvents.END_CLIENT_TICK.register(RivalClient::enforceConnection);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("rival", "status"), RivalClient::renderHud);
    }

    private static void receiveChallenge(byte[] raw) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));
            if (in.readUnsignedByte() != PROTOCOL || in.readUnsignedByte() != 1) return;
            int nonceLength = in.readInt();
            if (nonceLength != 32) return;
            byte[] nonce = in.readNBytes(nonceLength);
            int proofLength = in.readInt();
            if (proofLength != 32 || in.available() != proofLength) return;
            byte[] proof = in.readNBytes(proofLength);
            if (!MessageDigest.isEqual(proof, hmac("server", nonce))) {
                deny(Minecraft.getInstance());
                return;
            }
            authorized = true;
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(response);
            out.writeByte(PROTOCOL);
            out.writeByte(2);
            byte[] answer = hmac("client", nonce);
            out.writeInt(answer.length);
            out.write(answer);
            ClientPlayNetworking.send(new AuthPayload(response.toByteArray()));
        } catch (IOException | RuntimeException ignored) {
            deny(Minecraft.getInstance());
        }
    }

    private static void receiveState(byte[] raw) {
        if (!authorized) return;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));
            if (in.readUnsignedByte() != PROTOCOL) return;
            int nextHearts = in.readInt();
            int nextCombat = in.readInt();
            boolean nextRevealed = in.readBoolean();
            UUID nextId = new UUID(in.readLong(), in.readLong());
            String nextName = in.readUTF();
            boolean nextPlaytimeEnabled = in.readBoolean();
            long nextPlaytime = in.readLong();
            String nextClan = in.readUTF();
            if (nextHearts < 0 || nextHearts > 3 || nextCombat < 0 || nextCombat > 86_400
                || nextName.length() > 64 || nextPlaytime < 0 || nextPlaytime > 31_536_000L || nextClan.length() > 48) return;
            hearts = nextHearts;
            combatSeconds = nextCombat;
            nemesisRevealed = nextRevealed;
            nemesisId = nextId;
            nemesisName = nextName;
            playtimeEnabled = nextPlaytimeEnabled;
            playtimeSeconds = nextPlaytime;
            clanName = nextClan;
        } catch (IOException ignored) {
        }
    }

    private static void enforceConnection(Minecraft client) {
        installBranding(client);
        if (client.screen instanceof TitleScreen && !(client.screen instanceof RivalTitleScreen)) {
            client.setScreen(new RivalTitleScreen());
        }
        if (client.debugEntries.isOverlayVisible()) {
            client.debugEntries.setOverlayVisible(false);
            customDebug = !customDebug;
        }
        if (client.level == null || client.getConnection() == null || disconnecting) return;
        if (client.hasSingleplayerServer() || (!authorized && authorizationDeadline > 0 && System.currentTimeMillis() >= authorizationDeadline)) deny(client);
    }

    private static void installBranding(Minecraft client) {
        if (iconInstalled) return;
        try (RivalIconPack icons = new RivalIconPack()) {
            client.getWindow().setIcon(icons, IconSet.RELEASE);
            client.getWindow().setTitle("Minecraft Rival");
            iconInstalled = true;
        } catch (IOException | RuntimeException ignored) {
            // Einige Plattformen (insbesondere macOS) verwalten das Dock-Icon außerhalb von GLFW.
        }
    }

    private static void deny(Minecraft client) {
        if (disconnecting || client.level == null) return;
        disconnecting = true;
        client.disconnectFromWorld(Component.literal(DENIED));
    }

    private static byte[] hmac(String role, byte[] nonce) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(PROTOCOL);
            mac.update(role.getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(nonce);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    // Absichtlich zerlegt; der Release-Build verschleiert zusätzlich Klassen und Member.
    private static String secret() {
        String[] fragments = {"981d4bb69183df44", "v1:cc676936b24d497a"};
        return fragments[1] + fragments[0];
    }

    private static void renderHud(GuiGraphics graphics, net.minecraft.client.DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (!authorized || client.player == null || client.options.hideGui) return;
        int center = graphics.guiWidth() / 2;
        int heartCount = Math.max(0, Math.min(3, hearts));
        int textureWidth = HEART_WIDTHS[heartCount];
        int textureHeight = HEART_HEIGHTS[heartCount];
        int hotbarTop = graphics.guiHeight() - 22;
        int heartY = hotbarTop - 1 - textureHeight;
        if (heartCount > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, HEART_TEXTURES[heartCount], center - textureWidth / 2, heartY,
                0, 0, textureWidth, textureHeight, textureWidth, textureHeight);
        }

        int headX = center - 8;
        int headY = heartY - 20;
        graphics.fill(headX - 1, headY - 1, headX + 17, headY + 17, 0xFF111111);
        PlayerInfo target = nemesisRevealed && client.getConnection() != null
            ? client.getConnection().getOnlinePlayers().stream().filter(info -> info.getProfile().name().equalsIgnoreCase(nemesisName)).findFirst().orElse(null)
            : null;
        if (target != null) PlayerFaceRenderer.draw(graphics, target.getSkin(), headX, headY, 16);
        else if (nemesisRevealed && !nemesisId.equals(new UUID(0, 0))) {
            PlayerFaceRenderer.draw(graphics, DefaultPlayerSkin.get(nemesisId), headX, headY, 16);
        }
        else {
            graphics.fill(headX, headY, headX + 16, headY + 16, 0xFF050505);
            String marker = nemesisRevealed && !nemesisName.isBlank() ? nemesisName.substring(0, 1).toUpperCase() : "?";
            graphics.drawString(client.font, marker, center - client.font.width(marker) / 2, headY + 4, 0xFFFFFFFF, true);
        }
        if (nemesisRevealed && !nemesisName.isBlank()) {
            int width = client.font.width(nemesisName);
            graphics.drawString(client.font, nemesisName, center - width / 2, headY - 10, 0xFF55FFFF, true);
        }
        if (combatSeconds > 0) {
            String combat = "Im Kampf • " + combatSeconds + "s";
            int combatY = headY - (nemesisRevealed && !nemesisName.isBlank() ? 21 : 11);
            graphics.drawString(client.font, combat, center - client.font.width(combat) / 2, combatY, 0xFFFF5555, true);
        }
        if (customDebug) renderProjectDebug(graphics, client);
    }

    private static void renderProjectDebug(GuiGraphics graphics, Minecraft client) {
        int x = 5;
        int y = 5;
        String coordinates = String.format(java.util.Locale.ROOT, "XYZ  %.1f  %.1f  %.1f",
            client.player.getX(), client.player.getY(), client.player.getZ());
        String clan = "Clan  " + (clanName.isBlank() ? "–" : clanName);
        String time = "Spielzeit  " + (playtimeEnabled ? formatTime(playtimeSeconds) : "deaktiviert");
        for (String line : new String[]{coordinates, clan, time}) {
            graphics.fill(x - 2, y - 2, x + client.font.width(line) + 3, y + 10, 0xA6080D14);
            graphics.drawString(client.font, line, x, y, 0xFFE5EDF7, false);
            y += 12;
        }
    }

    private static String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainder = seconds % 60;
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainder);
    }
}
