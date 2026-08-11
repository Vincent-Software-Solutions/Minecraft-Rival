package de.minecraft.rival.game;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.data.DataStore;
import de.minecraft.rival.data.PlayerRecord;
import de.minecraft.rival.util.Messages;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.Vector;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public final class BorderManager implements Listener {
    private final RivalPlugin plugin;
    private final DataStore data;
    private boolean endFightOverride;

    public BorderManager(RivalPlugin plugin, DataStore data) {
        this.plugin = plugin;
        this.data = data;
    }

    public void enable() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkScheduledActivation, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::renderDivider, 5L, 5L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!activeFor(event.getPlayer()) || event.getTo() == null || plugin.adminMode().isActive(event.getPlayer())) return;
        PlayerRecord record = data.player(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        if (allowed(record.side(), event.getTo())) return;
        event.setCancelled(true);
        if (event instanceof PlayerTeleportEvent) Messages.error(event.getPlayer(), "Die Mittel-Border kann nicht überquert werden.");
    }

    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
        for (Player player : playerPassengers(event.getVehicle())) {
            if (!activeFor(player) || plugin.adminMode().isActive(player)) continue;
            PlayerRecord record = data.player(player.getUniqueId(), player.getName());
            if (allowed(record.side(), event.getTo())) continue;
            List<Entity> directPassengers = new ArrayList<>(event.getVehicle().getPassengers());
            event.getVehicle().eject();
            event.getVehicle().teleport(event.getFrom());
            event.getVehicle().setVelocity(new Vector());
            for (Entity passenger : directPassengers) {
                passenger.teleport(event.getFrom());
                event.getVehicle().addPassenger(passenger);
            }
            if (!allowed(record.side(), player.getLocation())) player.teleport(event.getFrom());
            Messages.error(player, "Die Mittel-Border kann auch mit Fahrzeugen nicht überquert werden.");
            break;
        }
    }

    public void setEnabled(boolean enabled) {
        plugin.getConfig().set("border.enabled", enabled);
        plugin.saveConfig();
        Bukkit.broadcast(Messages.normal("Die Mittel-Border wurde " + (enabled ? "aktiviert." : "deaktiviert.")));
    }

    public boolean isEnabled() { return plugin.getConfig().getBoolean("border.enabled", true) && !endFightOverride; }

    public void setEndFightOverride(boolean value) {
        endFightOverride = value;
    }

    private boolean activeFor(Player player) {
        return isEnabled() && plugin.projects().isParticipant(player)
            && player.getWorld().getName().equals(plugin.getConfig().getString("border.world", "world"));
    }

    private boolean allowed(int side, Location location) {
        double split = plugin.getConfig().getDouble("border.split-coordinate", 0);
        double coordinate = plugin.getConfig().getString("border.axis", "X").equalsIgnoreCase("X") ? location.getX() : location.getZ();
        return de.minecraft.rival.util.RivalRules.allowedSide(side, coordinate, split);
    }

    private static List<Player> playerPassengers(Entity root) {
        List<Player> players = new ArrayList<>();
        collectPlayerPassengers(root, players);
        return players;
    }

    private static void collectPlayerPassengers(Entity entity, List<Player> players) {
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof Player player) players.add(player);
            collectPlayerPassengers(passenger, players);
        }
    }

    private void renderDivider() {
        if (!isEnabled()) return;
        double split = plugin.getConfig().getDouble("border.split-coordinate", 0);
        boolean xAxis = plugin.getConfig().getString("border.axis", "X").equalsIgnoreCase("X");
        double distance = Math.max(4, plugin.getConfig().getDouble("border.visual-distance", 24));
        double spacing = Math.max(1, plugin.getConfig().getDouble("border.particle-spacing", 2.5));
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(70, 150, 255), 0.8f);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!activeFor(player) || plugin.adminMode().isActive(player)) continue;
            double coordinate = xAxis ? player.getX() : player.getZ();
            if (Math.abs(coordinate - split) > distance) continue;
            Location origin = player.getLocation();
            for (double horizontal = -distance; horizontal <= distance; horizontal += spacing) {
                for (double vertical = -4; vertical <= 7; vertical += spacing) {
                    Location point = xAxis
                        ? new Location(player.getWorld(), split, origin.getY() + vertical, origin.getZ() + horizontal)
                        : new Location(player.getWorld(), origin.getX() + horizontal, origin.getY() + vertical, split);
                    player.spawnParticle(Particle.REDSTONE, point, 1, 0, 0, 0, 0, dust);
                }
            }
        }
    }

    private void checkScheduledActivation() {
        if (endFightOverride) return;
        boolean enabled = isEnabled();
        String key = enabled ? "border.deactivate-at" : "border.activate-at";
        String raw = plugin.getConfig().getString(key, "");
        if (raw == null || raw.isBlank()) return;
        try {
            ZoneId zone = ZoneId.of(plugin.getConfig().getString("general.timezone", "Europe/Vienna"));
            Instant activation = LocalDateTime.parse(raw).atZone(zone).toInstant();
            if (!Instant.now().isBefore(activation)) {
                plugin.getConfig().set(key, "");
                setEnabled(!enabled);
            }
        } catch (DateTimeParseException ex) {
            plugin.getLogger().warning("Ungültige Zeit in " + key + "; erwartet wird YYYY-MM-DDTHH:MM");
            plugin.getConfig().set(key, "");
            plugin.saveConfig();
        }
    }
}
