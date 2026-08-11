package de.minecraft.rival.command;

import de.minecraft.rival.game.PlaytimeManager;
import de.minecraft.rival.util.Messages;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class PlaytimeCommand implements CommandExecutor {
    private final PlaytimeManager playtime;
    public PlaytimeCommand(PlaytimeManager playtime) { this.playtime = playtime; }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) { Messages.error(sender, "Dieser Befehl ist nur für Spieler."); return true; }
        if (args.length == 1 && args[0].equalsIgnoreCase("anzeige")) {
            playtime.toggleBar(player);
            Messages.normal(player, "Die Spielzeit-Anzeige wurde umgeschaltet.");
            return true;
        }
        var record = playtime.current(player);
        player.sendMessage(Messages.value("Heute gespielt: ", playtime.formattedPlayed(record), ""));
        player.sendMessage(Messages.value("Verbleibende tägliche Spielzeit: ", playtime.formatted(record), ""));
        return true;
    }
}
