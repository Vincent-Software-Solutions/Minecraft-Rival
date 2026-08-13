package de.minecraft.rival.game;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.util.Messages;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.logging.Level;

public final class GraveManager implements Listener {
    private final RivalPlugin plugin;
    private final NamespacedKey graveKey;
    private final File file;
    private final Map<UUID, Grave> graves = new HashMap<>();
    private final Map<UUID, Integer> pendingEmptyNotices = new HashMap<>();

    public GraveManager(RivalPlugin plugin) {
        this.plugin = plugin;
        graveKey = new NamespacedKey(plugin, "grave_id");
        file = new File(plugin.getDataFolder(), "graves.yml");
        Bukkit.getScheduler().runTaskTimer(plugin, this::purgeExpired, 1200L, 1200L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        List<ItemStack> contents = new ArrayList<>(Arrays.stream(dead.getInventory().getContents())
            .filter(Objects::nonNull).map(ItemStack::clone).toList());
        ItemStack cursor = dead.getItemOnCursor();
        if (!cursor.getType().isAir()) contents.add(cursor.clone());
        dead.setItemOnCursor(null);
        dead.getInventory().clear();
        event.setKeepInventory(false);
        event.getDrops().clear();
        if (contents.isEmpty()) return;
        create(dead, dead.getLocation(), contents, System.currentTimeMillis());
    }

    private void create(Player owner, Location source, List<ItemStack> items, long createdAt) {
        World world = source.getWorld();
        if (world == null) {
            world = plugin.mainWorld();
            source = world.getSpawnLocation();
        }
        final World graveWorld = world;
        Location location = safeGraveLocation(source);
        UUID id = UUID.randomUUID();
        GraveInventory holder = new GraveInventory(id);
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatColor.DARK_GRAY + "Grab von " + owner.getName());
        holder.inventory = inventory;
        for (ItemStack item : items) inventory.addItem(item).values().forEach(left -> graveWorld.dropItemNaturally(location, left));

        ArmorStand stand = graveWorld.spawn(location, ArmorStand.class, armor -> {
            armor.setInvisible(true);
            armor.setInvulnerable(true);
            armor.setGravity(false);
            armor.setSmall(true);
            armor.setBasePlate(false);
            armor.setGlowing(true);
            armor.getPersistentDataContainer().set(graveKey, PersistentDataType.STRING, id.toString());
            PlayerProfile profile = Bukkit.createPlayerProfile(owner.getUniqueId(), owner.getName());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            var meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            meta.setOwnerProfile(profile);
            head.setItemMeta(meta);
            armor.getEquipment().setHelmet(head);
        });
        TextDisplay text = graveWorld.spawn(location.clone().add(0, 1.65, 0), TextDisplay.class, display -> {
            configureHologram(display, "Grab von " + owner.getName(), id);
        });
        graves.put(id, new Grave(id, owner.getUniqueId(), owner.getName(), location, createdAt, inventory, stand.getUniqueId(), text.getUniqueId()));
        save();
        plugin.getLogger().info("Grab für " + owner.getName() + " erstellt bei " + graveWorld.getName() + " "
            + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ() + " (" + items.size() + " Stapel).");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractAtEntityEvent event) {
        Grave grave = grave(event.getRightClicked());
        if (grave == null) return;
        event.setCancelled(true);
        use(event.getPlayer(), grave);
    }

