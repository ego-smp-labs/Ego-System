package vn.nirussv.egosystem.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import vn.nirussv.egosystem.EgoSystemPlugin;

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

    // ==================== Auto-Update Settings (New) ====================

    public int getUpdateInterval() {
        return config.getInt("updates.interval", 120);
    }

    public int getUpdateBootTime() {
        return config.getInt("updates.bootTime", 50);
    }

    public List<String> getUpdateCronSchedule() {
        return config.getStringList("updates.schedule.cron");
    }

    public String getUpdateTimezone() {
        return config.getString("updates.schedule.timezone", "UTC+7");
    }

    public String getGitHubKey() {
        return config.getString("updates.key", "");
    }
    
    public boolean isUseUpdateFolder() {
        return config.getBoolean("updates.behavior.useUpdateFolder", true);
    }
    
    public int getMaxParallelDownloads() {
        return config.getInt("updates.performance.maxParallel", 4);
    }

    // Deprecated methods kept/adapted for compatibility or removal if unused
    public boolean isAutoUpdateEnabled() {
        // We can imply enabled if interval > 0 or cron is set, 
        // but for now let's assume always enabled if the new config is present,
        // or fall back to old config.
        return true; 
    }
    
    public Map<String, String> getPluginSources() {
        // This should read from list.yml, which is not in config.yml
        // We will handle list.yml loading in UpdateService
        return new HashMap<>();
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
