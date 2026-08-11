package de.minecraft.rival.game;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.data.DataStore;
import de.minecraft.rival.data.PlayerRecord;
import de.minecraft.rival.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.*;

public final class CombatManager implements Listener {
    private final RivalPlugin plugin;
    private final DataStore data;
    private final GraveManager graves;
    private final EndFightManager endFight;
    private final Map<UUID, CombatTag> tags = new HashMap<>();

    public CombatManager(RivalPlugin plugin, DataStore data, GraveManager graves, EndFightManager endFight) {
        this.plugin = plugin;
        this.data = data;
        this.graves = graves;
        this.endFight = endFight;
    }

    public void enable() {
        // Zustandsupdates übernimmt ModGate einmal pro Sekunde; dadurch gibt es keine doppelte Combat-Anzeige.
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = responsiblePlayer(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        if (!plugin.projects().isParticipant(victim) || !plugin.projects().isParticipant(attacker)
            || plugin.adminMode().isActive(victim) || plugin.adminMode().isActive(attacker)) return;
        long until = System.currentTimeMillis() + plugin.getConfig().getLong("combat.duration-seconds", 30) * 1000L;
        tags.put(victim.getUniqueId(), new CombatTag(attacker.getUniqueId(), until));
        tags.put(attacker.getUniqueId(), new CombatTag(victim.getUniqueId(), until));
    }

    private Player responsiblePlayer(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        if (entity instanceof TNTPrimed tnt && tnt.getSource() != null && !tnt.getSource().equals(entity)) return responsiblePlayer(tnt.getSource());
        if (entity instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player player) return player;
        if (entity instanceof Tameable tameable && tameable.getOwner() instanceof Player player) return player;
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        CombatTag tag = validTag(victim.getUniqueId());
        tags.remove(victim.getUniqueId());
        if (tag == null || plugin.adminMode().isActive(victim)) return;

        PlayerRecord record = data.player(victim.getUniqueId(), victim.getName());
        record.hearts(Math.max(0, record.hearts() - 1));
        rewardNemesis(tag.opponent, victim);

        if (record.hearts() == 0) {
            record.eliminated(true);
            data.save();
            Messages.broadcast(Messages.value("", victim.getName(), " hat alle Herzen verloren."));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (victim.isOnline()) victim.kickPlayer(plugin.getConfig().getString("messages.eliminated"));
                endFight.checkAutomaticStart();
            }, 2L);
        } else {
            data.save();
            victim.sendMessage(Messages.value("Du hast ein Herz verloren. Verbleibend: ", record.hearts(), ""));
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.modGate().sendState(victim), 2L);
        }
    }

    private void rewardNemesis(UUID killerId, Player victim) {
        PlayerRecord killerRecord = data.player(killerId);
        if (killerRecord == null) return;
        if (!killerRecord.nemesisRevealed() || !victim.getUniqueId().equals(killerRecord.nemesis())) return;
        int maximum = plugin.getConfig().getInt("combat.maximum-hearts", 3);
        if (killerRecord.hearts() >= maximum) return;
        killerRecord.hearts(killerRecord.hearts() + 1);
        killerRecord.nemesis(null);
        killerRecord.nemesisRevealed(false);
        Player killer = Bukkit.getPlayer(killerId);
        if (killer != null) {
            killer.sendMessage(Messages.value("Erzfeind besiegt! Du erhältst ein Herz und hast jetzt ", killerRecord.hearts(), "."));
            plugin.modGate().sendState(killer);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Kein automatischer Tod: Der bestehende Combat-Tag bleibt bis zu seinem echten Ablauf erhalten.
    }

    public int remainingSeconds(UUID player) {
        CombatTag tag = validTag(player);
        if (tag == null) return 0;
        return (int) Math.max(1, Math.ceil((tag.until - System.currentTimeMillis()) / 1000.0));
    }

    private CombatTag validTag(UUID player) {
        CombatTag tag = tags.get(player);
        if (tag != null && tag.until <= System.currentTimeMillis()) {
            tags.remove(player);
            return null;
        }
        return tag;
    }

    public int revealNemeses() {
        List<PlayerRecord> eligible = data.players().stream().filter(record -> !record.eliminated() && record.hearts() > 0 && record.side() != 0).toList();
        if (eligible.size() < 2) return 0;
        List<PlayerRecord> shuffled = new ArrayList<>(eligible);
        Collections.shuffle(shuffled);
        for (int i = 0; i < shuffled.size(); i++) {
            PlayerRecord current = shuffled.get(i);
            current.nemesis(shuffled.get((i + 1) % shuffled.size()).uuid());
            current.nemesisRevealed(true);
            Player online = Bukkit.getPlayer(current.uuid());
            if (online != null) plugin.modGate().sendState(online);
        }
        data.save();
        return shuffled.size();
    }

    public void resetNemeses() {
        tags.clear();
        for (PlayerRecord record : data.players()) {
            record.nemesis(null);
            record.nemesisRevealed(false);
        }
        data.save();
        Bukkit.getOnlinePlayers().forEach(player -> plugin.modGate().sendState(player));
    }

    /** Entfernt beim Wechsel in den Admin-Modus beide Seiten einer laufenden Kampfbeziehung. */
    public void clearCombat(Player player) {
        UUID playerId = player.getUniqueId();
        tags.remove(playerId);
        tags.entrySet().removeIf(entry -> entry.getValue().opponent.equals(playerId));
    }

    private record CombatTag(UUID opponent, long until) {}
}
