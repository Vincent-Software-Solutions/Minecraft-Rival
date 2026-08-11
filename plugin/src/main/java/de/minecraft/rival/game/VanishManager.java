package de.minecraft.rival.game;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public final class VanishManager implements Listener {
    private final RivalPlugin plugin;
    private final File file;
    private final Set<UUID> vanished = new HashSet<>();
    private final Map<UUID, Snapshot> snapshots = new HashMap<>();

    public VanishManager(RivalPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "admin-snapshots.yml");
        loadSnapshots();
    }

    public boolean isVanished(Player player) { return vanished.contains(player.getUniqueId()); }

    public void toggle(Player player) {
        if (isVanished(player)) leave(player, true);
        else enter(player);
    }

    private void enter(Player player) {
        plugin.youtube().disableSilently(player);
        Snapshot snapshot = new Snapshot(clone(player.getInventory().getContents()), player.getGameMode(), player.getLevel(), player.getExp(),
            player.getTotalExperience(), player.getHealth(), player.getFoodLevel(), player.getSaturation(), player.getAllowFlight(),
            player.isFlying(), player.isInvulnerable(), player.isCollidable());
        snapshots.put(player.getUniqueId(), snapshot);
        if (!saveSnapshots()) {
            snapshots.remove(player.getUniqueId());
            Messages.error(player, "Vanish konnte nicht sicher aktiviert werden: Statusdatei nicht speicherbar.");
            return;
        }
        player.getInventory().clear();
        player.setGameMode(GameMode.CREATIVE);
        player.setInvulnerable(true);
        player.setCollidable(false);
        vanished.add(player.getUniqueId());
        for (Player other : Bukkit.getOnlinePlayers()) if (!plugin.adminMode().isActive(other)) other.hidePlayer(plugin, player);
        Messages.normal(player, "Vanish aktiviert. Inventar und Status wurden ausfallsicher gespeichert.");
    }

    private void leave(Player player, boolean message) {
        Snapshot snapshot = snapshots.get(player.getUniqueId());
        vanished.remove(player.getUniqueId());
        for (Player other : Bukkit.getOnlinePlayers()) other.showPlayer(plugin, player);
        if (snapshot != null) {
            apply(player, snapshot);
            snapshots.remove(player.getUniqueId());
            saveSnapshots();
        } else {
            player.setInvulnerable(false);
            player.setCollidable(true);
        }
        if (message) Messages.normal(player, "Vanish deaktiviert. Dein vorheriger Status wurde wiederhergestellt.");
    }

    private static void apply(Player player, Snapshot snapshot) {
        player.getInventory().setContents(snapshot.inventory);
        player.setGameMode(snapshot.gameMode);
        player.setLevel(snapshot.level);
        player.setExp(snapshot.exp);
        player.setTotalExperience(snapshot.totalExperience);
        player.setHealth(Math.max(0.01, Math.min(snapshot.health, player.getMaxHealth())));
        player.setFoodLevel(snapshot.food);
        player.setSaturation(snapshot.saturation);
        player.setAllowFlight(snapshot.allowFlight);
        player.setFlying(snapshot.allowFlight && snapshot.flying);
        player.setInvulnerable(snapshot.invulnerable);
        player.setCollidable(snapshot.collidable);
    }

    public void disable(Player player) {
        if (isVanished(player)) leave(player, false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Snapshot interrupted = snapshots.get(event.getPlayer().getUniqueId());
        if (interrupted != null && !isVanished(event.getPlayer())) {
            apply(event.getPlayer(), interrupted);
            snapshots.remove(event.getPlayer().getUniqueId());
            saveSnapshots();
            Messages.normal(event.getPlayer(), "Dein Admin-Inventar wurde nach einem unterbrochenen Vanish sicher wiederhergestellt.");
        }
        for (UUID id : vanished) {
            Player hidden = Bukkit.getPlayer(id);
            if (hidden != null && !plugin.adminMode().isActive(event.getPlayer())) event.getPlayer().hidePlayer(plugin, hidden);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (isVanished(event.getPlayer())) leave(event.getPlayer(), false);
    }

    public void restoreAll() {
        for (UUID id : new HashSet<>(vanished)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) leave(player, false);
        }
    }

    private void loadSnapshots() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("snapshots");
        if (root == null) return;
        for (String key : root.getKeys(false)) try {
            UUID uuid = UUID.fromString(key);
            String path = "snapshots." + key + ".";
            int size = Math.max(0, Math.min(100, yaml.getInt(path + "inventory-size", 41)));
            ItemStack[] inventory = new ItemStack[size];
            ConfigurationSection items = yaml.getConfigurationSection(path + "inventory");
            if (items != null) for (String slot : items.getKeys(false)) {
                int index = Integer.parseInt(slot);
                if (index >= 0 && index < size) inventory[index] = items.getItemStack(slot);
            }
            snapshots.put(uuid, new Snapshot(inventory, GameMode.valueOf(yaml.getString(path + "game-mode", "SURVIVAL")),
                yaml.getInt(path + "level"), (float) yaml.getDouble(path + "exp"), yaml.getInt(path + "total-experience"),
                yaml.getDouble(path + "health", 20), yaml.getInt(path + "food", 20), (float) yaml.getDouble(path + "saturation", 5),
                yaml.getBoolean(path + "allow-flight"), yaml.getBoolean(path + "flying"), yaml.getBoolean(path + "invulnerable"),
                yaml.getBoolean(path + "collidable", true)));
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Admin-Snapshot konnte nicht geladen werden: " + key, ex);
        }
    }

    private synchronized boolean saveSnapshots() {
        YamlConfiguration yaml = new YamlConfiguration();
        snapshots.forEach((uuid, snapshot) -> {
            String path = "snapshots." + uuid + ".";
            yaml.set(path + "inventory-size", snapshot.inventory.length);
            for (int i = 0; i < snapshot.inventory.length; i++) if (snapshot.inventory[i] != null)
                yaml.set(path + "inventory." + i, snapshot.inventory[i]);
            yaml.set(path + "game-mode", snapshot.gameMode.name()); yaml.set(path + "level", snapshot.level);
            yaml.set(path + "exp", snapshot.exp); yaml.set(path + "total-experience", snapshot.totalExperience);
            yaml.set(path + "health", snapshot.health); yaml.set(path + "food", snapshot.food); yaml.set(path + "saturation", snapshot.saturation);
            yaml.set(path + "allow-flight", snapshot.allowFlight); yaml.set(path + "flying", snapshot.flying);
            yaml.set(path + "invulnerable", snapshot.invulnerable); yaml.set(path + "collidable", snapshot.collidable);
        });
        try {
            yaml.save(file);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "admin-snapshots.yml konnte nicht gespeichert werden", ex);
            return false;
        }
    }

    private static ItemStack[] clone(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) copy[i] = source[i] == null ? null : source[i].clone();
        return copy;
    }

    private record Snapshot(ItemStack[] inventory, GameMode gameMode, int level, float exp, int totalExperience,
                            double health, int food, float saturation, boolean allowFlight, boolean flying,
                            boolean invulnerable, boolean collidable) {}
}
