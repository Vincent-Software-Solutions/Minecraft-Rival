package de.minecraft.rival.data;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class ClanRecord {
    private final String id;
    private String name;
    private String tag;
    private String color;
    private UUID owner;
    private final Set<UUID> members = new LinkedHashSet<>();

    public ClanRecord(String id, String name, UUID owner) {
        this.id = id;
        this.name = name;
        this.tag = name.length() <= 6 ? name : name.substring(0, 6);
        this.color = "&b";
        this.owner = owner;
        members.add(owner);
    }

    public String id() { return id; }
    public String name() { return name; }
    public void name(String value) { name = value; }
    public String tag() { return tag; }
    public void tag(String value) { tag = value; }
    public String color() { return color; }
    public void color(String value) { color = value; }
    public UUID owner() { return owner; }
    public void owner(UUID value) { owner = value; }
    public Set<UUID> members() { return members; }
}
