package de.minecraft.rival.game;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.data.ClanRecord;
import de.minecraft.rival.data.DataStore;
import de.minecraft.rival.data.PlayerRecord;
import de.minecraft.rival.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.regex.Pattern;

public final class ClanManager {
    private static final Pattern NAME = Pattern.compile("[A-Za-zÄÖÜäöü0-9_-]{2,16}");
    private static final Pattern TAG = Pattern.compile("[A-Za-zÄÖÜäöü0-9]{2,6}");
    private static final Pattern COLOR = Pattern.compile("&[0-9a-f]");
    private final RivalPlugin plugin;
    private final DataStore data;
    private final Map<UUID, Invite> invites = new HashMap<>();

    public ClanManager(RivalPlugin plugin, DataStore data) {
        this.plugin = plugin;
        this.data = data;
    }

    public ClanRecord clan(Player player) {
        PlayerRecord record = data.player(player.getUniqueId(), player.getName());
        return record.clanId() == null ? null : data.clans().get(record.clanId());
    }

    public ClanRecord clan(UUID player) {
        PlayerRecord record = data.player(player);
        return record == null || record.clanId() == null ? null : data.clans().get(record.clanId());
    }

    public boolean create(Player owner, String name) {
        if (clan(owner) != null) return error(owner, "Du bist bereits in einem Clan.");
        if (!NAME.matcher(name).matches()) return error(owner, "Clannamen: 2–16 Buchstaben, Zahlen, _ oder -.");
        String id = name.toLowerCase(Locale.ROOT);
        if (data.clans().containsKey(id)) return error(owner, "Dieser Clanname ist bereits vergeben.");
        ClanRecord clan = new ClanRecord(id, name, owner.getUniqueId());
        data.clans().put(id, clan);
        data.player(owner.getUniqueId(), owner.getName()).clanId(id);
        data.save();
        Messages.normal(owner, "Clan " + name + " wurde gegründet.");
        return true;
    }

    public boolean invite(Player owner, Player target) {
        ClanRecord clan = clan(owner);
        if (clan == null) return error(owner, "Du bist in keinem Clan.");
        if (!clan.owner().equals(owner.getUniqueId())) return error(owner, "Nur der Clanbesitzer darf einladen.");
        if (clan.members().size() >= maximum()) return error(owner, "Der Clan ist voll.");
        if (clan(target) != null) return error(owner, "Dieser Spieler ist bereits in einem Clan.");
        invites.put(target.getUniqueId(), new Invite(clan.id(), System.currentTimeMillis() + 120_000));
        target.sendMessage(Messages.value("Du wurdest in den Clan ", clan.name(), " eingeladen. /clan accept"));
        Messages.normal(owner, target.getName() + " wurde eingeladen.");
        return true;
    }

    public boolean accept(Player player) {
        Invite invite = invites.remove(player.getUniqueId());
        if (invite == null || invite.expiresAt < System.currentTimeMillis()) return error(player, "Du hast keine gültige Einladung.");
        ClanRecord clan = data.clans().get(invite.clanId);
        if (clan == null || clan.members().size() >= maximum() || clan(player) != null) return error(player, "Diese Einladung kann nicht mehr angenommen werden.");
        clan.members().add(player.getUniqueId());
        data.player(player.getUniqueId(), player.getName()).clanId(clan.id());
        data.save();
        announce(clan, player.getName() + " ist dem Clan beigetreten.");
        return true;
    }

    public boolean kick(Player owner, UUID target) {
        ClanRecord clan = clan(owner);
        if (clan == null || !clan.owner().equals(owner.getUniqueId())) return error(owner, "Nur der Clanbesitzer darf Spieler entfernen.");
        if (target.equals(owner.getUniqueId())) return error(owner, "Nutze /clan leave, um den Clan aufzulösen.");
        if (!clan.members().remove(target)) return error(owner, "Dieser Spieler ist nicht in deinem Clan.");
        PlayerRecord record = data.player(target);
        if (record != null) record.clanId(null);
        data.save();
        Messages.normal(owner, "Spieler wurde aus dem Clan entfernt.");
        Player online = Bukkit.getPlayer(target);
        if (online != null) Messages.error(online, "Du wurdest aus deinem Clan entfernt.");
        return true;
    }

    public boolean leave(Player player) {
        ClanRecord clan = clan(player);
        if (clan == null) return error(player, "Du bist in keinem Clan.");
        if (clan.owner().equals(player.getUniqueId())) {
            for (UUID member : clan.members()) {
                PlayerRecord record = data.player(member);
                if (record != null) record.clanId(null);
            }
            data.clans().remove(clan.id());
            Messages.normal(player, "Der Clan wurde aufgelöst.");
        } else {
            clan.members().remove(player.getUniqueId());
            data.player(player.getUniqueId(), player.getName()).clanId(null);
            announce(clan, player.getName() + " hat den Clan verlassen.");
        }
        data.save();
        return true;
    }

    public boolean setColor(Player player, String color) {
        ClanRecord clan = owned(player);
        if (clan == null) return false;
        color = color.startsWith("&") ? color.toLowerCase(Locale.ROOT) : "&" + color.toLowerCase(Locale.ROOT);
        if (!COLOR.matcher(color).matches()) return error(player, "Erlaubte Farben: 0–9 und a–f.");
        clan.color(color); data.save(); Messages.normal(player, "Clanfarbe geändert."); return true;
    }

