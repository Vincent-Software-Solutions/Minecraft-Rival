package de.minecraft.rival.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/** Minimaler virtueller Ressourcen-Pack für die nativen GLFW-Fenstericons. */
final class RivalIconPack implements PackResources {
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

    @Override public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) { return null; }
    @Override public void listResources(PackType type, String namespace, String path, ResourceOutput output) { }
    @Override public Set<String> getNamespaces(PackType type) { return Set.of("minecraft_rival"); }
    @Override public <T> T getMetadataSection(MetadataSectionSerializer<T> section) throws IOException { return null; }
    @Override public String packId() { return "minecraft-rival-icons"; }
    @Override public void close() { }
}
