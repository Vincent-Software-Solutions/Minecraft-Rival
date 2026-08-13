package de.minecraft.rival.game;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.data.DataStore;
import de.minecraft.rival.data.PlayerRecord;
import de.minecraft.rival.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public final class PlaytimeManager implements Listener {
    private static final long[] WARNINGS = {1800, 900, 300, 180, 60, 30, 10, 5, 4, 3, 2, 1};
    private final RivalPlugin plugin;
    private final DataStore data;
    private final VanishManager vanish;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Long> previousRemaining = new HashMap<>();
    private final Map<UUID, Long> adminActivationGrace = new HashMap<>();
    private final Map<UUID, Long> accountingMillis = new HashMap<>();
    private int secondsSinceSave;

    public PlaytimeManager(RivalPlugin plugin, DataStore data, VanishManager vanish) {
        this.plugin = plugin;
        this.data = data;
        this.vanish = vanish;
    }

    public void enable() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerRecord record = current(event.getPlayer());
        long remaining = remaining(record);
        previousRemaining.put(event.getPlayer().getUniqueId(), remaining);
        accountingMillis.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        if (remaining <= 0 && event.getPlayer().hasPermission("rival.admin")) {
            adminActivationGrace.put(event.getPlayer().getUniqueId(), System.currentTimeMillis() + 30_000L);
            Messages.normal(event.getPlayer(), "Deine Spielzeit ist abgelaufen. Aktiviere innerhalb von 30 Sekunden /admin mode.");
        }
        for (long warning : WARNINGS) if (remaining == warning) warn(event.getPlayer(), warning);
        refreshVisibility(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hideBar(event.getPlayer());
        previousRemaining.remove(event.getPlayer().getUniqueId());
        adminActivationGrace.remove(event.getPlayer().getUniqueId());
        accountingMillis.remove(event.getPlayer().getUniqueId());
        data.save();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        secondsSinceSave++;
        if (!plugin.getConfig().getBoolean("playtime.enabled", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                accountingMillis.put(player.getUniqueId(), now);
                updateBar(player);
                plugin.modGate().sendState(player);
            }
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            long anchor = accountingMillis.getOrDefault(id, now);
            if (!isTracking(player) || adminActivationGrace.getOrDefault(id, 0L) > now) {
                accountingMillis.put(id, now);
                previousRemaining.put(id, remaining(current(player)));
                updateBar(player);
                plugin.modGate().sendState(player);
                continue;
            }
            PlayerRecord record = current(player);
            long elapsed = Math.max(0L, (now - anchor) / 1000L);
            if (elapsed == 0L) continue;
            accountingMillis.put(id, anchor + elapsed * 1000L);
            long before = previousRemaining.getOrDefault(id, remaining(record));
            record.playedSeconds(record.playedSeconds() + elapsed);
            long after = remaining(record);
            previousRemaining.put(id, after);
            for (long warning : WARNINGS) if (de.minecraft.rival.util.RivalRules.warningCrossed(before, after, warning)) warn(player, warning);
            updateBar(player);
            plugin.modGate().sendState(player);
            if (after <= 0) {
                data.save();
                player.kickPlayer(plugin.getConfig().getString("messages.playtime-expired"));
            }
        }
        if (secondsSinceSave >= 60) {
            secondsSinceSave = 0;
            data.save();
        }
    }

    private void warn(Player player, long seconds) {
        String value;
        if (seconds >= 60) value = (seconds / 60) + (seconds == 60 ? " Minute" : " Minuten");
        else value = seconds + (seconds == 1 ? " Sekunde" : " Sekunden");
        player.sendMessage(Messages.value("Verbleibende Spielzeit: ", value, ""));
    }

    public PlayerRecord current(Player player) {
        PlayerRecord record = data.player(player.getUniqueId(), player.getName());
        LocalDate today = LocalDate.now(zone());
        if (!record.playDate().equals(today)) {
            record.playDate(today);
            record.playedSeconds(0);
            previousRemaining.put(player.getUniqueId(), totalSeconds());
        }
        return record;
    }

    public long remaining(PlayerRecord record) {
        if (!record.playDate().equals(LocalDate.now(zone()))) return totalSeconds();
        return Math.max(0, totalSeconds() - record.playedSeconds());
    }

    public String formatted(PlayerRecord record) {
        return de.minecraft.rival.util.RivalRules.formatDuration(remaining(record));
    }

    public String formatted(Player player) {
        long seconds = remaining(current(player));
        return de.minecraft.rival.util.RivalRules.formatDuration(seconds);
    }

    public long playedToday(PlayerRecord record) {
        return record.playDate().equals(LocalDate.now(zone())) ? Math.max(0, record.playedSeconds()) : 0L;
    }

    public String formattedPlayed(PlayerRecord record) {
        return de.minecraft.rival.util.RivalRules.formatDuration(playedToday(record));
    }

    public void toggleBar(Player player) {
        PlayerRecord record = current(player);
        boolean visible = record.bossbarSet() ? record.bossbar() : plugin.getConfig().getBoolean("playtime.bossbar-default");
        record.bossbar(!visible);
        record.bossbarSet(true);
        showBar(player, record.bossbar());
        data.save();
    }

    public boolean isTracking(Player player) {
        return plugin.getConfig().getBoolean("playtime.enabled", true)
            && !plugin.adminMode().isActive(player)
            && !vanish.isVanished(player)
            && plugin.projects().isParticipant(player);
    }

    public String status(Player player) {
        if (!plugin.getConfig().getBoolean("playtime.enabled", true)) return "pausiert – global deaktiviert";
        if (plugin.adminMode().isActive(player)) return "pausiert – Admin-Modus";
        if (vanish.isVanished(player)) return "pausiert – Vanish";
        if (!plugin.projects().isStarted()) return "pausiert – Projekt noch nicht gestartet";
        PlayerRecord record = current(player);
        if (record.eliminated()) return "pausiert – ausgeschieden";
        if (record.side() == 0) return "pausiert – noch keiner Seite zugewiesen";
        return "läuft";
    }

    public void refreshVisibility(Player player) {
        PlayerRecord record = current(player);
        boolean wanted = record.bossbarSet() ? record.bossbar() : plugin.getConfig().getBoolean("playtime.bossbar-default");
        showBar(player, wanted && plugin.projects().isParticipant(player) && !plugin.adminMode().isActive(player));
    }

    private void showBar(Player player, boolean show) {
        if (!show) { hideBar(player); return; }
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(), ignored -> Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SOLID));
        if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
        updateBar(player);
    }

    private void hideBar(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) bar.removePlayer(player);
    }

    private void updateBar(Player player) {
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) return;
        if (!plugin.getConfig().getBoolean("playtime.enabled", true)) {
            bar.setTitle(Messages.prefix() + ChatColor.GRAY + "deaktiviert");
            bar.setProgress(1);
            return;
        }
        long remaining = remaining(current(player));
        bar.setTitle(Messages.prefix() + ChatColor.GOLD + de.minecraft.rival.util.RivalRules.formatDuration(remaining));
        bar.setProgress(Math.max(0, Math.min(1, remaining / (double) Math.max(1, totalSeconds()))));
        // Bukkit 1.20.1 besitzt keine eigene ORANGE-Bossbar. YELLOW ist die
        // vanilla-nächste orange/goldene Bossbarfarbe; der Text selbst ist GOLD.
        bar.setColor(BarColor.YELLOW);
    }

    public long totalSeconds() { return Math.max(0, plugin.getConfig().getLong("playtime.daily-minutes", 180)) * 60L; }
    private ZoneId zone() { return ZoneId.of(plugin.getConfig().getString("general.timezone", "Europe/Vienna")); }
}
