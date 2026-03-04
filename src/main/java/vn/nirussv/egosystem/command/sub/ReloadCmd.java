package vn.nirussv.egosystem.command.sub;

import org.bukkit.command.CommandSender;
import vn.nirussv.egosystem.EgoSystemPlugin;
import vn.nirussv.egosystem.command.SubCommand;

import java.util.Collections;
import java.util.List;

public class ReloadCmd implements SubCommand {

    private final EgoSystemPlugin plugin;

    public ReloadCmd(EgoSystemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String getPermission() {
        return "serverauto.admin";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        plugin.reload();
        sender.sendMessage("§aServerAutomation configuration reloaded!");
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
