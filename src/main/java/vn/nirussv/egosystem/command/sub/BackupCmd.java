package vn.nirussv.egosystem.command.sub;

import org.bukkit.command.CommandSender;
import vn.nirussv.egosystem.backup.LocalBackupService;
import vn.nirussv.egosystem.command.SubCommand;
import vn.nirussv.egosystem.config.ConfigManager;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class BackupCmd implements SubCommand {

    private final LocalBackupService backupService;
    private final ConfigManager config;

    public BackupCmd(LocalBackupService backupService, ConfigManager config) {
        this.backupService = backupService;
        this.config = config;
    }

    @Override
    public String getName() {
        return "backup";
    }

    @Override
    public String getPermission() {
        return "serverauto.backup";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eUsage: /ssm backup <now|status|list>");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "now" -> {
                sender.sendMessage("§aStarting backup... This may take a while.");
                backupService.performBackup(false);
            }
            case "status" -> {
                sender.sendMessage("§e=== Backup Status ===");
                sender.sendMessage("§7Last backup: §f" + formatTime(backupService.getLastBackupTime()));
                sender.sendMessage("§7Last file: §f" + (backupService.getLastBackupFile() != null ? backupService.getLastBackupFile() : "N/A"));
                sender.sendMessage("§7Status: §f" + backupService.getLastBackupStatus());
                sender.sendMessage("§7Backup folder: §f" + config.getBackupFolder());
                sender.sendMessage("§7Schedule: §f" + config.getBackupSchedule());
                sender.sendMessage("§7Backup format: §f" + config.getBackupFormat());
                sender.sendMessage("§7Backup worlds: §f" + (config.isBackupWorlds() ? "Yes" : "No"));
                sender.sendMessage("§7Backup plugins: §f" + (config.isBackupPlugins() ? "Yes" : "No"));
                sender.sendMessage("§7Backup configs: §f" + (config.isBackupConfigs() ? "Yes" : "No"));
            }
            case "list" -> {
                List<String> backups = backupService.listBackups();
                if (backups.isEmpty()) {
                    sender.sendMessage("§eNo backups found.");
                } else {
                    sender.sendMessage("§e=== Available Backups ===");
                    for (String backup : backups) {
                        sender.sendMessage("§7- §f" + backup);
                    }
                }
            }
            default -> sender.sendMessage("§eUsage: /ssm backup <now|status|list>");
        }
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return Arrays.asList("now", "status", "list").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private String formatTime(long timestamp) {
        if (timestamp == 0) return "Never";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));
    }
}
