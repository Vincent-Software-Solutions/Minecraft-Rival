package de.minecraft.rival.game;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;

public final class PresentationListener implements Listener {
    public PresentationListener(RivalPlugin plugin) {
        Bukkit.getWorlds().forEach(this::hideAdvancements);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.setJoinMessage(Messages.legacy(Messages.value("", event.getPlayer().getName(), " betritt das Rival-Projekt.")));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.setQuitMessage(Messages.legacy(Messages.value("", event.getPlayer().getName(), " hat das Rival-Projekt verlassen.")));
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) { hideAdvancements(event.getWorld()); }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        event.setFormat(ChatColor.GOLD + "%1$s" + ChatColor.DARK_GRAY + " » " + ChatColor.GRAY + "%2$s");
    }

    private void hideAdvancements(org.bukkit.World world) {
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
    }
}
