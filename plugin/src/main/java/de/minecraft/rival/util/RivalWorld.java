package de.minecraft.rival.util;

import de.minecraft.rival.RivalPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/** Resolves the dedicated project map without requiring it to be the server's primary world. */
public final class RivalWorld {
    public static final String NAME = "rival_main";

    private RivalWorld() {}

    public static World loadProjectWorld(RivalPlugin plugin) {
        World loaded = Bukkit.getWorld(NAME);
        if (loaded == null) {
            File folder = new File(Bukkit.getWorldContainer(), NAME);
            if (!folder.isDirectory()) {
                plugin.getLogger().severe("Der zusätzliche Weltordner '" + folder.getAbsolutePath() + "' wurde nicht gefunden.");
                plugin.getLogger().severe("Lege die Projektwelt als Ordner '" + NAME + "' neben der normalen Welt ab.");
                return null;
            }
            try {
                plugin.getLogger().info("Lade zusätzliche Projektwelt '" + NAME + "' ...");
                loaded = Bukkit.createWorld(new WorldCreator(NAME).environment(World.Environment.NORMAL));
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.SEVERE, "Die zusätzliche Projektwelt '" + NAME + "' konnte nicht geladen werden.", ex);
                return null;
            }
        }
        if (loaded == null || loaded.getEnvironment() != World.Environment.NORMAL) {
            plugin.getLogger().severe("'" + NAME + "' muss eine normale Overworld sein.");
            return null;
        }
        plugin.getLogger().info("Projektwelt erkannt: " + loaded.getName()
            + " (primäre Serverwelt: " + Bukkit.getWorlds().get(0).getName() + ")");
        return loaded;
    }

    /** Rewrites serialized Bukkit locations before YAML deserialization can reject the renamed world. */
    public static void rewriteLegacyConfigWorld(RivalPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.isFile()) return;
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            boolean changed = false;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String rewritten = rewriteWorldLine(line);
                if (!rewritten.equals(line)) {
                    lines.set(i, rewritten);
                    changed = true;
                }
            }
            if (changed) {
                Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
                plugin.getLogger().info("Alte Weltverweise in config.yml wurden vor dem Laden auf '" + NAME + "' migriert.");
            }
            plugin.reloadConfig();
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "config.yml konnte nicht auf " + NAME + " migriert werden.", ex);
        }
    }

    static String rewriteWorldLine(String line) {
        String trimmed = line.stripLeading();
        if (!trimmed.startsWith("world:")) return line;
        String value = trimmed.substring("world:".length()).strip();
        if (value.equals(NAME) || value.equals('"' + NAME + '"') || value.equals('\'' + NAME + '\'')) return line;
        int indentation = line.length() - trimmed.length();
        return line.substring(0, indentation) + "world: " + NAME;
    }

    /** Keeps coordinates while moving legacy setup locations from the old world name to rival_main. */
    public static boolean migrateConfiguredLocations(RivalPlugin plugin, World mainWorld) {
        boolean changed = migrateLocation(plugin, mainWorld, "project.waiting-room");
        changed |= migrateLocation(plugin, mainWorld, "zones.nether.pos1");
        changed |= migrateLocation(plugin, mainWorld, "zones.nether.pos2");
        changed |= migrateLocation(plugin, mainWorld, "zones.end.pos1");
        changed |= migrateLocation(plugin, mainWorld, "zones.end.pos2");
        changed |= migrateLocations(plugin, mainWorld, "project.spawns.negative");
        changed |= migrateLocations(plugin, mainWorld, "project.spawns.positive");
        if (changed) {
            plugin.saveConfig();
            plugin.getLogger().info("Gespeicherte Projektpositionen wurden auf '" + NAME + "' umgestellt.");
        }
        return changed;
    }

    private static boolean migrateLocation(RivalPlugin plugin, World mainWorld, String path) {
        Location location = plugin.getConfig().getLocation(path);
        if (location == null || mainWorld.equals(location.getWorld())) return false;
        plugin.getConfig().set(path, inMainWorld(location, mainWorld));
        return true;
    }

    private static boolean migrateLocations(RivalPlugin plugin, World mainWorld, String path) {
        List<?> raw = plugin.getConfig().getList(path);
        if (raw == null || raw.isEmpty()) return false;
        boolean changed = false;
        List<Object> migrated = new ArrayList<>(raw.size());
        for (Object value : raw) {
            if (value instanceof Location location && !mainWorld.equals(location.getWorld())) {
                migrated.add(inMainWorld(location, mainWorld));
                changed = true;
            } else {
                migrated.add(value);
            }
        }
        if (changed) plugin.getConfig().set(path, migrated);
        return changed;
    }

    private static Location inMainWorld(Location source, World mainWorld) {
        return new Location(mainWorld, source.getX(), source.getY(), source.getZ(), source.getYaw(), source.getPitch());
    }
}
