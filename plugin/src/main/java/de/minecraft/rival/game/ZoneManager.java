package de.minecraft.rival.game;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.util.Messages;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ZoneManager implements Listener {
    public enum Zone { NETHER, END, OVERWORLD }

    private final RivalPlugin plugin;
    private final NamespacedKey originKey;
    private final NamespacedKey originSideKey;
    private final NamespacedKey projectileOriginKey;
    private final NamespacedKey projectileSideKey;
    private final Map<UUID, Location> lastSafeMobLocations = new HashMap<>();

    public ZoneManager(RivalPlugin plugin) {
        this.plugin = plugin;
        this.originKey = new NamespacedKey(plugin, "mob_origin_zone");
        this.originSideKey = new NamespacedKey(plugin, "mob_origin_side");
        this.projectileOriginKey = new NamespacedKey(plugin, "projectile_origin_zone");
        this.projectileSideKey = new NamespacedKey(plugin, "projectile_origin_side");
    }

    public void enable() {
        for (World world : Bukkit.getWorlds()) for (LivingEntity entity : world.getLivingEntities()) {
            if (entity instanceof Player) continue;
            if (!isMainWorld(world) || forbidden(entity)) entity.remove();
            else if (entity instanceof Mob) tag(entity, zoneAt(entity.getLocation()));
        }
        Bukkit.getScheduler().runTask(plugin, this::unloadForbiddenWorlds);
        Bukkit.getScheduler().runTaskTimer(plugin, this::unloadForbiddenWorlds, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::containMobsAndProjectiles, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        if (!isMainWorld(entity.getWorld()) || forbidden(entity)) {
            event.setCancelled(true);
            return;
        }
        Zone zone = zoneAt(entity.getLocation());
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            int rate = spawnRate(zone);
            if (rate <= 0 || ThreadLocalRandom.current().nextInt(100) >= rate) {
                event.setCancelled(true);
                return;
            }
        }
        tag(entity, zone);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (event.getTransformedEntities().stream().anyMatch(this::forbidden)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(EntityTeleportEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || event.getTo() == null) return;
        if (!isMainWorld(event.getTo().getWorld()) || zoneAt(event.getTo()) != origin(mob)
            || sideAt(event.getTo()) != originSide(mob)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        ProjectileSource shooter = projectile.getShooter();
        if (shooter instanceof LivingEntity living && !(living instanceof Player)) {
            projectile.getPersistentDataContainer().set(projectileOriginKey, PersistentDataType.STRING, origin(living).name());
            projectile.getPersistentDataContainer().set(projectileSideKey, PersistentDataType.INTEGER, originSide(living));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortalCreate(PortalCreateEvent event) { event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPortal(PlayerPortalEvent event) {
        event.setCanCreatePortal(false);
        event.setCancelled(true);
        Messages.error(event.getPlayer(), "Nether und End sind deaktiviert. Die Themeninseln liegen in der Hauptwelt.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onForbiddenTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null && !isMainWorld(event.getTo().getWorld())) {
            event.setCancelled(true);
            Messages.error(event.getPlayer(), "Nur die konfigurierte Hauptwelt ist zugelassen.");
        } else if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
            || event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL
            || event.getCause() == PlayerTeleportEvent.TeleportCause.END_GATEWAY) {
            event.setCancelled(true);
            Messages.error(event.getPlayer(), "Dimensionsportale und End-Gateways sind deaktiviert.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPortal(EntityPortalEvent event) { event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVillagerInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof AbstractVillager) {
            event.setCancelled(true);
            Messages.error(event.getPlayer(), "Villager und Handel sind in diesem Projekt deaktiviert.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMerchantOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() == org.bukkit.event.inventory.InventoryType.MERCHANT) event.setCancelled(true);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (isMainWorld(event.getPlayer().getWorld())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Location target = safeTarget(event.getPlayer());
            if (target != null) event.getPlayer().teleport(target);
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> enforcePlayerWorld(event.getPlayer()), 1L);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (!isMainWorld(event.getWorld())) Bukkit.getScheduler().runTask(plugin, this::unloadForbiddenWorlds);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (isMainWorld(event.getRespawnLocation().getWorld())) return;
        Location target = safeTarget(event.getPlayer());
        if (target != null) event.setRespawnLocation(target);
    }

    public Zone zoneAt(Location location) {
        if (inside(location, "zones.end")) return Zone.END;
        if (inside(location, "zones.nether")) return Zone.NETHER;
        return Zone.OVERWORLD;
    }

    public void setCorner(Zone zone, int corner, Location location) {
        if (zone == Zone.OVERWORLD) throw new IllegalArgumentException("Overworld ist automatisch alles außerhalb der Spezialzonen.");
        if (!plugin.isMainWorld(location.getWorld()))
            throw new IllegalArgumentException("Inselzonen müssen in rival_main liegen.");
        plugin.getConfig().set("zones." + key(zone) + ".pos" + corner, location);
        plugin.saveConfig();
        retagAllMobs();
    }

    public void clear(Zone zone) {
        if (zone == Zone.OVERWORLD) return;
        plugin.getConfig().set("zones." + key(zone) + ".pos1", null);
        plugin.getConfig().set("zones." + key(zone) + ".pos2", null);
        plugin.saveConfig();
        retagAllMobs();
    }

    public boolean isDefined(Zone zone) {
        if (zone == Zone.OVERWORLD) return true;
        Location a = plugin.getConfig().getLocation("zones." + key(zone) + ".pos1");
        Location b = plugin.getConfig().getLocation("zones." + key(zone) + ".pos2");
        return a != null && b != null && plugin.isMainWorld(a.getWorld()) && a.getWorld().equals(b.getWorld());
    }

    public boolean specialZonesOverlap() {
        if (!isDefined(Zone.NETHER) || !isDefined(Zone.END)) return false;
        Location n1 = plugin.getConfig().getLocation("zones.nether.pos1");
        Location n2 = plugin.getConfig().getLocation("zones.nether.pos2");
        Location e1 = plugin.getConfig().getLocation("zones.end.pos1");
        Location e2 = plugin.getConfig().getLocation("zones.end.pos2");
        if (!n1.getWorld().equals(e1.getWorld())) return false;
        return de.minecraft.rival.util.RivalRules.verticalZonesOverlap(n1.getX(), n1.getZ(), n2.getX(), n2.getZ(),
            e1.getX(), e1.getZ(), e2.getX(), e2.getZ());
    }

    public int spawnRate(Zone zone) { return plugin.getConfig().getInt("zones." + key(zone) + ".spawn-rate-percent", 100); }

    public void setSpawnRate(Zone zone, int percent) {
        if (percent < 0 || percent > 100) throw new IllegalArgumentException("Spawnrate muss zwischen 0 und 100 liegen.");
        plugin.getConfig().set("zones." + key(zone) + ".spawn-rate-percent", percent);
        plugin.saveConfig();
    }

    private boolean inside(Location location, String path) {
        Location a = plugin.getConfig().getLocation(path + ".pos1");
        Location b = plugin.getConfig().getLocation(path + ".pos2");
        if (a == null || b == null || a.getWorld() == null || !a.getWorld().equals(b.getWorld()) || !a.getWorld().equals(location.getWorld())) return false;
        return de.minecraft.rival.util.RivalRules.insideVerticalZone(location.getX(), location.getZ(), a.getX(), a.getZ(), b.getX(), b.getZ());
    }

    private boolean forbidden(Entity entity) {
        return entity instanceof EnderDragon || entity instanceof Villager || entity instanceof WanderingTrader;
    }

    private void tag(LivingEntity entity, Zone zone) {
        entity.getPersistentDataContainer().set(originKey, PersistentDataType.STRING, zone.name());
        entity.getPersistentDataContainer().set(originSideKey, PersistentDataType.INTEGER, sideAt(entity.getLocation()));
        lastSafeMobLocations.put(entity.getUniqueId(), entity.getLocation().clone());
    }

    private Zone origin(LivingEntity entity) {
        String value = entity.getPersistentDataContainer().get(originKey, PersistentDataType.STRING);
        if (value != null) try { return Zone.valueOf(value); } catch (IllegalArgumentException ignored) { }
        Zone zone = zoneAt(entity.getLocation());
        tag(entity, zone);
        return zone;
    }

    private int originSide(LivingEntity entity) {
        Integer side = entity.getPersistentDataContainer().get(originSideKey, PersistentDataType.INTEGER);
        if (side != null) return side;
        side = sideAt(entity.getLocation());
        entity.getPersistentDataContainer().set(originSideKey, PersistentDataType.INTEGER, side);
        return side;
    }

    private int sideAt(Location location) {
        double split = plugin.getConfig().getDouble("border.split-coordinate", 0);
        double coordinate = plugin.getConfig().getString("border.axis", "X").equalsIgnoreCase("X") ? location.getX() : location.getZ();
        return coordinate < split ? -1 : coordinate > split ? 1 : 0;
    }

    private void containMobsAndProjectiles() {
        for (Player player : Bukkit.getOnlinePlayers()) enforcePlayerWorld(player);
        Set<UUID> activeMobs = new HashSet<>();
        for (World world : Bukkit.getWorlds()) for (LivingEntity entity : world.getLivingEntities()) {
            if (entity instanceof Player || entity.isDead()) continue;
            if (!(entity instanceof Mob) && !forbidden(entity)) continue;
            UUID id = entity.getUniqueId();
            activeMobs.add(id);
            if (!isMainWorld(world) || forbidden(entity)) {
                lastSafeMobLocations.remove(id);
                entity.remove();
                continue;
            }

            Zone expectedZone = origin(entity);
            int expectedSide = originSide(entity);
            Location current = entity.getLocation();
            if (zoneAt(current) == expectedZone && sideAt(current) == expectedSide) {
                lastSafeMobLocations.put(id, current.clone());
                continue;
            }

            Location safe = lastSafeMobLocations.get(id);
            if (safe != null && safe.getWorld() != null && safe.getWorld().equals(world)
                && zoneAt(safe) == expectedZone && sideAt(safe) == expectedSide) {
                entity.teleport(safe);
                entity.setVelocity(new org.bukkit.util.Vector());
            } else {
                entity.remove();
                lastSafeMobLocations.remove(id);
            }
        }
        lastSafeMobLocations.keySet().retainAll(activeMobs);

        for (World world : Bukkit.getWorlds()) for (Projectile projectile : world.getEntitiesByClass(Projectile.class)) {
            if (!isMainWorld(world)) {
                projectile.remove();
                continue;
            }
            String raw = projectile.getPersistentDataContainer().get(projectileOriginKey, PersistentDataType.STRING);
            if (raw == null) continue;
            Integer side = projectile.getPersistentDataContainer().get(projectileSideKey, PersistentDataType.INTEGER);
            try {
                if (zoneAt(projectile.getLocation()) != Zone.valueOf(raw) || side == null || sideAt(projectile.getLocation()) != side) projectile.remove();
            } catch (IllegalArgumentException ex) {
                projectile.remove();
            }
        }
    }

    public void retagAllMobs() {
        for (World world : Bukkit.getWorlds()) for (LivingEntity entity : world.getLivingEntities()) {
            if (entity instanceof Player) continue;
            if (!isMainWorld(world) || forbidden(entity)) entity.remove();
            else if (entity instanceof Mob) tag(entity, zoneAt(entity.getLocation()));
        }
    }

    private boolean isMainWorld(World world) {
        return plugin.isMainWorld(world);
    }

    private Location safeTarget(Player player) {
        Location target = plugin.projects().waitingRoom();
        if (plugin.projects().isParticipant(player)) {
            var record = plugin.data().player(player.getUniqueId(), player.getName());
            var spawns = plugin.projects().spawns(record.side());
            if (!spawns.isEmpty()) target = spawns.get(0);
        }
        if (target != null && isMainWorld(target.getWorld())) return target;
        return plugin.mainWorld().getSpawnLocation();
    }

    private void enforcePlayerWorld(Player player) {
        if (!player.isOnline() || isMainWorld(player.getWorld())) return;
        Location target = safeTarget(player);
        if (target == null || !player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            player.kickPlayer("Nur die Projektwelt rival_main ist zugelassen.");
        }
    }

    private void unloadForbiddenWorlds() {
        for (World world : new java.util.ArrayList<>(Bukkit.getWorlds())) {
            if (isMainWorld(world)) continue;
            for (Player player : new java.util.ArrayList<>(world.getPlayers())) {
                enforcePlayerWorld(player);
            }
            if (world.getPlayers().isEmpty() && Bukkit.unloadWorld(world, false)) {
                plugin.getLogger().info("Nicht zugelassene Welt entladen: " + world.getName());
            }
        }
    }

    private static String key(Zone zone) { return zone.name().toLowerCase(Locale.ROOT); }
}
