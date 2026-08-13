package de.minecraft.rival.game;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.util.Messages;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public final class AdminBroadcastManager {
    private final RivalPlugin plugin;
    private final File file;
    private final Map<UUID, List<String>> queued = new HashMap<>();

    public AdminBroadcastManager(RivalPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "broadcast-queue.yml");
        load();
    }

    public void broadcastNow(String message) {
        String normalized = message == null ? "" : message.replace("\\n", "\n");
        for (String line : normalized.split("\n", -1)) Messages.broadcast(Messages.styledLine(line));
    }

    public int flush(Player admin) {
        List<String> messages = queued.remove(admin.getUniqueId());
        if (messages == null || messages.isEmpty()) return 0;
        messages.forEach(this::broadcastNow);
        save();
        Messages.normal(admin, messages.size() + " wartende Admin-Broadcast(s) wurden gesendet.");
        return messages.size();
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var root = yaml.getConfigurationSection("queue");
        if (root == null) return;
        for (String key : root.getKeys(false)) try {
            List<String> messages = new ArrayList<>(yaml.getStringList("queue." + key + ".messages"));
            if (!messages.isEmpty()) queued.put(UUID.fromString(key), messages);
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Ungültige Broadcast-Warteschlange für " + key);
        }
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        queued.forEach((uuid, messages) -> yaml.set("queue." + uuid + ".messages", messages));
        try { yaml.save(file); }
        catch (IOException ex) { plugin.getLogger().log(Level.SEVERE, "broadcast-queue.yml konnte nicht gespeichert werden", ex); }
    }
}
