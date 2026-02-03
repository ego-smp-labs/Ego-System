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

    // ==================== LOCAL BACKUP Settings ====================

    public boolean isLocalBackupEnabled() {
        return config.getBoolean("local-backup.enabled", true);
    }

    public String getBackupFolder() {
        return config.getString("local-backup.backup-folder", "backups");
    }

    public int getMaxBackups() {
        return config.getInt("local-backup.max-backups", 10);
    }

    public String getBackupSchedule() {
        return config.getString("local-backup.schedule", "0 */6 * * *");
    }

    public boolean isBackupOnShutdown() {
        return config.getBoolean("local-backup.on-shutdown", true);
    }

    public boolean isBackupWorlds() {
        return config.getBoolean("local-backup.backup-worlds", true);
    }

    public boolean isBackupPlugins() {
        return config.getBoolean("local-backup.backup-plugins", true);
    }

    public boolean isBackupConfigs() {
        return config.getBoolean("local-backup.backup-configs", true);
    }

    public List<String> getIncludePaths() {
        return config.getStringList("local-backup.include-paths");
    }

    public List<String> getExcludePaths() {
        return config.getStringList("local-backup.exclude-paths");
    }

    public boolean isCompressionEnabled() {
        return config.getBoolean("local-backup.compress", true);
    }

    // ==================== GitHub Settings ====================

    public boolean isGitHubEnabled() {
        return config.getBoolean("github.enabled", false);
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

    public boolean isSyncBackups() {
        return config.getBoolean("github.sync-backups", true);
    }

    public String getCommitMessageTemplate() {
        return config.getString("github.commit-message", "Backup: %date% %time%");
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

    public record PluginUpdateConfig(String source, String repo, String url, boolean autoApply) {}
}
