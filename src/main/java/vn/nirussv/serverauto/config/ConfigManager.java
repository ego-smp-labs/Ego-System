package vn.nirussv.serverauto.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import vn.nirussv.serverauto.ServerAutoPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages plugin configuration.
 */
public class ConfigManager {

    private final ServerAutoPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(ServerAutoPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    // ==================== GitHub Settings ====================

    public boolean isGitHubEnabled() {
        return config.getBoolean("github.enabled", true);
    }

    public String getGitHubToken() {
        return config.getString("github.token", "");
    }

    public String getGitHubRepository() {
        return config.getString("github.repository", "");
    }

    public String getGitHubBranch() {
        return config.getString("github.branch", "main");
    }

    public List<String> getBackupPaths() {
        return config.getStringList("github.backup-paths");
    }

    // ==================== Backup Settings ====================

    public boolean isBackupEnabled() {
        return config.getBoolean("backup.enabled", true);
    }

    public String getBackupSchedule() {
        return config.getString("backup.schedule", "0 */6 * * *");
    }

    public boolean isBackupOnShutdown() {
        return config.getBoolean("backup.on-shutdown", true);
    }

    public boolean isCompressionEnabled() {
        return config.getBoolean("backup.compress", true);
    }

    public int getMaxBackups() {
        return config.getInt("backup.max-backups", 10);
    }

    public String getCommitMessageTemplate() {
        return config.getString("backup.commit-message", "Backup: %date% %time%");
    }

    // ==================== Auto-Update Settings ====================

    public boolean isAutoUpdateEnabled() {
        return config.getBoolean("auto-update.enabled", false);
    }

    public long getUpdateCheckInterval() {
        return config.getLong("auto-update.check-interval", 86400);
    }

    public boolean isNotifyAdmins() {
        return config.getBoolean("auto-update.notify-admins", true);
    }

    public boolean isAutoApply() {
        return config.getBoolean("auto-update.auto-apply", false);
    }

    /**
     * Gets the list of plugins to monitor for updates.
     * @return Map of plugin name to source configuration
     */
    public Map<String, PluginUpdateConfig> getPluginsToUpdate() {
        Map<String, PluginUpdateConfig> plugins = new HashMap<>();
        
        ConfigurationSection section = config.getConfigurationSection("auto-update.plugins");
        if (section == null) return plugins;
        
        for (String pluginName : section.getKeys(false)) {
            ConfigurationSection pluginSection = section.getConfigurationSection(pluginName);
            if (pluginSection == null) continue;
            
            String source = pluginSection.getString("source", "github");
            String repo = pluginSection.getString("repo", "");
            String url = pluginSection.getString("url", "");
            boolean autoApply = pluginSection.getBoolean("auto-apply", isAutoApply());
            
            plugins.put(pluginName, new PluginUpdateConfig(source, repo, url, autoApply));
        }
        
        return plugins;
    }

    // ==================== Logging Settings ====================

    public boolean isVerbose() {
        return config.getBoolean("logging.verbose", false);
    }

    public boolean isLogToFile() {
        return config.getBoolean("logging.log-to-file", true);
    }

    /**
     * Configuration for a plugin update source.
     */
    public record PluginUpdateConfig(String source, String repo, String url, boolean autoApply) {}
}
