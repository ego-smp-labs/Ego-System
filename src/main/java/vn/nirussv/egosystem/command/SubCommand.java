package vn.nirussv.egosystem.command;

import org.bukkit.command.CommandSender;
import java.util.List;

public interface SubCommand {
    
    /**
     * Get the primary name of the subcommand.
     */
    String getName();

    /**
     * Get the permission required to run this subcommand. Can be null.
     */
    String getPermission();

    /**
     * Execute the subcommand.
     * @param sender The sender (Console or Player).
     * @param args The full array of arguments (including the subcommand name at args[0]).
     */
    void execute(CommandSender sender, String[] args);

    /**
     * Provide tab completions for the subcommand.
     * @param sender The sender.
     * @param args The current array of arguments.
     * @return A list of suggestions.
     */
    List<String> getTabCompletions(CommandSender sender, String[] args);
}
