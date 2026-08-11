package de.minecraft.rival.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

/** Minimaler virtueller Ressourcen-Pack für die nativen GLFW-Fenstericons. */
final class RivalIconPack implements PackResources {
    private static final PackLocationInfo LOCATION = new PackLocationInfo(
        "minecraft-rival-icons", Component.literal("Minecraft Rival Icons"), PackSource.BUILT_IN, Optional.empty());

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        if (path.length != 2 || !"icons".equals(path[0])) return null;
        String resource = "/assets/minecraft_rival/icons/" + path[1];
        return () -> {
            InputStream stream = RivalIconPack.class.getResourceAsStream(resource);
            if (stream == null) throw new FileNotFoundException(resource);
            return stream;
        };
    }

    @Override public IoSupplier<InputStream> getResource(PackType type, Identifier id) { return null; }
    @Override public void listResources(PackType type, String namespace, String path, ResourceOutput output) { }
    @Override public Set<String> getNamespaces(PackType type) { return Set.of("minecraft_rival"); }
    @Override public <T> T getMetadataSection(MetadataSectionType<T> section) { return null; }
    @Override public PackLocationInfo location() { return LOCATION; }
    @Override public void close() { }
}
