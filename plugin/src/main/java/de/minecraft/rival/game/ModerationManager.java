package de.minecraft.rival.game;

import de.minecraft.rival.RivalPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;

public final class ModerationManager implements Listener {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
    private final RivalPlugin plugin;
    private final File file;
    private final Map<UUID, BanRecord> bans = new HashMap<>();
    private final Map<UUID, List<WarningRecord>> warnings = new HashMap<>();

    public ModerationManager(RivalPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "moderation.yml");
        load();
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection banSection = yaml.getConfigurationSection("bans");
        if (banSection != null) for (String key : banSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String path = "bans." + key + ".";
                bans.put(uuid, new BanRecord(uuid, yaml.getString(path + "name", "Unbekannt"),
                    yaml.getString(path + "reason", "Kein Grund angegeben"), yaml.getString(path + "actor", "Konsole"),
                    yaml.getLong(path + "created-at"), yaml.getLong(path + "expires-at")));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ungültiger Bann-Eintrag für " + key);
            }
        }
        ConfigurationSection warningSection = yaml.getConfigurationSection("warnings");
        if (warningSection != null) for (String key : warningSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                List<WarningRecord> records = new ArrayList<>();
                for (Map<?, ?> raw : yaml.getMapList("warnings." + key + ".entries")) {
                    Object reason = raw.containsKey("reason") ? raw.get("reason") : "Kein Grund";
                    Object actor = raw.containsKey("actor") ? raw.get("actor") : "Konsole";
                    records.add(new WarningRecord(number(raw.get("at")), String.valueOf(reason), String.valueOf(actor)));
                }
                if (!records.isEmpty()) warnings.put(uuid, records);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ungültiger Verwarnungs-Eintrag für " + key);
            }
        }
        purgeExpired();
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    public BanRecord ban(OfflinePlayer target, String reason, long durationMillis, String actor) {
        if (durationMillis < 0) throw new IllegalArgumentException("Die Dauer darf nicht negativ sein.");
        long now = System.currentTimeMillis();
        long expires = durationMillis == 0 ? 0 : Math.addExact(now, durationMillis);
        String name = Optional.ofNullable(target.getName()).orElse(target.getUniqueId().toString());
        BanRecord record = new BanRecord(target.getUniqueId(), name, cleanReason(reason), actor, now, expires);
        bans.put(target.getUniqueId(), record);
        save();
        Player online = target.getPlayer();
        if (online != null) online.kickPlayer(kickMessage(record));
        return record;
    }

    public boolean unban(UUID uuid) {
        if (bans.remove(uuid) == null) return false;
        save();
        return true;
    }

    public WarnResult warn(OfflinePlayer target, String reason, String actor) {
        List<WarningRecord> entries = warnings.computeIfAbsent(target.getUniqueId(), ignored -> new ArrayList<>());
        WarningRecord warning = new WarningRecord(System.currentTimeMillis(), cleanReason(reason), actor);
        entries.add(warning);
        int threshold = Math.max(1, plugin.getConfig().getInt("moderation.warns-before-ban", 3));
        boolean autoBanned = entries.size() % threshold == 0;
        save();
        Player online = target.getPlayer();
        if (online != null) online.sendMessage(de.minecraft.rival.util.Messages.value("Du wurdest verwarnt: ", warning.reason(), ""));
        if (autoBanned) {
            long days = Math.max(1, plugin.getConfig().getLong("moderation.auto-ban-days", 5));
            ban(target, "Automatischer Bann nach " + entries.size() + " Verwarnungen • Letzter Grund: " + warning.reason(),
                Math.multiplyExact(days, 86_400_000L), "System");
        }
        return new WarnResult(entries.size(), autoBanned);
    }

    public int warningCount(UUID uuid) {
        return warnings.getOrDefault(uuid, List.of()).size();
    }

    public List<WarningRecord> warnings(UUID uuid) {
        return List.copyOf(warnings.getOrDefault(uuid, List.of()));
    }

    public int clearWarnings(UUID uuid) {
        List<WarningRecord> removed = warnings.remove(uuid);
        if (removed == null) return 0;
        save();
        return removed.size();
    }

    public UUID findUuidByName(String name) {
        return bans.values().stream().filter(record -> record.name().equalsIgnoreCase(name)).map(BanRecord::uuid).findFirst()
            .or(() -> warnings.keySet().stream().filter(uuid -> {
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                return player.getName() != null && player.getName().equalsIgnoreCase(name);
            }).findFirst()).orElse(null);
    }

    public BanRecord activeBan(UUID uuid) {
        BanRecord record = bans.get(uuid);
        if (record != null && record.expired()) {
            bans.remove(uuid);
            save();
            return null;
        }
        return record;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(PlayerLoginEvent event) {
        BanRecord record = activeBan(event.getPlayer().getUniqueId());
        if (record != null) event.disallow(PlayerLoginEvent.Result.KICK_BANNED, kickMessage(record));
    }

    private String kickMessage(BanRecord record) {
        String message = ChatColor.RED + (record.permanent() ? "Du wurdest permanent gebannt." : "Du wurdest temporär gebannt.")
            + "\n" + ChatColor.GRAY + "Grund: " + record.reason();
        if (!record.permanent()) message += "\n" + ChatColor.GOLD
            + "Gebannt bis: " + DATE.format(Instant.ofEpochMilli(record.expiresAt()));
        return message;
    }

    private static String cleanReason(String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Ein Grund ist erforderlich.");
        return reason.strip();
    }

    private void purgeExpired() {
        if (bans.values().removeIf(BanRecord::expired)) save();
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        bans.forEach((uuid, record) -> {
            String path = "bans." + uuid + ".";
            yaml.set(path + "name", record.name());
            yaml.set(path + "reason", record.reason());
            yaml.set(path + "actor", record.actor());
            yaml.set(path + "created-at", record.createdAt());
            yaml.set(path + "expires-at", record.expiresAt());
        });
        warnings.forEach((uuid, entries) -> yaml.set("warnings." + uuid + ".entries", entries.stream().map(record -> Map.of(
            "at", record.at(), "reason", record.reason(), "actor", record.actor())).toList()));
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "moderation.yml konnte nicht gespeichert werden", ex);
        }
    }

    public record BanRecord(UUID uuid, String name, String reason, String actor, long createdAt, long expiresAt) {
        public boolean permanent() { return expiresAt == 0; }
        public boolean expired() { return expiresAt > 0 && expiresAt <= System.currentTimeMillis(); }
        public String expiryText() { return permanent() ? "permanent" : DATE.format(Instant.ofEpochMilli(expiresAt)); }
    }
    public record WarningRecord(long at, String reason, String actor) {}
    public record WarnResult(int count, boolean autoBanned) {}
}