    @EventHandler(ignoreCancelled = true)
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        Grave grave = grave(event.getRightClicked());
        if (grave == null) return;
        event.setCancelled(true);
        use(event.getPlayer(), grave);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Grave grave = grave(event.getEntity());
        if (grave == null) return;
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player) use(player, grave);
    }

    private void use(Player player, Grave grave) {
        long protectionLeft = protectionEnd(grave) - System.currentTimeMillis();
        if (!player.getUniqueId().equals(grave.owner) && protectionLeft > 0) {
            Messages.error(player, "Dieses Grab ist noch " + formatDuration(protectionLeft)
                + " ausschließlich für " + grave.ownerName + " geschützt.");
            return;
        }
        if (player.isSneaking()) {
            remove(grave, false);
            Messages.normal(player, "Der Grabstein und sein Inhalt wurden gelöscht.");
        } else {
            player.openInventory(grave.inventory);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GraveInventory holder)) return;
        Grave grave = graves.get(holder.id);
        if (grave != null && grave.inventory.isEmpty()) {
            remove(grave, false);
            notifyEmpty(grave);
        }
        else save();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Integer count = pendingEmptyNotices.remove(event.getPlayer().getUniqueId());
        if (count == null || count <= 0) return;
        String message = count == 1
            ? "Dein Grab wurde vollständig geleert und ist verschwunden."
            : count + " deiner Gräber wurden vollständig geleert und sind verschwunden.";
        Messages.normal(event.getPlayer(), message);
        save();
    }

    private long protectionEnd(Grave grave) {
        long minutes = plugin.getConfig().getLong("grave.owner-protection-minutes", 8L);
        return grave.createdAt + Duration.ofMinutes(Math.max(0L, minutes)).toMillis();
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(1L, (millis + 999L) / 1000L);
        long minutes = seconds / 60L;
        long remainder = seconds % 60L;
        if (minutes == 0L) return seconds + " Sekunde" + (seconds == 1L ? "" : "n");
        return minutes + " Min " + remainder + " Sek";
    }

    private void notifyEmpty(Grave grave) {
        Player owner = Bukkit.getPlayer(grave.owner);
        if (owner != null && owner.isOnline()) {
            Messages.normal(owner, "Dein Grab bei " + grave.location.getBlockX() + ", "
                + grave.location.getBlockY() + ", " + grave.location.getBlockZ()
                + " wurde vollständig geleert und ist verschwunden.");
        } else {
            pendingEmptyNotices.merge(grave.owner, 1, Integer::sum);
            save();
        }
    }

    private Grave grave(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(graveKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try { return graves.get(UUID.fromString(raw)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private Location safeGraveLocation(Location source) {
        World world = Objects.requireNonNullElse(source.getWorld(), plugin.mainWorld());
        int x = source.getBlockX();
        int z = source.getBlockZ();
        if (source.getY() < world.getMinHeight() || source.getY() >= world.getMaxHeight() - 1)
            return surfaceGraveLocation(world, x, z);
        int preferredY = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 2, source.getBlockY()));

        // Zuerst bleibt das Grab möglichst nahe am Todesort (auch in Höhlen), sucht aber
        // niemals in festen Blöcken oder Flüssigkeiten. Der Raum über dem Kopf wird für
        // Kopf und Hologramm ebenfalls freigehalten.
        for (int radius = 0; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                for (int offset = 0; offset <= 12; offset++) {
                    int above = preferredY + offset;
                    if (openGraveSpace(world, x + dx, above, z + dz))
                        return centered(world, x + dx, above, z + dz);
                    if (offset == 0) continue;
                    int below = preferredY - offset;
                    if (openGraveSpace(world, x + dx, below, z + dz))
                        return centered(world, x + dx, below, z + dz);
                }
            }
        }

        return surfaceGraveLocation(world, x, z);
    }

    private static boolean openGraveSpace(World world, int x, int y, int z) {
        if (y < world.getMinHeight() || y + 1 >= world.getMaxHeight()) return false;
        var feet = world.getBlockAt(x, y, z);
        var head = world.getBlockAt(x, y + 1, z);
        return feet.isPassable() && head.isPassable() && !feet.isLiquid() && !head.isLiquid();
    }

    private static Location centered(World world, int x, int y, int z) {
        return new Location(world, x + 0.5, y + 0.05, z + 0.5);
    }

    private static Location surfaceGraveLocation(World world, int x, int z) {
        int y = Math.max(world.getMinHeight(), Math.min(world.getMaxHeight() - 2,
            world.getHighestBlockYAt(x, z) + 1));
        return centered(world, x, y, z);
    }

    private void purgeExpired() {
        long lifetime = Duration.ofHours(plugin.getConfig().getLong("grave.lifetime-hours", 24)).toMillis();
        new ArrayList<>(graves.values()).stream()
            .filter(grave -> System.currentTimeMillis() - grave.createdAt >= lifetime)
            .forEach(grave -> remove(grave, false));
    }

    private void remove(Grave grave, boolean keepRecord) {
        if (!keepRecord) graves.remove(grave.id);
        if (!grave.inventory.isEmpty()) new ArrayList<>(grave.inventory.getViewers()).forEach(HumanEntity::closeInventory);
        if (!keepRecord) grave.inventory.clear();
        Entity stand = Bukkit.getEntity(grave.standId);
        Entity text = Bukkit.getEntity(grave.textId);
        if (stand != null) stand.remove();
        if (text != null) text.remove();
        save();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            String raw = entity.getPersistentDataContainer().get(graveKey, PersistentDataType.STRING);
            if (raw == null) continue;
            try {
                if (!graves.containsKey(UUID.fromString(raw))) entity.remove();
            } catch (IllegalArgumentException ex) {
                entity.remove();
            }
        }
    }

    public int deleteAll() {
        int count = graves.size();
        new ArrayList<>(graves.values()).forEach(grave -> remove(grave, false));
        return count;
    }

    public int deleteNear(Location center, double radius) {
        List<Grave> matches = graves.values().stream().filter(grave -> grave.location.getWorld().equals(center.getWorld())
            && grave.location.distanceSquared(center) <= radius * radius).toList();
        matches.forEach(grave -> remove(grave, false));
        return matches.size();
    }

    public int deleteByOwner(UUID owner) {
        List<Grave> matches = graves.values().stream().filter(grave -> grave.owner.equals(owner)).toList();
        matches.forEach(grave -> remove(grave, false));
        return matches.size();
    }

    public int count() { return graves.size(); }

    public void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var noticeRoot = yaml.getConfigurationSection("pending-empty-notices");
        if (noticeRoot != null) for (String key : noticeRoot.getKeys(false)) try {
            int count = Math.max(0, noticeRoot.getInt(key));
            if (count > 0) pendingEmptyNotices.put(UUID.fromString(key), count);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Ungültiger Spieler in pending-empty-notices: " + key);
        }
        var root = yaml.getConfigurationSection("graves");
        if (root != null) for (String key : root.getKeys(false)) try {
            UUID id = UUID.fromString(key);
            String path = "graves." + key + ".";
            String worldName = yaml.getString(path + "world", plugin.mainWorld().getName());
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("Grabwelt '" + worldName + "' ist nicht geladen; Grab " + key + " wird später erneut versucht.");
                continue;
            }
            Location location = safeGraveLocation(new Location(world, yaml.getDouble(path + "x"), yaml.getDouble(path + "y"), yaml.getDouble(path + "z")));
            UUID owner = UUID.fromString(Objects.requireNonNull(yaml.getString(path + "owner")));
            String ownerName = yaml.getString(path + "owner-name", "Unbekannt");
            List<ItemStack> items = decode(yaml.getString(path + "items", ""));
            if (items.stream().allMatch(item -> item == null || item.getType().isAir())) {
                pendingEmptyNotices.merge(owner, 1, Integer::sum);
                continue;
            }
            // Entities werden nach einem Neustart eindeutig neu erzeugt; verwaiste alte Marker werden vorher entfernt.
            world.getNearbyEntities(location, 2, 3, 2).stream().filter(e -> key.equals(e.getPersistentDataContainer().get(graveKey, PersistentDataType.STRING))).forEach(Entity::remove);
            createLoaded(id, owner, ownerName, location, yaml.getLong(path + "created-at"), items);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Grab konnte nicht geladen werden: " + key, ex);
        }
        purgeExpired();
        save();
    }

    private void createLoaded(UUID id, UUID owner, String ownerName, Location location, long createdAt, List<ItemStack> items) {
        World world = Objects.requireNonNull(location.getWorld());
        GraveInventory holder = new GraveInventory(id);
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatColor.DARK_GRAY + "Grab von " + ownerName);
        holder.inventory = inventory;
        items.stream().filter(Objects::nonNull).forEach(item -> inventory.addItem(item).values().forEach(left -> world.dropItemNaturally(location, left)));
        ArmorStand stand = world.spawn(location, ArmorStand.class, armor -> {
            armor.setInvisible(true); armor.setInvulnerable(true); armor.setGravity(false); armor.setSmall(true); armor.setBasePlate(false); armor.setGlowing(true);
            armor.getPersistentDataContainer().set(graveKey, PersistentDataType.STRING, id.toString());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            var meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            meta.setOwnerProfile(Bukkit.createPlayerProfile(owner, ownerName));
            head.setItemMeta(meta); armor.getEquipment().setHelmet(head);
        });
        TextDisplay text = world.spawn(location.clone().add(0, 1.65, 0), TextDisplay.class, display -> {
            configureHologram(display, "Grab von " + ownerName, id);
        });
        graves.put(id, new Grave(id, owner, ownerName, location, createdAt, inventory, stand.getUniqueId(), text.getUniqueId()));
    }

    private void configureHologram(TextDisplay display, String label, UUID id) {
        display.setText(ChatColor.AQUA + label);
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(true);
        display.setShadowed(true);
        display.setLineWidth(160);
        // Mohist 1.20.1 liefert für Displays teils eine übergroße
        // Standardtransformation. Eine explizite Skalierung hält das
        // Hologramm auf allen getesteten Clients auf normaler Nametag-Größe.
        display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
            new Vector3f(0.04f, 0.04f, 0.04f), new AxisAngle4f()));
        display.setBackgroundColor(org.bukkit.Color.fromARGB(120, 8, 8, 8));
        display.setInvulnerable(true);
        display.getPersistentDataContainer().set(graveKey, PersistentDataType.STRING, id.toString());
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        pendingEmptyNotices.forEach((owner, count) -> yaml.set("pending-empty-notices." + owner, count));
        for (Grave grave : graves.values()) {
            String path = "graves." + grave.id + ".";
            yaml.set(path + "owner", grave.owner.toString());
            yaml.set(path + "owner-name", grave.ownerName);
            yaml.set(path + "world", grave.location.getWorld().getName());
            yaml.set(path + "x", grave.location.getX()); yaml.set(path + "y", grave.location.getY()); yaml.set(path + "z", grave.location.getZ());
            yaml.set(path + "created-at", grave.createdAt);
            yaml.set(path + "items", encode(grave.inventory.getContents()));
        }
        try { yaml.save(file); }
        catch (IOException ex) { plugin.getLogger().log(Level.SEVERE, "graves.yml konnte nicht gespeichert werden", ex); }
    }

    private static String encode(ItemStack[] items) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(raw)) {
                out.writeInt(items.length);
                for (ItemStack item : items) out.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(raw.toByteArray());
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }

    private static List<ItemStack> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
            int size = in.readInt();
            if (size < 0 || size > 54) throw new IllegalStateException("Ungültige Grabgröße: " + size);
            List<ItemStack> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) result.add((ItemStack) in.readObject());
            return result;
        } catch (IOException | ClassNotFoundException | IllegalArgumentException ex) {
            throw new IllegalStateException("Ungültige Grabdaten", ex);
        }
    }

    private record Grave(UUID id, UUID owner, String ownerName, Location location, long createdAt,
                         Inventory inventory, UUID standId, UUID textId) {}
    private static final class GraveInventory implements InventoryHolder {
        private final UUID id;
        private Inventory inventory;
        private GraveInventory(UUID id) { this.id = id; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
