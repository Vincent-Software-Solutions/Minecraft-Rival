package de.minecraft.rival.game;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.util.Messages;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class YouTubeManager implements Listener, CommandExecutor {
    private static final String LABEL = "&#FB0000ʏ&#FC2B2Bᴏ&#FC5555ᴜ&#FD8080ᴛ&#FEAAAAᴜ&#FED5D5ʙ&#FFFFFFᴇ";
    private final RivalPlugin plugin;
    private final NamespacedKey markerKey;
    private final Set<UUID> active = new HashSet<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, UUID> displays = new HashMap<>();

    public YouTubeManager(RivalPlugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "youtube_label");
    }

    public void enable() {
        Bukkit.getWorlds().forEach(world -> world.getEntitiesByClass(TextDisplay.class).stream()
            .filter(display -> display.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE)).forEach(Entity::remove));
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickDisplays, 1L, 2L);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) { Messages.error(sender, "Dieser Befehl ist nur für Spieler."); return true; }
        if (args.length > 0 && (args[0].equalsIgnoreCase("bestätigen") || args[0].equalsIgnoreCase("bestaetigen") || args[0].equalsIgnoreCase("confirm"))) {
            confirm(player);
            return true;
        }
        boolean enabling = !active.contains(player.getUniqueId());
        long expires = System.currentTimeMillis() + plugin.getConfig().getLong("youtube.confirmation-seconds", 30) * 1000L;
        pending.put(player.getUniqueId(), new Pending(enabling, expires));
        Messages.normal(player, "Möchtest du den YouTube-Modus wirklich " + (enabling ? "aktivieren" : "deaktivieren") + "?");
        TextComponent confirmation = new TextComponent("[JETZT BESTÄTIGEN]");
        confirmation.setColor(ChatColor.GREEN);
        confirmation.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/youtube bestätigen"));
        confirmation.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder("Klicken zum Bestätigen").color(ChatColor.GRAY).create()));
        BaseComponent[] prefix = TextComponent.fromLegacyText(Messages.styledLine(""));
        BaseComponent[] line = Arrays.copyOf(prefix, prefix.length + 1);
        line[prefix.length] = confirmation;
        player.spigot().sendMessage(line);
        Messages.normal(player, "Alternativ: /youtube bestätigen • Gültig für " + plugin.getConfig().getLong("youtube.confirmation-seconds", 30) + " Sekunden.");
        return true;
    }

    private void confirm(Player player) {
        Pending request = pending.remove(player.getUniqueId());
        if (request == null || request.expiresAt < System.currentTimeMillis()) {
            Messages.error(player, "Es gibt keine gültige YouTube-Bestätigung. Nutze zuerst /youtube.");
            return;
        }
        if (request.enabling) activate(player);
        else deactivate(player, true);
    }

    private void activate(Player player) {
        if (!active.add(player.getUniqueId())) return;
        createDisplay(player);
        Messages.broadcast(Messages.value("Der Spieler ", player.getName(), " hat eine YouTube aufnahme gestartet."));
    }

    private void deactivate(Player player, boolean announce) {
        if (!active.remove(player.getUniqueId())) return;
        removeDisplay(player.getUniqueId());
        if (announce) Messages.broadcast(Messages.value("Der Spieler ", player.getName(), " hat seine YouTube Aufnahme beendet."));
    }

    private void createDisplay(Player player) {
        removeDisplay(player.getUniqueId());
        Location location = labelLocation(player);
        TextDisplay display = player.getWorld().spawn(location, TextDisplay.class, text -> {
            text.setText(Messages.text(LABEL));
            text.setBillboard(Display.Billboard.CENTER);
            text.setSeeThrough(false);
            text.setShadowed(true);
            text.setDefaultBackground(false);
            text.setLineWidth(120);
            text.setInvulnerable(true);
            text.setPersistent(false);
            text.setGravity(false);
            text.setViewRange(32);
            var transformation = text.getTransformation();
            transformation.getScale().set(0.65f, 0.65f, 0.65f);
            text.setTransformation(transformation);
            text.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        });
        displays.put(player.getUniqueId(), display.getUniqueId());
    }

    private void tickDisplays() {
        pending.entrySet().removeIf(entry -> entry.getValue().expiresAt < System.currentTimeMillis());
        for (UUID playerId : new HashSet<>(active)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) continue;
            Entity entity = displays.containsKey(playerId) ? Bukkit.getEntity(displays.get(playerId)) : null;
            if (!(entity instanceof TextDisplay display) || !display.isValid() || !display.getWorld().equals(player.getWorld())) {
                createDisplay(player);
                continue;
            }
            display.teleport(labelLocation(player));
        }
    }

    private static Location labelLocation(Player player) {
        return player.getLocation().add(0, player.getHeight() + 0.18, 0);
    }

    private void removeDisplay(UUID playerId) {
        UUID displayId = displays.remove(playerId);
        Entity entity = displayId == null ? null : Bukkit.getEntity(displayId);
        if (entity != null) entity.remove();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
        deactivate(event.getPlayer(), active.contains(event.getPlayer().getUniqueId()));
    }

    public void shutdown() {
        new HashSet<>(displays.keySet()).forEach(this::removeDisplay);
        displays.clear();
        active.clear();
        pending.clear();
    }

    public void disableSilently(Player player) {
        pending.remove(player.getUniqueId());
        deactivate(player, false);
    }

    public boolean isActive(UUID playerId) {
        return active.contains(playerId);
    }

    private record Pending(boolean enabling, long expiresAt) {}
}
