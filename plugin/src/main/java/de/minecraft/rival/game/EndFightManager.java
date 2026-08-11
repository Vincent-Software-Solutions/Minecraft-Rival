package de.minecraft.rival.game;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.data.DataStore;
import de.minecraft.rival.data.PlayerRecord;
import de.minecraft.rival.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;

public final class EndFightManager implements Listener {
    private final RivalPlugin plugin;
    private final DataStore data;
    private final BorderManager borders;
    private final File stateFile;
    private boolean running;
    private boolean readyNotified;
    private BorderSnapshot previous;

    public EndFightManager(RivalPlugin plugin, DataStore data, BorderManager borders) {
        this.plugin = plugin;
        this.data = data;
        this.borders = borders;
        this.stateFile = new File(plugin.getDataFolder(), "endfight-state.yml");
    }

    public void enable() {
        restoreInterruptedFight();
    }

    /** Informiert Admins nur über die Bereitschaft; ein automatischer Start findet ausdrücklich nicht statt. */
    public void checkAutomaticStart() {
        int count = remainingPlayers().size();
        if (!plugin.projects().isStarted() || count != 2) {
            readyNotified = false;
            return;
        }
        if (readyNotified || running) return;
        readyNotified = true;
        Bukkit.getOnlinePlayers().stream().filter(plugin.adminMode()::isActive).forEach(admin ->
            Messages.normal(admin, "Noch 2 Spieler übrig. Prüfe /admin endfight status und starte den Endkampf manuell."));
    }

