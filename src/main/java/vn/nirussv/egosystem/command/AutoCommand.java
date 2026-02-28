package vn.nirussv.egosystem.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import vn.nirussv.egosystem.EgoSystemPlugin;
import vn.nirussv.egosystem.backup.LocalBackupService;
import vn.nirussv.egosystem.config.ConfigManager;
import vn.nirussv.egosystem.update.UpdateService;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Main command handler for /auto command.
 */
public class AutoCommand implements CommandExecutor, TabCompleter {

    private final EgoSystemPlugin plugin;
    private final LocalBackupService backupService;
    private final UpdateService updateService;
    private final ConfigManager config;

    public AutoCommand(EgoSystemPlugin plugin, LocalBackupService backupService, 
                       UpdateService updateService, ConfigManager config) {
        this.plugin = plugin;
        this.backupService = backupService;
        this.updateService = updateService;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("serverauto.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        if (args[0].equalsIgnoreCase("backup")) {
            handleBackup(sender, args);
        } else if (args[0].equalsIgnoreCase("update")) {
            handleUpdate(sender, args);
        } else if (args[0].equalsIgnoreCase("reload")) {
            handleReload(sender);
        } else if (args[0].equalsIgnoreCase("status")) {
            handleStatus(sender);
        } else if (args[0].equalsIgnoreCase("event")) {
            handleEvent(sender, args);
        } else {
            showHelp(sender);
        }
        
        return true;
    }

    private void handleBackup(CommandSender sender, String[] args) {
        if (!sender.hasPermission("serverauto.backup")) {
            sender.sendMessage("§cYou don't have permission to use backup commands.");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§eUsage: /auto backup <now|status|list>");
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
            default -> sender.sendMessage("§eUsage: /auto backup <now|status|list>");
        }
    }

    private void handleUpdate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("serverauto.update")) {
            sender.sendMessage("§cYou don't have permission to use update commands.");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§eUsage: /auto update <check|apply|list>");
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
                    sender.sendMessage("§eUsage: /auto update download <plugin>");
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
            default -> sender.sendMessage("§eUsage: /auto update <check|apply|list|download>");
        }
    }

    private void handleReload(CommandSender sender) {
        plugin.reload();
        sender.sendMessage("§aServerAutomation configuration reloaded!");
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage("§e=== ServerAutomation Status ===");
        sender.sendMessage("§7Version: §f" + plugin.getDescription().getVersion());
        sender.sendMessage("§7Local Backup: §f" + (config.isLocalBackupEnabled() ? "Enabled" : "Disabled"));
        sender.sendMessage("§7Auto-Update: §f" + (config.isAutoUpdateEnabled() ? "Enabled" : "Disabled"));
        sender.sendMessage("§7GitHub Sync: §f" + (config.isGitHubEnabled() ? "Enabled" : "Disabled"));
        sender.sendMessage("§7Last Backup: §f" + formatTime(backupService.getLastBackupTime()));
        sender.sendMessage("§7Last Update Check: §f" + formatTime(updateService.getLastCheckTime()));
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§8[§cEgo System§8] §7Commands:");
        sender.sendMessage("§8- §c/esm backup start §7- Start a manual backup");
        sender.sendMessage("§8- §c/esm update check §7- Check for plugin updates");
        sender.sendMessage("§8- §c/esm update force §7- Force update and restart");
        sender.sendMessage("§8- §c/esm reload §7- Reload config");
        sender.sendMessage("§8- §c/esm status §7- Show system status");
        sender.sendMessage("§8- §c/esm event <start/stop/locate> §7- Manage EgoSMP events");
    }

    private void handleEvent(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /esm event <start/stop/locate>");
            return;
        }
        
        // Dispatch the command as if the player typed /sabi event <args>
        String subAction = args[1].toLowerCase();
        org.bukkit.Bukkit.dispatchCommand(sender, "sabi event " + subAction);
        
        // Custom cleanup logic for stopping the event across the server
        if (subAction.equals("stop")) {
            sender.sendMessage("§a[Ego-System] Initiating strict cleanup of 'event_betrayer_heart'...");
            int removed = 0;
            org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey("sabi", "ego_item_key");
            
            // Clean online players
            for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                org.bukkit.inventory.PlayerInventory inv = p.getInventory();
                for (int i = 0; i < inv.getSize(); i++) {
                    org.bukkit.inventory.ItemStack item = inv.getItem(i);
                    if (item != null && item.hasItemMeta() && "event_betrayer_heart".equals(item.getItemMeta().getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.STRING))) {
                        inv.setItem(i, null);
                        removed++;
                    }
                }
            }
            
            // Clean dropped items in all loaded worlds
            for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
                for (org.bukkit.entity.Entity entity : world.getEntitiesByClass(org.bukkit.entity.Item.class)) {
                    org.bukkit.inventory.ItemStack item = ((org.bukkit.entity.Item) entity).getItemStack();
                    if (item != null && item.hasItemMeta() && "event_betrayer_heart".equals(item.getItemMeta().getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.STRING))) {
                        entity.remove();
                        removed++;
                    }
                }
            }
            sender.sendMessage("§a[Ego-System] Cleanup complete. Removed " + removed + " instances of the Heart.");
        }
    }
    private String formatTime(long timestamp) {
        if (timestamp == 0) return "Never";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("egosystem.admin")) {
            return Collections.emptyList();
        }
        
        if (args.length == 1) {
            return filterStartsWith(Arrays.asList("backup", "update", "reload", "status", "event"), args[0]);
        }
        
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "backup" -> filterStartsWith(Arrays.asList("start"), args[1]);
                case "update" -> filterStartsWith(Arrays.asList("check", "force"), args[1]);
                case "event" -> filterStartsWith(Arrays.asList("start", "stop", "locate"), args[1]);
                default -> Collections.emptyList();
            };
        }
        
        if (args.length == 3 && args[0].equalsIgnoreCase("update") && args[1].equalsIgnoreCase("download")) {
            return filterStartsWith(new ArrayList<>(updateService.getPendingUpdates().keySet()), args[2]);
        }
        
        return Collections.emptyList();
    }

    private List<String> filterStartsWith(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .toList();
    }
}
