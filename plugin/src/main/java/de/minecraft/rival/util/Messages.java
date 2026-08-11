package de.minecraft.rival.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public final class Messages {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('&').hexColors().build();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.builder().character('§')
        .hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static Component prefix = LEGACY.deserialize("&#FF0000&lR&#FD462B&lI&#FA8B56&lV&#FCA359&lA&#FDBB5B&lL&#FFD35E&lS &r&8&l➜ ");

    private Messages() {}

    public static void load(FileConfiguration config) {
        prefix = LEGACY.deserialize(config.getString("general.prefix", "&#FF0000&lR&#FD462B&lI&#FA8B56&lV&#FCA359&lA&#FDBB5B&lL&#FFD35E&lS &r&8&l➜ "));
    }

    public static Component text(String value) {
        return LEGACY.deserialize(value == null ? "" : value);
    }

    public static Component normal(String value) {
        return prefix.append(Component.text(value, NamedTextColor.GRAY));
    }

    public static Component error(String value) {
        return prefix.append(Component.text(value, NamedTextColor.RED));
    }

    public static Component value(String before, Object value, String after) {
        return prefix.append(Component.text(before, NamedTextColor.GRAY))
            .append(Component.text(String.valueOf(value), NamedTextColor.GOLD))
            .append(Component.text(after, NamedTextColor.GRAY));
    }

    public static Component styledLine(String value) {
        return prefix.append(LEGACY.deserialize("&7" + (value == null ? "" : value)));
    }

    public static String legacy(Component value) { return SECTION.serialize(value); }

    public static void normal(CommandSender sender, String value) { sender.sendMessage(normal(value)); }
    public static void error(CommandSender sender, String value) { sender.sendMessage(error(value)); }
}
