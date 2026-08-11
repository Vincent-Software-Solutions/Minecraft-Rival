package de.minecraft.rival.data;

import java.time.LocalDate;
import java.util.UUID;

public final class PlayerRecord {
    private final UUID uuid;
    private String lastName;
    private int hearts;
    private boolean eliminated;
    private LocalDate playDate;
    private long playedSeconds;
    private boolean bossbar;
    private boolean bossbarSet;
    private int side;
    private UUID nemesis;
    private boolean nemesisRevealed;
    private String clanId;

    public PlayerRecord(UUID uuid, String lastName, int hearts) {
        this.uuid = uuid;
        this.lastName = lastName;
        this.hearts = hearts;
        this.playDate = LocalDate.MIN;
    }

    public UUID uuid() { return uuid; }
    public String lastName() { return lastName; }
    public void lastName(String value) { lastName = value; }
    public int hearts() { return hearts; }
    public void hearts(int value) { hearts = value; }
    public boolean eliminated() { return eliminated; }
    public void eliminated(boolean value) { eliminated = value; }
    public LocalDate playDate() { return playDate; }
    public void playDate(LocalDate value) { playDate = value; }
    public long playedSeconds() { return playedSeconds; }
    public void playedSeconds(long value) { playedSeconds = value; }
    public boolean bossbar() { return bossbar; }
    public void bossbar(boolean value) { bossbar = value; }
    public boolean bossbarSet() { return bossbarSet; }
    public void bossbarSet(boolean value) { bossbarSet = value; }
    public int side() { return side; }
    public void side(int value) { side = Integer.signum(value); }
    public UUID nemesis() { return nemesis; }
    public void nemesis(UUID value) { nemesis = value; }
    public boolean nemesisRevealed() { return nemesisRevealed; }
    public void nemesisRevealed(boolean value) { nemesisRevealed = value; }
    public String clanId() { return clanId; }
    public void clanId(String value) { clanId = value; }
}
