package vn.nirussv.egosystem.command.sub;

import org.bukkit.command.CommandSender;
import vn.nirussv.egosystem.EgoSystemPlugin;
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

    private final EgoSystemPlugin plugin;
    private final LocalBackupService backupService;
    private final ConfigManager config;

    public BackupCmd(EgoSystemPlugin plugin, LocalBackupService backupService, ConfigManager config) {
        this.plugin = plugin;
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
            sender.sendMessage("§eUsage: /ssm backup <now|status|list|gdrive>");
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
            case "gdrive" -> {
                if (args.length < 3) {
                    sender.sendMessage("§eUsage: /ssm backup gdrive <link|verify|status>");
                    return;
                }
                switch (args[2].toLowerCase()) {
                    case "link" -> {
                        if (backupService.isGoogleDriveLinked()) {
                            sender.sendMessage("§aGoogle Drive đã liên kết rồi! Dùng /ssm backup gdrive status để xem thông tin.");
                            return;
                        }
                        String authUrl = backupService.generateGoogleDriveAuthUrl();
                        if (authUrl == null) {
                            sender.sendMessage("§cLỗi: Thiếu client-id trong config.yml. Vui lòng thiết lập google-drive.client-id trước.");
                            return;
                        }
                        sender.sendMessage("§a§l=== Liên kết Google Drive ===");
                        sender.sendMessage("§eBước 1: Mở link bên dưới trong trình duyệt:");
                        sender.sendMessage("§b§n" + authUrl);
                        sender.sendMessage("§eBước 2: Đăng nhập Google và cấp quyền.");
                        sender.sendMessage("§eBước 3: Copy code và chạy:");
                        sender.sendMessage("§f/ssm backup gdrive verify <code>");
                    }
                    case "verify" -> {
                        if (args.length < 4) {
                            sender.sendMessage("§eUsage: /ssm backup gdrive verify <authorization_code>");
                            return;
                        }
                        String code = args[3].trim();
                        sender.sendMessage("§eĐang xác minh...");
                        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                            boolean success = backupService.exchangeGoogleDriveCode(code);
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                if (success) {
                                    sender.sendMessage("§a§l✔ Liên kết Google Drive thành công!");
                                    sender.sendMessage("§7Backup sẽ tự động sync lên Drive khi chạy.");
                                } else {
                                    sender.sendMessage("§c§l✘ Liên kết thất bại. Kiểm tra console để xem lỗi chi tiết.");
                                    sender.sendMessage("§7Đảm bảo code chính xác và client-id/client-secret đúng trong config.yml.");
                                }
                            });
                        });
                    }
                    case "status" -> {
                        boolean linked = backupService.isGoogleDriveLinked();
                        sender.sendMessage("§e=== Google Drive Status ===");
                        sender.sendMessage("§7Enabled: §f" + (config.isGoogleDriveEnabled() ? "§aYes" : "§cNo"));
                        sender.sendMessage("§7Linked: " + (linked ? "§a✔ Connected" : "§c✘ Not linked"));
                        sender.sendMessage("§7Folder ID: §f" + (config.getGoogleDriveFolderId().isEmpty() ? "(root)" : config.getGoogleDriveFolderId()));
                        if (!linked) {
                            sender.sendMessage("§7Chạy §f/ssm backup gdrive link §7để liên kết.");
                        }
                    }
                    default -> sender.sendMessage("§eUsage: /ssm backup gdrive <link|verify|status>");
                }
            }
            default -> sender.sendMessage("§eUsage: /ssm backup <now|status|list|gdrive>");
        }
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return Arrays.asList("now", "status", "list", "gdrive").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("gdrive")) {
            return Arrays.asList("link", "verify", "status").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private String formatTime(long timestamp) {
        if (timestamp == 0) return "Never";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));
    }
}
