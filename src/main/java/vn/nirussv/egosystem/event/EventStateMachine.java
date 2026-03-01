package vn.nirussv.egosystem.event;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import vn.nirussv.egosystem.EgoSystemPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class EventStateMachine {

    private final EgoSystemPlugin plugin;
    private final EventConfig eventConfig;
    private final EventKillTracker killTracker;
    private final BossZombieManager bossManager;

    private EventState currentState = EventState.IDLE;
    private BukkitTask activeTask;
    private BossBar bossBar;
    private BukkitTask trackingTask;

    private UUID currentCarrier;
    private Location lastDroppedLocation;
    private Location lastBroadcastLocation;
    private int tickCounter = 0;

    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> eliminated = new HashSet<>();

    public EventStateMachine(EgoSystemPlugin plugin, EventConfig eventConfig,
                             EventKillTracker killTracker, BossZombieManager bossManager) {
        this.plugin = plugin;
        this.eventConfig = eventConfig;
        this.killTracker = killTracker;
        this.bossManager = bossManager;
    }

    public EventState getCurrentState() { return currentState; }
    public Set<UUID> getParticipants() { return participants; }
    public Set<UUID> getEliminated() { return eliminated; }
    public EventKillTracker getKillTracker() { return killTracker; }
    public EventConfig getEventConfig() { return eventConfig; }

    public void startEvent() {
        if (currentState != EventState.IDLE) return;
        cancelActiveTask();
        participants.clear();
        eliminated.clear();
        killTracker.clearKills();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != GameMode.SPECTATOR) {
                participants.add(p.getUniqueId());
            }
        }
        
        currentCarrier = null;
        lastDroppedLocation = null;
        lastBroadcastLocation = null;
        tickCounter = 0;

        transitionTo(EventState.COUNTDOWN);
        int totalSeconds = eventConfig.getCountdownSeconds();
        Bukkit.broadcast(Component.text("§4§l[!] §cSự kiện Cuộc Săn Kẻ Phản Bội sẽ bắt đầu trong " + formatTime(totalSeconds) + "!"));
        startPhaseTimer(totalSeconds, "§e§lĐếm Ngược", BarColor.YELLOW, () -> transitionTo(EventState.PHASE_1));
    }

    public void forcePhase(int phase) {
        cancelActiveTask();
        switch (phase) {
            case 1 -> enterPhase1();
            case 2 -> enterPhase2();
            case 3 -> enterPhase3();
        }
    }

    public void pauseEvent() {
        cancelActiveTask();
        Bukkit.broadcast(Component.text("§e§l[!] §7Sự kiện đã tạm dừng."));
    }

    public void endEvent() {
        cancelActiveTask();
        removeBossBar();
        if (trackingTask != null) {
            trackingTask.cancel();
            trackingTask = null;
        }

        for (UUID uuid : participants) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && !eliminated.contains(uuid)) {
                p.setGameMode(GameMode.SPECTATOR);
                p.getWorld().strikeLightningEffect(p.getLocation());
            }
        }

        killTracker.clearKills();
        participants.clear();
        eliminated.clear();
        currentState = EventState.IDLE;

        Bukkit.broadcast(Component.text("§4§l[!] §cSự kiện Cuộc Săn Kẻ Phản Bội đã kết thúc!"));
    }

    public void onPlayerDeath(Player victim, Player killer) {
        if (currentState != EventState.PHASE_2 && currentState != EventState.PHASE_3) return;
        if (!participants.contains(victim.getUniqueId())) return;

        eliminated.add(victim.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> victim.setGameMode(GameMode.SPECTATOR));

        Bukkit.broadcast(Component.text("§c☠ §e" + victim.getName() + " §7đã bị loại khỏi sự kiện!"));

        if (killer != null && participants.contains(killer.getUniqueId())
                && !eliminated.contains(killer.getUniqueId())) {
            killTracker.recordKill(killer.getUniqueId());
        }
    }

    public void onPlayerReachWinZone(Player player) {
        if (currentState != EventState.PHASE_3) return;

        cancelActiveTask();
        removeBossBar();

        Bukkit.broadcast(Component.text("§a§l✦ " + player.getName() + " §a§lđã giao Trái Tim và chiến thắng sự kiện!"));
        player.getWorld().strikeLightningEffect(player.getLocation());

        killTracker.clearKills();
        participants.clear();
        eliminated.clear();
        currentState = EventState.IDLE;
    }

    // ---------- Phase Logic ----------

    private void enterPhase1() {
        transitionTo(EventState.PHASE_1);
        int seconds = eventConfig.getPhase1Seconds();
        Bukkit.broadcast(Component.text("§b§l[Phase 1] §7Giai đoạn Chuẩn bị! PvP §c§lTẮT §7— " + formatTime(seconds)));
        startPhaseTimer(seconds, "§b§lPhase 1: Chuẩn Bị (PvP TẮT)", BarColor.BLUE, this::enterPhase2);
    }

    private void enterPhase2() {
        transitionTo(EventState.PHASE_2);
        int seconds = eventConfig.getPhase2Seconds();
        Bukkit.broadcast(Component.text("§c§l[Phase 2] §7Cuộc Săn bắt đầu! PvP §a§lBẬT §7— " + formatTime(seconds)));

        int radius = eventConfig.getBossSpawnRadius();
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        int x = rng.nextInt(-radius, radius + 1);
        int z = rng.nextInt(-radius, radius + 1);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sabi event spawn-zombie-at " + x + " " + z);
        Bukkit.broadcast(Component.text("§4§l[!] §cThe Betrayer's Guardian đã xuất hiện! Hãy tìm và hạ gục nó!"));

        startPhaseTimer(seconds, "§c§lPhase 2: Cuộc Săn (PvP BẬT)", BarColor.RED, this::enterPhase3);
        startTrackingLoop();
    }

    private void enterPhase3() {
        transitionTo(EventState.PHASE_3);
        Location winZone = eventConfig.getWinZone();
        if (winZone != null) {
            String coords = String.format("§e%.0f, %.0f, %.0f", winZone.getX(), winZone.getY(), winZone.getZ());
            Bukkit.broadcast(Component.text("§d§l[Phase 3] §7Giao Trái Tim tại: " + coords + " §7để chiến thắng!"));
        } else {
            Bukkit.broadcast(Component.text("§d§l[Phase 3] §7Phán Xét Cuối Cùng! §c(Chưa cấu hình win zone!)"));
        }

        createBossBar("§d§lPhase 3: Phán Xét Cuối Cùng", BarColor.PURPLE);
    }

    // ---------- Timer Helpers ----------

    private void startPhaseTimer(int totalSeconds, String barTitle, BarColor color, Runnable onComplete) {
        createBossBar(barTitle, color);
        final int[] remaining = {totalSeconds};

        activeTask = new BukkitRunnable() {
            @Override
            public void run() {
                remaining[0]--;
                if (remaining[0] <= 0) {
                    this.cancel();
                    onComplete.run();
                    return;
                }
                
                // Track BossBar title changes (if not handled by tracking loop)
                if (bossBar != null) {
                    double progress = (double) remaining[0] / totalSeconds;
                    bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
                    if (currentState == EventState.IDLE || currentState == EventState.COUNTDOWN || currentState == EventState.PHASE_1) {
                        bossBar.setTitle(barTitle + " §7— " + formatTime(remaining[0]));
                    } else if (trackingTask == null) {
                        // Fallback title just in case tracking fails
                         bossBar.setTitle(barTitle + " §7— " + formatTime(remaining[0]));
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void createBossBar(String title, BarColor color) {
        removeBossBar();
        bossBar = Bukkit.createBossBar(title, color, BarStyle.SOLID);
        for (Player p : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(p);
        }
    }

    private void removeBossBar() {
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
    }

    public void addPlayerToBossBar(Player player) {
        if (bossBar != null) {
            bossBar.addPlayer(player);
        }
    }
    
    // ---------- Tracking Loop ----------
    private void startTrackingLoop() {
        if (trackingTask != null) trackingTask.cancel();
        trackingTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (currentState != EventState.PHASE_2 && currentState != EventState.PHASE_3) return;

            tickCounter++;
            org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey("sabi", "ego_item_key");
            
            Player foundCarrier = null;
            for (Player p : Bukkit.getOnlinePlayers()) {
                boolean hasHeart = false;
                ItemStack offhand = p.getInventory().getItemInOffHand();
                ItemStack mainhand = p.getInventory().getItemInMainHand();

                if (offhand != null && offhand.hasItemMeta() && "the_betrayer_heart".equals(offhand.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING))) {
                    hasHeart = true;
                } else if (mainhand != null && mainhand.hasItemMeta() && "the_betrayer_heart".equals(mainhand.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING))) {
                    hasHeart = true;
                } else {
                    for (ItemStack item : p.getInventory().getContents()) {
                        if (item != null && item.hasItemMeta() && "the_betrayer_heart".equals(item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING))) {
                            hasHeart = true;
                            break;
                        }
                    }
                }

                if (hasHeart) {
                    foundCarrier = p;
                    // Apply glowing to real carrier
                    p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false, true));
                    break;
                }
            }

            if (foundCarrier != null) {
                if (currentCarrier == null || !currentCarrier.equals(foundCarrier.getUniqueId())) {
                    currentCarrier = foundCarrier.getUniqueId();
                    lastDroppedLocation = null;
                    tickCounter = 15; // Force instant broadcast
                }
                if (tickCounter % 15 == 0) {
                    lastBroadcastLocation = foundCarrier.getLocation();
                    // Heartbeat sound
                    foundCarrier.getWorld().playSound(foundCarrier.getLocation(), org.bukkit.Sound.ENTITY_WARDEN_HEARTBEAT, 2.0f, 1.0f);
                }
            } else {
                // Not found on any player, it might be dropped. Let's find dropped items.
                if (currentCarrier != null) {
                    currentCarrier = null;
                    lastBroadcastLocation = null;
                    // Find it on the ground
                    for (org.bukkit.World world : Bukkit.getWorlds()) {
                        for (org.bukkit.entity.Entity entity : world.getEntitiesByClass(org.bukkit.entity.Item.class)) {
                            ItemStack item = ((org.bukkit.entity.Item) entity).getItemStack();
                            if (item != null && item.hasItemMeta() && "the_betrayer_heart".equals(item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING))) {
                                lastDroppedLocation = entity.getLocation();
                                break;
                            }
                        }
                    }
                }
            }

            // Update BossBar Titles
            if (bossBar != null) {
                if (currentCarrier != null) {
                    Player c = Bukkit.getPlayer(currentCarrier);
                    if (c != null && lastBroadcastLocation != null) {
                        bossBar.setTitle("§4§lKẻ Phản Bội: §c" + c.getName() + " §8| §fTọa độ: §c" + lastBroadcastLocation.getBlockX() + ", " + lastBroadcastLocation.getBlockY() + ", " + lastBroadcastLocation.getBlockZ());
                    }
                } else if (lastDroppedLocation != null) {
                    bossBar.setTitle("§eTim đang rơi tại: §f" + lastDroppedLocation.getBlockX() + ", " + lastDroppedLocation.getBlockY() + ", " + lastDroppedLocation.getBlockZ() + " §e(" + lastDroppedLocation.getWorld().getName() + ")");
                } else {
                    bossBar.setTitle("§8Đang dò tìm tọa độ Quả Tim...");
                }
            }

        }, 20L, 20L);
    }

    private void transitionTo(EventState newState) {
        this.currentState = newState;
    }

    private void cancelActiveTask() {
        if (activeTask != null && !activeTask.isCancelled()) {
            activeTask.cancel();
            activeTask = null;
        }
    }

    private String formatTime(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }
}
