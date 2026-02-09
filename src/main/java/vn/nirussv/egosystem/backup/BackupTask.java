package vn.nirussv.egosystem.backup;

import org.bukkit.scheduler.BukkitRunnable;
import vn.nirussv.egosystem.EgoSystemPlugin;

/**
 * Scheduled task for automatic backups.
 */
public class BackupTask extends BukkitRunnable {

    private final ServerAutoPlugin plugin;
    private final LocalBackupService backupService;

    public BackupTask(ServerAutoPlugin plugin, LocalBackupService backupService) {
        this.plugin = plugin;
        this.backupService = backupService;
    }

    @Override
    public void run() {
        plugin.getLogger().info("Running scheduled backup...");
        backupService.performBackup(false);
    }
}
