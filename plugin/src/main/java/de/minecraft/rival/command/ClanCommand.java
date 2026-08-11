package de.minecraft.rival.command;

import de.minecraft.rival.data.ClanRecord;
import de.minecraft.rival.game.ClanManager;
import de.minecraft.rival.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class ClanCommand implements CommandExecutor, TabCompleter {
    private final ClanManager clans;
    public ClanCommand(ClanManager clans) { this.clans = clans; }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) { Messages.error(sender, "Dieser Befehl ist nur für Spieler."); return true; }
        if (args.length == 0) { help(player); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> { if (need(player, args, 2)) clans.create(player, args[1]); }
            case "invite" -> { if (need(player, args, 2)) { Player target = Bukkit.getPlayerExact(args[1]); if (target == null) Messages.error(player, "Spieler ist nicht online."); else clans.invite(player, target); } }
            case "accept" -> clans.accept(player);
            case "kick" -> { if (need(player, args, 2)) { OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]); if (target == null) Messages.error(player, "Spieler nicht gefunden."); else clans.kick(player, target.getUniqueId()); } }
            case "leave" -> clans.leave(player);
            case "color" -> { if (need(player, args, 2)) clans.setColor(player, args[1]); }
            case "tag" -> { if (need(player, args, 2)) clans.setTag(player, args[1]); }
            case "info" -> info(player);
            case "help" -> help(player);
            default -> help(player);
        }
        return true;
    }

    private void info(Player player) {
        ClanRecord clan = clans.clan(player);
        if (clan == null) { Messages.error(player, "Du bist in keinem Clan."); return; }
        player.sendMessage(Messages.value("Clan: ", clan.name(), " • Tag: " + clan.tag()));
        player.sendMessage(Messages.value("Mitglieder: ", clan.members().size(), "/" + clans.maximum()));
        String names = clan.members().stream().map(Bukkit::getOfflinePlayer).map(p -> Optional.ofNullable(p.getName()).orElse("?"))
            .reduce((a, b) -> a + ", " + b).orElse("-");
        Messages.normal(player, names);
    }

    private void help(Player player) {
        Messages.normal(player, "Clan • /clan create <Name> • invite <Spieler> • accept • kick <Spieler>");
        Messages.normal(player, "Clan • /clan leave • color <0-9/a-f> • tag <2-6 Zeichen> • info");
        player.sendMessage(Messages.styledLine("&8by pluginsmc.com"));
    }

    private boolean need(Player player, String[] args, int count) { if (args.length >= count) return true; Messages.error(player, "Es fehlen Argumente. Nutze /clan help."); return false; }

    @Override public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (args.length == 1) return filter(List.of("create", "invite", "accept", "kick", "leave", "color", "tag", "info", "help"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick")))
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        return List.of();
    }
    private static List<String> filter(List<String> values, String prefix) { return values.stream().filter(v -> v.regionMatches(true, 0, prefix, 0, prefix.length())).toList(); }
}
