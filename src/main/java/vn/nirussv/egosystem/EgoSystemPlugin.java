package vn.nirussv.egosystem;

import org.bukkit.plugin.java.JavaPlugin;
import vn.nirussv.egosystem.backup.BackupTask;
import vn.nirussv.egosystem.backup.LocalBackupService;
import vn.nirussv.egosystem.command.AutoCommand;
import vn.nirussv.egosystem.config.ConfigManager;
import vn.nirussv.egosystem.event.*;
import vn.nirussv.egosystem.update.UpdateService;

import java.util.logging.Level;

/**
 * Main plugin class for Ego-System.
 * Provides local backup and auto-update functionality.
 */
public class EgoSystemPlugin extends JavaPlugin {

    private static EgoSystemPlugin instance;
    
    private ConfigManager configManager;
    private LocalBackupService backupService;
    private UpdateService updateService;
    private BackupTask backupTask;
    private EventStateMachine eventStateMachine;
    private EventKillTracker eventKillTracker;

    @Override
    public void onEnable() {
        instance = this;
        
        try {
            // Save default config
            saveDefaultConfig();
            
            // Initialize managers
            this.configManager = new ConfigManager(this);
            // TODO: Refactor services to use new package structure if needed
            this.backupService = new LocalBackupService(this, configManager);
            this.updateService = new UpdateService(this, configManager);
            
            EventConfig eventConfig = new EventConfig(this);
            this.eventKillTracker = new EventKillTracker();
            BossZombieManager bossManager = new BossZombieManager(this, eventConfig);
            this.eventStateMachine = new EventStateMachine(this, eventConfig, eventKillTracker, bossManager);
            getServer().getPluginManager().registerEvents(new EventListener(this, eventStateMachine), this);
            
            if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                new vn.nirussv.egosystem.papi.EgoEventExpansion(this, eventKillTracker).register();
                getLogger().info("PlaceholderAPI expansion registered.");
            }
            
            AutoCommand autoCommand = new AutoCommand(this, backupService, updateService, configManager, eventStateMachine);
            getCommand("ssm").setExecutor(autoCommand);
            getCommand("ssm").setTabCompleter(autoCommand);
            
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
        
        getLogger().info("Ego-System has been disabled.");
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

    public static EgoSystemPlugin getInstance() {
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

    public EventStateMachine getEventStateMachine() {
        return eventStateMachine;
    }

    public EventKillTracker getEventKillTracker() {
        return eventKillTracker;
    }
}
