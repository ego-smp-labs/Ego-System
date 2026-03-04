package vn.nirussv.egosystem.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import vn.nirussv.egosystem.EgoSystemPlugin;
import vn.nirussv.egosystem.backup.LocalBackupService;
import vn.nirussv.egosystem.command.sub.*;
import vn.nirussv.egosystem.config.ConfigManager;
import vn.nirussv.egosystem.event.EventStateMachine;
import vn.nirussv.egosystem.update.UpdateService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Main command handler acting as a Registry for SubCommands.
 */
public class AutoCommand implements CommandExecutor, TabCompleter {

    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public AutoCommand(EgoSystemPlugin plugin, LocalBackupService backupService,
                       UpdateService updateService, ConfigManager config,
                       EventStateMachine eventStateMachine) {
        
        register(new BackupCmd(backupService, config));
        register(new UpdateCmd(plugin, updateService));
        register(new EventCmd(eventStateMachine));
        register(new ReloadCmd(plugin));
        register(new StatusCmd(plugin, backupService, updateService, config));
        register(new RestoreCmd(plugin, backupService, config));
    }

    private void register(SubCommand cmd) {
        subCommands.put(cmd.getName().toLowerCase(), cmd);
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

        SubCommand subCmd = subCommands.get(args[0].toLowerCase());
        if (subCmd == null) {
            showHelp(sender);
            return true;
        }

        if (subCmd.getPermission() != null && !sender.hasPermission(subCmd.getPermission())) {
            sender.sendMessage("§cYou don't have permission to use this sub-command.");
            return true;
        }

        subCmd.execute(sender, args);
        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§8[§cEgo System§8] §7Commands:");
        sender.sendMessage("§8- §c/ssm backup <now|status|list> §7- Backup management");
        sender.sendMessage("§8- §c/ssm update <check|apply|list|download> §7- Plugin updates");
        sender.sendMessage("§8- §c/ssm restore <source> <file> <unzip> §7- Restore backups");
        sender.sendMessage("§8- §c/ssm reload §7- Reload config");
        sender.sendMessage("§8- §c/ssm status §7- Show system status");
        sender.sendMessage("§8- §c/ssm event <start|stop|end|phase|spawn-zombie|setting> §7- Event management");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("serverauto.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return subCommands.keySet().stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        SubCommand subCmd = subCommands.get(args[0].toLowerCase());
        if (subCmd != null) {
            return subCmd.getTabCompletions(sender, args);
        }

        return new ArrayList<>();
    }
}
