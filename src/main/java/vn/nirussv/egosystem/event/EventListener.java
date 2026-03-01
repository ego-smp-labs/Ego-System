package vn.nirussv.egosystem.event;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import vn.nirussv.egosystem.EgoSystemPlugin;

public class EventListener implements Listener {

    private final EgoSystemPlugin plugin;
    private final EventStateMachine stateMachine;

    private static final NamespacedKey HEART_KEY = new NamespacedKey("sabi", "ego_item_key");

    public EventListener(EgoSystemPlugin plugin, EventStateMachine stateMachine) {
        this.plugin = plugin;
        this.stateMachine = stateMachine;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        EventState state = stateMachine.getCurrentState();
        if (state != EventState.PHASE_2 && state != EventState.PHASE_3) return;

        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        stateMachine.onPlayerDeath(victim, killer);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvP(EntityDamageByEntityEvent event) {
        if (stateMachine.getCurrentState() != EventState.PHASE_1) return;
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (stateMachine.getCurrentState() != EventState.PHASE_3) return;

        Location winZone = stateMachine.getEventConfig().getWinZone();
        if (winZone == null) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        if (!stateMachine.getParticipants().contains(player.getUniqueId())) return;
        if (stateMachine.getEliminated().contains(player.getUniqueId())) return;

        if (player.getLocation().distanceSquared(winZone) > 4.0) return;

        if (isHoldingHeart(player)) {
            stateMachine.onPlayerReachWinZone(player);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        EventState state = stateMachine.getCurrentState();
        if (state != EventState.IDLE) {
            stateMachine.addPlayerToBossBar(event.getPlayer());
        }
    }

    private boolean isHoldingHeart(Player player) {
        return isHeartItem(player.getInventory().getItemInMainHand())
                || isHeartItem(player.getInventory().getItemInOffHand());
    }

    private boolean isHeartItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return "the_betrayer_heart".equals(
                item.getItemMeta().getPersistentDataContainer().get(HEART_KEY, PersistentDataType.STRING));
    }
}
