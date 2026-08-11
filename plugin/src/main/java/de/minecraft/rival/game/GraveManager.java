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
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.logging.Level;

public final class GraveManager implements Listener {
    private final RivalPlugin plugin;
    private final NamespacedKey graveKey;
    private final File file;
    private final Map<UUID, Grave> graves = new HashMap<>();

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
        Location location = source.getBlock().getLocation().add(0.5, 0.05, 0.5);
        UUID id = UUID.randomUUID();
        GraveInventory holder = new GraveInventory(id);
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatColor.DARK_GRAY + "Grab von " + owner.getName());
        holder.inventory = inventory;
        for (ItemStack item : items) inventory.addItem(item).values().forEach(left -> world.dropItemNaturally(location, left));

        ArmorStand stand = world.spawn(location, ArmorStand.class, armor -> {
            armor.setInvisible(true);
            armor.setInvulnerable(true);
            armor.setGravity(false);
            armor.setSmall(true);
            armor.setBasePlate(false);
            armor.getPersistentDataContainer().set(graveKey, PersistentDataType.STRING, id.toString());
            PlayerProfile profile = Bukkit.createPlayerProfile(owner.getUniqueId(), owner.getName());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            var meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            meta.setOwnerProfile(profile);
            head.setItemMeta(meta);
            armor.getEquipment().setHelmet(head);
        });
        TextDisplay text = world.spawn(location.clone().add(0, 1.65, 0), TextDisplay.class, display -> {
            display.setText(ChatColor.AQUA + "Grab von " + owner.getName());
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setInvulnerable(true);
            display.getPersistentDataContainer().set(graveKey, PersistentDataType.STRING, id.toString());
        });
        graves.put(id, new Grave(id, owner.getUniqueId(), owner.getName(), location, createdAt, inventory, stand.getUniqueId(), text.getUniqueId()));
        save();
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
        if (grave != null && grave.inventory.isEmpty()) remove(grave, false);
        else save();
    }

    private Grave grave(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(graveKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try { return graves.get(UUID.fromString(raw)); }
        catch (IllegalArgumentException ignored) { return null; }
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
        var root = yaml.getConfigurationSection("graves");
        if (root == null) return;
        for (String key : root.getKeys(false)) try {
            UUID id = UUID.fromString(key);
            String path = "graves." + key + ".";
            World world = Bukkit.getWorld(Objects.requireNonNull(yaml.getString(path + "world")));
            if (world == null) continue;
            Location location = new Location(world, yaml.getDouble(path + "x"), yaml.getDouble(path + "y"), yaml.getDouble(path + "z"));
            UUID owner = UUID.fromString(Objects.requireNonNull(yaml.getString(path + "owner")));
            String ownerName = yaml.getString(path + "owner-name", "Unbekannt");
            List<ItemStack> items = decode(yaml.getString(path + "items", ""));
            // Entities werden nach einem Neustart eindeutig neu erzeugt; verwaiste alte Marker werden vorher entfernt.
            world.getNearbyEntities(location, 2, 3, 2).stream().filter(e -> key.equals(e.getPersistentDataContainer().get(graveKey, PersistentDataType.STRING))).forEach(Entity::remove);
            createLoaded(id, owner, ownerName, location, yaml.getLong(path + "created-at"), items);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Grab konnte nicht geladen werden: " + key, ex);
        }
        purgeExpired();
    }

    private void createLoaded(UUID id, UUID owner, String ownerName, Location location, long createdAt, List<ItemStack> items) {
        World world = Objects.requireNonNull(location.getWorld());
        GraveInventory holder = new GraveInventory(id);
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatColor.DARK_GRAY + "Grab von " + ownerName);
        holder.inventory = inventory;
        items.stream().filter(Objects::nonNull).forEach(item -> inventory.addItem(item).values().forEach(left -> world.dropItemNaturally(location, left)));
        ArmorStand stand = world.spawn(location, ArmorStand.class, armor -> {
            armor.setInvisible(true); armor.setInvulnerable(true); armor.setGravity(false); armor.setSmall(true); armor.setBasePlate(false);
            armor.getPersistentDataContainer().set(graveKey, PersistentDataType.STRING, id.toString());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            var meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            meta.setOwnerProfile(Bukkit.createPlayerProfile(owner, ownerName));
            head.setItemMeta(meta); armor.getEquipment().setHelmet(head);
        });
        TextDisplay text = world.spawn(location.clone().add(0, 1.65, 0), TextDisplay.class, display -> {
            display.setText(ChatColor.AQUA + "Grab von " + ownerName);
            display.setBillboard(Display.Billboard.CENTER); display.setSeeThrough(true); display.setInvulnerable(true);
            display.getPersistentDataContainer().set(graveKey, PersistentDataType.STRING, id.toString());
        });
        graves.put(id, new Grave(id, owner, ownerName, location, createdAt, inventory, stand.getUniqueId(), text.getUniqueId()));
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
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
