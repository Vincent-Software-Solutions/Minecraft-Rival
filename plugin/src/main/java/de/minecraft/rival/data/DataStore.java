package de.minecraft.rival.data;

import de.minecraft.rival.RivalPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Level;

public final class DataStore {
    private final RivalPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerRecord> players = new HashMap<>();
    private final Map<String, ClanRecord> clans = new LinkedHashMap<>();

    public DataStore(RivalPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        players.clear();
        clans.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection playerSection = yaml.getConfigurationSection("players");
        if (playerSection != null) for (String key : playerSection.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String path = "players." + key + ".";
                int maximumHearts = plugin.getConfig().getInt("combat.maximum-hearts", 3);
                int hearts = Math.max(0, Math.min(maximumHearts, yaml.getInt(path + "hearts", 3)));
                PlayerRecord record = new PlayerRecord(id, yaml.getString(path + "name", "?"), hearts);
                record.eliminated(yaml.getBoolean(path + "eliminated") || hearts == 0);
                record.playDate(parseDate(yaml.getString(path + "play-date")));
                record.playedSeconds(Math.max(0, yaml.getLong(path + "played-seconds")));
                record.bossbar(yaml.getBoolean(path + "bossbar"));
                record.bossbarSet(yaml.contains(path + "bossbar-set") ? yaml.getBoolean(path + "bossbar-set") : record.bossbar());
                record.side(yaml.getInt(path + "side"));
                record.nemesis(parseUuid(yaml.getString(path + "nemesis")));
                record.nemesisRevealed(yaml.getBoolean(path + "nemesis-revealed"));
                record.clanId(yaml.getString(path + "clan"));
                players.put(id, record);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Ungültiger Spieler-Datensatz: " + key);
            }
        }
        ConfigurationSection clanSection = yaml.getConfigurationSection("clans");
        if (clanSection != null) for (String id : clanSection.getKeys(false)) {
            String path = "clans." + id + ".";
            UUID owner = parseUuid(yaml.getString(path + "owner"));
            if (owner == null) continue;
            ClanRecord clan = new ClanRecord(id, yaml.getString(path + "name", id), owner);
            clan.tag(yaml.getString(path + "tag", clan.tag()));
            clan.color(yaml.getString(path + "color", "&b"));
            clan.members().clear();
            for (String member : yaml.getStringList(path + "members")) {
                UUID uuid = parseUuid(member);
                if (uuid != null) clan.members().add(uuid);
            }
            clan.members().add(owner);
            clans.put(id, clan);
        }
        // Die Clanliste ist die maßgebliche Quelle. Dadurch kann ein Spieler auch nach manuellen Dateiedits
        // oder alten Pluginversionen niemals gleichzeitig mehreren Clans zugeordnet sein.
        players.values().forEach(record -> record.clanId(null));
        Set<UUID> assigned = new HashSet<>();
        Iterator<ClanRecord> clanIterator = clans.values().iterator();
        while (clanIterator.hasNext()) {
            ClanRecord clan = clanIterator.next();
            if (!assigned.add(clan.owner())) {
                plugin.getLogger().warning("Clan " + clan.name() + " wurde ignoriert: Besitzer ist bereits einem anderen Clan zugeordnet.");
                clanIterator.remove();
                continue;
            }
            clan.members().removeIf(uuid -> !uuid.equals(clan.owner()) && !assigned.add(uuid));
            clan.members().add(clan.owner());
            for (UUID member : clan.members()) {
                PlayerRecord record = players.computeIfAbsent(member,
                    uuid -> new PlayerRecord(uuid, "?", plugin.getConfig().getInt("combat.starting-hearts", 3)));
                record.clanId(clan.id());
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PlayerRecord record : players.values()) {
            String path = "players." + record.uuid() + ".";
            yaml.set(path + "name", record.lastName());
            yaml.set(path + "hearts", record.hearts());
            yaml.set(path + "eliminated", record.eliminated());
            yaml.set(path + "play-date", record.playDate().toString());
            yaml.set(path + "played-seconds", record.playedSeconds());
            yaml.set(path + "bossbar", record.bossbar());
            yaml.set(path + "bossbar-set", record.bossbarSet());
            yaml.set(path + "side", record.side());
            yaml.set(path + "nemesis", record.nemesis() == null ? null : record.nemesis().toString());
            yaml.set(path + "nemesis-revealed", record.nemesisRevealed());
            yaml.set(path + "clan", record.clanId());
        }
        for (ClanRecord clan : clans.values()) {
            String path = "clans." + clan.id() + ".";
            yaml.set(path + "name", clan.name());
            yaml.set(path + "tag", clan.tag());
            yaml.set(path + "color", clan.color());
            yaml.set(path + "owner", clan.owner().toString());
            yaml.set(path + "members", clan.members().stream().map(UUID::toString).toList());
        }
        try { yaml.save(file); }
        catch (IOException ex) { plugin.getLogger().log(Level.SEVERE, "data.yml konnte nicht gespeichert werden", ex); }
    }

    public PlayerRecord player(UUID uuid, String name) {
        PlayerRecord record = players.computeIfAbsent(uuid,
            id -> new PlayerRecord(id, name, plugin.getConfig().getInt("combat.starting-hearts", 3)));
        record.lastName(name);
        return record;
    }

    public PlayerRecord player(UUID uuid) { return players.get(uuid); }
    public Collection<PlayerRecord> players() { return Collections.unmodifiableCollection(players.values()); }
    public Map<String, ClanRecord> clans() { return clans; }

    private static LocalDate parseDate(String value) {
        try { return value == null ? LocalDate.MIN : LocalDate.parse(value); }
        catch (RuntimeException ignored) { return LocalDate.MIN; }
    }

    private static UUID parseUuid(String value) {
        try { return value == null ? null : UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
