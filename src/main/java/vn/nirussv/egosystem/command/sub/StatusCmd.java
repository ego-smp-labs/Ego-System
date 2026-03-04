package vn.nirussv.egosystem.command.sub;

import org.bukkit.command.CommandSender;
import vn.nirussv.egosystem.EgoSystemPlugin;
import vn.nirussv.egosystem.backup.LocalBackupService;
import vn.nirussv.egosystem.command.SubCommand;
import vn.nirussv.egosystem.config.ConfigManager;
import vn.nirussv.egosystem.update.UpdateService;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class StatusCmd implements SubCommand {

    private final EgoSystemPlugin plugin;
    private final LocalBackupService backupService;
    private final UpdateService updateService;
    private final ConfigManager config;

    public StatusCmd(EgoSystemPlugin plugin, LocalBackupService backupService, UpdateService updateService, ConfigManager config) {
        this.plugin = plugin;
        this.backupService = backupService;
        this.updateService = updateService;
        this.config = config;
    }

    @Override
    public String getName() {
        return "status";
    }

    @Override
    public String getPermission() {
        return "serverauto.admin";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        sender.sendMessage("§e=== ServerAutomation Status ===");
        sender.sendMessage("§7Version: §f" + plugin.getDescription().getVersion());
        sender.sendMessage("§7Local Backup: §f" + (config.isLocalBackupEnabled() ? "Enabled" : "Disabled"));
        sender.sendMessage("§7Auto-Update: §f" + (config.isAutoUpdateEnabled() ? "Enabled" : "Disabled"));
        sender.sendMessage("§7GitHub Sync: §f" + (config.isGitHubEnabled() ? "Enabled" : "Disabled"));
        sender.sendMessage("§7Google Drive Sync: §f" + (config.isGoogleDriveEnabled() ? "Enabled" : "Disabled"));
        sender.sendMessage("§7Last Backup: §f" + formatTime(backupService.getLastBackupTime()));
        sender.sendMessage("§7Last Update Check: §f" + formatTime(updateService.getLastCheckTime()));
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    private String formatTime(long timestamp) {
        if (timestamp == 0) return "Never";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));
    }
}
