package de.minecraft.rival.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Message formatting limited to the Bukkit/Spigot 1.20.1 API for Mohist compatibility. */
public final class Messages {
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static String prefix = color("&#FF0000&lR&#FD462B&lI&#FA8B56&lV&#FCA359&lA&#FDBB5B&lL&#FFD35E&lS &r&8&l➜ ");

    private Messages() {}

    public static void load(FileConfiguration config) {
        prefix = color(config.getString("general.prefix", "&#FF0000&lR&#FD462B&lI&#FA8B56&lV&#FCA359&lA&#FDBB5B&lL&#FFD35E&lS &r&8&l➜ "));
    }

    public static String text(String value) { return color(value); }
    public static String normal(String value) { return prefix + ChatColor.GRAY + safe(value); }
    public static String error(String value) { return prefix + ChatColor.RED + safe(value); }
    public static String value(String before, Object value, String after) {
        return prefix + ChatColor.GRAY + safe(before) + ChatColor.GOLD + String.valueOf(value) + ChatColor.GRAY + safe(after);
    }
    public static String styledLine(String value) { return prefix + color("&7" + safe(value)); }
    public static String legacy(String value) { return value; }

    public static void normal(CommandSender sender, String value) { sender.sendMessage(normal(value)); }
    public static void error(CommandSender sender, String value) { sender.sendMessage(error(value)); }

    /** Sends a chat announcement without relying on Paper's Bukkit.broadcast(Component). */
    public static void broadcast(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) player.sendMessage(message);
        Bukkit.getConsoleSender().sendMessage(message);
    }

    public static String color(String value) {
        String source = safe(value);
        Matcher matcher = HEX.matcher(source);
        StringBuffer converted = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char digit : hex.toCharArray()) replacement.append('§').append(digit);
            matcher.appendReplacement(converted, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(converted);
        return ChatColor.translateAlternateColorCodes('&', converted.toString());
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
