package vn.nirussv.serverauto;

import org.bukkit.plugin.java.JavaPlugin;
import vn.nirussv.serverauto.backup.BackupTask;
import vn.nirussv.serverauto.backup.LocalBackupService;
import vn.nirussv.serverauto.command.AutoCommand;
import vn.nirussv.serverauto.config.ConfigManager;
import vn.nirussv.serverauto.update.UpdateService;

import java.util.logging.Level;

/**
 * Main plugin class for Server Automation.
 * Provides local backup and auto-update functionality.
 */
public class ServerAutoPlugin extends JavaPlugin {

    private static ServerAutoPlugin instance;
    
    private ConfigManager configManager;
    private LocalBackupService backupService;
    private UpdateService updateService;
    private BackupTask backupTask;

    @Override
    public void onEnable() {
        instance = this;
        
        try {
            // Save default config
            saveDefaultConfig();
            
            // Initialize managers
            this.configManager = new ConfigManager(this);
            this.backupService = new LocalBackupService(this, configManager);
            this.updateService = new UpdateService(this, configManager);
            
            // Register commands
            AutoCommand autoCommand = new AutoCommand(this, backupService, updateService, configManager);
            getCommand("auto").setExecutor(autoCommand);
            getCommand("auto").setTabCompleter(autoCommand);
            
            // Start scheduled tasks
            if (configManager.isLocalBackupEnabled()) {
                startBackupScheduler();
            }
            
            if (configManager.isAutoUpdateEnabled()) {
                startUpdateChecker();
            }
            
            getLogger().info("ServerAutomation has been enabled! Version: " + getDescription().getVersion());
            getLogger().info("Backup folder: " + configManager.getBackupFolder());
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable ServerAutomation!", e);
        }
    }

    @Override
    public void onDisable() {
        // Cancel scheduled tasks
        if (backupTask != null) {
            backupTask.cancel();
        }
        
        // Backup on shutdown if enabled
        if (configManager != null && configManager.isBackupOnShutdown()) {
            getLogger().info("Performing shutdown backup...");
            try {
                backupService.performBackup(true);
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Shutdown backup failed", e);
            }
        }
        
        getLogger().info("ServerAutomation has been disabled.");
        instance = null;
    }

    /**
     * Starts the backup scheduler based on cron expression.
     */
    private void startBackupScheduler() {
        String cron = configManager.getBackupSchedule();
        long intervalTicks = parseCronToTicks(cron);
        
        if (intervalTicks > 0) {
            this.backupTask = new BackupTask(this, backupService);
            backupTask.runTaskTimerAsynchronously(this, intervalTicks, intervalTicks);
            getLogger().info("Backup scheduler started. Interval: " + (intervalTicks / 20 / 60) + " minutes");
        }
    }

    /**
     * Starts the update checker.
     */
    private void startUpdateChecker() {
        long intervalSeconds = configManager.getUpdateCheckInterval();
        long intervalTicks = intervalSeconds * 20L;
        
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            updateService.checkForUpdates();
        }, intervalTicks, intervalTicks);
        
        getLogger().info("Update checker started. Interval: " + intervalSeconds + " seconds");
    }

    /**
     * Simple cron parser - converts to tick interval.
     */
    private long parseCronToTicks(String cron) {
        long defaultTicks = 6 * 60 * 60 * 20L; // 6 hours in ticks
        
        try {
            String[] parts = cron.split(" ");
            if (parts.length < 2) return defaultTicks;
            
            String hourPart = parts[1];
            if (hourPart.startsWith("*/")) {
                int hours = Integer.parseInt(hourPart.substring(2));
                return hours * 60L * 60L * 20L;
            } else if (hourPart.equals("*")) {
                return 60L * 60L * 20L;
            } else if (hourPart.equals("0")) {
                return 24L * 60L * 60L * 20L;
            }
        } catch (Exception e) {
            getLogger().warning("Failed to parse cron: " + cron + ". Using default 6-hour interval.");
        }
        
        return defaultTicks;
    }

    public void reload() {
        reloadConfig();
        configManager.reload();
        getLogger().info("Configuration reloaded.");
    }

    public static ServerAutoPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LocalBackupService getBackupService() {
        return backupService;
    }

    public UpdateService getUpdateService() {
        return updateService;
    }
}
