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

public class EventStateMachine {

    private final EgoSystemPlugin plugin;
    private final EventConfig eventConfig;
    private final EventKillTracker killTracker;
    private final BossZombieManager bossManager;

    private EventState currentState = EventState.IDLE;
    private BukkitTask activeTask;
    private BossBar bossBar;

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

        World world = Bukkit.getWorlds().get(0);
        Location spawnLoc = bossManager.getRandomSpawnLocation(world);
        bossManager.spawnBossZombie(spawnLoc);
        Bukkit.broadcast(Component.text("§4§l[!] §cThe Betrayer's Guardian đã xuất hiện! Hãy tìm và hạ gục nó!"));

        startPhaseTimer(seconds, "§c§lPhase 2: Cuộc Săn (PvP BẬT)", BarColor.RED, this::enterPhase3);
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
                if (bossBar != null) {
                    double progress = (double) remaining[0] / totalSeconds;
                    bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
                    bossBar.setTitle(barTitle + " §7— " + formatTime(remaining[0]));
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
