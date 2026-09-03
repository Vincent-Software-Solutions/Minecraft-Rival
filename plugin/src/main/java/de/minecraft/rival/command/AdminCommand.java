package de.minecraft.rival.command;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.data.ClanRecord;
import de.minecraft.rival.data.PlayerRecord;
import de.minecraft.rival.game.ZoneManager;
import de.minecraft.rival.game.ItemBlacklistManager;
import de.minecraft.rival.util.Messages;
import de.minecraft.rival.util.RivalRules;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public final class AdminCommand implements CommandExecutor, TabCompleter {
    private final RivalPlugin plugin;
    public AdminCommand(RivalPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        // Nur Spieler benötigen Permission + aktivierten Modus. Die Serverkonsole
        // ist definitionsgemäß immer die Spielleitung und kann alles sofort nutzen.
        if (sender instanceof Player && !sender.hasPermission("rival.admin")) {
            Messages.error(sender, "Dafür fehlt dir rival.admin.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("mode")) {
            if (sender instanceof Player player) plugin.adminMode().toggle(player);
            else Messages.error(sender, "Die Konsole benötigt keinen Admin-Modus.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("broadcast")) {
            if (args.length < 2) { Messages.error(sender, "/admin broadcast <Nachricht> (für Zeilenumbruch: \\n)"); return true; }
            String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            if (sender instanceof Player player && !plugin.adminMode().isActive(player)) {
                Messages.error(sender, "Aktiviere zuerst den Admin-Modus mit /admin mode.");
                return true;
            }
            // Die Konsole besitzt implizit immer den Admin-Modus.
            plugin.broadcasts().broadcastNow(message);
            Messages.normal(sender, "Admin-Broadcast gesendet.");
            return true;
        }
        if (args.length == 0 && sender instanceof Player player) {
            plugin.menus().openAdmin(player);
            return true;
        }
        if (sender instanceof Player player && !plugin.adminMode().isActive(player)) {
            Messages.error(sender, "Aktiviere zuerst den Admin-Modus mit /admin mode.");
            return true;
        }
        if (args.length == 0) { help(sender); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> { if (sender instanceof Player player) plugin.menus().openAdminHelp(player); else help(sender); }
            case "vanish" -> { if (sender instanceof Player player) plugin.vanish().toggle(player); else Messages.error(sender, "Nur im Spiel verfügbar."); }
            case "reload" -> { plugin.reloadRival(); Messages.normal(sender, "Konfiguration neu geladen."); }
            case "border" -> border(sender, args);
            case "endfight" -> endFight(sender, args);
            case "erzfeind" -> { int count = plugin.combat().revealNemeses(); sender.sendMessage(Messages.value("Aufgedeckte Erzfeinde: ", count, "")); }
            case "project" -> project(sender, args);
            case "setlocation" -> setLocation(sender, args);
            case "spawn" -> spawn(sender, args);
            case "zone" -> zone(sender, args);
            case "mobrate" -> mobRate(sender, args);
            case "graves" -> graves(sender, args);
            case "config" -> config(sender, args);
            case "player" -> player(sender, args);
            case "clan" -> clan(sender, args);
            case "rules" -> rules(sender, args);
            case "ban" -> ban(sender, args);
            case "unban" -> unban(sender, args);
            case "warn" -> warn(sender, args);
            case "warnings" -> warnings(sender, args);
            case "players" -> players(sender, args);
            case "playtime" -> playtime(sender, args);
            case "blacklist" -> blacklist(sender, args);
            case "setup" -> {
                if (!(sender instanceof Player player)) Messages.error(sender, "Nur im Spiel verfügbar.");
                else plugin.menus().openSetup(player);
            }
            default -> help(sender);
        }
        return true;
    }

    private void blacklist(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (sender instanceof Player player) plugin.menus().openBlacklist(player, 0);
            else Messages.error(sender, "/admin blacklist <add|remove|list|clear> [Material]");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                Material material = args.length >= 3 ? ItemBlacklistManager.parse(args[2])
                    : sender instanceof Player player ? player.getInventory().getItemInMainHand().getType() : null;
                if (material == null || material.isAir() || !material.isItem()) Messages.error(sender, "Ungültiges Item-Material oder keine Haupthand ausgewählt.");
                else if (plugin.blacklist().add(material)) Messages.normal(sender, material.name() + " wurde gesperrt und sofort entfernt.");
                else Messages.error(sender, "Dieses Material ist bereits gesperrt.");
            }
            case "remove" -> {
                Material material = args.length >= 3 ? ItemBlacklistManager.parse(args[2]) : null;
                if (plugin.blacklist().remove(material)) Messages.normal(sender, material.name() + " ist wieder erlaubt.");
                else Messages.error(sender, "Dieses Material ist nicht gesperrt.");
            }
            case "list" -> Messages.normal(sender, "Item-Blacklist (" + plugin.blacklist().materials().size() + "): "
                + String.join(", ", plugin.blacklist().materials().stream().map(Material::name).toList()));
            case "clear" -> { plugin.blacklist().clear(); Messages.normal(sender, "Die Item-Blacklist wurde geleert."); }
            default -> Messages.error(sender, "/admin blacklist <add|remove|list|clear> [Material]");
        }
    }

    private void border(CommandSender sender, String[] args) {
        if (args.length < 2) { Messages.error(sender, "/admin border <on|off|toggle>"); return; }
        boolean enable;
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on" -> enable = true;
            case "off" -> enable = false;
            case "toggle" -> enable = !plugin.borders().isEnabled();
            default -> { Messages.error(sender, "Nutze on, off oder toggle."); return; }
        }
        plugin.borders().setEnabled(enable);
    }

    private void endFight(CommandSender sender, String[] args) {
        if (args.length < 2) { Messages.error(sender, "/admin endfight <status|start|stop>"); return; }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "status" -> plugin.endFight().showStatus(sender);
            case "start" -> {
                if (!plugin.endFight().start()) {
                    Messages.error(sender, "Start nicht möglich: Das Projekt muss laufen und exakt zwei Spieler müssen online übrig sein.");
                    plugin.endFight().showStatus(sender);
                }
            }
            case "stop" -> { if (!plugin.endFight().stop()) Messages.error(sender, "Der Endkampf läuft nicht."); }
            default -> Messages.error(sender, "Nutze status, start oder stop.");
        }
    }

    private void project(CommandSender sender, String[] args) {
        if (args.length < 2) { Messages.error(sender, "/admin project <start|stop|schedule> [YYYY-MM-DDTHH:MM:SS]"); return; }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                if (!plugin.projects().start(true)) Messages.error(sender, "Start nicht möglich: Prüfe Zuweisungen und Seiten-Spawns.");
            }
            case "stop" -> {
                if (!plugin.projects().stop()) Messages.error(sender, "Das Projekt ist nicht gestartet.");
            }
            case "schedule" -> {
                if (args.length < 3) { Messages.error(sender, "/admin project schedule <YYYY-MM-DDTHH:MM[:SS]>"); return; }
                if (plugin.projects().isStarted()) { Messages.error(sender, "Stoppe das Projekt, bevor du einen neuen Start planst."); return; }
                try { LocalDateTime.parse(args[2]); }
                catch (RuntimeException ex) { Messages.error(sender, "Ungültiges Datum. Beispiel: 2026-08-11T20:00:00"); return; }
                plugin.getConfig().set("project.start-at", args[2]);
                plugin.saveConfig();
                Messages.normal(sender, "Projektstart wurde auf " + args[2] + " gesetzt.");
            }
            default -> Messages.error(sender, "Nutze start, stop oder schedule.");
        }
    }

    private void setLocation(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Messages.error(sender, "Nur im Spiel verfügbar."); return; }
        if (args.length < 2 || !args[1].equalsIgnoreCase("waiting")) { Messages.error(sender, "/admin setlocation waiting"); return; }
        try {
            plugin.projects().setWaitingRoom(player.getLocation());
            Messages.normal(sender, "Warteraum wurde an deiner Position gesetzt.");
        } catch (IllegalArgumentException ex) { Messages.error(sender, ex.getMessage()); }
    }

    private void spawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Messages.error(sender, "Nur im Spiel verfügbar."); return; }
        if (args.length < 3) { Messages.error(sender, "/admin spawn <negative|positive> <add|clear>"); return; }
        int side;
        try { side = parseSide(args[1], false); }
        catch (IllegalArgumentException ex) { Messages.error(sender, "Seite muss negative/-1 oder positive/1 sein."); return; }
        if (args[2].equalsIgnoreCase("add")) {
            try {
                plugin.projects().addSpawn(side, player.getLocation());
                Messages.normal(sender, "Spawnpunkt für Seite " + side + " hinzugefügt.");
            } catch (IllegalArgumentException ex) { Messages.error(sender, ex.getMessage()); }
        } else if (args[2].equalsIgnoreCase("clear")) {
            plugin.projects().clearSpawns(side);
            Messages.normal(sender, "Spawnpunkte für Seite " + side + " gelöscht.");
        } else Messages.error(sender, "Nutze add oder clear.");
    }

    private void zone(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Messages.error(sender, "Nur im Spiel verfügbar."); return; }
        if (args.length < 3) { Messages.error(sender, "/admin zone <nether|end> <pos1|pos2|clear|info>"); return; }
        ZoneManager.Zone zone;
        try { zone = parseZone(args[1]); if (zone == ZoneManager.Zone.OVERWORLD) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException ex) { Messages.error(sender, "Hier sind nur nether oder end erlaubt."); return; }
        try {
            switch (args[2].toLowerCase(Locale.ROOT)) {
                case "pos1" -> { plugin.zones().setCorner(zone, 1, player.getLocation()); Messages.normal(sender, zone + "-Zone: Ecke 1 gesetzt (Y wird ignoriert)."); }
                case "pos2" -> { plugin.zones().setCorner(zone, 2, player.getLocation()); Messages.normal(sender, zone + "-Zone: Ecke 2 gesetzt (Y wird ignoriert)."); }
                case "clear" -> { plugin.zones().clear(zone); Messages.normal(sender, zone + "-Zone wurde gelöscht."); }
                case "info" -> sender.sendMessage(Messages.value(zone + "-Zone definiert: ", plugin.zones().isDefined(zone), " • Spawnrate: " + plugin.zones().spawnRate(zone) + "%"));
                default -> Messages.error(sender, "Nutze pos1, pos2, clear oder info.");
            }
        } catch (IllegalArgumentException ex) { Messages.error(sender, ex.getMessage()); }
    }

    private void mobRate(CommandSender sender, String[] args) {
        if (args.length < 3) { Messages.error(sender, "/admin mobrate <nether|end|overworld> <0-100>"); return; }
        try {
            ZoneManager.Zone zone = parseZone(args[1]);
            int rate = Integer.parseInt(args[2]);
            plugin.zones().setSpawnRate(zone, rate);
            Messages.normal(sender, "Natürliche Spawnrate für " + zone + " auf " + rate + "% gesetzt.");
        } catch (IllegalArgumentException ex) { Messages.error(sender, "Zone oder Prozentwert ist ungültig (0–100)."); }
    }

    private void graves(CommandSender sender, String[] args) {
        if (args.length < 2) { Messages.error(sender, "/admin graves <count|deleteall|near|player> [Wert]"); return; }
        int count;
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "count" -> { sender.sendMessage(Messages.value("Aktive Gräber: ", plugin.graves().count(), "")); return; }
            case "deleteall" -> count = plugin.graves().deleteAll();
            case "near" -> {
                if (!(sender instanceof Player player) || args.length < 3) { Messages.error(sender, "/admin graves near <Radius>"); return; }
                try {
                    double radius = Double.parseDouble(args[2]);
                    if (!Double.isFinite(radius) || radius < 0) throw new NumberFormatException();
                    count = plugin.graves().deleteNear(player.getLocation(), radius);
                }
                catch (NumberFormatException ex) { Messages.error(sender, "Ungültiger Radius."); return; }
            }
            case "player" -> {
                if (args.length < 3) { Messages.error(sender, "/admin graves player <Spieler>"); return; }
                OfflinePlayer target = findPlayer(args[2]);
                if (target == null) { Messages.error(sender, "Spieler nicht gefunden."); return; }
                count = plugin.graves().deleteByOwner(target.getUniqueId());
            }
            default -> { Messages.error(sender, "Nutze count, deleteall, near oder player."); return; }
        }
        sender.sendMessage(Messages.value("Gelöschte Gräber: ", count, ""));
    }

    private void config(CommandSender sender, String[] args) {
        if (args.length < 3) { Messages.error(sender, "/admin config <Pfad> <Wert>"); return; }
        String path = args[1];
        if (!plugin.getConfig().contains(path)) { Messages.error(sender, "Unbekannter Config-Pfad: " + path); return; }
        String raw = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        Object old = plugin.getConfig().get(path);
        Object value;
        try {
            if (old instanceof Boolean) value = parseBoolean(raw);
            else if (old instanceof Integer) value = Integer.parseInt(raw);
            else if (old instanceof Long) value = Long.parseLong(raw);
            else if (old instanceof Double) value = Double.parseDouble(raw);
            else value = raw;
        } catch (IllegalArgumentException ex) { Messages.error(sender, "Wert passt nicht zum Typ von " + path + "."); return; }
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
        plugin.reloadRival();
        sender.sendMessage(Messages.value(path + " = ", plugin.getConfig().get(path), ""));
    }

    private void player(CommandSender sender, String[] args) {
        if (args.length < 3) { Messages.error(sender, "/admin player <hearts|revive|eliminate|timereset|side> <Spieler> [Wert]"); return; }
        OfflinePlayer target = findPlayer(args[2]);
        if (target == null) { Messages.error(sender, "Spieler nicht gefunden."); return; }
        PlayerRecord record = plugin.data().player(target.getUniqueId(), Optional.ofNullable(target.getName()).orElse(args[2]));
        try {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "hearts" -> {
                    if (args.length < 4) throw new IllegalArgumentException();
                    int value = Integer.parseInt(args[3]);
                    int max = plugin.getConfig().getInt("combat.maximum-hearts", 3);
                    if (value < 0 || value > max) throw new IllegalArgumentException();
                    record.hearts(value); record.eliminated(value == 0);
                }
                case "revive" -> { record.hearts(plugin.getConfig().getInt("combat.starting-hearts", 3)); record.eliminated(false); }
                case "eliminate" -> { record.hearts(0); record.eliminated(true); }
                case "timereset" -> { record.playDate(LocalDate.MIN); record.playedSeconds(0); }
                case "side" -> {
                    if (args.length < 4) throw new IllegalArgumentException();
                    int side = parseSide(args[3], true);
                    if (side != 0 && record.side() != side) {
                        long occupied = plugin.data().players().stream().filter(other -> !other.uuid().equals(record.uuid()) && !other.eliminated() && other.side() == side).count();
                        if (occupied >= plugin.getConfig().getInt("border.side-capacity", 50)) {
                            Messages.error(sender, "Diese Seite hat ihre konfigurierte Kapazität erreicht."); return;
                        }
                    }
                    record.side(side);
                }
                default -> { Messages.error(sender, "Unbekannte Spieleraktion."); return; }
            }
        } catch (IllegalArgumentException ex) { Messages.error(sender, "Ungültiger oder fehlender Wert."); return; }
        plugin.data().save();
        Player online = target.getPlayer();
        if (online != null) {
            if (record.eliminated()) online.kickPlayer(plugin.getConfig().getString("messages.eliminated"));
            else { plugin.projects().playerAssigned(online); plugin.modGate().sendState(online); }
        }
        plugin.endFight().checkAutomaticStart();
        Messages.normal(sender, "Spielerdaten wurden aktualisiert.");
    }

    private void clan(CommandSender sender, String[] args) {
        if (args.length < 2) { Messages.error(sender, "/admin clan <create|add|remove|owner|color|tag|info|disband> ..."); return; }
        try {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "create" -> {
                    if (args.length < 4) throw new IllegalArgumentException("/admin clan create <Name> <Besitzer>");
                    OfflinePlayer owner = requirePlayer(args[3]);
                    plugin.clans().adminCreate(owner, args[2]);
                    Messages.normal(sender, "Clan " + args[2] + " wurde erstellt.");
                }
                case "add" -> {
                    if (args.length < 4) throw new IllegalArgumentException("/admin clan add <Clan> <Spieler>");
                    plugin.clans().adminAdd(plugin.clans().find(args[2]), requirePlayer(args[3]));
                    Messages.normal(sender, "Spieler wurde dem Clan hinzugefügt.");
                }
                case "remove" -> {
                    if (args.length < 3) throw new IllegalArgumentException("/admin clan remove <Spieler>");
                    plugin.clans().adminRemove(requirePlayer(args[2]));
                    Messages.normal(sender, "Spieler wurde aus dem Clan entfernt.");
                }
                case "owner" -> {
                    if (args.length < 4) throw new IllegalArgumentException("/admin clan owner <Clan> <Spieler>");
                    plugin.clans().adminSetOwner(plugin.clans().find(args[2]), requirePlayer(args[3]));
                    Messages.normal(sender, "Clanbesitzer wurde geändert.");
                }
                case "color" -> {
                    if (args.length < 4) throw new IllegalArgumentException("/admin clan color <Clan> <0-9|a-f>");
                    plugin.clans().adminSetColor(plugin.clans().find(args[2]), args[3]);
                    Messages.normal(sender, "Clanfarbe wurde geändert.");
                }
                case "tag" -> {
                    if (args.length < 4) throw new IllegalArgumentException("/admin clan tag <Clan> <Tag>");
                    plugin.clans().adminSetTag(plugin.clans().find(args[2]), args[3]);
                    Messages.normal(sender, "Clantag wurde geändert.");
                }
                case "info" -> {
                    if (args.length < 3) throw new IllegalArgumentException("/admin clan info <Clan>");
                    ClanRecord clan = plugin.clans().find(args[2]);
                    if (clan == null) throw new IllegalArgumentException("Clan nicht gefunden.");
                    sender.sendMessage(Messages.value("Clan " + clan.name() + " • Tag: ", clan.tag(), " • Mitglieder: " + clan.members().size() + "/" + plugin.clans().maximum()));
                }
                case "disband" -> {
                    if (args.length < 3) throw new IllegalArgumentException("/admin clan disband <Clan>");
                    ClanRecord clan = plugin.clans().find(args[2]);
                    if (clan == null) throw new IllegalArgumentException("Clan nicht gefunden.");
                    clan.members().forEach(uuid -> { PlayerRecord record = plugin.data().player(uuid); if (record != null) record.clanId(null); });
                    plugin.data().clans().remove(clan.id());
                    plugin.data().save();
                    Messages.normal(sender, "Clan wurde administrativ aufgelöst.");
                }
                default -> throw new IllegalArgumentException("Nutze create, add, remove, owner, color, tag, info oder disband.");
            }
        } catch (IllegalArgumentException ex) { Messages.error(sender, ex.getMessage()); }
    }

    private static OfflinePlayer requirePlayer(String name) {
        OfflinePlayer player = findPlayer(name);
        if (player == null) throw new IllegalArgumentException("Spieler nicht gefunden: " + name);
        return player;
    }

    private void rules(CommandSender sender, String[] args) {
        if (args.length < 2) { Messages.error(sender, "/admin rules <add|remove|list> [Wert]"); return; }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 3) { Messages.error(sender, "/admin rules add <Regel>"); return; }
                int id = plugin.rules().add(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                sender.sendMessage(Messages.value("Regel hinzugefügt. ID: ", id, ""));
            }
            case "remove" -> {
                if (args.length < 3) { Messages.error(sender, "/admin rules remove <ID>"); return; }
                try {
                    int id = Integer.parseInt(args[2]);
                    if (plugin.rules().remove(id)) Messages.normal(sender, "Regel #" + id + " entfernt.");
                    else Messages.error(sender, "Diese Regel-ID existiert nicht.");
                } catch (NumberFormatException ex) { Messages.error(sender, "Die Regel-ID muss eine Zahl sein."); }
            }
            case "list" -> plugin.rules().show(sender);
            default -> Messages.error(sender, "Nutze add, remove oder list.");
        }
    }

    private void ban(CommandSender sender, String[] args) {
        if (args.length < 4) { Messages.error(sender, "/admin ban <Spieler> <Dauer|permanent> <Grund>"); return; }
        OfflinePlayer target = findPlayer(args[1]);
        if (target == null) { Messages.error(sender, "Spieler nicht gefunden."); return; }
        try {
            long duration = RivalRules.parseBanDurationMillis(args[2]);
            String reason = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            var record = plugin.moderation().ban(target, reason, duration, sender.getName());
            sender.sendMessage(Messages.value(record.name() + " wurde gebannt: ", record.expiryText(), " • " + record.reason()));
        } catch (IllegalArgumentException | ArithmeticException ex) {
            Messages.error(sender, "Ungültige Dauer. Beispiele: 30m, 12h, 5d, 1w2d oder permanent.");
        }
    }

    private void unban(CommandSender sender, String[] args) {
        if (args.length < 2) { Messages.error(sender, "/admin unban <Spieler>"); return; }
        OfflinePlayer target = findPlayer(args[1]);
        UUID uuid = target == null ? plugin.moderation().findUuidByName(args[1]) : target.getUniqueId();
        if (uuid != null && plugin.moderation().unban(uuid)) Messages.normal(sender, args[1] + " wurde entbannt.");
        else Messages.error(sender, "Für diesen Spieler existiert kein aktiver Rival-Bann.");
    }

    private void warn(CommandSender sender, String[] args) {
        if (args.length < 3) { Messages.error(sender, "/admin warn <Spieler> <Grund>"); return; }
        OfflinePlayer target = findPlayer(args[1]);
        if (target == null) { Messages.error(sender, "Spieler nicht gefunden."); return; }
        try {
            var result = plugin.moderation().warn(target, String.join(" ", Arrays.copyOfRange(args, 2, args.length)), sender.getName());
            if (result.autoBanned()) Messages.normal(sender, args[1] + " wurde verwarnt und automatisch gebannt.");
            else sender.sendMessage(Messages.value("Verwarnungen für " + args[1] + ": ", result.count(), ""));
        } catch (IllegalArgumentException | ArithmeticException ex) { Messages.error(sender, ex.getMessage()); }
    }

    private void warnings(CommandSender sender, String[] args) {
        if (args.length < 2) { Messages.error(sender, "/admin warnings <Spieler> [list|clear]"); return; }
        OfflinePlayer target = findPlayer(args[1]);
        UUID uuid = target == null ? plugin.moderation().findUuidByName(args[1]) : target.getUniqueId();
        if (uuid == null) { Messages.error(sender, "Spieler nicht gefunden."); return; }
        if (args.length > 2 && args[2].equalsIgnoreCase("clear")) {
            sender.sendMessage(Messages.value("Gelöschte Verwarnungen: ", plugin.moderation().clearWarnings(uuid), ""));
            return;
        }
        var entries = plugin.moderation().warnings(uuid);
        sender.sendMessage(Messages.value("Verwarnungen für " + args[1] + ": ", entries.size(), ""));
        for (int i = 0; i < entries.size(); i++) {
            var warning = entries.get(i);
            sender.sendMessage(Messages.styledLine("&6#" + (i + 1) + " &7" + warning.reason() + " &8(von " + warning.actor() + ")"));
        }
    }

    private static OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        return Arrays.stream(Bukkit.getOfflinePlayers())
            .filter(player -> player.getName() != null && player.getName().equalsIgnoreCase(name))
            .findFirst().orElse(null);
    }

    private void players(CommandSender sender, String[] args) {
        if (sender instanceof Player player) {
            int page = 1;
            if (args.length > 1) try { page = Integer.parseInt(args[1]); }
            catch (NumberFormatException ex) { Messages.error(sender, "Die Seite muss eine Zahl sein."); return; }
            plugin.menus().openPlayerOverview(player, Math.max(0, page - 1));
            return;
        }
        List<PlayerRecord> records = plugin.data().players().stream()
            .sorted(Comparator.comparing(PlayerRecord::lastName, String.CASE_INSENSITIVE_ORDER)).toList();
        sender.sendMessage(Messages.value("Bekannte Projektspieler: ", records.size(), ""));
        for (PlayerRecord record : records) {
            boolean alive = !record.eliminated() && record.hearts() > 0;
            sender.sendMessage(Messages.value(record.lastName() + " • Herzen: ", record.hearts(),
                " • " + (alive ? record.side() == 0 ? "nicht zugewiesen" : "im Spiel" : "ausgeschieden")
                    + " • " + (Bukkit.getPlayer(record.uuid()) == null ? "offline" : "online")
                    + " • YouTube: " + (plugin.youtube().isActive(record.uuid()) ? "AN" : "AUS")));
        }
    }

    private void playtime(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("ranking")) {
            Messages.error(sender, "/admin playtime ranking");
            return;
        }
        List<PlayerRecord> ranking = plugin.data().players().stream()
            .sorted(Comparator.comparingLong(plugin.playtime()::playedToday).reversed()
                .thenComparing(PlayerRecord::lastName, String.CASE_INSENSITIVE_ORDER))
            .toList();
        sender.sendMessage(Messages.value("Playtime-Ranking heute: ", ranking.size(), " Spieler"));
        if (ranking.isEmpty()) {
            Messages.normal(sender, "Noch keine Spielzeit erfasst.");
            return;
        }
        for (int index = 0; index < ranking.size(); index++) {
            PlayerRecord record = ranking.get(index);
            sender.sendMessage(Messages.styledLine("&6#" + (index + 1) + " &b" + record.lastName()
                + " &8• &7" + plugin.playtime().formattedPlayed(record)));
        }
    }

    private void help(CommandSender sender) {
        Messages.normal(sender, "Admin-Modus • /admin mode • GUI: /admin • vanish • reload");
        Messages.normal(sender, "Dashboard • /admin • setup öffnet die Einrichtung mit Setup-Stick");
        Messages.normal(sender, "Projekt • project <start|stop|schedule> • setlocation waiting • spawn <negative|positive> <add|clear>");
        Messages.normal(sender, "Inseln • zone <nether|end> <pos1|pos2|clear|info> • mobrate <nether|end|overworld> <0-100>");
        Messages.normal(sender, "Spiel • border <on|off|toggle> • endfight <status|start|stop> • erzfeind");
        Messages.normal(sender, "Spieler • player <hearts|revive|eliminate|timereset|side> <Spieler> [Wert]");
        Messages.normal(sender, "Übersicht • players [Seite] zeigt Status, Herzen, YouTube, Clan, Combat und Spielzeit aller Spieler");
        Messages.normal(sender, "Playtime • playtime ranking zeigt die heute gespielte Zeit aller Spieler sortiert");
        Messages.normal(sender, "Blacklist • blacklist <add|remove|list|clear> [Material] sperrt Items vollständig");
        Messages.normal(sender, "Gräber • graves <count|deleteall|near|player> [Wert]");
        Messages.normal(sender, "Clans • clan <create|add|remove|owner|color|tag|info|disband> ...");
        Messages.normal(sender, "Kommunikation • broadcast <Text> • rules <add|remove|list> • Zeilenumbruch im Broadcast: \\n");
        Messages.normal(sender, "Moderation • ban <Spieler> <Dauer|permanent> <Grund> • unban <Spieler> • warn <Spieler> <Grund> • warnings <Spieler> [list|clear]");
        Messages.normal(sender, "Alle einfachen Einstellungen • /admin config <config.yml-Pfad> <Wert>");
        sender.sendMessage(Messages.styledLine("&8by pluginsmc.com"));
    }

    private static boolean parseBoolean(String raw) {
        if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("on") || raw.equalsIgnoreCase("an")) return true;
        if (raw.equalsIgnoreCase("false") || raw.equalsIgnoreCase("off") || raw.equalsIgnoreCase("aus")) return false;
        throw new IllegalArgumentException();
    }

    private static int parseSide(String raw, boolean zeroAllowed) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "negative", "left", "links", "-1" -> -1;
            case "positive", "right", "rechts", "1" -> 1;
            case "none", "keine", "0" -> { if (!zeroAllowed) throw new IllegalArgumentException(); yield 0; }
            default -> throw new IllegalArgumentException();
        };
    }

    private static ZoneManager.Zone parseZone(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "nether" -> ZoneManager.Zone.NETHER;
            case "end" -> ZoneManager.Zone.END;
            case "overworld" -> ZoneManager.Zone.OVERWORLD;
            default -> throw new IllegalArgumentException();
        };
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (sender instanceof Player && !sender.hasPermission("rival.admin")) return List.of();
        if (sender instanceof Player player && !plugin.adminMode().isActive(player))
            return args.length == 1 ? filter(List.of("mode"), args[0]) : List.of();
        if (args.length == 1) return filter(List.of("mode", "help", "vanish", "reload", "broadcast", "rules", "ban", "unban", "warn", "warnings", "players", "playtime", "blacklist", "border", "endfight", "erzfeind", "project", "setlocation", "spawn", "zone", "mobrate", "graves", "config", "player", "clan"), args[0]);
        if (args.length == 2) return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "border" -> filter(List.of("on", "off", "toggle"), args[1]);
            case "endfight" -> filter(List.of("status", "start", "stop"), args[1]);
            case "project" -> filter(List.of("start", "stop", "schedule"), args[1]);
            case "setlocation" -> filter(List.of("waiting"), args[1]);
            case "setup" -> List.of();
            case "spawn" -> filter(List.of("negative", "positive"), args[1]);
            case "zone" -> filter(List.of("nether", "end"), args[1]);
            case "mobrate" -> filter(List.of("nether", "end", "overworld"), args[1]);
            case "graves" -> filter(List.of("count", "deleteall", "near", "player"), args[1]);
            case "player" -> filter(List.of("hearts", "revive", "eliminate", "timereset", "side"), args[1]);
            case "clan" -> filter(List.of("create", "add", "remove", "owner", "color", "tag", "info", "disband"), args[1]);
            case "rules" -> filter(List.of("add", "remove", "list"), args[1]);
            case "playtime" -> filter(List.of("ranking"), args[1]);
            case "blacklist" -> filter(List.of("add", "remove", "list", "clear"), args[1]);
            case "ban", "unban", "warn", "warnings" -> filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
            default -> List.of();
        };
        if (args.length == 3 && args[0].equalsIgnoreCase("spawn")) return filter(List.of("add", "clear"), args[2]);
        if (args.length == 3 && args[0].equalsIgnoreCase("blacklist")) return filter(Arrays.stream(Material.values()).map(Material::name).toList(), args[2]);
        if (args.length == 3 && args[0].equalsIgnoreCase("zone")) return filter(List.of("pos1", "pos2", "clear", "info"), args[2]);
        if (args.length == 3 && args[0].equalsIgnoreCase("player")) return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        if (args.length == 3 && args[0].equalsIgnoreCase("clan")) {
            if (args[1].equalsIgnoreCase("remove")) return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
            if (args[1].equalsIgnoreCase("create")) return List.of();
            return filter(new ArrayList<>(plugin.data().clans().keySet()), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("clan") && List.of("create", "add", "owner").contains(args[1].toLowerCase(Locale.ROOT)))
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[3]);
        if (args.length == 3 && args[0].equalsIgnoreCase("graves") && args[1].equalsIgnoreCase("player")) return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        if (args.length == 3 && args[0].equalsIgnoreCase("ban")) return filter(List.of("30m", "12h", "1d", "5d", "1w", "permanent"), args[2]);
        if (args.length == 3 && args[0].equalsIgnoreCase("warnings")) return filter(List.of("list", "clear"), args[2]);
        if (args.length == 4 && args[0].equalsIgnoreCase("player") && args[1].equalsIgnoreCase("side")) return filter(List.of("-1", "0", "1"), args[3]);
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        return values.stream().filter(value -> value.regionMatches(true, 0, prefix, 0, prefix.length())).toList();
    }
}
