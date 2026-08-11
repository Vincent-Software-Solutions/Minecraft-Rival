package de.minecraft.rival.command;

import de.minecraft.rival.game.RuleManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class RulesCommand implements CommandExecutor {
    private final RuleManager rules;

    public RulesCommand(RuleManager rules) {
        this.rules = rules;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             String @NotNull [] args) {
        rules.show(sender);
        return true;
    }
}
