package de.minecraft.rival.placeholder;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.data.ClanRecord;
import de.minecraft.rival.game.ClanManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RivalExpansion extends PlaceholderExpansion {
    private final RivalPlugin plugin;
    private final ClanManager clans;
    public RivalExpansion(RivalPlugin plugin, ClanManager clans) { this.plugin = plugin; this.clans = clans; }
    @Override public @NotNull String getIdentifier() { return "rival"; }
    @Override public @NotNull String getAuthor() { return "MinecraftRival"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer offline, @NotNull String params) {
        Player player = offline.getPlayer();
        if (player == null) return "";
        ClanRecord clan = clans.clan(player);
        return switch (params.toLowerCase()) {
            case "clan" -> clans.placeholder(player);
            case "clan_name" -> clan == null ? "" : clan.name();
            case "clan_tag" -> clan == null ? "" : clan.tag();
            case "clan_color" -> clan == null ? "" : clan.color();
            case "hearts" -> Integer.toString(plugin.data().player(player.getUniqueId(), player.getName()).hearts());
            default -> null;
        };
    }
}
