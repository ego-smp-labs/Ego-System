package vn.nirussv.egosystem.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import vn.nirussv.egosystem.EgoSystemPlugin;
import vn.nirussv.egosystem.backup.LocalBackupService;
import vn.nirussv.egosystem.command.SubCommand;
import vn.nirussv.egosystem.config.ConfigManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class RestoreCmd implements SubCommand {

    private final EgoSystemPlugin plugin;
    private final LocalBackupService backupService;
    private final ConfigManager config;

    public RestoreCmd(EgoSystemPlugin plugin, LocalBackupService backupService, ConfigManager config) {
        this.plugin = plugin;
        this.backupService = backupService;
        this.config = config;
    }

    @Override
    public String getName() {
        return "restore";
    }

    @Override
    public String getPermission() {
        return "serverauto.admin";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eUsage: /ssm restore <local|gdrive|github> <filename> [unzip: true/false] OR /ssm restore confirm-stop");
            return;
        }

        if (args[1].equalsIgnoreCase("confirm-stop")) {
            sender.sendMessage("§c[Restore] Đang tự động lưu thế giới và đẩy người chơi ra...");
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                p.kickPlayer("§cServer đang đóng để áp dụng bản Backup Restore.\n§eXin vui lòng trở lại sau ít phút.");
            }
            Bukkit.savePlayers();
            for (org.bukkit.World w : Bukkit.getWorlds()) {
                w.save();
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Bukkit.shutdown();
            }, 60L);
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§eUsage: /ssm restore <local|gdrive|github> <filename> [unzip: true/false]");
            return;
        }

        String source = args[1].toLowerCase();
        String fileName = args[2];
        boolean unzip = false;

        if (args.length >= 4) {
            unzip = Boolean.parseBoolean(args[3]);
        }

        backupService.prepareRestoreContext(source, fileName, unzip, sender);
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return Arrays.asList("local", "gdrive", "github", "confirm-stop").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && !args[1].equalsIgnoreCase("confirm-stop")) {
            if (args[1].equalsIgnoreCase("local")) {
                return backupService.listBackups().stream()
                        .map(n -> n.split(" ")[0]) // remove file sizes from "listBackups"
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        if (args.length == 4 && !args[1].equalsIgnoreCase("confirm-stop")) {
            return Arrays.asList("true", "false").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
