package de.minecraft.rival.game;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

public final class RuleManager {
    private final RivalPlugin plugin;
    private final File file;
    private final Map<Integer, String> rules = new TreeMap<>();
    private int nextId = 1;

    public RuleManager(RivalPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rules.yml");
        load();
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        nextId = Math.max(1, yaml.getInt("next-id", 1));
        ConfigurationSection section = yaml.getConfigurationSection("rules");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                String text = section.getString(key);
                if (id > 0 && text != null && !text.isBlank()) {
                    rules.put(id, text);
                    nextId = Math.max(nextId, id + 1);
                }
            } catch (NumberFormatException ignored) {
                plugin.getLogger().warning("Ungültige Regel-ID in rules.yml: " + key);
            }
        }
    }

    public int add(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Die Regel darf nicht leer sein.");
        int id = nextId++;
        rules.put(id, text.strip());
        save();
        return id;
    }

    public boolean remove(int id) {
        if (rules.remove(id) == null) return false;
        save();
        return true;
    }

    public Map<Integer, String> entries() {
        return Map.copyOf(rules);
    }

    public void show(CommandSender sender) {
        Messages.normal(sender, "Projektregeln:");
        if (rules.isEmpty()) {
            Messages.normal(sender, "Es wurden noch keine Regeln eingetragen.");
            return;
        }
        rules.forEach((id, text) -> sender.sendMessage(Messages.styledLine("&6#" + id + " &7" + text)));
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("next-id", nextId);
        rules.forEach((id, text) -> yaml.set("rules." + id, text));
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "rules.yml konnte nicht gespeichert werden", ex);
        }
    }
}
