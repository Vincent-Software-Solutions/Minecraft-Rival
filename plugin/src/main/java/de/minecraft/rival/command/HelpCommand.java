package de.minecraft.rival.command;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class HelpCommand implements CommandExecutor {
    private final RivalPlugin plugin;
    public HelpCommand(RivalPlugin plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            Messages.normal(sender, "Spielerhilfe: /spielzeit, /clan help, /youtube, /rules; Adminhilfe: /admin help");
            sender.sendMessage(Messages.styledLine("&8by pluginsmc.com"));
            return true;
        }
        plugin.menus().openHelp(player);
        return true;
    }
}
