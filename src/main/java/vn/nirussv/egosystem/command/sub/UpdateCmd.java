package vn.nirussv.egosystem.command.sub;

import org.bukkit.command.CommandSender;
import vn.nirussv.egosystem.EgoSystemPlugin;
import vn.nirussv.egosystem.command.SubCommand;
import vn.nirussv.egosystem.update.UpdateService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class UpdateCmd implements SubCommand {

    private final EgoSystemPlugin plugin;
    private final UpdateService updateService;

    public UpdateCmd(EgoSystemPlugin plugin, UpdateService updateService) {
        this.plugin = plugin;
        this.updateService = updateService;
    }

    @Override
    public String getName() {
        return "update";
    }

    @Override
    public String getPermission() {
        return "serverauto.update";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eUsage: /ssm update <check|apply|list|download>");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "check" -> {
                sender.sendMessage("§aChecking for updates...");
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    updateService.checkForUpdates();
                    var pending = updateService.getPendingUpdates();
                    if (pending.isEmpty()) {
                        sender.sendMessage("§aAll plugins are up to date!");
                    } else {
                        sender.sendMessage("§eUpdates available:");
                        pending.forEach((name, info) ->
                                sender.sendMessage("§7- §f" + name + ": " + info.currentVersion() + " -> " + info.latestVersion())
                        );
                    }
                });
            }
            case "apply" -> {
                sender.sendMessage("§aApplying updates...");
                updateService.applyUpdates();
            }
            case "list" -> {
                var pending = updateService.getPendingUpdates();
                if (pending.isEmpty()) {
                    sender.sendMessage("§aNo pending updates.");
                } else {
                    sender.sendMessage("§e=== Pending Updates ===");
                    pending.forEach((name, info) ->
                            sender.sendMessage("§7- §f" + name + ": " + info.currentVersion() + " -> " + info.latestVersion())
                    );
                }
            }
            case "download" -> {
                if (args.length < 3) {
                    sender.sendMessage("§eUsage: /ssm update download <plugin>");
                    return;
                }
                String pluginName = args[2];
                sender.sendMessage("§aDownloading update for " + pluginName + "...");
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    if (updateService.downloadUpdate(pluginName)) {
                        sender.sendMessage("§aUpdate downloaded! Restart server to apply.");
                    } else {
                        sender.sendMessage("§cFailed to download update.");
                    }
                });
            }
            default -> sender.sendMessage("§eUsage: /ssm update <check|apply|list|download>");
        }
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return Arrays.asList("check", "apply", "list", "download").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("download")) {
            return new ArrayList<>(updateService.getPendingUpdates().keySet()).stream()
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
