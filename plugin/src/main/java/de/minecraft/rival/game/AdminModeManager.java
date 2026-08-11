package de.minecraft.rival.game;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.util.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class AdminModeManager implements Listener {
    private final RivalPlugin plugin;
    private final Set<UUID> active = new HashSet<>();

    public AdminModeManager(RivalPlugin plugin) { this.plugin = plugin; }

    public boolean isActive(Player player) {
        return player.hasPermission("rival.admin") && active.contains(player.getUniqueId());
    }

    public boolean toggle(Player player) {
        if (!player.hasPermission("rival.admin")) {
            Messages.error(player, "Dafür fehlt dir rival.admin.");
            return false;
        }
        if (isActive(player)) disable(player, true);
        else {
            active.add(player.getUniqueId());
            plugin.projects().hideCountdown(player);
            plugin.playtime().refreshVisibility(player);
            Messages.normal(player, "Admin-Modus aktiviert. Adminbefehle sind jetzt verfügbar und deine Spielzeit pausiert.");
            player.sendMessage(Messages.value("Verbleibende Projektspieler: ", plugin.endFight().remainingPlayers().size(), " • Details: /admin endfight status"));
            plugin.broadcasts().flush(player);
        }
        return true;
    }

    public void disable(Player player, boolean message) {
        if (!active.remove(player.getUniqueId())) return;
        plugin.vanish().disable(player);
        plugin.projects().placePlayer(player);
        if (message) Messages.normal(player, "Admin-Modus deaktiviert. Du agierst wieder als normaler Spieler.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { active.remove(event.getPlayer().getUniqueId()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (isActive(event.getPlayer())) disable(event.getPlayer(), false);
    }
}
