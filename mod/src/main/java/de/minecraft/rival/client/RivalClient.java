package de.minecraft.rival.client;

import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import de.minecraft.rival.RivalMod;
import de.minecraft.rival.client.mixin.GuiAccessor;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
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
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public final class RivalClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String MOD_ID = RivalMod.MOD_ID;
    private static final byte PROTOCOL = 2;
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
    private static final int[] HEART_TEXTURE_WIDTHS = {0, 28, 48, 46};
    private static final int[] HEART_TEXTURE_HEIGHTS = {0, 24, 25, 35};
    // 40 % der gelieferten Grafiken: Ein sichtbares Herz ist damit etwa so groß
    // wie ein Vanilla-HUD-Herz (9 x 9 Pixel), niemals größer.
    private static final int[] HEART_RENDER_WIDTHS = {0, 11, 19, 18};
    private static final int[] HEART_RENDER_HEIGHTS = {0, 10, 10, 14};
    private static final long AUTHORIZATION_TIMEOUT_MS = 3_000L;
    private static final KeyMapping OPEN_MAP = new KeyMapping(
        "key.minecraft_rival.open_map", InputConstants.Type.KEYSYM, 74, "key.categories.minecraft_rival");
    private static final Set<String> FORBIDDEN_MOD_MARKERS = Set.of(
        "xray", "x-ray", "freecam", "baritone", "wurst", "meteor", "impact", "aristois",
        "liquidbounce", "cheat", "wallhack", "seedcracker", "orefinder", "findercompass");

    private static volatile boolean authorized;
    private static volatile long authorizationDeadline;
    private static volatile int hearts = 3;
    private static volatile int combatSeconds;
    private static volatile boolean nemesisRevealed;
    private static volatile String nemesisName = "";
    private static volatile UUID nemesisId = new UUID(0, 0);
    private static volatile String clanName = "";
    private static volatile long playtimeSeconds;
    private static volatile long playedSeconds;
    private static volatile boolean playtimeEnabled;
    private static boolean disconnecting;
    private static boolean customDebug;
    private static boolean f3Held;
    private static boolean iconInstalled;
    private static boolean registrationPending;
    private static volatile long chatVisibleUntil;

    private final EventNetworkChannel authChannel;

    public static void initialize() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(RivalClient::registerKeys);
        new RivalClient();
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MAP);
    }

    static boolean matchesMapKey(int keyCode, int scanCode) {
        return OPEN_MAP.matches(keyCode, scanCode);
    }

    static String mapKeyLabel() {
        return OPEN_MAP.getTranslatedKeyMessage().getString();
    }

    private RivalClient() {
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
        authorizationDeadline = System.currentTimeMillis() + AUTHORIZATION_TIMEOUT_MS;
        hearts = 3;
        combatSeconds = 0;
        nemesisRevealed = false;
        nemesisName = "";
        nemesisId = new UUID(0, 0);
        clanName = "";
        playtimeSeconds = 0;
        playedSeconds = 0;
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
        Minecraft client = Minecraft.getInstance();
        while (OPEN_MAP.consumeClick()) {
            if (client.level != null && client.player != null && authorized && !(client.screen instanceof RivalMapScreen))
                client.setScreen(new RivalMapScreen());
        }
        enforceConnection(client);
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
    public void onScreenRender(ScreenEvent.Render.Pre event) {
        if (!RivalScreenStyle.applies(event.getScreen())) return;
        RivalScreenStyle.renderBackground(event.getScreen(), event.getGuiGraphics());
        event.getScreen().renderables.stream()
            .filter(net.minecraft.client.gui.components.AbstractButton.class::isInstance)
            .forEach(renderable -> renderable.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick()));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onHudRender(RenderGuiEvent.Post event) {
        renderHud(event.getGuiGraphics());
    }

    /** Uses Forge's stable client event instead of a startup-critical chat mixin. */
    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        noteChatMessage();
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
            long nextPlayed = in.readLong();
            String nextClan = in.readUTF();
            if (nextHearts < 0 || nextHearts > 3 || nextCombat < 0 || nextCombat > 86_400
                || nextName.length() > 64 || nextPlaytime < 0 || nextPlaytime > 31_536_000L
                || nextPlayed < 0 || nextPlayed > 31_536_000L || nextClan.length() > 48) return;
            hearts = nextHearts;
            combatSeconds = nextCombat;
            nemesisRevealed = nextRevealed;
            nemesisId = nextId;
            nemesisName = nextName;
            playtimeEnabled = nextPlaytimeEnabled;
            playtimeSeconds = nextPlaytime;
            playedSeconds = nextPlayed;
            clanName = nextClan;
        } catch (IOException ignored) {
        }
    }

    private static void enforceConnection(Minecraft client) {
        installBranding(client);
        boolean f3Down = InputConstants.isKeyDown(client.getWindow().getWindow(), GLFW.GLFW_KEY_F3);
        if (f3Down && !f3Held) customDebug = !customDebug;
        f3Held = f3Down;
        client.options.renderDebug = false;
        client.options.renderDebugCharts = false;
        client.options.renderFpsChart = false;
        if (client.level == null || client.getConnection() == null || disconnecting) return;
        String forbidden = forbiddenModification();
        if (forbidden != null) {
            deny(client, "Nicht zugelassene Client-Modifikation erkannt: " + forbidden);
            return;
        }
        if (registrationPending) {
            sendChannelRegistration(client);
            registrationPending = false;
        }
        if (client.hasSingleplayerServer()
            || (!authorized && authorizationDeadline > 0 && System.currentTimeMillis() >= authorizationDeadline)) {
            deny(client, DENIED);
        }
    }

    private static void sendChannelRegistration(Minecraft client) {
        byte[] channels = (AUTH_ID + "\0" + STATE_ID + "\0").getBytes(StandardCharsets.UTF_8);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(channels));
        client.getConnection().getConnection().send(new ServerboundCustomPayloadPacket(REGISTER_ID, buffer));
        LOGGER.debug("Rival-Plugin-Kanäle beim Server registriert.");
    }

    private static void installBranding(Minecraft client) {
        client.getWindow().setTitle("Minecraft Rival by pluginsmc.com");
        if (iconInstalled) return;
        try (RivalIconPack icons = new RivalIconPack()) {
            client.getWindow().setIcon(icons, IconSet.RELEASE);
            iconInstalled = true;
        } catch (IOException | RuntimeException ignored) {
            // macOS und einzelne Window-Manager verwalten das Programmsymbol selbst.
        }
    }

    private static void deny(Minecraft client) {
        deny(client, DENIED);
    }

    private static void deny(Minecraft client, String reason) {
        if (disconnecting || client.getConnection() == null) return;
        disconnecting = true;
        client.getConnection().getConnection().disconnect(Component.literal(reason));
    }

    private static String forbiddenModification() {
        for (var mod : ModList.get().getMods()) {
            String identity = (mod.getModId() + " " + mod.getDisplayName()).toLowerCase(Locale.ROOT);
            for (String marker : FORBIDDEN_MOD_MARKERS) if (identity.contains(marker)) return mod.getDisplayName();
        }
        Minecraft client = Minecraft.getInstance();
        if (client.getResourcePackRepository() != null) {
            String packs = client.getResourcePackRepository().getSelectedPacks().stream()
                .map(pack -> pack.getId() + " " + pack.getTitle().getString())
                .collect(Collectors.joining(" ")).toLowerCase(Locale.ROOT);
            for (String marker : FORBIDDEN_MOD_MARKERS) if (packs.contains(marker)) return "X-Ray-Ressourcenpaket";
        }
        return null;
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
        renderLowHealthVignette(graphics, client.player.getHealth() + client.player.getAbsorptionAmount());
        int center = graphics.guiWidth() / 2;
        int heartCount = Math.max(0, Math.min(3, hearts));
        int textureWidth = HEART_TEXTURE_WIDTHS[heartCount];
        int textureHeight = HEART_TEXTURE_HEIGHTS[heartCount];
        int renderWidth = HEART_RENDER_WIDTHS[heartCount];
        int renderHeight = HEART_RENDER_HEIGHTS[heartCount];
        // Die Vanilla-XP-Leiste beginnt ungefähr 32 Pixel über dem unteren Rand.
        // Das komplette Herzbild sitzt mit Abstand oberhalb davon und überdeckt
        // weder XP-Leiste noch Hotbar.
        int xpBarTop = graphics.guiHeight() - 32;
        // Actionbar-Nachrichten liegen im Vanilla-HUD genau in diesem Bereich.
        // Solange eine eingeblendet ist, wandert der gesamte Rival-Block nach
        // oben und kehrt danach automatisch direkt über die XP-Leiste zurück.
        GuiAccessor gui = (GuiAccessor) client.gui;
        boolean actionbarVisible = gui.rival$getOverlayMessageTime() > 0;
        boolean chatVisible = client.screen instanceof net.minecraft.client.gui.screens.ChatScreen
            || System.currentTimeMillis() < chatVisibleUntil;
        int messageOffset = chatVisible ? 34 : actionbarVisible ? 27 : 0;
        int heartY = xpBarTop - renderHeight - 8 - messageOffset;
        if (heartCount > 0) {
            graphics.blit(HEART_TEXTURES[heartCount], center - renderWidth / 2, heartY,
                renderWidth, renderHeight, 0, 0, textureWidth, textureHeight, textureWidth, textureHeight);
        }

        int headSize = 12;
        int headX = center - headSize / 2;
        int headY = heartY - headSize - 4;
        // Kleine, halbtransparente Doppelkante statt des früheren massiven
        // schwarzen 18x18-Felds.
        graphics.fill(headX - 2, headY - 2, headX + headSize + 2, headY + headSize + 2, 0x68101014);
        graphics.fill(headX - 1, headY - 1, headX + headSize + 1, headY + headSize + 1, 0xB0202228);
        PlayerInfo target = nemesisRevealed && client.getConnection() != null
            ? client.getConnection().getOnlinePlayers().stream()
                .filter(info -> info.getProfile().getName().equalsIgnoreCase(nemesisName)).findFirst().orElse(null)
            : null;
        if (target != null) PlayerFaceRenderer.draw(graphics, target.getSkinLocation(), headX, headY, headSize);
        else if (nemesisRevealed && !nemesisId.equals(new UUID(0, 0))) {
            PlayerFaceRenderer.draw(graphics, DefaultPlayerSkin.getDefaultSkin(nemesisId), headX, headY, headSize);
        } else {
            graphics.fill(headX, headY, headX + headSize, headY + headSize, 0xD6050507);
            String marker = nemesisRevealed && !nemesisName.isBlank() ? nemesisName.substring(0, 1).toUpperCase() : "?";
            graphics.drawString(client.font, marker, center - client.font.width(marker) / 2, headY + 2, 0xFFE9EDF3, false);
        }
        if (nemesisRevealed && !nemesisName.isBlank()) {
            graphics.drawString(client.font, nemesisName, center - client.font.width(nemesisName) / 2,
                headY - 9, 0xFF55FFFF, true);
        }
        if (combatSeconds > 0) {
            String combat = "Im Kampf • " + combatSeconds + "s";
            int combatY = headY - (nemesisRevealed && !nemesisName.isBlank() ? 20 : 10);
            graphics.drawString(client.font, combat, center - client.font.width(combat) / 2, combatY, 0xFFFF5555, true);
        }
        if (customDebug) renderProjectDebug(graphics, client);
    }

    public static void renderCredit(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        String credit = "by pluginsmc.com";
        float scale = 0.70f;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 1000.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        int scaledWidth = Math.round(graphics.guiWidth() / scale);
        graphics.drawString(client.font, credit, scaledWidth - client.font.width(credit) - 9,
            9, 0x809AA3AE, false);
        graphics.pose().popPose();
    }

    public static void noteChatMessage() {
        chatVisibleUntil = System.currentTimeMillis() + 10_500L;
    }

    private static void renderLowHealthVignette(GuiGraphics graphics, float effectiveHealth) {
        if (effectiveHealth >= 7.0f) return;
        float danger = 1.0f - Math.max(0.0f, effectiveHealth) / 7.0f;
        int maximumAlpha = Math.round(45.0f + 100.0f * danger);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int edge = Math.max(34, Math.min(width, height) / 5);
        int red = 0x00C01824;
        int strong = maximumAlpha << 24 | red;

        graphics.fillGradient(0, 0, width, edge, strong, red);
        graphics.fillGradient(0, height - edge, width, height, red, strong);

        int steps = 14;
        for (int step = 0; step < steps; step++) {
            float remaining = 1.0f - step / (float) steps;
            int alpha = Math.round(maximumAlpha * remaining * remaining);
            int color = alpha << 24 | red;
            int from = Math.round(step * edge / (float) steps);
            int to = Math.max(from + 1, Math.round((step + 1) * edge / (float) steps));
            graphics.fill(from, 0, to, height, color);
            graphics.fill(width - to, 0, width - from, height, color);
        }
    }

    private static void renderProjectDebug(GuiGraphics graphics, Minecraft client) {
        int x = 5;
        int y = 5;
        String coordinates = String.format(java.util.Locale.ROOT, "XYZ  %.1f  %.1f  %.1f",
            client.player.getX(), client.player.getY(), client.player.getZ());
        String clan = "Clan  " + (clanName.isBlank() ? "–" : clanName);
        String time = "Spielzeit  " + (playtimeEnabled
            ? formatTime(playedSeconds) + " gespielt / " + formatTime(playtimeSeconds) + " übrig"
            : "deaktiviert");
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
