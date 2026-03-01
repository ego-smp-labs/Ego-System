package vn.nirussv.egosystem.event;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import vn.nirussv.egosystem.EgoSystemPlugin;

import java.io.File;
import java.io.IOException;

public class EventConfig {

    private final EgoSystemPlugin plugin;
    private final File configFile;
    private FileConfiguration config;

    private Location winZone;
    private int bossSpawnRadius;
    private int countdownSeconds;
    private int phase1Seconds;
    private int phase2Seconds;

    public EventConfig(EgoSystemPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "event.yml");
        loadDefaults();
        load();
    }

    private void loadDefaults() {
        this.bossSpawnRadius = 100;
        this.countdownSeconds = 900;
        this.phase1Seconds = 300;
        this.phase2Seconds = 600;
    }

    public void load() {
        if (!configFile.exists()) {
            save();
            return;
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        bossSpawnRadius = config.getInt("boss-spawn-radius", 100);
        countdownSeconds = config.getInt("countdown-seconds", 900);
        phase1Seconds = config.getInt("phase1-seconds", 300);
        phase2Seconds = config.getInt("phase2-seconds", 600);

        if (config.contains("win-zone.world")) {
            String worldName = config.getString("win-zone.world");
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
            if (world != null) {
                double x = config.getDouble("win-zone.x");
                double y = config.getDouble("win-zone.y");
                double z = config.getDouble("win-zone.z");
                winZone = new Location(world, x, y, z);
            }
        }
    }

    public void save() {
        config = new YamlConfiguration();
        config.set("boss-spawn-radius", bossSpawnRadius);
        config.set("countdown-seconds", countdownSeconds);
        config.set("phase1-seconds", phase1Seconds);
        config.set("phase2-seconds", phase2Seconds);
        if (winZone != null && winZone.getWorld() != null) {
            config.set("win-zone.world", winZone.getWorld().getName());
            config.set("win-zone.x", winZone.getX());
            config.set("win-zone.y", winZone.getY());
            config.set("win-zone.z", winZone.getZ());
        }
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save event.yml: " + e.getMessage());
        }
    }

    public Location getWinZone() { return winZone; }
    public void setWinZone(Location loc) { this.winZone = loc; save(); }
    public int getBossSpawnRadius() { return bossSpawnRadius; }
    public void setBossSpawnRadius(int r) { this.bossSpawnRadius = r; save(); }
    public int getCountdownSeconds() { return countdownSeconds; }
    public int getPhase1Seconds() { return phase1Seconds; }
    public int getPhase2Seconds() { return phase2Seconds; }
}
