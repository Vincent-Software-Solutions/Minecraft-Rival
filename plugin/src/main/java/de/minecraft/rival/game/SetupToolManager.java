package de.minecraft.rival.game;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.util.Messages;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Admin setup wand for all location-based project settings. */
public final class SetupToolManager implements Listener {
    public enum Mode {
        WAITING("Warteraum", Material.CLOCK),
        NETHER_1("Nether-Insel • Ecke 1", Material.NETHERRACK),
        NETHER_2("Nether-Insel • Ecke 2", Material.NETHER_BRICKS),
        END_1("End-Insel • Ecke 1", Material.END_STONE),
        END_2("End-Insel • Ecke 2", Material.END_STONE_BRICKS),
        BORDER_X("Mittel-Border • X-Linie", Material.LIGHT_BLUE_STAINED_GLASS),
        BORDER_Z("Mittel-Border • Z-Linie", Material.CYAN_STAINED_GLASS),
        SPAWN_NEGATIVE("Spawn • negative Seite", Material.BLUE_BED),
        SPAWN_POSITIVE("Spawn • positive Seite", Material.RED_BED),
        FINAL_CENTER("Finale • Mittelinsel", Material.DRAGON_HEAD);

        private final String title;
        private final Material icon;
        Mode(String title, Material icon) { this.title = title; this.icon = icon; }
        public String title() { return title; }
        public Material icon() { return icon; }
        public Mode next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private final RivalPlugin plugin;
    private final NamespacedKey modeKey;

    public SetupToolManager(RivalPlugin plugin) {
        this.plugin = plugin;
        this.modeKey = new NamespacedKey(plugin, "setup_wand_mode");
    }

    public void give(Player player, Mode mode) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack existing = player.getInventory().getItem(slot);
            if (isWand(existing)) player.getInventory().setItem(slot, null);
        }
        ItemStack wand = wand(mode);
        int slot = player.getInventory().firstEmpty();
        if (slot < 0) {
            player.getWorld().dropItemNaturally(player.getLocation(), wand);
            Messages.error(player, "Inventar voll: Der Setup-Stick wurde vor dir abgelegt.");
        } else player.getInventory().setItem(slot, wand);
        Messages.normal(player, "Setup-Stick: " + mode.title() + ". Rechtsklick setzt, Linksklick wechselt den Modus.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isWand(event.getItem())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!plugin.adminMode().isActive(player)) {
            Messages.error(player, "Der Setup-Stick funktioniert nur im aktiven Admin-Modus.");
            return;
        }
        Mode mode = mode(event.getItem());
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            give(player, mode.next());
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Location selected = event.getClickedBlock() == null
            ? player.getLocation().clone()
            : event.getClickedBlock().getLocation().add(.5, 1, .5);
        try {
            apply(player, mode, selected);
            player.getInventory().setItemInMainHand(wand(mode));
        } catch (IllegalArgumentException ex) {
            Messages.error(player, ex.getMessage());
        }
    }

    private void apply(Player player, Mode mode, Location location) {
        if (!plugin.isMainWorld(location.getWorld()))
            throw new IllegalArgumentException("Wechsle zum Einrichten zuerst in die Projektwelt.");
        switch (mode) {
            case WAITING -> plugin.projects().setWaitingRoom(location);
            case NETHER_1 -> plugin.zones().setCorner(ZoneManager.Zone.NETHER, 1, location);
            case NETHER_2 -> plugin.zones().setCorner(ZoneManager.Zone.NETHER, 2, location);
            case END_1 -> plugin.zones().setCorner(ZoneManager.Zone.END, 1, location);
            case END_2 -> plugin.zones().setCorner(ZoneManager.Zone.END, 2, location);
            case BORDER_X, BORDER_Z -> {
                boolean x = mode == Mode.BORDER_X;
                plugin.getConfig().set("border.axis", x ? "X" : "Z");
                plugin.getConfig().set("border.split-coordinate", x ? location.getX() : location.getZ());
                plugin.getConfig().set("setup.completed." + (x ? "border_z" : "border_x"), false);
                plugin.saveConfig();
            }
            case SPAWN_NEGATIVE -> plugin.projects().addSpawn(-1, location);
            case SPAWN_POSITIVE -> plugin.projects().addSpawn(1, location);
            case FINAL_CENTER -> {
                plugin.getConfig().set("end-fight.center-x", location.getX());
                plugin.getConfig().set("end-fight.center-y", location.getY());
                plugin.getConfig().set("end-fight.center-z", location.getZ());
                plugin.saveConfig();
            }
        }
        plugin.getConfig().set("setup.completed." + mode.name().toLowerCase(Locale.ROOT), true);
        plugin.saveConfig();
        Messages.normal(player, mode.title() + " wurde gesetzt: " + coordinates(location)
            + (mode.name().startsWith("NETHER") || mode.name().startsWith("END") ? " • gilt auf allen Höhen" : ""));
    }

    public boolean isConfigured(Mode mode) {
        return switch (mode) {
            case WAITING -> plugin.projects().waitingRoom() != null;
            case NETHER_1 -> plugin.getConfig().getLocation("zones.nether.pos1") != null;
            case NETHER_2 -> plugin.getConfig().getLocation("zones.nether.pos2") != null;
            case END_1 -> plugin.getConfig().getLocation("zones.end.pos1") != null;
            case END_2 -> plugin.getConfig().getLocation("zones.end.pos2") != null;
            case BORDER_X, BORDER_Z -> plugin.getConfig().getBoolean(
                "setup.completed." + mode.name().toLowerCase(Locale.ROOT), false);
            case SPAWN_NEGATIVE -> !plugin.projects().spawns(-1).isEmpty();
            case SPAWN_POSITIVE -> !plugin.projects().spawns(1).isEmpty();
            case FINAL_CENTER -> plugin.getConfig().getBoolean("setup.completed.final_center", false);
        };
    }

    public boolean isWand(ItemStack item) {
        return item != null && item.getType() == Material.STICK && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(modeKey, PersistentDataType.STRING);
    }

    private Mode mode(ItemStack item) {
        String value = item.getItemMeta().getPersistentDataContainer().get(modeKey, PersistentDataType.STRING);
        try { return Mode.valueOf(value == null ? Mode.WAITING.name() : value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return Mode.WAITING; }
    }

    private ItemStack wand(Mode mode) {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Rival Setup-Stick" + ChatColor.DARK_GRAY + " • " + ChatColor.AQUA + mode.title());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Rechtsklick: aktuelle Position setzen");
        lore.add(ChatColor.GRAY + "Linksklick: nächsten Modus auswählen");
        lore.add(ChatColor.DARK_GRAY + "Nur im Admin-Modus verwendbar");
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "by pluginsmc.com");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, mode.name());
        item.setItemMeta(meta);
        return item;
    }

    private static String coordinates(Location location) {
        return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }
}
