package de.minecraft.rival.security;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.data.DataStore;
import de.minecraft.rival.data.PlayerRecord;
import de.minecraft.rival.game.CombatManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public final class ModGate implements Listener, PluginMessageListener {
    public static final String AUTH_CHANNEL = "rival:auth";
    public static final String STATE_CHANNEL = "rival:state";
    private static final byte PROTOCOL = 2;
    private final RivalPlugin plugin;
    private final DataStore data;
    private final CombatManager combat;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, Challenge> pending = new HashMap<>();
    private final Set<UUID> verified = new HashSet<>();

    public ModGate(RivalPlugin plugin, DataStore data, CombatManager combat) {
        this.plugin = plugin;
        this.data = data;
        this.combat = combat;
    }

    public void enable() {
        var messenger = plugin.getServer().getMessenger();
        messenger.registerIncomingPluginChannel(plugin, AUTH_CHANNEL, this);
        messenger.registerOutgoingPluginChannel(plugin, AUTH_CHANNEL);
        messenger.registerOutgoingPluginChannel(plugin, STATE_CHANNEL);
        Bukkit.getScheduler().runTaskTimer(plugin, this::broadcastState, 20L, 20L);
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        PlayerRecord record = data.player(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        if (record.eliminated()) {
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, plugin.getConfig().getString("messages.eliminated"));
            return;
        }
        ZoneId zone = ZoneId.of(plugin.getConfig().getString("general.timezone", "Europe/Vienna"));
        if (plugin.getConfig().getBoolean("playtime.enabled") && record.playDate().equals(LocalDate.now(zone))
            && record.playedSeconds() >= plugin.getConfig().getLong("playtime.daily-minutes", 180) * 60L
            && !event.getPlayer().hasPermission("rival.admin")) {
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, plugin.getConfig().getString("messages.playtime-expired"));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Auch im optionalen Modus muss die offizielle Mod den Server authentifizieren können.
        // Der Schalter bestimmt nur, ob Spieler ohne registrierten Mod-Kanal abgewiesen werden.
        waitForClientChannel(event.getPlayer(), 0);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
        verified.remove(event.getPlayer().getUniqueId());
    }

    private void issueChallenge(Player player) {
        if (!player.isOnline()) return;
        byte[] nonce = new byte[32];
        random.nextBytes(nonce);
        long expires = System.currentTimeMillis() + plugin.getConfig().getLong("security.handshake-timeout-seconds", 8) * 1000L;
        pending.put(player.getUniqueId(), new Challenge(nonce, expires));
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(raw);
            out.writeByte(PROTOCOL);
            out.writeByte(1);
            out.writeInt(nonce.length);
            out.write(nonce);
            byte[] proof = hmac("server", nonce);
            out.writeInt(proof.length);
            out.write(proof);
            player.sendPluginMessage(plugin, AUTH_CHANNEL, raw.toByteArray());
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Challenge current = pending.get(player.getUniqueId());
            if (current != null && current.expiresAt <= System.currentTimeMillis() && player.isOnline()) {
                pending.remove(player.getUniqueId());
                if (plugin.getConfig().getBoolean("security.require-client-mod", true))
                    player.kickPlayer(plugin.getConfig().getString("messages.unauthorized-server"));
            }
        }, plugin.getConfig().getLong("security.handshake-timeout-seconds", 8) * 20L + 2L);
    }

    private void waitForClientChannel(Player player, int attempts) {
        if (!player.isOnline()) return;
        if (player.getListeningPluginChannels().contains(AUTH_CHANNEL)) {
            issueChallenge(player);
            return;
        }
        long timeoutSeconds = plugin.getConfig().getLong("security.handshake-timeout-seconds", 8);
        if (attempts * 2L >= timeoutSeconds * 20L) {
            if (plugin.getConfig().getBoolean("security.require-client-mod", true))
                player.kickPlayer(plugin.getConfig().getString("messages.unauthorized-server"));
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> waitForClientChannel(player, attempts + 1), 2L);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!channel.equals(AUTH_CHANNEL)) return;
        Challenge challenge = pending.get(player.getUniqueId());
        if (challenge == null || challenge.expiresAt < System.currentTimeMillis()) return;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            if (in.readUnsignedByte() != PROTOCOL || in.readUnsignedByte() != 2) return;
            int length = in.readInt();
            if (length != 32 || in.available() != length) return;
            byte[] received = in.readNBytes(length);
            byte[] expected = hmac("client", challenge.nonce);
            if (MessageDigest.isEqual(received, expected)) {
                pending.remove(player.getUniqueId());
                verified.add(player.getUniqueId());
                sendState(player);
            }
        } catch (IOException ignored) {
        }
    }

    private byte[] hmac(String role, byte[] nonce) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(plugin.getConfig().getString("security.shared-secret", "").getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(PROTOCOL);
            mac.update(role.getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(nonce);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC nicht verfügbar", ex);
        }
    }

    public void sendState(Player player) {
        if (!verified.contains(player.getUniqueId())) return;
        PlayerRecord record = data.player(player.getUniqueId(), player.getName());
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(raw);
            out.writeByte(PROTOCOL);
            out.writeInt(record.hearts());
            out.writeInt(combat.remainingSeconds(player.getUniqueId()));
            out.writeBoolean(record.nemesisRevealed());
            UUID target = record.nemesis();
            out.writeLong(target == null ? 0L : target.getMostSignificantBits());
            out.writeLong(target == null ? 0L : target.getLeastSignificantBits());
            String name = record.nemesis() == null ? "" : Optional.ofNullable(Bukkit.getOfflinePlayer(record.nemesis()).getName()).orElse("Unbekannt");
            out.writeUTF(name);
            boolean playtimeEnabled = plugin.getConfig().getBoolean("playtime.enabled", true);
            out.writeBoolean(playtimeEnabled);
            out.writeLong(playtimeEnabled ? plugin.playtime().remaining(plugin.playtime().current(player)) : 0L);
            out.writeLong(playtimeEnabled ? plugin.playtime().playedToday(plugin.playtime().current(player)) : 0L);
            var clan = plugin.clans().clan(player);
            out.writeUTF(clan == null ? "" : clan.color() + clan.name() + " &8[" + clan.color() + clan.tag() + "&8]");
            player.sendPluginMessage(plugin, STATE_CHANNEL, raw.toByteArray());
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
    }

    private void broadcastState() {
        for (Player player : Bukkit.getOnlinePlayers()) sendState(player);
    }

    private record Challenge(byte[] nonce, long expiresAt) {}
}
