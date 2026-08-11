package de.minecraft.rival.game;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.data.DataStore;
import de.minecraft.rival.data.PlayerRecord;
import de.minecraft.rival.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

public final class ProjectManager implements Listener {
    private final RivalPlugin plugin;
    private final DataStore data;
    private final Map<UUID, BossBar> countdownBars = new HashMap<>();
    private long initialCountdownSeconds;
    private long lastStartFailureLog;

    public ProjectManager(RivalPlugin plugin, DataStore data) {
        this.plugin = plugin;
        this.data = data;
    }

    public void enable() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public boolean isStarted() { return plugin.getConfig().getBoolean("project.started", false); }

    public boolean isParticipant(Player player) {
        PlayerRecord record = data.player(player.getUniqueId(), player.getName());
        return isStarted() && record.side() != 0 && !record.eliminated();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> placePlayer(event.getPlayer()), 3L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { hideCountdown(event.getPlayer()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || plugin.adminMode().isActive(event.getPlayer()) || isParticipant(event.getPlayer())) return;
        Location waiting = waitingRoom();
        if (waiting == null) return;
        double radius = plugin.getConfig().getDouble("project.waiting-radius", 15);
        if (sameWorld(waiting, event.getTo()) && waiting.distanceSquared(event.getTo()) <= radius * radius) return;
        event.setCancelled(true);
        event.getPlayer().teleport(waiting);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && !plugin.adminMode().isActive(player) && !isParticipant(player)) event.setCancelled(true);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (plugin.adminMode().isActive(event.getPlayer())) return;
        Location destination = destination(event.getPlayer());
        if (destination != null) event.setRespawnLocation(destination);
    }

    public void placePlayer(Player player) {
        if (!player.isOnline() || plugin.adminMode().isActive(player)) return;
        Location destination = destination(player);
        if (destination != null && (!sameWorld(destination, player.getLocation()) || destination.distanceSquared(player.getLocation()) > 4)) {
            player.teleport(destination);
        }
        if (isParticipant(player)) hideCountdown(player); else showCountdown(player);
        plugin.playtime().refreshVisibility(player);
    }

    private Location destination(Player player) {
        PlayerRecord record = data.player(player.getUniqueId(), player.getName());
        if (!isStarted() || record.side() == 0) return waitingRoom();
        List<Location> spawns = spawns(record.side());
        if (spawns.isEmpty()) return waitingRoom();
        return spawns.get(Math.floorMod(player.getUniqueId().hashCode(), spawns.size())).clone();
    }

