package vn.nirussv.egosystem.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import vn.nirussv.egosystem.EgoSystemPlugin;
import vn.nirussv.egosystem.event.EventKillTracker;

public class EgoEventExpansion extends PlaceholderExpansion {

    private final EgoSystemPlugin plugin;
    private final EventKillTracker killTracker;

    public EgoEventExpansion(EgoSystemPlugin plugin, EventKillTracker killTracker) {
        this.plugin = plugin;
        this.killTracker = killTracker;
    }

    @Override
    public @NotNull String getIdentifier() { return "egosmp"; }

    @Override
    public @NotNull String getAuthor() { return "NirussVn0"; }

    @Override
    public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "0";

        if (params.equalsIgnoreCase("event_kills")) {
            return String.valueOf(killTracker.getKills(player.getUniqueId()));
        }

        return null;
    }
}
