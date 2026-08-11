package de.minecraft.rival.client;

import com.mojang.blaze3d.platform.IconSet;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.slf4j.Logger;

@Mod(RivalClient.MOD_ID)
public final class RivalClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String MOD_ID = "minecraft_rival";
    private static final byte PROTOCOL = 1;
    private static final String DENIED = "Dieser Server ist nicht zugelassen, benutze den offiziellen Projekt Server.";
    private static final ResourceLocation AUTH_ID = new ResourceLocation("rival", "auth");
    private static final ResourceLocation STATE_ID = new ResourceLocation("rival", "state");
    private static final ResourceLocation REGISTER_ID = new ResourceLocation("minecraft", "register");
    private static final ResourceLocation[] HEART_TEXTURES = {
        null,
        new ResourceLocation(MOD_ID, "textures/gui/hearts_1.png"),
        new ResourceLocation(MOD_ID, "textures/gui/hearts_2.png"),
        new ResourceLocation(MOD_ID, "textures/gui/hearts_3.png")
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
    private static boolean registrationPending;

    private final EventNetworkChannel authChannel;

    public RivalClient() {
        authChannel = NetworkRegistry.newEventChannel(
            AUTH_ID, () -> "1", NetworkRegistry.acceptMissingOr("1"), NetworkRegistry.acceptMissingOr("1"));
        EventNetworkChannel stateChannel = NetworkRegistry.newEventChannel(
            STATE_ID, () -> "1", NetworkRegistry.acceptMissingOr("1"), NetworkRegistry.acceptMissingOr("1"));
        authChannel.<NetworkEvent.ServerCustomPayloadEvent>addListener(this::onAuthPayload);
        stateChannel.<NetworkEvent.ServerCustomPayloadEvent>addListener(this::onStatePayload);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void onAuthPayload(NetworkEvent.ServerCustomPayloadEvent event) {
        byte[] raw = copyPayload(event.getPayload());
        LOGGER.debug("Rival-Authentifizierungsanfrage empfangen ({} Bytes).", raw.length);
        event.getSource().get().enqueueWork(() -> receiveChallenge(raw));
        event.getSource().get().setPacketHandled(true);
    }

    private void onStatePayload(NetworkEvent.ServerCustomPayloadEvent event) {
        byte[] raw = copyPayload(event.getPayload());
        LOGGER.debug("Rival-Spielstatus empfangen ({} Bytes).", raw.length);
        event.getSource().get().enqueueWork(() -> receiveState(raw));
        event.getSource().get().setPacketHandled(true);
    }

    private static byte[] copyPayload(FriendlyByteBuf payload) {
        byte[] raw = new byte[payload.readableBytes()];
        payload.getBytes(payload.readerIndex(), raw);
        return raw;
    }

    @SubscribeEvent
    public void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        LOGGER.debug("Rival-Clientverbindung wird initialisiert.");
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
        registrationPending = true;
    }

    @SubscribeEvent
    public void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        authorized = false;
        authorizationDeadline = 0;
        combatSeconds = 0;
        registrationPending = false;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        enforceConnection(Minecraft.getInstance());
    }

    @SubscribeEvent
    public void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof AccessibilityOnboardingScreen) {
            Minecraft.getInstance().options.onboardAccessibility = true;
            Minecraft.getInstance().options.save();
            event.setNewScreen(new RivalTitleScreen());
        } else if (event.getNewScreen() instanceof TitleScreen) {
            event.setNewScreen(new RivalTitleScreen());
        }
    }

    @SubscribeEvent
    public void onHudRender(RenderGuiEvent.Post event) {
        renderHud(event.getGuiGraphics());
    }

    private void receiveChallenge(byte[] raw) {
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

            ByteArrayOutputStream response = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(response);
            out.writeByte(PROTOCOL);
            out.writeByte(2);
            byte[] answer = hmac("client", nonce);
            out.writeInt(answer.length);
            out.write(answer);
            Minecraft client = Minecraft.getInstance();
            if (client.getConnection() == null) return;
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(response.toByteArray()));
            client.getConnection().getConnection().send(new ServerboundCustomPayloadPacket(AUTH_ID, buffer));
            authorized = true;
            LOGGER.info("Rival-Server erfolgreich authentifiziert.");
        } catch (IOException | RuntimeException ignored) {
            LOGGER.warn("Rival-Authentifizierungsanfrage war ungültig.");
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
        if (client.options.renderDebug) {
            client.options.renderDebug = false;
            client.options.renderDebugCharts = false;
            client.options.renderFpsChart = false;
            customDebug = !customDebug;
        }
        if (client.level == null || client.getConnection() == null || disconnecting) return;
        if (registrationPending) {
            sendChannelRegistration(client);
            registrationPending = false;
        }
        if (client.hasSingleplayerServer()
            || (!authorized && authorizationDeadline > 0 && System.currentTimeMillis() >= authorizationDeadline)) {
            deny(client);
        }
    }

    private static void sendChannelRegistration(Minecraft client) {
        byte[] channels = (AUTH_ID + "\0" + STATE_ID + "\0").getBytes(StandardCharsets.UTF_8);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(channels));
        client.getConnection().getConnection().send(new ServerboundCustomPayloadPacket(REGISTER_ID, buffer));
        LOGGER.debug("Rival-Plugin-Kanäle beim Server registriert.");
    }

    private static void installBranding(Minecraft client) {
        if (iconInstalled) return;
        try (RivalIconPack icons = new RivalIconPack()) {
            client.getWindow().setIcon(icons, IconSet.RELEASE);
            client.getWindow().setTitle("Minecraft Rival");
            iconInstalled = true;
        } catch (IOException | RuntimeException ignored) {
            // macOS und einzelne Window-Manager verwalten das Programmsymbol selbst.
        }
    }

    private static void deny(Minecraft client) {
        if (disconnecting || client.getConnection() == null) return;
        disconnecting = true;
        client.getConnection().getConnection().disconnect(Component.literal(DENIED));
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

    private static String secret() {
        String[] fragments = {"981d4bb69183df44", "v1:cc676936b24d497a"};
        return fragments[1] + fragments[0];
    }

    private static void renderHud(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (!authorized || client.player == null || client.options.hideGui) return;
        int center = graphics.guiWidth() / 2;
        int heartCount = Math.max(0, Math.min(3, hearts));
        int textureWidth = HEART_WIDTHS[heartCount];
        int textureHeight = HEART_HEIGHTS[heartCount];
        int hotbarTop = graphics.guiHeight() - 22;
        int heartY = hotbarTop - textureHeight;
        if (heartCount > 0) {
            graphics.blit(HEART_TEXTURES[heartCount], center - textureWidth / 2, heartY,
                0, 0, textureWidth, textureHeight, textureWidth, textureHeight);
        }

        int headX = center - 8;
        int headY = heartY - 20;
        graphics.fill(headX - 1, headY - 1, headX + 17, headY + 17, 0xFF111111);
        PlayerInfo target = nemesisRevealed && client.getConnection() != null
            ? client.getConnection().getOnlinePlayers().stream()
                .filter(info -> info.getProfile().getName().equalsIgnoreCase(nemesisName)).findFirst().orElse(null)
            : null;
        if (target != null) PlayerFaceRenderer.draw(graphics, target.getSkinLocation(), headX, headY, 16);
        else if (nemesisRevealed && !nemesisId.equals(new UUID(0, 0))) {
            PlayerFaceRenderer.draw(graphics, DefaultPlayerSkin.getDefaultSkin(nemesisId), headX, headY, 16);
        } else {
            graphics.fill(headX, headY, headX + 16, headY + 16, 0xFF050505);
            String marker = nemesisRevealed && !nemesisName.isBlank() ? nemesisName.substring(0, 1).toUpperCase() : "?";
            graphics.drawString(client.font, marker, center - client.font.width(marker) / 2, headY + 4, 0xFFFFFFFF, true);
        }
        if (nemesisRevealed && !nemesisName.isBlank()) {
            graphics.drawString(client.font, nemesisName, center - client.font.width(nemesisName) / 2,
                headY - 10, 0xFF55FFFF, true);
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
