package vn.nirussv.egosystem.event;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.nirussv.egosystem.EgoSystemPlugin;

import java.util.concurrent.ThreadLocalRandom;

public class BossZombieManager {

    private final EgoSystemPlugin plugin;
    private final EventConfig eventConfig;

    public BossZombieManager(EgoSystemPlugin plugin, EventConfig eventConfig) {
        this.plugin = plugin;
        this.eventConfig = eventConfig;
    }

    public Zombie spawnBossZombie(Location loc) {
        Zombie boss = (Zombie) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);

        boss.setAdult();
        boss.setRemoveWhenFarAway(false);
        boss.customName(net.kyori.adventure.text.Component.text("§4§lThe Betrayer's Guardian"));
        boss.setCustomNameVisible(true);

        boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(80.0);
        boss.setHealth(80.0);

        boss.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));

        EntityEquipment eq = boss.getEquipment();
        eq.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
        eq.setItemInOffHand(createHeartItem());
        eq.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        eq.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        eq.setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
        eq.setBoots(new ItemStack(Material.DIAMOND_BOOTS));

        eq.setItemInMainHandDropChance(0f);
        eq.setItemInOffHandDropChance(1f);
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);

        return boss;
    }

    public Location getRandomSpawnLocation(org.bukkit.World world) {
        int radius = eventConfig.getBossSpawnRadius();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int x = rng.nextInt(-radius, radius + 1);
        int z = rng.nextInt(-radius, radius + 1);
        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private ItemStack createHeartItem() {
        ItemStack heart = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = heart.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("§c§lThe Betrayer's Heart"));
        NamespacedKey key = new NamespacedKey("sabi", "ego_item_key");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "the_betrayer_heart");
        NamespacedKey pdcKey = new NamespacedKey("sabi", "the_betrayer_heart");
        meta.getPersistentDataContainer().set(pdcKey, PersistentDataType.BYTE, (byte) 1);
        heart.setItemMeta(meta);
        return heart;
    }
}
