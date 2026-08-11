package de.minecraft.rival.menu;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.data.PlayerRecord;
import de.minecraft.rival.game.ZoneManager;
import de.minecraft.rival.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MenuListener implements Listener {
    private static final Component CREDIT = Component.text("by pluginsmc.com", NamedTextColor.DARK_GRAY);
    private final RivalPlugin plugin;

    public MenuListener(RivalPlugin plugin) { this.plugin = plugin; }

    public void openHelp(Player player) {
        Inventory menu = createMenu("help", 45, "Minecraft Rival • Spielerhilfe", NamedTextColor.AQUA);
        menu.setItem(10, helpItem(Material.COMPASS, "Start & Warteraum",
            "Vor dem Start zeigt die Bossbar den Countdown.", "Ohne Seitenzuweisung bleibst du geschützt", "im festgelegten Warteraum."));
        menu.setItem(11, helpItem(Material.REDSTONE, "Projekt-Herzen & Combat",
            "Du startest mit 3 Projekt-Herzen.", "Die Grafik sitzt mittig direkt über der Hotbar.", "Spielerschaden markiert beide 30 Sekunden.", "Nur ein Tod im Combat kostet ein Herz."));
        menu.setItem(12, helpItem(Material.PLAYER_HEAD, "Gräber",
            "Jeder Tod sichert Inventar, Rüstung und Offhand.", "Rechtsklick: öffnen • Schleichen+Klick: löschen.", "Jeder darf zugreifen; Löschung nach 24 Stunden."));
        menu.setItem(13, helpItem(Material.CLOCK, "Tägliche Spielzeit",
            "/spielzeit zeigt gespielt und verbleibend.", "/spielzeit anzeige schaltet die Live-Bossbar um.", "Im Warteraum läuft keine Spielzeit ab."));
        menu.setItem(14, helpItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "Mitteltrennung",
            "Eine eigene Partikelwand trennt beide Seiten.", "Perle, Chorusfrucht, Boot und Teleports helfen nicht.", "Die normale Worldborder bleibt unverändert."));
        menu.setItem(15, helpItem(Material.WITHER_SKELETON_SKULL, "Erzfeind",
            "Vor dem Reveal siehst du ein schwarzes ?.", "Danach zeigt das HUD dein persönliches Ziel.", "Ein Combat-Kill gibt bis maximal 3 Herzen zurück."));
        menu.setItem(16, helpItem(Material.DRAGON_HEAD, "Endkampf",
            "Bei 2 verbleibenden Spielern ist das Finale bereit.", "Es startet ausschließlich durch einen Admin.", "Dann gilt auf der Mittelinsel eine echte 100×100-Border."));

        menu.setItem(19, helpItem(Material.NETHERRACK, "Nether- & End-Insel",
            "Beides sind X/Z-Zonen in derselben Hauptwelt.", "Die gesetzte Fläche gilt vertikal über alle Höhen.", "Portale, Gateways und Enderdrache sind deaktiviert."));
        menu.setItem(20, helpItem(Material.ZOMBIE_HEAD, "Mobs & Handel",
            "Mobs bleiben in Ursprungszone und Kartenseite.", "Spawnraten gelten getrennt für alle 3 Zonentypen.", "Villager, Wandering Trader und Handel sind deaktiviert."));
        menu.setItem(21, helpItem(Material.WHITE_BANNER, "Clans",
            "Ein Spieler kann nur einem Clan angehören.", "Standardmaximum: 4 Mitglieder.", "/clan help zeigt Gründen, Einladen und Verwalten."));
        menu.setItem(22, helpItem(Material.REDSTONE_TORCH, "YouTube-Modus",
            "/youtube fordert immer eine Bestätigung an.", "Währenddessen steht ʏᴏᴜᴛᴜʙᴇ unter deinem Namen.", "Erneutes /youtube beendet die Aufnahme bestätigt."));
        menu.setItem(23, helpItem(Material.WRITABLE_BOOK, "Regeln",
            "/rules oder /regeln zeigt alle Projektregeln.", "Regelnummern bleiben stabil, bis ein Admin sie löscht."));
        menu.setItem(24, helpItem(Material.COMMAND_BLOCK, "Wichtige Befehle",
            "/spielzeit [anzeige] • /clan help", "/youtube • /rules • /help"));
        menu.setItem(25, helpItem(Material.SPYGLASS, "Reduziertes F3",
            "F3 schaltet die kleine Projektanzeige um.", "Sichtbar sind nur XYZ, Clan und Restspielzeit.", "Das Vanilla-Debugfenster bleibt verborgen."));
        menu.setItem(40, creditItem());
        player.openInventory(menu);
    }

    public void openAdmin(Player player) {
        Inventory menu = createMenu("admin", 54, "Minecraft Rival • Administration", NamedTextColor.RED);
        boolean time = plugin.getConfig().getBoolean("playtime.enabled");
        boolean border = plugin.borders().isEnabled();
        menu.setItem(10, helpItem(time ? Material.LIME_DYE : Material.GRAY_DYE, "Spielzeit: " + onOff(time), "Klick: global umschalten"));
        menu.setItem(11, helpItem(Material.CLOCK, "Tageslimit: " + plugin.getConfig().getInt("playtime.daily-minutes") + " min", "Linksklick: +15 Minuten", "Rechtsklick: -15 Minuten"));
        menu.setItem(12, helpItem(border ? Material.LIGHT_BLUE_STAINED_GLASS : Material.GRAY_STAINED_GLASS,
            "Mitteltrennung: " + onOff(border), "Klick: umschalten", "Verändert die normale Worldborder nicht."));
        menu.setItem(13, helpItem(Material.WITHER_SKELETON_SKULL, "Erzfeinde aufdecken", "Klick: Ziele zufällig und eindeutig zuordnen"));
        menu.setItem(14, endFightItem());
        menu.setItem(15, helpItem(Material.ENDER_EYE, "Vanish", "Inventar ausfallsicher speichern", "Creative, unsichtbar, keine Spielzeit"));
        menu.setItem(16, helpItem(Material.BOOK, "Vollständige Admin-Hilfe", "Klick oder /admin help"));
        menu.setItem(17, helpItem(plugin.projects().isStarted() ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK,
            "Projekt: " + (plugin.projects().isStarted() ? "GESTARTET" : "WARTERAUM"), "Klick: Projekt starten oder stoppen"));

        menu.setItem(18, helpItem(Material.NETHERRACK, "Nether-Spawnrate: " + plugin.zones().spawnRate(ZoneManager.Zone.NETHER) + "%", "Links +10% • Rechts -10%"));
        menu.setItem(19, helpItem(Material.END_STONE, "End-Spawnrate: " + plugin.zones().spawnRate(ZoneManager.Zone.END) + "%", "Links +10% • Rechts -10%"));
        menu.setItem(20, helpItem(Material.GRASS_BLOCK, "Overworld-Spawnrate: " + plugin.zones().spawnRate(ZoneManager.Zone.OVERWORLD) + "%", "Links +10% • Rechts -10%"));
        menu.setItem(21, helpItem(Material.PLAYER_HEAD, "Aktive Gräber: " + plugin.graves().count(), "Klick: Löschbefehle anzeigen"));
        menu.setItem(22, helpItem(Material.BELL, "Admin-Broadcast", "/admin broadcast <Text>", "\\n = neue Zeile mit eigenem Prefix", "Ohne Admin-Modus: persistente Warteschlange"));
        menu.setItem(23, helpItem(Material.WRITABLE_BOOK, "Regelverwaltung", "/admin rules add|remove|list"));
        menu.setItem(24, helpItem(Material.IRON_BARS, "Moderation", "/admin ban|unban|warn|warnings", "Warnschwelle und Banndauer sind konfigurierbar."));
        menu.setItem(25, helpItem(Material.WHITE_BANNER, "Clanverwaltung", "/admin clan create|add|remove|owner", "/admin clan color|tag|info|disband"));
        menu.setItem(26, helpItem(Material.SPYGLASS, "Zentrale Spielerübersicht", "Klick: alle bekannten Spieler", "Status, Herzen, Seite, Clan, YouTube, Combat und mehr"));
        menu.setItem(27, helpItem(Material.CLOCK, "Playtime-Ranking", "Klick: heute gespielte Zeit sortiert", "/admin playtime ranking"));

        List<PlayerRecord> remaining = plugin.endFight().remainingPlayers();
        for (int i = 0; i < Math.min(17, remaining.size()); i++) menu.setItem(36 + i, remainingPlayer(remaining.get(i)));
        menu.setItem(53, creditItem());
        player.openInventory(menu);
    }

    public void openPlayerOverview(Player viewer, int requestedPage) {
        List<PlayerRecord> players = knownPlayers();
        int pages = Math.max(1, (players.size() + 44) / 45);
        int page = Math.max(0, Math.min(pages - 1, requestedPage));
        Inventory menu = createMenu("players:" + page, 54,
            "Rival • Spieler " + (page + 1) + "/" + pages, NamedTextColor.GOLD);
        int start = page * 45;
        for (int slot = 0; slot < 45 && start + slot < players.size(); slot++)
            menu.setItem(slot, playerStatusItem(players.get(start + slot)));
        if (page > 0) menu.setItem(45, helpItem(Material.ARROW, "Vorherige Seite", "Seite " + page));
        menu.setItem(48, helpItem(Material.PAPER, "Bekannte Spieler: " + players.size(), "Seite " + (page + 1) + " von " + pages));
        menu.setItem(49, helpItem(Material.BARRIER, "Zurück zur Administration", "Klick: Admin-Hauptmenü"));
        if (page + 1 < pages) menu.setItem(53, helpItem(Material.ARROW, "Nächste Seite", "Seite " + (page + 2)));
        else menu.setItem(53, creditItem());
        viewer.openInventory(menu);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || holder.id.equals("help")) return;
        if (!plugin.adminMode().isActive(player)) {
            player.closeInventory();
            Messages.error(player, "Der Admin-Modus ist nicht aktiv.");
            return;
        }
        if (holder.id.startsWith("players:")) {
            int page;
            try { page = Integer.parseInt(holder.id.substring("players:".length())); }
            catch (NumberFormatException ex) { page = 0; }
            if (event.getRawSlot() == 45) openPlayerOverview(player, page - 1);
            else if (event.getRawSlot() == 49) openAdmin(player);
            else if (event.getRawSlot() == 53 && (page + 1) * 45 < knownPlayers().size()) openPlayerOverview(player, page + 1);
            return;
        }
        switch (event.getRawSlot()) {
            case 10 -> {
                plugin.getConfig().set("playtime.enabled", !plugin.getConfig().getBoolean("playtime.enabled"));
                plugin.saveConfig();
                Bukkit.getOnlinePlayers().forEach(plugin.playtime()::refreshVisibility);
                openAdmin(player);
            }
            case 11 -> {
                int delta = event.isRightClick() ? -15 : 15;
                int value = Math.max(0, plugin.getConfig().getInt("playtime.daily-minutes", 180) + delta);
                plugin.getConfig().set("playtime.daily-minutes", value); plugin.saveConfig(); openAdmin(player);
            }
            case 12 -> { plugin.borders().setEnabled(!plugin.borders().isEnabled()); openAdmin(player); }
            case 13 -> { int count = plugin.combat().revealNemeses(); Messages.normal(player, count + " Erzfeinde wurden aufgedeckt."); }
            case 14 -> {
                if (plugin.endFight().isRunning()) plugin.endFight().stop();
                else if (!plugin.endFight().start()) plugin.endFight().showStatus(player);
                openAdmin(player);
            }
            case 15 -> { player.closeInventory(); plugin.vanish().toggle(player); }
            case 16 -> { player.closeInventory(); player.performCommand("admin help"); }
            case 17 -> {
                if (plugin.projects().isStarted()) plugin.projects().stop();
                else if (!plugin.projects().start(true)) Messages.error(player, "Start nicht möglich: Prüfe Warteraum, Zonen, Zuweisungen und Spawns.");
                openAdmin(player);
            }
            case 18 -> adjustRate(player, ZoneManager.Zone.NETHER, event.isRightClick() ? -10 : 10);
            case 19 -> adjustRate(player, ZoneManager.Zone.END, event.isRightClick() ? -10 : 10);
            case 20 -> adjustRate(player, ZoneManager.Zone.OVERWORLD, event.isRightClick() ? -10 : 10);
            case 21 -> { player.closeInventory(); Messages.normal(player, "Gräber: /admin graves count|deleteall|near|player"); }
            case 22 -> { player.closeInventory(); Messages.normal(player, "Broadcast: /admin broadcast <Text> • Zeilenumbruch: \\n"); }
            case 23 -> { player.closeInventory(); Messages.normal(player, "Regeln: /admin rules add <Text> | remove <ID> | list"); }
            case 24 -> { player.closeInventory(); Messages.normal(player, "Moderation: /admin ban|unban|warn|warnings • Details: /admin help"); }
            case 25 -> { player.closeInventory(); Messages.normal(player, "Clans: /admin clan create|add|remove|owner|color|tag|info|disband"); }
            case 26 -> openPlayerOverview(player, 0);
            case 27 -> { player.closeInventory(); player.performCommand("admin playtime ranking"); }
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof MenuHolder) event.setCancelled(true);
    }

    private ItemStack endFightItem() {
        List<String> lore = new ArrayList<>();
        lore.add("Verbleibende Spieler: " + plugin.endFight().remainingPlayers().size());
        lore.add(plugin.endFight().isRunning() ? "Klick: beenden und normale Border wiederherstellen" : "Start nur bei exakt 2 Spielern");
        lore.add("Status: /admin endfight status");
        return helpItem(Material.DRAGON_HEAD, "Endkampf: " + (plugin.endFight().isRunning() ? "AKTIV" : "BEREIT/MANUELL"), lore.toArray(String[]::new));
    }

    private static ItemStack remainingPlayer(PlayerRecord record) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwnerProfile(Bukkit.createPlayerProfile(record.uuid(), record.lastName().equals("?") ? "Unbekannt" : record.lastName()));
        meta.displayName(Component.text(record.lastName(), NamedTextColor.GOLD));
        meta.lore(List.of(Component.text(record.hearts() + (record.hearts() == 1 ? " Herz" : " Herzen"), NamedTextColor.GRAY), CREDIT));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack playerStatusItem(PlayerRecord record) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        String name = record.lastName().equals("?") ? "Unbekannt" : record.lastName();
        meta.setOwnerProfile(Bukkit.createPlayerProfile(record.uuid(), name));
        boolean online = Bukkit.getPlayer(record.uuid()) != null;
        boolean alive = !record.eliminated() && record.hearts() > 0;
        String state = !alive ? "AUSGESCHIEDEN" : record.side() == 0 ? "NICHT ZUGEWIESEN" : "IM SPIEL";
        NamedTextColor stateColor = !alive ? NamedTextColor.RED : record.side() == 0 ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
        meta.displayName(Component.text(name, alive ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Status: ", NamedTextColor.GRAY).append(Component.text(state, stateColor)));
        lore.add(Component.text("Verbindung: " + (online ? "ONLINE" : "OFFLINE"), online ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        lore.add(Component.text("Herzen: " + record.hearts() + "/3", NamedTextColor.GRAY));
        lore.add(Component.text("Seite: " + (record.side() < 0 ? "NEGATIV" : record.side() > 0 ? "POSITIV" : "KEINE"), NamedTextColor.GRAY));
        var clan = plugin.clans().clan(record.uuid());
        lore.add(Component.text("Clan: " + (clan == null ? "–" : clan.name() + " [" + clan.tag() + "]"), NamedTextColor.GRAY));
        lore.add(Component.text("YouTube: " + (plugin.youtube().isActive(record.uuid()) ? "AKTIV" : "AUS"),
            plugin.youtube().isActive(record.uuid()) ? NamedTextColor.RED : NamedTextColor.GRAY));
        int combat = plugin.combat().remainingSeconds(record.uuid());
        lore.add(Component.text("Combat: " + (combat > 0 ? combat + "s" : "AUS"), combat > 0 ? NamedTextColor.RED : NamedTextColor.GRAY));
        Player onlinePlayer = Bukkit.getPlayer(record.uuid());
        lore.add(Component.text("Admin/Vanish: " + (onlinePlayer != null && plugin.adminMode().isActive(onlinePlayer) ? "ADMIN" : "AUS")
            + (onlinePlayer != null && plugin.vanish().isVanished(onlinePlayer) ? " + VANISH" : ""), NamedTextColor.GRAY));
        lore.add(Component.text("Heute gespielt: " + plugin.playtime().formattedPlayed(record), NamedTextColor.GRAY));
        lore.add(Component.text("Spielzeit übrig: " + plugin.playtime().formatted(record), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(CREDIT);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private List<PlayerRecord> knownPlayers() {
        return plugin.data().players().stream()
            .sorted(Comparator.comparing(PlayerRecord::lastName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private static ItemStack helpItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(Component.text(line, NamedTextColor.GRAY));
        lines.add(Component.empty());
        lines.add(CREDIT);
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack creditItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(CREDIT);
        meta.lore(List.of(Component.text("Minecraft Rival Plugin & Mod", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private static Inventory createMenu(String id, int size, String title, NamedTextColor color) {
        MenuHolder holder = new MenuHolder(id);
        Inventory inventory = Bukkit.createInventory(holder, size, Component.text(title, color));
        holder.inventory = inventory;
        return inventory;
    }

    private void adjustRate(Player player, ZoneManager.Zone zone, int delta) {
        plugin.zones().setSpawnRate(zone, Math.max(0, Math.min(100, plugin.zones().spawnRate(zone) + delta)));
        openAdmin(player);
    }

    private static String onOff(boolean value) { return value ? "AN" : "AUS"; }

    private static final class MenuHolder implements InventoryHolder {
        private final String id;
        private Inventory inventory;
        private MenuHolder(String id) { this.id = id; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
