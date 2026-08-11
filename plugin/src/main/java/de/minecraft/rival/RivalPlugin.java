package de.minecraft.rival;

import de.minecraft.rival.command.AdminCommand;
import de.minecraft.rival.command.ClanCommand;
import de.minecraft.rival.command.HelpCommand;
import de.minecraft.rival.command.PlaytimeCommand;
import de.minecraft.rival.command.RulesCommand;
import de.minecraft.rival.data.DataStore;
import de.minecraft.rival.game.*;
import de.minecraft.rival.menu.MenuListener;
import de.minecraft.rival.placeholder.RivalExpansion;
import de.minecraft.rival.security.ModGate;
import de.minecraft.rival.util.Messages;
import de.minecraft.rival.util.ConfigSanitizer;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class RivalPlugin extends JavaPlugin {
    private DataStore data;
    private ModGate modGate;
    private AdminModeManager adminMode;
    private AdminBroadcastManager broadcasts;
    private YouTubeManager youtube;
    private RuleManager rules;
    private ModerationManager moderation;
    private VanishManager vanish;
    private ClanManager clans;
    private GraveManager graves;
    private BorderManager borders;
    private PlaytimeManager playtime;
    private CombatManager combat;
    private EndFightManager endFight;
    private ProjectManager projects;
    private ZoneManager zones;
    private MenuListener menus;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ConfigSanitizer.sanitize(this);
        Messages.load(getConfig());
        data = new DataStore(this);
        data.load();

        broadcasts = new AdminBroadcastManager(this);
        rules = new RuleManager(this);
        moderation = new ModerationManager(this);
        adminMode = new AdminModeManager(this);
        vanish = new VanishManager(this);
        clans = new ClanManager(this, data);
        graves = new GraveManager(this);
        projects = new ProjectManager(this, data);
        zones = new ZoneManager(this);
        borders = new BorderManager(this, data);
        playtime = new PlaytimeManager(this, data, vanish);
        endFight = new EndFightManager(this, data, borders);
        combat = new CombatManager(this, data, graves, endFight);
        modGate = new ModGate(this, data, combat);
        menus = new MenuListener(this);
        youtube = new YouTubeManager(this);

        register(moderation, adminMode, vanish, graves, projects, zones, borders, playtime, combat, endFight, modGate, youtube, menus, new PresentationListener(this));
        command("help", new HelpCommand(this));
        command("spielzeit", new PlaytimeCommand(playtime));
        ClanCommand clanCommand = new ClanCommand(clans);
        command("clan", clanCommand);
        getCommand("clan").setTabCompleter(clanCommand);
        command("youtube", youtube);
        command("rules", new RulesCommand(rules));
        AdminCommand adminCommand = new AdminCommand(this);
        command("admin", adminCommand);
        getCommand("admin").setTabCompleter(adminCommand);

        modGate.enable();
        graves.load();
        zones.enable();
        projects.enable();
        borders.enable();
        endFight.enable();
        playtime.enable();
        combat.enable();
        youtube.enable();

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new RivalExpansion(this, clans).register();
        }
        getLogger().info("Minecraft Rival ist bereit.");
    }

    @Override
    public void onDisable() {
        if (endFight != null) endFight.shutdown();
        if (youtube != null) youtube.shutdown();
        if (vanish != null) vanish.restoreAll();
        if (graves != null) graves.save();
        if (broadcasts != null) broadcasts.save();
        if (rules != null) rules.save();
        if (moderation != null) moderation.save();
        if (data != null) data.save();
    }

    private void register(org.bukkit.event.Listener... listeners) {
        for (var listener : listeners) Bukkit.getPluginManager().registerEvents(listener, this);
    }

    private void command(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) throw new IllegalStateException("Befehl fehlt in plugin.yml: " + name);
        command.setExecutor(executor);
    }

    public void reloadRival() {
        reloadConfig();
        ConfigSanitizer.sanitize(this);
        Messages.load(getConfig());
        zones.retagAllMobs();
        Bukkit.getOnlinePlayers().forEach(playtime::refreshVisibility);
    }

    public DataStore data() { return data; }
    public ModGate modGate() { return modGate; }
    public AdminModeManager adminMode() { return adminMode; }
    public AdminBroadcastManager broadcasts() { return broadcasts; }
    public YouTubeManager youtube() { return youtube; }
    public RuleManager rules() { return rules; }
    public ModerationManager moderation() { return moderation; }
    public VanishManager vanish() { return vanish; }
    public ClanManager clans() { return clans; }
    public GraveManager graves() { return graves; }
    public BorderManager borders() { return borders; }
    public PlaytimeManager playtime() { return playtime; }
    public CombatManager combat() { return combat; }
    public EndFightManager endFight() { return endFight; }
    public ProjectManager projects() { return projects; }
    public ZoneManager zones() { return zones; }
    public MenuListener menus() { return menus; }
}
