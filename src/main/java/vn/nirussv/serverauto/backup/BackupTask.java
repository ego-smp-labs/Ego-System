package vn.nirussv.serverauto.backup;

import org.bukkit.scheduler.BukkitRunnable;
import vn.nirussv.serverauto.ServerAutoPlugin;

/**
 * Scheduled task for automatic backups.
 */
public class BackupTask extends BukkitRunnable {

    private final ServerAutoPlugin plugin;
    private final BackupService backupService;

    public BackupTask(ServerAutoPlugin plugin, BackupService backupService) {
        this.plugin = plugin;
        this.backupService = backupService;
    }

    @Override
    public void run() {
        plugin.getLogger().info("Running scheduled backup...");
        backupService.performBackup(false);
    }
}