    public boolean start(boolean manual) {
        if (isStarted()) return false;
        if (waitingRoom() == null || !plugin.zones().isDefined(ZoneManager.Zone.NETHER) || !plugin.zones().isDefined(ZoneManager.Zone.END)) {
            logStartFailure("Warteraum sowie Nether- und End-Zone müssen gesetzt sein.");
            return false;
        }
        if (plugin.zones().specialZonesOverlap()) {
            logStartFailure("Nether- und End-Zone dürfen sich nicht überschneiden.");
            return false;
        }
        List<? extends Player> unassigned = Bukkit.getOnlinePlayers().stream().filter(player -> !plugin.adminMode().isActive(player))
            .filter(player -> data.player(player.getUniqueId(), player.getName()).side() == 0).toList();
        if (!unassigned.isEmpty()) {
            logStartFailure("Nicht zugewiesene Online-Spieler: " + String.join(", ", unassigned.stream().map(Player::getName).toList()));
            return false;
        }
        long participants = data.players().stream().filter(record -> !record.eliminated() && record.side() != 0).count();
        if (participants < 2) {
            logStartFailure("Mindestens zwei Spieler müssen einer Seite zugewiesen sein.");
            return false;
        }
        Set<Integer> needed = new HashSet<>();
        data.players().stream().filter(record -> !record.eliminated() && record.side() != 0).forEach(record -> needed.add(record.side()));
        for (int side : needed) if (spawns(side).isEmpty()) {
            plugin.getLogger().warning("Projektstart abgebrochen: Für Seite " + side + " ist kein Spawn gesetzt.");
            return false;
        }
        for (int side : needed) {
            long onlineOnSide = Bukkit.getOnlinePlayers().stream().filter(player -> !plugin.adminMode().isActive(player))
                .filter(player -> data.player(player.getUniqueId(), player.getName()).side() == side).count();
            int distinctSpawns = distinctSpawnCount(spawns(side));
            if (distinctSpawns < onlineOnSide) {
                logStartFailure("Seite " + side + " benötigt " + onlineOnSide + " unterschiedliche Spawnpunkte, hat aber nur " + distinctSpawns + ".");
                return false;
            }
        }
        plugin.getConfig().set("project.started", true);
        plugin.getConfig().set("project.start-at", "");
        plugin.getConfig().set("border.enabled", true);
        plugin.saveConfig();
        plugin.combat().resetNemeses();
        plugin.borders().setEnabled(true);
        Map<Integer, Integer> nextSpawn = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.adminMode().isActive(player)) continue;
            PlayerRecord record = data.player(player.getUniqueId(), player.getName());
            List<Location> sideSpawns = spawns(record.side());
            int index = nextSpawn.getOrDefault(record.side(), 0);
            nextSpawn.put(record.side(), index + 1);
            hideCountdown(player);
            player.teleport(sideSpawns.get(index));
            plugin.playtime().refreshVisibility(player);
        }
        hideAllCountdowns();
        Messages.broadcast(Messages.normal("Minecraft Rival beginnt jetzt! Viel Erfolg."));
        plugin.endFight().checkAutomaticStart();
        return true;
    }

    public boolean stop() {
        if (!isStarted()) return false;
        if (plugin.endFight().isRunning()) plugin.endFight().stop();
        plugin.getConfig().set("project.started", false);
        plugin.saveConfig();
        plugin.combat().resetNemeses();
        for (Player player : Bukkit.getOnlinePlayers()) placePlayer(player);
        Messages.broadcast(Messages.normal("Das Projekt wurde gestoppt. Alle Spieler kehren in den Warteraum zurück."));
        return true;
    }

    public void setWaitingRoom(Location location) {
        requireMainWorld(location);
        plugin.getConfig().set("project.waiting-room", location);
        plugin.saveConfig();
        if (!isStarted()) Bukkit.getOnlinePlayers().forEach(this::placePlayer);
    }

    public Location waitingRoom() {
        Location location = plugin.getConfig().getLocation("project.waiting-room");
        return location != null && plugin.isMainWorld(location.getWorld()) ? location : null;
    }

    public void addSpawn(int side, Location location) {
        requireMainWorld(location);
        if (spawns(side).stream().anyMatch(existing -> sameBlock(existing, location)))
            throw new IllegalArgumentException("An diesem Block existiert bereits ein Spawnpunkt für diese Seite.");
        String path = spawnPath(side);
        List<Object> values = new ArrayList<>(Optional.ofNullable(plugin.getConfig().getList(path)).orElse(List.of()));
        values.add(location);
        plugin.getConfig().set(path, values);
        plugin.saveConfig();
    }

    public void clearSpawns(int side) {
        plugin.getConfig().set(spawnPath(side), new ArrayList<>());
        plugin.saveConfig();
    }

    public List<Location> spawns(int side) {
        List<?> values = Optional.ofNullable(plugin.getConfig().getList(spawnPath(side))).orElse(List.of());
        return values.stream().filter(Location.class::isInstance).map(Location.class::cast)
            .filter(location -> plugin.isMainWorld(location.getWorld())).toList();
    }

    public void playerAssigned(Player player) {
        placePlayer(player);
    }

    public void showCountdown(Player player) {
        if (plugin.adminMode().isActive(player) || isParticipant(player)) return;
        BossBar bar = countdownBars.computeIfAbsent(player.getUniqueId(), ignored ->
            Bukkit.createBossBar(ChatColor.AQUA + "Warte auf Projektstart …", BarColor.BLUE, BarStyle.SOLID));
        if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
        updateBar(bar);
    }

    public void hideCountdown(Player player) {
        BossBar bar = countdownBars.remove(player.getUniqueId());
        if (bar != null) bar.removePlayer(player);
    }

    private void hideAllCountdowns() {
        for (Player player : Bukkit.getOnlinePlayers()) hideCountdown(player);
    }

    private void tick() {
        if (isStarted()) return;
        Instant start = configuredStart();
        if (start != null) {
            long remaining = Math.max(0, Duration.between(Instant.now(), start).toSeconds());
            initialCountdownSeconds = Math.max(initialCountdownSeconds, remaining);
            if (remaining <= 0) {
                if (start(false)) return;
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.adminMode().isActive(player)) showCountdown(player);
        }
        countdownBars.values().forEach(this::updateBar);
    }

    private void updateBar(BossBar bar) {
        if (isStarted()) {
            bar.setTitle(ChatColor.YELLOW + "Warte auf Seitenzuweisung …");
            bar.setProgress(1);
            bar.setColor(BarColor.YELLOW);
            return;
        }
        Instant start = configuredStart();
        if (start == null) {
            bar.setTitle(ChatColor.AQUA + "Warte auf Projektstart …");
            bar.setProgress(1);
            return;
        }
        long seconds = Math.max(0, Duration.between(Instant.now(), start).toSeconds());
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long rest = seconds % 60;
        bar.setTitle(ChatColor.AQUA + "Projektstart in: %02d:%02d:%02d:%02d".formatted(days, hours, minutes, rest));
        bar.setProgress(Math.max(0, Math.min(1, seconds / (double) Math.max(1, initialCountdownSeconds))));
        bar.setColor(seconds <= 60 ? BarColor.RED : seconds <= 300 ? BarColor.YELLOW : BarColor.BLUE);
    }

    private Instant configuredStart() {
        String raw = plugin.getConfig().getString("project.start-at", "");
        if (raw == null || raw.isBlank()) return null;
        try {
            ZoneId zone = ZoneId.of(plugin.getConfig().getString("general.timezone", "Europe/Vienna"));
            return LocalDateTime.parse(raw).atZone(zone).toInstant();
        } catch (DateTimeParseException ex) {
            plugin.getLogger().warning("Ungültiges project.start-at; erwartet wird YYYY-MM-DDTHH:MM[:SS]");
            plugin.getConfig().set("project.start-at", "");
            plugin.saveConfig();
            return null;
        }
    }

    private void logStartFailure(String reason) {
        if (System.currentTimeMillis() - lastStartFailureLog < 10_000L) return;
        lastStartFailureLog = System.currentTimeMillis();
        plugin.getLogger().warning("Projektstart abgebrochen: " + reason);
    }

    private String spawnPath(int side) { return side < 0 ? "project.spawns.negative" : "project.spawns.positive"; }
    private static int distinctSpawnCount(List<Location> locations) {
        return (int) locations.stream().map(location -> location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ()).distinct().count();
    }
    private static boolean sameBlock(Location first, Location second) {
        return first.getBlockX() == second.getBlockX() && first.getBlockY() == second.getBlockY() && first.getBlockZ() == second.getBlockZ();
    }
    private void requireMainWorld(Location location) {
        if (!plugin.isMainWorld(location.getWorld()))
            throw new IllegalArgumentException("Position muss in der Projektwelt rival_main liegen.");
    }
    private static boolean sameWorld(Location a, Location b) { return a.getWorld() != null && a.getWorld().equals(b.getWorld()); }
}
