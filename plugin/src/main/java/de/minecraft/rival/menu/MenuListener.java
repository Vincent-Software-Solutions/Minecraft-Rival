package de.minecraft.rival.menu;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.data.PlayerRecord;
import de.minecraft.rival.game.ZoneManager;
import de.minecraft.rival.game.SetupToolManager;
import de.minecraft.rival.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
    private static final String CREDIT = ChatColor.DARK_GRAY + "by pluginsmc.com";
    private final RivalPlugin plugin;

    public MenuListener(RivalPlugin plugin) { this.plugin = plugin; }

    public void openHelp(Player player) {
        Inventory menu = createMenu("help", 54, "Rival • Spielerhilfe", ChatColor.AQUA);
        menu.setItem(10, helpItem(Material.COMPASS, "Projektstart & Warteraum",
            "Vor dem Start zeigt die Bossbar den Countdown.",
            "Ohne Seitenzuweisung bleibst du geschützt", "im Warteraum, bis das Projekt beginnt."));
        menu.setItem(12, helpItem(Material.REDSTONE, "Herzen, Combat & Gräber",
            "Du startest mit 3 Projekt-Herzen.", "Die Grafik sitzt mittig direkt über der Hotbar.", "Spielerschaden markiert beide 30 Sekunden.", "Nur ein Tod im Combat kostet ein Herz."));
        menu.setItem(14, helpItem(Material.CLOCK, "Spielzeit & Anzeigen",
            "/spielzeit zeigt gespielt und verbleibend.", "/spielzeit anzeige schaltet die Live-Bossbar um.", "Im Warteraum läuft keine Spielzeit ab."));
        menu.setItem(16, helpItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "Karte, Inseln & Border",
            "Eine eigene Partikelwand trennt beide Seiten.", "Perle, Chorusfrucht, Boot und Teleports helfen nicht.", "Die normale Worldborder bleibt unverändert."));
        menu.setItem(20, helpItem(Material.WITHER_SKELETON_SKULL, "Erzfeind & Endkampf",
            "Vor dem Reveal siehst du ein schwarzes ?.", "Danach zeigt das HUD dein persönliches Ziel.", "Ein Combat-Kill gibt bis maximal 3 Herzen zurück."));
        menu.setItem(22, helpItem(Material.WHITE_BANNER, "Clans & YouTube",
            "Du kannst immer nur einem Clan angehören.", "/clan help erklärt alle Clan-Funktionen.",
            "/youtube startet oder beendet nach Bestätigung."));
        menu.setItem(24, helpItem(Material.NETHERRACK, "Inseln, Mobs & Portale",
            "Nether und End sind Zonen derselben Welt.", "Portale, Gateways, Villager-Handel und Drache sind aus.",
            "Mobs können Insel- und Seitengrenzen nicht verlassen."));
        menu.setItem(28, helpItem(Material.PLAYER_HEAD, "Grab-Schutz & Freigabe",
            "Jeder Tod sichert Inventar, Rüstung und Offhand.",
            "8 Minuten darf nur der Besitzer zugreifen.",
            "Danach ist das Grab für alle freigegeben.",
            "Leer: sofort weg + Nachricht • sonst 24 Stunden."));
        menu.setItem(30, helpItem(Material.FILLED_MAP, "Eingefrorene Weltkarte",
            "J öffnet die zoombare Übersicht.", "Die Karte ändert sich nur nach einem Admin-Update.",
            "Deine aktuelle Position wird darauf markiert."));
        menu.setItem(32, helpItem(Material.SPYGLASS, "Reduziertes F3",
            "F3 schaltet die kleine Projektanzeige um.", "Sichtbar sind nur XYZ, Clan sowie gespielt/übrig.",
            "Das Vanilla-Debugfenster bleibt verborgen."));
        menu.setItem(34, helpItem(Material.WRITABLE_BOOK, "Regeln & Befehle",
            "/rules oder /regeln zeigt die Projektregeln.",
            "/spielzeit [anzeige] • /clan help", "/youtube • /help"));
        menu.setItem(49, creditItem());
        player.openInventory(menu);
    }

    public void openAdmin(Player player) {
        Inventory menu = createMenu("admin", 45, "Rival • Spielleitung", ChatColor.RED);
        boolean active = plugin.adminMode().isActive(player);
        menu.setItem(4, helpItem(active ? Material.LIME_DYE : Material.RED_DYE,
            "Admin-Modus: " + onOff(active), "Klick: Admin-Modus " + (active ? "verlassen" : "aktivieren"),
            active ? "Alle Bereiche sind freigeschaltet." : "Aktivieren, um Änderungen vorzunehmen."));
        menu.setItem(11, helpItem(Material.STICK, "1 • Karte einrichten",
            "Warteraum, Inseln, Trennlinie und Spawns."));
        menu.setItem(13, helpItem(Material.PLAYER_HEAD, "2 • Spieler verwalten",
            "Status, Herzen, Seiten und Spielzeit."));
        menu.setItem(15, helpItem(Material.NETHER_STAR, "3 • Spiel steuern",
            "Start, Border, Erzfeinde und Endkampf."));
        menu.setItem(29, helpItem(Material.COMPARATOR, "Einstellungen",
            "Spielzeit und Mob-Spawnraten anpassen."));
        menu.setItem(31, helpItem(Material.ENDER_EYE, "Teamwerkzeuge",
            "Vanish, Gräber, Blacklist, Broadcast und Ranking."));
        menu.setItem(33, helpItem(Material.WRITABLE_BOOK, "Admin-Hilfe",
            "Alle Befehle nach Kategorien anzeigen."));
        menu.setItem(40, creditItem());
        player.openInventory(menu);
    }

    public void openAdminHelp(Player player) {
        Inventory menu = createMenu("adminhelp", 45, "Rival • Admin-Handbuch", ChatColor.RED);
        menu.setItem(10, helpItem(Material.LEVER, "Projektsteuerung",
            "/admin project start|stop|schedule", "/admin border on|off|toggle",
            "/admin erzfeind • /admin endfight status|start|stop"));
        menu.setItem(12, helpItem(Material.COMPASS, "Karte & Einrichtung",
            "/admin setup öffnet den Setup-Fortschritt.", "/admin worldmap update aktualisiert den Kartenstand.",
            "Der Setup-Stick setzt Punkte ohne Koordinatenbefehle."));
        menu.setItem(14, helpItem(Material.PLAYER_HEAD, "Spieler & Herzen",
            "/admin players öffnet die Spielerzentrale.", "Rechtsklick auf Kopf: direkt zur Herzverwaltung.",
            "/admin player hearts|side|revive|eliminate|timereset"));
        menu.setItem(16, helpItem(Material.BARRIER, "Item-Blacklist",
            "/admin blacklist öffnet das GUI.", "add|remove|list|clear [Material]",
            "Gesperrte Items werden beim Aufheben, Verschieben und Platzieren gelöscht."));
        menu.setItem(20, helpItem(Material.CLOCK, "Spielzeit & Mobs",
            "/admin playtime ranking", "/admin mobrate nether|end|overworld 0-100",
            "Weitere Werte: /admin config <Pfad> <Wert>"));
        menu.setItem(22, helpItem(Material.ENDER_EYE, "Teamwerkzeuge",
            "/admin vanish • /admin graves …", "Grab-Schutz für Besitzer: 8 Minuten.", "/admin broadcast <Text>",
            "/admin rules add|remove|list"));
        menu.setItem(24, helpItem(Material.IRON_SWORD, "Moderation",
            "/admin ban <Spieler> <Dauer|permanent> <Grund>", "/admin unban <Spieler>",
            "/admin warn … • /admin warnings …"));
        menu.setItem(31, backItem());
        menu.setItem(40, creditItem());
        player.openInventory(menu);
    }

    public void openGameControl(Player player) {
        Inventory menu = createMenu("game", 45, "Rival • Spiel steuern", ChatColor.RED);
        menu.setItem(10, helpItem(plugin.projects().isStarted() ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK,
            "Projekt " + (plugin.projects().isStarted() ? "stoppen" : "starten"),
            plugin.projects().isStarted() ? "Alle Spieler zurück in den Warteraum." : "Einrichtung prüfen und Spiel freigeben."));
        menu.setItem(12, helpItem(Material.WITHER_SKELETON_SKULL, "Erzfeinde aufdecken",
            "Persönliche Ziele an aktive Spieler verteilen."));
        menu.setItem(14, helpItem(plugin.borders().isEnabled() ? Material.LIGHT_BLUE_STAINED_GLASS : Material.GRAY_STAINED_GLASS,
            "Mittel-Border: " + onOff(plugin.borders().isEnabled()), "Klick: sicher umschalten.",
            "Die normale Worldborder bleibt unberührt."));
        menu.setItem(16, endFightItem());
        menu.setItem(22, helpItem(Material.COMPASS, "Aktueller Status",
            "Projekt: " + (plugin.projects().isStarted() ? "GESTARTET" : "WARTERAUM"),
            "Verbleibende Spieler: " + plugin.endFight().remainingPlayers().size()));
        menu.setItem(31, backItem());
        menu.setItem(40, creditItem());
        player.openInventory(menu);
    }

    public void openSettings(Player player) {
        Inventory menu = createMenu("settings", 45, "Rival • Einstellungen", ChatColor.GOLD);
        boolean time = plugin.getConfig().getBoolean("playtime.enabled");
        menu.setItem(10, helpItem(time ? Material.CLOCK : Material.GRAY_DYE,
            "Spielzeit: " + onOff(time) + " • " + plugin.getConfig().getInt("playtime.daily-minutes") + " min",
            "Links: an/aus • Rechts: +15 Minuten", "Shift+Rechts: -15 Minuten"));
        menu.setItem(12, helpItem(Material.NETHERRACK, "Nether-Mobs: " + plugin.zones().spawnRate(ZoneManager.Zone.NETHER) + "%", "Links +10% • Rechts -10%"));
        menu.setItem(13, helpItem(Material.END_STONE, "End-Mobs: " + plugin.zones().spawnRate(ZoneManager.Zone.END) + "%", "Links +10% • Rechts -10%"));
        menu.setItem(14, helpItem(Material.GRASS_BLOCK, "Overworld-Mobs: " + plugin.zones().spawnRate(ZoneManager.Zone.OVERWORLD) + "%", "Links +10% • Rechts -10%"));
        menu.setItem(31, backItem());
        menu.setItem(40, creditItem());
        player.openInventory(menu);
    }

    public void openTeamTools(Player player) {
        Inventory menu = createMenu("tools", 45, "Rival • Teamwerkzeuge", ChatColor.DARK_PURPLE);
        menu.setItem(10, helpItem(Material.ENDER_EYE, "Vanish", "Creative-Vanish umschalten.", "Inventar und Spielzeit bleiben geschützt."));
        menu.setItem(12, helpItem(Material.PLAYER_HEAD, "Gräber: " + plugin.graves().count(),
            "Verwaltungsbefehle anzeigen.", "Erste 8 Minuten nur für den Besitzer."));
        menu.setItem(14, helpItem(Material.BELL, "Broadcast", "Syntax für Ankündigungen anzeigen."));
        menu.setItem(16, helpItem(Material.CLOCK, "Spielzeit-Ranking", "Heutiges Ranking im Chat anzeigen."));
        menu.setItem(22, helpItem(Material.BARRIER, "Item-Blacklist • " + plugin.blacklist().materials().size(),
            "Gesperrte Gegenstände verwalten.", "Platzieren, Aufheben und Verschieben wird verhindert."));
        menu.setItem(31, backItem());
        menu.setItem(40, creditItem());
        player.openInventory(menu);
    }

    public void openSetup(Player player) {
        Inventory menu = createMenu("setup", 45, "Rival • Karte einrichten", ChatColor.GOLD);
        menu.setItem(10, setupItem(SetupToolManager.Mode.WAITING, "Punkt für den geschützten Warteraum."));
        menu.setItem(12, setupItem(SetupToolManager.Mode.NETHER_1, "Erste X/Z-Ecke; Höhe wird ignoriert."));
        menu.setItem(13, setupItem(SetupToolManager.Mode.NETHER_2, "Zweite X/Z-Ecke; Höhe wird ignoriert."));
        menu.setItem(15, setupItem(SetupToolManager.Mode.END_1, "Erste X/Z-Ecke; Höhe wird ignoriert."));
        menu.setItem(16, setupItem(SetupToolManager.Mode.END_2, "Zweite X/Z-Ecke; Höhe wird ignoriert."));
        menu.setItem(19, setupItem(SetupToolManager.Mode.BORDER_X, "Trennlinie auf deiner X-Koordinate."));
        menu.setItem(20, setupItem(SetupToolManager.Mode.BORDER_Z, "Trennlinie auf deiner Z-Koordinate."));
        menu.setItem(22, setupItem(SetupToolManager.Mode.SPAWN_NEGATIVE, "Jeder Klick fügt einen Spawn hinzu."));
        menu.setItem(24, setupItem(SetupToolManager.Mode.SPAWN_POSITIVE, "Jeder Klick fügt einen Spawn hinzu."));
        menu.setItem(26, setupItem(SetupToolManager.Mode.FINAL_CENTER, "Mitte und Höhe des Endkampfs."));
        menu.setItem(31, helpItem(Material.BARRIER, "Zurück zum Dashboard", "Klick: Admin-Hauptmenü"));
        menu.setItem(40, helpItem(Material.STICK, "Bedienung", "Eintrag wählen → Stick erhalten", "Rechtsklick setzt • Linksklick wechselt"));
        menu.setItem(42, helpItem(Material.FILLED_MAP, plugin.worldMap().hasSnapshot() ? "✔ Weltkarte aktualisieren" : "○ Weltkarte erstellen",
            "Erzeugt einen eingefrorenen Stand generierter Chunks.", "Erst der nächste Admin-Klick aktualisiert sie wieder."));
        menu.setItem(44, creditItem());
        player.openInventory(menu);
    }

    public void openBlacklist(Player player, int requestedPage) {
        List<Material> materials = plugin.blacklist().materials();
        int pages = Math.max(1, (materials.size() + 26) / 27);
        int page = Math.max(0, Math.min(pages - 1, requestedPage));
        Inventory menu = createMenu("blacklist:" + page, 45, "Rival • Item-Blacklist " + (page + 1) + "/" + pages, ChatColor.RED);
        int start = page * 27;
        for (int slot = 0; slot < 27 && start + slot < materials.size(); slot++) {
            Material material = materials.get(start + slot);
            menu.setItem(slot + 9, helpItem(material, "✖ " + material.name(), "Klick: von der Blacklist entfernen"));
        }
        menu.setItem(4, helpItem(Material.HOPPER, "Item aus Hand hinzufügen",
            "Halte den gewünschten Gegenstand in der Haupthand.", "Klick: Material sperren und überall löschen."));
        if (page > 0) menu.setItem(36, helpItem(Material.ARROW, "Vorherige Seite"));
        menu.setItem(40, backItem());
        if (page + 1 < pages) menu.setItem(44, helpItem(Material.ARROW, "Nächste Seite")); else menu.setItem(44, creditItem());
        player.openInventory(menu);
    }

    public void openPlayerOverview(Player viewer, int requestedPage) {
        List<PlayerRecord> players = knownPlayers();
        int pages = Math.max(1, (players.size() + 44) / 45);
        int page = Math.max(0, Math.min(pages - 1, requestedPage));
        Inventory menu = createMenu("players:" + page, 54,
            "Rival • Spielerzentrale " + (page + 1) + "/" + pages, ChatColor.GOLD);
        int start = page * 45;
        for (int slot = 0; slot < 45 && start + slot < players.size(); slot++)
            menu.setItem(slot, playerStatusItem(players.get(start + slot)));
        if (page > 0) menu.setItem(45, helpItem(Material.ARROW, "Vorherige Seite", "Seite " + page));
        long active = players.stream().filter(record -> !record.eliminated() && record.hearts() > 0).count();
        menu.setItem(47, helpItem(Material.REDSTONE, "Herzen verwalten",
            "Spielerkopf anklicken und Herz-Menü öffnen."));
        menu.setItem(48, helpItem(Material.PAPER, "Spieler: " + players.size() + " • Aktiv: " + active,
            "Auf jedem Kopf siehst du Herzen und Status."));
        menu.setItem(49, helpItem(Material.BARRIER, "Zurück zur Administration", "Klick: Admin-Hauptmenü"));
        if (page + 1 < pages) menu.setItem(53, helpItem(Material.ARROW, "Nächste Seite", "Seite " + (page + 2)));
        else menu.setItem(53, creditItem());
        viewer.openInventory(menu);
    }

    public void openPlayerControl(Player viewer, PlayerRecord record, int returnPage) {
        Inventory menu = createMenu("player:" + record.uuid() + ":" + returnPage, 45,
            "Rival • Spieler • " + record.lastName(), ChatColor.GOLD);
        menu.setItem(4, playerStatusItem(record));
        menu.setItem(11, helpItem(Material.BLUE_BED, "Seite NEGATIV",
            "Klick: Spieler der negativen Kartenseite zuweisen"));
        menu.setItem(12, helpItem(Material.WHITE_BED, "Keine Seite",
            "Klick: Zuweisung entfernen und in den Warteraum setzen"));
        menu.setItem(13, helpItem(Material.RED_BED, "Seite POSITIV",
            "Klick: Spieler der positiven Kartenseite zuweisen"));
        menu.setItem(15, helpItem(Material.REDSTONE, "Herzen verwalten • " + record.hearts() + "/3",
            "Klick: eigenes Herz-Menü öffnen", "Direkt 0, 1, 2 oder 3 Herzen setzen"));
        menu.setItem(21, helpItem(record.eliminated() ? Material.TOTEM_OF_UNDYING : Material.SKELETON_SKULL,
            record.eliminated() ? "Spieler wiederbeleben" : "Spieler ausscheiden lassen",
            "Klick: Status kontrolliert umschalten"));
        menu.setItem(23, helpItem(Material.CLOCK, "Spielzeit zurücksetzen",
            "Klick: heutige Spielzeit dieses Spielers löschen"));
        menu.setItem(31, helpItem(Material.ARROW, "Zurück zur Spielerzentrale", "Klick: vorherige Seite"));
        menu.setItem(40, creditItem());
        viewer.openInventory(menu);
    }

    public void openHeartControl(Player viewer, PlayerRecord record, int returnPage) {
        Inventory menu = createMenu("hearts:" + record.uuid() + ":" + returnPage, 45,
            "Rival • Herzen • " + record.lastName(), ChatColor.RED);
        menu.setItem(4, playerStatusItem(record));
        menu.setItem(10, heartValueItem(record, 0, Material.BARRIER, "Ausscheiden"));
        menu.setItem(12, heartValueItem(record, 1, Material.RED_DYE, "1 Herz"));
        menu.setItem(14, heartValueItem(record, 2, Material.REDSTONE, "2 Herzen"));
        menu.setItem(16, heartValueItem(record, 3, Material.REDSTONE_BLOCK, "3 Herzen"));
        menu.setItem(20, helpItem(Material.GRAY_DYE, "Ein Herz abziehen",
            "Aktuell: " + record.hearts() + "/3", "Klick: -1"));
        menu.setItem(24, helpItem(Material.LIME_DYE, "Ein Herz hinzufügen",
            "Aktuell: " + record.hearts() + "/3", "Klick: +1"));
        menu.setItem(31, helpItem(Material.ARROW, "Zurück zu " + record.lastName(), "Klick: Spieler-Menü"));
        menu.setItem(40, creditItem());
        viewer.openInventory(menu);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || holder.id.equals("help")) return;
        if (holder.id.equals("admin") && event.getRawSlot() == 4) {
            plugin.adminMode().toggle(player);
            openAdmin(player);
            return;
        }
        if (!plugin.adminMode().isActive(player)) {
            player.closeInventory();
            Messages.error(player, "Der Admin-Modus ist nicht aktiv.");
            return;
        }
        if (holder.id.startsWith("players:")) {
            int page;
            try { page = Integer.parseInt(holder.id.substring("players:".length())); }
            catch (NumberFormatException ex) { page = 0; }
            if (event.getRawSlot() >= 0 && event.getRawSlot() < 45) {
                int index = page * 45 + event.getRawSlot();
                List<PlayerRecord> records = knownPlayers();
                if (index < records.size()) {
                    if (event.isRightClick()) openHeartControl(player, records.get(index), page);
                    else openPlayerControl(player, records.get(index), page);
                }
            } else if (event.getRawSlot() == 45) openPlayerOverview(player, page - 1);
            else if (event.getRawSlot() == 49) openAdmin(player);
            else if (event.getRawSlot() == 53 && (page + 1) * 45 < knownPlayers().size()) openPlayerOverview(player, page + 1);
            return;
        }
        if (holder.id.startsWith("player:")) {
            handlePlayerControl(player, holder.id, event);
            return;
        }
        if (holder.id.startsWith("hearts:")) {
            handleHeartControl(player, holder.id, event.getRawSlot());
            return;
        }
        if (holder.id.startsWith("blacklist:")) {
            handleBlacklist(player, holder.id, event.getRawSlot());
            return;
        }
        if (holder.id.equals("setup")) {
            SetupToolManager.Mode mode = setupMode(event.getRawSlot());
            if (mode != null) {
                player.closeInventory();
                plugin.setupTools().give(player, mode);
            } else if (event.getRawSlot() == 31) openAdmin(player);
            else if (event.getRawSlot() == 42) { player.closeInventory(); plugin.worldMap().update(player); }
            return;
        }
        if (holder.id.equals("game")) {
            handleGameMenu(player, event.getRawSlot());
            return;
        }
        if (holder.id.equals("settings")) {
            handleSettingsMenu(player, event);
            return;
        }
        if (holder.id.equals("tools")) {
            handleToolsMenu(player, event.getRawSlot());
            return;
        }
        if (holder.id.equals("adminhelp")) {
            if (event.getRawSlot() == 31) openAdmin(player);
            return;
        }
        switch (event.getRawSlot()) {
            case 11 -> openSetup(player);
            case 13 -> openPlayerOverview(player, 0);
            case 15 -> openGameControl(player);
            case 29 -> openSettings(player);
            case 31 -> openTeamTools(player);
            case 33 -> openAdminHelp(player);
            default -> { }
        }
    }

    private void handleGameMenu(Player player, int slot) {
        switch (slot) {
            case 10 -> {
                if (plugin.projects().isStarted()) plugin.projects().stop();
                else if (!plugin.projects().start(true)) Messages.error(player, "Start nicht möglich: Prüfe Einrichtung, Zuweisungen und Spawns.");
                openGameControl(player);
            }
            case 12 -> { int count = plugin.combat().revealNemeses(); Messages.normal(player, count + " Erzfeinde wurden aufgedeckt."); openGameControl(player); }
            case 14 -> { plugin.borders().setEnabled(!plugin.borders().isEnabled()); openGameControl(player); }
            case 16 -> {
                if (plugin.endFight().isRunning()) plugin.endFight().stop();
                else if (!plugin.endFight().start()) plugin.endFight().showStatus(player);
                openGameControl(player);
            }
            case 31 -> openAdmin(player);
            default -> { }
        }
    }

    private void handleSettingsMenu(Player player, InventoryClickEvent event) {
        switch (event.getRawSlot()) {
            case 10 -> {
                if (event.isRightClick()) {
                    int delta = event.isShiftClick() ? -15 : 15;
                    int value = Math.max(0, plugin.getConfig().getInt("playtime.daily-minutes", 180) + delta);
                    plugin.getConfig().set("playtime.daily-minutes", value);
                } else plugin.getConfig().set("playtime.enabled", !plugin.getConfig().getBoolean("playtime.enabled"));
                plugin.saveConfig();
                Bukkit.getOnlinePlayers().forEach(plugin.playtime()::refreshVisibility);
                openSettings(player);
            }
            case 12 -> adjustRate(player, ZoneManager.Zone.NETHER, event.isRightClick() ? -10 : 10);
            case 13 -> adjustRate(player, ZoneManager.Zone.END, event.isRightClick() ? -10 : 10);
            case 14 -> adjustRate(player, ZoneManager.Zone.OVERWORLD, event.isRightClick() ? -10 : 10);
            case 31 -> openAdmin(player);
            default -> { }
        }
    }

    private void handleToolsMenu(Player player, int slot) {
        switch (slot) {
            case 10 -> { player.closeInventory(); plugin.vanish().toggle(player); }
            case 12 -> { player.closeInventory(); Messages.normal(player, "Gräber: /admin graves count|deleteall|near|player"); }
            case 14 -> { player.closeInventory(); Messages.normal(player, "Broadcast: /admin broadcast <Text> • Zeilenumbruch: \\n"); }
            case 16 -> { player.closeInventory(); player.performCommand("admin playtime ranking"); }
            case 22 -> openBlacklist(player, 0);
            case 31 -> openAdmin(player);
            default -> { }
        }
    }

    private void handleBlacklist(Player player, String id, int slot) {
        int page;
        try { page = Integer.parseInt(id.substring("blacklist:".length())); }
        catch (NumberFormatException ex) { page = 0; }
        if (slot == 4) {
            Material material = player.getInventory().getItemInMainHand().getType();
            if (material.isAir()) Messages.error(player, "Halte zuerst einen Gegenstand in der Haupthand.");
            else if (plugin.blacklist().add(material)) Messages.normal(player, material.name() + " wurde gesperrt und entfernt.");
            else Messages.error(player, "Dieses Material ist bereits gesperrt.");
            openBlacklist(player, page);
            return;
        }
        List<Material> materials = plugin.blacklist().materials();
        int index = page * 27 + slot - 9;
        if (slot >= 9 && slot < 36 && index >= 0 && index < materials.size()) {
            Material removed = materials.get(index);
            plugin.blacklist().remove(removed);
            Messages.normal(player, removed.name() + " ist wieder erlaubt.");
            openBlacklist(player, page);
        } else if (slot == 36) openBlacklist(player, page - 1);
        else if (slot == 40) openTeamTools(player);
        else if (slot == 44) openBlacklist(player, page + 1);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    private ItemStack endFightItem() {
        List<String> lore = new ArrayList<>();
        lore.add("Verbleibende Spieler: " + plugin.endFight().remainingPlayers().size());
        lore.add(plugin.endFight().isRunning() ? "Klick: beenden und normale Border wiederherstellen" : "Start nur bei exakt 2 Spielern");
        lore.add("Status: /admin endfight status");
        return helpItem(Material.DRAGON_HEAD, "Endkampf: " + (plugin.endFight().isRunning() ? "AKTIV" : "BEREIT/MANUELL"), lore.toArray(String[]::new));
    }

    private ItemStack setupItem(SetupToolManager.Mode mode, String description) {
        boolean configured = plugin.setupTools().isConfigured(mode);
        return helpItem(configured ? Material.LIME_DYE : mode.icon(),
            (configured ? "✔ " : "○ ") + mode.title(), description,
            configured ? "EINGERICHTET • Klick: neu setzen" : "NOCH OFFEN • Klick: Setup-Stick erhalten");
    }

    private static SetupToolManager.Mode setupMode(int slot) {
        return switch (slot) {
            case 10 -> SetupToolManager.Mode.WAITING;
            case 12 -> SetupToolManager.Mode.NETHER_1;
            case 13 -> SetupToolManager.Mode.NETHER_2;
            case 15 -> SetupToolManager.Mode.END_1;
            case 16 -> SetupToolManager.Mode.END_2;
            case 19 -> SetupToolManager.Mode.BORDER_X;
            case 20 -> SetupToolManager.Mode.BORDER_Z;
            case 22 -> SetupToolManager.Mode.SPAWN_NEGATIVE;
            case 24 -> SetupToolManager.Mode.SPAWN_POSITIVE;
            case 26 -> SetupToolManager.Mode.FINAL_CENTER;
            default -> null;
        };
    }

    private void handlePlayerControl(Player viewer, String id, InventoryClickEvent event) {
        String[] parts = id.split(":");
        if (parts.length != 3) { openPlayerOverview(viewer, 0); return; }
        PlayerRecord record;
        int page;
        try {
            record = plugin.data().player(java.util.UUID.fromString(parts[1]));
            page = Integer.parseInt(parts[2]);
        } catch (IllegalArgumentException ex) { openPlayerOverview(viewer, 0); return; }
        if (record == null) { openPlayerOverview(viewer, page); return; }
        switch (event.getRawSlot()) {
            case 11 -> setSide(viewer, record, -1);
            case 12 -> setSide(viewer, record, 0);
            case 13 -> setSide(viewer, record, 1);
            case 15 -> { openHeartControl(viewer, record, page); return; }
            case 21 -> {
                boolean revive = record.eliminated() || record.hearts() == 0;
                record.eliminated(!revive);
                record.hearts(revive ? plugin.getConfig().getInt("combat.starting-hearts", 3) : 0);
                savePlayerChange(record);
            }
            case 23 -> {
                record.playDate(java.time.LocalDate.MIN);
                record.playedSeconds(0);
                savePlayerChange(record);
            }
            case 31 -> { openPlayerOverview(viewer, page); return; }
            default -> { return; }
        }
        openPlayerControl(viewer, record, page);
    }

    private void handleHeartControl(Player viewer, String id, int slot) {
        String[] parts = id.split(":");
        if (parts.length != 3) { openPlayerOverview(viewer, 0); return; }
        PlayerRecord record;
        int page;
        try {
            record = plugin.data().player(java.util.UUID.fromString(parts[1]));
            page = Integer.parseInt(parts[2]);
        } catch (IllegalArgumentException ex) { openPlayerOverview(viewer, 0); return; }
        if (record == null) { openPlayerOverview(viewer, page); return; }
        if (slot == 31) { openPlayerControl(viewer, record, page); return; }
        int hearts = switch (slot) {
            case 10 -> 0;
            case 12 -> 1;
            case 14 -> 2;
            case 16 -> 3;
            case 20 -> Math.max(0, record.hearts() - 1);
            case 24 -> Math.min(plugin.getConfig().getInt("combat.maximum-hearts", 3), record.hearts() + 1);
            default -> -1;
        };
        if (hearts < 0) return;
        record.hearts(hearts);
        record.eliminated(hearts == 0);
        savePlayerChange(record);
        Messages.normal(viewer, record.lastName() + " hat jetzt " + hearts + (hearts == 1 ? " Herz." : " Herzen."));
        openHeartControl(viewer, record, page);
    }

    private void setSide(Player viewer, PlayerRecord record, int side) {
        if (side != 0 && record.side() != side) {
            long occupied = plugin.data().players().stream()
                .filter(other -> !other.uuid().equals(record.uuid()) && !other.eliminated() && other.side() == side).count();
            if (occupied >= plugin.getConfig().getInt("border.side-capacity", 50)) {
                Messages.error(viewer, "Diese Seite hat ihre konfigurierte Kapazität erreicht.");
                return;
            }
        }
        record.side(side);
        savePlayerChange(record);
    }

    private void savePlayerChange(PlayerRecord record) {
        plugin.data().save();
        Player online = Bukkit.getPlayer(record.uuid());
        if (online != null) {
            if (record.eliminated()) online.kickPlayer(plugin.getConfig().getString("messages.eliminated"));
            else {
                plugin.projects().playerAssigned(online);
                plugin.playtime().refreshVisibility(online);
                plugin.modGate().sendState(online);
            }
        }
        plugin.endFight().checkAutomaticStart();
    }

    private static ItemStack remainingPlayer(PlayerRecord record) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwnerProfile(Bukkit.createPlayerProfile(record.uuid(), record.lastName().equals("?") ? "Unbekannt" : record.lastName()));
        meta.setDisplayName(ChatColor.GOLD + record.lastName());
        meta.setLore(List.of(ChatColor.GRAY + String.valueOf(record.hearts()) + (record.hearts() == 1 ? " Herz" : " Herzen"), CREDIT));
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
        ChatColor stateColor = !alive ? ChatColor.RED : record.side() == 0 ? ChatColor.YELLOW : ChatColor.GREEN;
        meta.setDisplayName((alive ? ChatColor.GOLD : ChatColor.DARK_GRAY) + name);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Status: " + stateColor + state);
        lore.add((online ? ChatColor.GREEN : ChatColor.DARK_GRAY) + "Verbindung: " + (online ? "ONLINE" : "OFFLINE"));
        lore.add(ChatColor.GRAY + "Herzen: " + record.hearts() + "/" + plugin.getConfig().getInt("combat.maximum-hearts", 3));
        lore.add(ChatColor.GRAY + "Seite: " + (record.side() < 0 ? "NEGATIV" : record.side() > 0 ? "POSITIV" : "KEINE"));
        var clan = plugin.clans().clan(record.uuid());
        lore.add(ChatColor.GRAY + "Clan: " + (clan == null ? "–" : clan.name() + " [" + clan.tag() + "]"));
        lore.add((plugin.youtube().isActive(record.uuid()) ? ChatColor.RED : ChatColor.GRAY)
            + "YouTube: " + (plugin.youtube().isActive(record.uuid()) ? "AKTIV" : "AUS"));
        int combat = plugin.combat().remainingSeconds(record.uuid());
        lore.add((combat > 0 ? ChatColor.RED : ChatColor.GRAY) + "Combat: " + (combat > 0 ? combat + "s" : "AUS"));
        Player onlinePlayer = Bukkit.getPlayer(record.uuid());
        lore.add(ChatColor.GRAY + "Admin/Vanish: " + (onlinePlayer != null && plugin.adminMode().isActive(onlinePlayer) ? "ADMIN" : "AUS")
            + (onlinePlayer != null && plugin.vanish().isVanished(onlinePlayer) ? " + VANISH" : ""));
        lore.add(ChatColor.GRAY + "Heute gespielt: " + plugin.playtime().formattedPlayed(record));
        lore.add(ChatColor.GRAY + "Spielzeit übrig: " + plugin.playtime().formatted(record));
        lore.add("");
        lore.add(ChatColor.AQUA + "Linksklick: Spielerprofil");
        lore.add(ChatColor.RED + "Rechtsklick: Herzen direkt verwalten");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack heartValueItem(PlayerRecord record, int value, Material material, String label) {
        String selected = record.hearts() == value ? " • AKTUELL" : "";
        return helpItem(material, label + selected,
            value == 0 ? "Setzt 0 Herzen und scheidet den Spieler aus." : "Setzt die Projekt-Herzen direkt auf " + value + ".",
            "Klick: Wert übernehmen");
    }

    private List<PlayerRecord> knownPlayers() {
        return plugin.data().players().stream()
            .sorted(Comparator.<PlayerRecord, Boolean>comparing(record -> record.eliminated() || record.hearts() <= 0)
                .thenComparing(record -> Bukkit.getPlayer(record.uuid()) == null)
                .thenComparing(PlayerRecord::lastName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private static ItemStack helpItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + name);
        List<String> lines = new ArrayList<>();
        for (String line : lore) lines.add(ChatColor.GRAY + line);
        meta.setLore(lines);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack creditItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(CREDIT);
        meta.setLore(List.of(ChatColor.GRAY + "Minecraft Rival Plugin & Mod"));
        item.setItemMeta(meta);
        return item;
    }

    private static Inventory createMenu(String id, int size, String title, ChatColor color) {
        MenuHolder holder = new MenuHolder(id);
        Inventory inventory = Bukkit.createInventory(holder, size, color + title);
        holder.inventory = inventory;
        ItemStack background = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta backgroundMeta = background.getItemMeta();
        backgroundMeta.setDisplayName(" ");
        background.setItemMeta(backgroundMeta);
        for (int slot = 0; slot < size; slot++) inventory.setItem(slot, background);
        return inventory;
    }

    private void adjustRate(Player player, ZoneManager.Zone zone, int delta) {
        plugin.zones().setSpawnRate(zone, Math.max(0, Math.min(100, plugin.zones().spawnRate(zone) + delta)));
        openSettings(player);
    }

    private static ItemStack backItem() {
        return helpItem(Material.ARROW, "Zurück", "Zur Spielleitung.");
    }

    private static String onOff(boolean value) { return value ? "AN" : "AUS"; }

    private static final class MenuHolder implements InventoryHolder {
        private final String id;
        private Inventory inventory;
        private MenuHolder(String id) { this.id = id; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