    public List<PlayerRecord> remainingPlayers() {
        return data.players().stream()
            .filter(record -> !record.eliminated() && record.hearts() > 0 && record.side() != 0)
            .sorted(Comparator.comparing(PlayerRecord::lastName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public void showStatus(CommandSender sender) {
        List<PlayerRecord> remaining = remainingPlayers();
        sender.sendMessage(Messages.value("Verbleibende Spieler: ", remaining.size(), ""));
        remaining.forEach(record -> sender.sendMessage(Messages.value(record.lastName() + ": ", record.hearts(),
            (record.hearts() == 1 ? " Herz" : " Herzen") + (Bukkit.getPlayer(record.uuid()) == null ? " • offline" : " • online"))));
        int online = (int) remaining.stream().filter(record -> Bukkit.getPlayer(record.uuid()) != null).count();
        if (de.minecraft.rival.util.RivalRules.canStartEndFight(remaining.size(), online))
            Messages.normal(sender, "Der Endkampf kann jetzt mit /admin endfight start gestartet werden.");
    }

    public boolean start() {
        if (running || !plugin.projects().isStarted()) return false;
        List<PlayerRecord> finalists = remainingPlayers();
        int online = (int) finalists.stream().filter(record -> Bukkit.getPlayer(record.uuid()) != null).count();
        if (!de.minecraft.rival.util.RivalRules.canStartEndFight(finalists.size(), online)) return false;
        World world = Bukkit.getWorld(plugin.getConfig().getString("border.world", "world"));
        if (world == null) return false;

        WorldBorder border = world.getWorldBorder();
        previous = BorderSnapshot.capture(world, border);
        if (!savePending(previous)) {
            previous = null;
            return false;
        }
        borders.setEndFightOverride(true);
        double x = plugin.getConfig().getDouble("end-fight.center-x", .5);
        double z = plugin.getConfig().getDouble("end-fight.center-z", .5);
        border.setCenter(x, z);
        border.setSize(Math.max(1, plugin.getConfig().getDouble("end-fight.border-size", 100)));
        border.setWarningDistance(5);
        Location center = new Location(world, x, plugin.getConfig().getDouble("end-fight.center-y", 100), z);
        for (int i = 0; i < finalists.size(); i++) {
            Player player = Bukkit.getPlayer(finalists.get(i).uuid());
            if (player != null) player.teleportAsync(center.clone().add(i == 0 ? -10 : 10, 0, 0));
        }
        running = true;
        Bukkit.broadcast(Messages.normal("Der Endkampf beginnt! Die letzten zwei Spieler wurden zur Mittelinsel gerufen."));
        return true;
    }

    public boolean stop() { return stop(true); }

    private boolean stop(boolean announce) {
        if (!running) return false;
        if (!restore(previous)) {
            plugin.getLogger().severe("Endkampf konnte nicht beendet werden: ursprüngliche Worldborder ist momentan nicht wiederherstellbar.");
            return false;
        }
        previous = null;
        running = false;
        readyNotified = false;
        borders.setEndFightOverride(false);
        clearPending();
        if (announce) Bukkit.broadcast(Messages.normal("Der Endkampf wurde beendet und die ursprüngliche Worldborder wiederhergestellt."));
        return true;
    }

    public void shutdown() {
        if (running) stop(false);
    }

    private void restoreInterruptedFight() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        if (!yaml.getBoolean("active")) return;
        BorderSnapshot snapshot = BorderSnapshot.load(yaml);
        if (snapshot != null) {
            if (restore(snapshot)) {
                plugin.getLogger().warning("Eine unterbrochene Endkampf-Border wurde auf die zuvor gespeicherte Worldborder zurückgesetzt.");
                clearPending();
            } else {
                plugin.getLogger().severe("Die gespeicherte Worldborder konnte noch nicht wiederhergestellt werden; endfight-state.yml bleibt erhalten.");
            }
        }
    }

    private boolean restore(BorderSnapshot snapshot) {
        if (snapshot == null) return false;
        World world = Bukkit.getWorld(snapshot.world);
        if (world == null) return false;
        WorldBorder border = world.getWorldBorder();
        border.setCenter(snapshot.centerX, snapshot.centerZ);
        border.setSize(snapshot.size);
        border.setWarningDistance(snapshot.warningDistance);
        border.setWarningTime(snapshot.warningTime);
        border.setDamageAmount(snapshot.damageAmount);
        border.setDamageBuffer(snapshot.damageBuffer);
        return true;
    }

    private boolean savePending(BorderSnapshot snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("active", true);
        snapshot.save(yaml);
        return saveYaml(yaml);
    }

    private void clearPending() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("active", false);
        saveYaml(yaml);
    }

    private boolean saveYaml(YamlConfiguration yaml) {
        try {
            yaml.save(stateFile);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "endfight-state.yml konnte nicht gespeichert werden", ex);
            return false;
        }
    }

    public boolean isRunning() { return running; }

    private record BorderSnapshot(String world, double centerX, double centerZ, double size, int warningDistance,
                                  int warningTime, double damageAmount, double damageBuffer) {
        static BorderSnapshot capture(World world, WorldBorder border) {
            return new BorderSnapshot(world.getName(), border.getCenter().getX(), border.getCenter().getZ(), border.getSize(),
                border.getWarningDistance(), border.getWarningTime(), border.getDamageAmount(), border.getDamageBuffer());
        }

        void save(YamlConfiguration yaml) {
            yaml.set("world", world); yaml.set("center-x", centerX); yaml.set("center-z", centerZ); yaml.set("size", size);
            yaml.set("warning-distance", warningDistance); yaml.set("warning-time", warningTime);
            yaml.set("damage-amount", damageAmount); yaml.set("damage-buffer", damageBuffer);
        }

        static BorderSnapshot load(YamlConfiguration yaml) {
            String world = yaml.getString("world");
            if (world == null || world.isBlank()) return null;
            return new BorderSnapshot(world, yaml.getDouble("center-x"), yaml.getDouble("center-z"), yaml.getDouble("size"),
                yaml.getInt("warning-distance"), yaml.contains("warning-time")
                    ? yaml.getInt("warning-time") : yaml.getInt("warning-time-ticks") / 20,
                yaml.getDouble("damage-amount"), yaml.getDouble("damage-buffer"));
        }
    }
}
