package de.minecraft.rival.game;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.util.Messages;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PresentationListener implements Listener {
    private final RivalPlugin plugin;
    public PresentationListener(RivalPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(Messages.value("", event.getPlayer().getName(), " betritt das Rival-Projekt."));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(Messages.value("", event.getPlayer().getName(), " hat das Rival-Projekt verlassen."));
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) { event.message(null); }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        event.renderer((source, sourceDisplayName, message, viewer) ->
            Component.text("", NamedTextColor.DARK_GRAY)
                .append(sourceDisplayName.color(NamedTextColor.AQUA))
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                .append(message.color(NamedTextColor.GRAY)));
    }
}
