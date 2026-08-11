package de.minecraft.rival.util;

import de.minecraft.rival.RivalPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.ZoneId;
import java.util.Locale;

public final class ConfigSanitizer {
    private ConfigSanitizer() {}

    public static void sanitize(RivalPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        boolean changed = false;
        try {
            ZoneId.of(config.getString("general.timezone", "Europe/Vienna"));
        } catch (RuntimeException ex) {
            config.set("general.timezone", "Europe/Vienna");
            plugin.getLogger().warning("Ungültige general.timezone wurde auf Europe/Vienna zurückgesetzt.");
            changed = true;
        }
        String axis = config.getString("border.axis", "X").toUpperCase(Locale.ROOT);
        if (!axis.equals("X") && !axis.equals("Z")) {
            config.set("border.axis", "X");
            plugin.getLogger().warning("Ungültige border.axis wurde auf X zurückgesetzt.");
            changed = true;
        }
        if (!RivalWorld.NAME.equals(config.getString("border.world"))) {
            config.set("border.world", RivalWorld.NAME);
            plugin.getLogger().warning("border.world wurde verbindlich auf " + RivalWorld.NAME + " gesetzt.");
            changed = true;
        }
        changed |= clamp(config, "security.handshake-timeout-seconds", 2, 60);
        changed |= clamp(config, "combat.duration-seconds", 1, 86_400);
        changed |= clamp(config, "combat.maximum-hearts", 1, 3);
        int maximum = config.getInt("combat.maximum-hearts", 3);
        changed |= clamp(config, "combat.starting-hearts", 1, maximum);
        changed |= clamp(config, "grave.lifetime-hours", 1, 8760);
        changed |= clamp(config, "playtime.daily-minutes", 0, 1440);
        changed |= clamp(config, "project.waiting-radius", 1.0, 1000.0);
        changed |= clamp(config, "border.side-capacity", 1, 1000);
        changed |= clamp(config, "border.split-coordinate", -29_999_000.0, 29_999_000.0);
        changed |= clamp(config, "border.visual-distance", 4.0, 64.0);
        changed |= clamp(config, "border.particle-spacing", 1.0, 8.0);
        changed |= clamp(config, "zones.nether.spawn-rate-percent", 0, 100);
        changed |= clamp(config, "zones.end.spawn-rate-percent", 0, 100);
        changed |= clamp(config, "zones.overworld.spawn-rate-percent", 0, 100);
        changed |= clamp(config, "end-fight.border-size", 1.0, 59_999_968.0);
        changed |= clamp(config, "end-fight.center-x", -29_999_000.0, 29_999_000.0);
        changed |= clamp(config, "end-fight.center-y", -2048.0, 2048.0);
        changed |= clamp(config, "end-fight.center-z", -29_999_000.0, 29_999_000.0);
        changed |= clamp(config, "clans.maximum-members", 1, 100);
        changed |= clamp(config, "moderation.warns-before-ban", 1, 100);
        changed |= clamp(config, "moderation.auto-ban-days", 1, 3650);
        changed |= clamp(config, "youtube.confirmation-seconds", 5, 300);
        if (changed) plugin.saveConfig();
    }

    private static boolean clamp(FileConfiguration config, String path, int minimum, int maximum) {
        int value = config.getInt(path, minimum);
        int safe = Math.max(minimum, Math.min(maximum, value));
        if (value == safe) return false;
        config.set(path, safe);
        return true;
    }

    private static boolean clamp(FileConfiguration config, String path, double minimum, double maximum) {
        double value = config.getDouble(path, minimum);
        double safe = Double.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : minimum;
        if (Double.compare(value, safe) == 0) return false;
        config.set(path, safe);
        return true;
    }
}
