package de.minecraft.rival.game;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.data.DataStore;
import de.minecraft.rival.data.PlayerRecord;
import de.minecraft.rival.util.Messages;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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
        data.save();
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("playtime.enabled", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) updateBar(player);
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.adminMode().isActive(player) || vanish.isVanished(player)) continue;
            if (!plugin.projects().isParticipant(player)) continue;
            if (adminActivationGrace.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis()) continue;
            PlayerRecord record = current(player);
            long before = previousRemaining.getOrDefault(player.getUniqueId(), remaining(record));
            record.playedSeconds(record.playedSeconds() + 1);
            long after = remaining(record);
            previousRemaining.put(player.getUniqueId(), after);
            for (long warning : WARNINGS) if (de.minecraft.rival.util.RivalRules.warningCrossed(before, after, warning)) warn(player, warning);
            updateBar(player);
            if (after <= 0) {
                data.save();
                player.kick(Component.text(plugin.getConfig().getString("messages.playtime-expired"), NamedTextColor.RED));
            }
        }
        if (++secondsSinceSave >= 60) {
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
        record.bossbar(!record.bossbar());
        showBar(player, record.bossbar());
        data.save();
    }

    public void refreshVisibility(Player player) {
        PlayerRecord record = current(player);
        boolean wanted = record.bossbar() || plugin.getConfig().getBoolean("playtime.bossbar-default");
        showBar(player, wanted && plugin.projects().isParticipant(player) && !plugin.adminMode().isActive(player));
    }

    private void showBar(Player player, boolean show) {
        if (!show) { hideBar(player); return; }
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(), ignored -> BossBar.bossBar(Component.empty(), 1, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS));
        player.showBossBar(bar);
        updateBar(player);
    }

    private void hideBar(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }

    private void updateBar(Player player) {
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) return;
        if (!plugin.getConfig().getBoolean("playtime.enabled", true)) {
            bar.name(Component.text("Tägliche Spielzeit: deaktiviert", NamedTextColor.GRAY));
            bar.progress(1);
            return;
        }
        long remaining = remaining(current(player));
        bar.name(Component.text("Spielzeit: " + formatted(player), NamedTextColor.AQUA));
        bar.progress(Math.max(0, Math.min(1, remaining / (float) Math.max(1, totalSeconds()))));
        bar.color(remaining <= 300 ? BossBar.Color.RED : remaining <= 900 ? BossBar.Color.YELLOW : BossBar.Color.BLUE);
    }

    public long totalSeconds() { return Math.max(0, plugin.getConfig().getLong("playtime.daily-minutes", 180)) * 60L; }
    private ZoneId zone() { return ZoneId.of(plugin.getConfig().getString("general.timezone", "Europe/Vienna")); }
}
