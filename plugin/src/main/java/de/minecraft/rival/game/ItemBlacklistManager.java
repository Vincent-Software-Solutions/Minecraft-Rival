package de.minecraft.rival.game;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Persistent material blacklist enforced on every player inventory boundary. */
public final class ItemBlacklistManager implements Listener {
    private final RivalPlugin plugin;
    private final Set<Material> blocked = EnumSet.noneOf(Material.class);

    public ItemBlacklistManager(RivalPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        blocked.clear();
        for (String value : plugin.getConfig().getStringList("item-blacklist.materials")) {
            Material material = Material.matchMaterial(value);
            if (material != null && !material.isAir() && material.isItem()) blocked.add(material);
        }
        purgeAll();
    }

    public void enable() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::purgeAll, 20L, 20L);
    }

    public boolean add(Material material) {
        if (material == null || material.isAir() || !material.isItem() || !blocked.add(material)) return false;
        save();
        purgeAll();
        return true;
    }

    public boolean remove(Material material) {
        if (material == null || !blocked.remove(material)) return false;
        save();
        return true;
    }

    public boolean isBlocked(Material material) { return material != null && blocked.contains(material); }

    public List<Material> materials() {
        return blocked.stream().sorted(Comparator.comparing(Enum::name)).toList();
    }

    public void clear() {
        blocked.clear();
        save();
    }

    private void save() {
        plugin.getConfig().set("item-blacklist.materials", materials().stream().map(Material::name).toList());
        plugin.saveConfig();
    }

    private void purgeAll() {
        Bukkit.getOnlinePlayers().forEach(this::purge);
        Bukkit.getWorlds().forEach(world -> world.getEntitiesByClass(Item.class).stream()
            .filter(item -> isBlocked(item.getItemStack().getType())).forEach(Item::remove));
    }

    public int purge(Player player) {
        int removed = 0;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null && isBlocked(item.getType())) {
                removed += item.getAmount();
                player.getInventory().setItem(slot, null);
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && isBlocked(cursor.getType())) {
            removed += cursor.getAmount();
            player.setItemOnCursor(null);
        }
        return removed;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) { Bukkit.getScheduler().runTask(plugin, () -> purge(event.getPlayer())); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (!isBlocked(event.getItem().getItemStack().getType())) return;
        event.setCancelled(true);
        event.getItem().remove();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (!isBlocked(event.getItemDrop().getItemStack().getType())) return;
        event.getItemDrop().remove();
        Messages.error(event.getPlayer(), "Ein gesperrter Gegenstand wurde gelöscht.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        if (!isBlocked(event.getItemInHand().getType())) return;
        event.setCancelled(true);
        removeHeldStack(event.getPlayer(), event.getItemInHand().getType());
        Messages.error(event.getPlayer(), "Dieser Block ist gesperrt und wurde gelöscht.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (!blocked(item)) return;
        event.setCancelled(true);
        removeHeldStack(event.getPlayer(), item.getType());
        Messages.error(event.getPlayer(), "Dieser Gegenstand ist gesperrt und wurde gelöscht.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ItemStack hotbar = event.getHotbarButton() >= 0 ? player.getInventory().getItem(event.getHotbarButton()) : null;
        if (!blocked(current) && !blocked(cursor) && !blocked(hotbar)) return;
        event.setCancelled(true);
        if (blocked(current)) event.setCurrentItem(null);
        if (blocked(cursor)) player.setItemOnCursor(null);
        if (blocked(hotbar)) player.getInventory().setItem(event.getHotbarButton(), null);
        Bukkit.getScheduler().runTask(plugin, () -> purge(player));
        Messages.error(player, "Gesperrte Gegenstände können nicht verschoben werden und wurden gelöscht.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !blocked(event.getOldCursor())) return;
        event.setCancelled(true);
        player.setItemOnCursor(null);
        Bukkit.getScheduler().runTask(plugin, () -> purge(player));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(InventoryMoveItemEvent event) {
        if (!blocked(event.getItem())) return;
        event.setCancelled(true);
        event.getSource().removeItem(event.getItem());
    }

    private boolean blocked(ItemStack item) { return item != null && isBlocked(item.getType()); }

    private void removeHeldStack(Player player, Material material) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main.getType() == material) player.getInventory().setItemInMainHand(null);
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off.getType() == material) player.getInventory().setItemInOffHand(null);
    }

    public static Material parse(String value) {
        return Material.matchMaterial(value == null ? "" : value.toUpperCase(Locale.ROOT));
    }
}
