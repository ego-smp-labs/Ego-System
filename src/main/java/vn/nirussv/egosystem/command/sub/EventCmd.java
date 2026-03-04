package vn.nirussv.egosystem.command.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import vn.nirussv.egosystem.command.SubCommand;
import vn.nirussv.egosystem.event.EventStateMachine;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class EventCmd implements SubCommand {

    private final EventStateMachine eventStateMachine;

    public EventCmd(EventStateMachine eventStateMachine) {
        this.eventStateMachine = eventStateMachine;
    }

    @Override
    public String getName() {
        return "event";
    }

    @Override
    public String getPermission() {
        return "serverauto.admin";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /ssm event <start|stop|end|phase|spawn-zombie|setting>");
            return;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "start" -> {
                eventStateMachine.startEvent();
                sender.sendMessage("§a[Event] Sự kiện đã bắt đầu đếm ngược!");
            }
            case "stop", "pause" -> {
                eventStateMachine.pauseEvent();
                sender.sendMessage("§e[Event] Sự kiện đã tạm dừng.");
            }
            case "end" -> {
                eventStateMachine.endEvent();
                cleanupHeartItems();
                sender.sendMessage("§c[Event] Sự kiện đã kết thúc. Đã dọn dẹp Heart items.");
            }
            case "phase" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /ssm event phase <1|2|3>");
                    return;
                }
                try {
                    int phase = Integer.parseInt(args[2]);
                    if (phase < 1 || phase > 3) throw new NumberFormatException();
                    eventStateMachine.forcePhase(phase);
                    sender.sendMessage("§a[Event] Đã chuyển sang Phase " + phase + "!");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cPhase phải là 1, 2, hoặc 3.");
                }
            }
            case "spawn-zombie" -> {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cLệnh này chỉ dành cho người chơi.");
                    return;
                }
                org.bukkit.Bukkit.dispatchCommand(sender, "sabi event spawn-zombie");
            }
            case "setting" -> handleEventSetting(sender, args);
            default -> sender.sendMessage("§cUsage: /ssm event <start|stop|end|phase|spawn-zombie|setting>");
        }
    }

    private void handleEventSetting(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /ssm event setting <set-win|radius>");
            return;
        }
        String setting = args[2].toLowerCase();
        switch (setting) {
            case "set-win" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cLệnh này chỉ dành cho người chơi.");
                    return;
                }
                eventStateMachine.getEventConfig().setWinZone(player.getLocation());
                sender.sendMessage("§a[Event] Win zone đã được lưu tại vị trí của bạn!");
            }
            case "radius" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /ssm event setting radius <number>");
                    return;
                }
                try {
                    int radius = Integer.parseInt(args[3]);
                    eventStateMachine.getEventConfig().setBossSpawnRadius(radius);
                    sender.sendMessage("§a[Event] Boss spawn radius đã được đặt thành " + radius + "!");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cGiá trị radius không hợp lệ.");
                }
            }
            default -> sender.sendMessage("§cUsage: /ssm event setting <set-win|radius>");
        }
    }

    private void cleanupHeartItems() {
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey("sabi", "ego_item_key");
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            org.bukkit.inventory.PlayerInventory inv = p.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                org.bukkit.inventory.ItemStack item = inv.getItem(i);
                if (item != null && item.hasItemMeta()
                        && "the_betrayer_heart".equals(item.getItemMeta().getPersistentDataContainer()
                                .get(key, org.bukkit.persistence.PersistentDataType.STRING))) {
                    inv.setItem(i, null);
                }
            }
        }
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntitiesByClass(org.bukkit.entity.Item.class)) {
                org.bukkit.inventory.ItemStack item = ((org.bukkit.entity.Item) entity).getItemStack();
                if (item != null && item.hasItemMeta()
                        && "the_betrayer_heart".equals(item.getItemMeta().getPersistentDataContainer()
                                .get(key, org.bukkit.persistence.PersistentDataType.STRING))) {
                    entity.remove();
                }
            }
        }
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return Arrays.asList("start", "stop", "end", "phase", "spawn-zombie", "setting").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3) {
            if (args[1].equalsIgnoreCase("phase")) {
                return Arrays.asList("1", "2", "3").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args[1].equalsIgnoreCase("setting")) {
                return Arrays.asList("set-win", "radius").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }
}