    public boolean setTag(Player player, String tag) {
        ClanRecord clan = owned(player);
        if (clan == null) return false;
        if (!TAG.matcher(tag).matches()) return error(player, "Clantag: 2–6 Buchstaben oder Zahlen.");
        clan.tag(tag); data.save(); Messages.normal(player, "Clantag geändert."); return true;
    }

    private ClanRecord owned(Player player) {
        ClanRecord clan = clan(player);
        if (clan == null) { error(player, "Du bist in keinem Clan."); return null; }
        if (!clan.owner().equals(player.getUniqueId())) { error(player, "Nur der Clanbesitzer darf das ändern."); return null; }
        return clan;
    }

    public ClanRecord find(String name) {
        return name == null ? null : data.clans().get(name.toLowerCase(Locale.ROOT));
    }

    public ClanRecord adminCreate(OfflinePlayer owner, String name) {
        validateName(name);
        if (clan(owner.getUniqueId()) != null) throw new IllegalArgumentException("Der Besitzer ist bereits in einem Clan.");
        String id = name.toLowerCase(Locale.ROOT);
        if (data.clans().containsKey(id)) throw new IllegalArgumentException("Dieser Clanname ist bereits vergeben.");
        ClanRecord clan = new ClanRecord(id, name, owner.getUniqueId());
        data.clans().put(id, clan);
        data.player(owner.getUniqueId(), Optional.ofNullable(owner.getName()).orElse("?")).clanId(id);
        data.save();
        return clan;
    }

    public void adminAdd(ClanRecord clan, OfflinePlayer target) {
        if (clan == null) throw new IllegalArgumentException("Clan nicht gefunden.");
        if (clan(target.getUniqueId()) != null) throw new IllegalArgumentException("Dieser Spieler ist bereits in einem Clan.");
        if (clan.members().size() >= maximum()) throw new IllegalArgumentException("Der Clan ist voll.");
        clan.members().add(target.getUniqueId());
        data.player(target.getUniqueId(), Optional.ofNullable(target.getName()).orElse("?")).clanId(clan.id());
        data.save();
    }

    public void adminRemove(OfflinePlayer target) {
        ClanRecord clan = clan(target.getUniqueId());
        if (clan == null) throw new IllegalArgumentException("Dieser Spieler ist in keinem Clan.");
        if (clan.owner().equals(target.getUniqueId())) throw new IllegalArgumentException("Übertrage zuerst den Besitz oder löse den Clan auf.");
        clan.members().remove(target.getUniqueId());
        PlayerRecord record = data.player(target.getUniqueId());
        if (record != null) record.clanId(null);
        data.save();
    }

    public void adminSetColor(ClanRecord clan, String color) {
        if (clan == null) throw new IllegalArgumentException("Clan nicht gefunden.");
        String normalized = color.startsWith("&") ? color.toLowerCase(Locale.ROOT) : "&" + color.toLowerCase(Locale.ROOT);
        if (!COLOR.matcher(normalized).matches()) throw new IllegalArgumentException("Erlaubte Farben: 0–9 und a–f.");
        clan.color(normalized);
        data.save();
    }

    public void adminSetTag(ClanRecord clan, String tag) {
        if (clan == null) throw new IllegalArgumentException("Clan nicht gefunden.");
        if (!TAG.matcher(tag).matches()) throw new IllegalArgumentException("Clantag: 2–6 Buchstaben oder Zahlen.");
        clan.tag(tag);
        data.save();
    }

    public void adminSetOwner(ClanRecord clan, OfflinePlayer owner) {
        if (clan == null) throw new IllegalArgumentException("Clan nicht gefunden.");
        ClanRecord existing = clan(owner.getUniqueId());
        if (existing != null && existing != clan) throw new IllegalArgumentException("Dieser Spieler ist bereits in einem anderen Clan.");
        if (existing == null && clan.members().size() >= maximum()) throw new IllegalArgumentException("Der Clan ist voll.");
        clan.members().add(owner.getUniqueId());
        data.player(owner.getUniqueId(), Optional.ofNullable(owner.getName()).orElse("?")).clanId(clan.id());
        clan.owner(owner.getUniqueId());
        data.save();
    }

    private static void validateName(String name) {
        if (name == null || !NAME.matcher(name).matches()) throw new IllegalArgumentException("Clannamen: 2–16 Buchstaben, Zahlen, _ oder -.");
    }

    public int maximum() { return Math.max(1, plugin.getConfig().getInt("clans.maximum-members", 4)); }
    public String placeholder(Player player) {
        ClanRecord clan = clan(player);
        return clan == null ? "" : "&8[" + clan.color() + clan.tag() + "&8]";
    }

    private void announce(ClanRecord clan, String text) {
        clan.members().stream().map(Bukkit::getPlayer).filter(Objects::nonNull).forEach(player -> Messages.normal(player, text));
    }

    private static boolean error(Player player, String text) { Messages.error(player, text); return false; }
    private record Invite(String clanId, long expiresAt) {}
}
