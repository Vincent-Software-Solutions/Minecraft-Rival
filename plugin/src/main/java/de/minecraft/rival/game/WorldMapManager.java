package de.minecraft.rival.game;

import de.minecraft.rival.RivalPlugin;
import de.minecraft.rival.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.DeflaterOutputStream;

/** Creates frozen top-down terrain snapshots and streams them to authenticated clients. */
public final class WorldMapManager {
    public static final String CHANNEL = "rival:map";
    private static final byte PROTOCOL = 2;
    private static final int PAYLOAD = 28_000;
    private final RivalPlugin plugin;
    private final File file;
    private volatile Snapshot current;
    private volatile boolean updating;

    public WorldMapManager(RivalPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "world-map.bin");
        load();
    }

    public void enable() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    public boolean isUpdating() { return updating; }
    public boolean hasSnapshot() { return current != null; }
    public long updatedAt() { return current == null ? 0L : current.version; }

    public void update(Player actor) {
        if (updating) { Messages.error(actor, "Die Weltkarte wird bereits aktualisiert."); return; }
        World world = plugin.mainWorld();
        updating = true;
        int configuredResolution = plugin.getConfig().getInt("world-map.resolution", 512);
        int resolution = Math.max(128, Math.min(768, configuredResolution));
        int radius = Math.max(128, Math.min(4096, plugin.getConfig().getInt("world-map.radius-blocks", 1024)));
        int centerX = actor.getWorld().equals(world) ? actor.getLocation().getBlockX() : world.getSpawnLocation().getBlockX();
        int centerZ = actor.getWorld().equals(world) ? actor.getLocation().getBlockZ() : world.getSpawnLocation().getBlockZ();
        int minX = centerX - radius, minZ = centerZ - radius, maxX = centerX + radius, maxZ = centerZ + radius;
        Map<Long, List<Integer>> pixelsByChunk = new LinkedHashMap<>();
        byte[] rgb = new byte[resolution * resolution * 3];
        for (int index = 0; index < resolution * resolution; index++) {
            int pixelX = index % resolution, pixelY = index / resolution;
            int worldX = coordinate(pixelX, resolution, minX, maxX);
            int worldZ = coordinate(pixelY, resolution, minZ, maxZ);
            pixelsByChunk.computeIfAbsent(key(Math.floorDiv(worldX, 16), Math.floorDiv(worldZ, 16)), ignored -> new ArrayList<>()).add(index);
            setColor(rgb, index, 0x101720);
        }
        Deque<Long> candidates = new ArrayDeque<>(pixelsByChunk.keySet());
        Messages.normal(actor, "Weltkarten-Snapshot scannt " + candidates.size() + " Chunk-Positionen schonend in Teilabschnitten …");
        new BukkitRunnable() {
            @Override public void run() {
                int batch = 0;
                while (batch++ < 24 && !candidates.isEmpty()) {
                    long packed = candidates.removeFirst();
                    int chunkX = (int) (packed >> 32), chunkZ = (int) packed;
                    if (!world.isChunkGenerated(chunkX, chunkZ)) continue;
                    boolean alreadyLoaded = world.isChunkLoaded(chunkX, chunkZ);
                    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                    ChunkSnapshot snapshot = chunk.getChunkSnapshot(true, true, false);
                    for (int index : pixelsByChunk.remove(packed)) {
                        int pixelX = index % resolution, pixelY = index / resolution;
                        int worldX = coordinate(pixelX, resolution, minX, maxX);
                        int worldZ = coordinate(pixelY, resolution, minZ, maxZ);
                        int localX = Math.floorMod(worldX, 16), localZ = Math.floorMod(worldZ, 16);
                        int y = snapshot.getHighestBlockYAt(localX, localZ);
                        Material material = snapshot.getBlockType(localX, y, localZ);
                        setColor(rgb, index, color(material, snapshot.getBiome(localX, y, localZ), y));
                    }
                    if (!alreadyLoaded) chunk.unload(true);
                }
                if (!candidates.isEmpty()) return;
                cancel();
                finishUpdate(actor, rgb, resolution, minX, minZ, maxX, maxZ);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void finishUpdate(Player actor, byte[] rgb, int resolution,
                              int minX, int minZ, int maxX, int maxZ) {
        CompletableFuture.supplyAsync(() -> compress(rgb, resolution, minX, minZ, maxX, maxZ))
            .whenComplete((snapshot, failure) -> {
                if (!plugin.isEnabled()) { updating = false; return; }
                Bukkit.getScheduler().runTask(plugin, () -> {
                updating = false;
                if (failure != null || snapshot == null) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Weltkarte konnte nicht erstellt werden", failure);
                    Messages.error(actor, "Die Weltkarte konnte nicht aktualisiert werden.");
                    return;
                }
                current = snapshot;
                save(snapshot);
                Bukkit.getOnlinePlayers().forEach(this::send);
                Messages.normal(actor, "Weltkarte aktualisiert und an alle Clients gesendet.");
                });
            });
    }

    public void send(Player player) {
        Snapshot snapshot = current;
        if (snapshot == null || !player.isOnline()) return;
        int count = (snapshot.compressed.length + PAYLOAD - 1) / PAYLOAD;
        try {
            ByteArrayOutputStream headerRaw = new ByteArrayOutputStream();
            DataOutputStream header = new DataOutputStream(headerRaw);
            header.writeByte(PROTOCOL); header.writeByte(1); header.writeLong(snapshot.version);
            header.writeInt(snapshot.width); header.writeInt(snapshot.height);
            header.writeInt(snapshot.minX); header.writeInt(snapshot.minZ); header.writeInt(snapshot.maxX); header.writeInt(snapshot.maxZ);
            header.writeInt(count); header.writeInt(snapshot.compressed.length);
            player.sendPluginMessage(plugin, CHANNEL, headerRaw.toByteArray());
            for (int index = 0; index < count; index++) {
                int offset = index * PAYLOAD;
                int length = Math.min(PAYLOAD, snapshot.compressed.length - offset);
                ByteArrayOutputStream partRaw = new ByteArrayOutputStream(length + 32);
                DataOutputStream part = new DataOutputStream(partRaw);
                part.writeByte(PROTOCOL); part.writeByte(2); part.writeLong(snapshot.version);
                part.writeInt(index); part.writeInt(length); part.write(snapshot.compressed, offset, length);
                player.sendPluginMessage(plugin, CHANNEL, partRaw.toByteArray());
            }
        } catch (IOException impossible) { throw new UncheckedIOException(impossible); }
    }

    private Snapshot compress(byte[] rgb, int size, int minX, int minZ, int maxX, int maxZ) {
        try {
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) { deflater.write(rgb); }
            return new Snapshot(System.currentTimeMillis(), size, size, minX, minZ, maxX, maxZ, compressed.toByteArray());
        } catch (IOException impossible) { throw new UncheckedIOException(impossible); }
    }

    private static int coordinate(int pixel, int resolution, int minimum, int maximum) {
        return minimum + (int) ((long) pixel * (maximum - minimum) / Math.max(1, resolution - 1));
    }

    private static void setColor(byte[] rgb, int index, int color) {
        int offset = index * 3;
        rgb[offset] = (byte) (color >> 16);
        rgb[offset + 1] = (byte) (color >> 8);
        rgb[offset + 2] = (byte) color;
    }

    private static int color(Material material, Biome biome, int y) {
        String name = material.name();
        int color;
        if (name.contains("WATER") || name.contains("ICE")) color = 0x285E9A;
        else if (name.contains("LAVA")) color = 0xE85A19;
        else if (name.contains("GRASS") || name.contains("LEAVES") || name.contains("MOSS")) color = 0x4F8A3C;
        else if (name.contains("SAND")) color = 0xCDBA78;
        else if (name.contains("SNOW") || name.contains("QUARTZ")) color = 0xE1E6E8;
        else if (name.contains("NETHERRACK") || name.contains("CRIMSON")) color = 0x7D3534;
        else if (name.contains("END_STONE")) color = 0xD7D58B;
        else if (name.contains("DEEPSLATE") || name.contains("BLACKSTONE")) color = 0x34363B;
        else if (name.contains("STONE") || name.contains("COBBLE")) color = 0x777A7D;
        else if (name.contains("WOOD") || name.contains("LOG") || name.contains("PLANK")) color = 0x80613E;
        else if (name.contains("DIRT") || name.contains("MUD")) color = 0x71523B;
        else color = biome.name().contains("OCEAN") || biome.name().contains("RIVER") ? 0x285E9A : 0x63844B;
        float shade = Math.max(0.72f, Math.min(1.22f, 0.88f + (y - 62) / 320.0f));
        int r = Math.min(255, Math.round((color >> 16 & 255) * shade));
        int g = Math.min(255, Math.round((color >> 8 & 255) * shade));
        int b = Math.min(255, Math.round((color & 255) * shade));
        return r << 16 | g << 8 | b;
    }

    private void save(Snapshot snapshot) {
        try {
            plugin.getDataFolder().mkdirs();
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file.toPath())))) {
                out.writeInt(0x524D4150); out.writeLong(snapshot.version); out.writeInt(snapshot.width); out.writeInt(snapshot.height);
                out.writeInt(snapshot.minX); out.writeInt(snapshot.minZ); out.writeInt(snapshot.maxX); out.writeInt(snapshot.maxZ);
                out.writeInt(snapshot.compressed.length); out.write(snapshot.compressed);
            }
        } catch (IOException ex) { plugin.getLogger().warning("world-map.bin konnte nicht gespeichert werden: " + ex.getMessage()); }
    }

    private void load() {
        if (!file.isFile()) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file.toPath())))) {
            if (in.readInt() != 0x524D4150) return;
            long version = in.readLong(); int width = in.readInt(), height = in.readInt();
            int minX = in.readInt(), minZ = in.readInt(), maxX = in.readInt(), maxZ = in.readInt();
            int length = in.readInt();
            if (width < 1 || height < 1 || length < 1 || length > 8_000_000) return;
            byte[] compressed = in.readNBytes(length);
            if (compressed.length != length) throw new EOFException("Unvollständiger Weltkarten-Snapshot");
            current = new Snapshot(version, width, height, minX, minZ, maxX, maxZ, compressed);
        } catch (IOException ex) { plugin.getLogger().warning("world-map.bin konnte nicht geladen werden: " + ex.getMessage()); }
    }

    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
    private record Snapshot(long version, int width, int height, int minX, int minZ, int maxX, int maxZ, byte[] compressed) {}
}
