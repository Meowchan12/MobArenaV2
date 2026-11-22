package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * [v7.5.0 REWORK]: Soul Magnetism (Soul Orb)
 * Hạt linh hồn tự động bay lượn và bị hút về phía người chơi trong bán kính 12 block.
 * Tự động hấp thụ: Hồi 4.0 HP + 2.0 Khiên Vàng (Absorption) và nhận Speed I.
 * Milestone Lv.5: Cứ mỗi 10 hạt linh hồn thu thập được, giáng luồng sét Thánh Quang trừng phạt quái vật yếu máu nhất!
 */
public class SoulOrbUpgrade implements UpgradeHandler {

    private final Set<Item> activeOrbs = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final String KEY_SOULS_COUNT = "ma_souls_absorbed_count";

    @Override
    public String getType() {
        return "SOUL_ORB";
    }

    @Override
    public void onKill(Player player, LivingEntity victim, int level, Arena arena) {
        if (level <= 0 || victim == null || player == null) return;

        double chance = 20.0 + (level - 1) * 12.0; // 20% -> 68%
        if (ThreadLocalRandom.current().nextDouble(100.0) > chance) return;

        ItemStack orbStack = new ItemStack(Material.HEART_OF_THE_SEA);
        Item dropped = victim.getWorld().dropItem(victim.getLocation().add(0, 0.5, 0), orbStack);
        dropped.setPickupDelay(Integer.MAX_VALUE);
        dropped.setCustomName("§b§l✦ SOUL ORB ✦");
        dropped.setCustomNameVisible(true);
        dropped.setGlowing(true);
        dropped.setGravity(false);

        activeOrbs.add(dropped);

        MobArenav2 plugin = (arena != null && arena.getPlugin() != null) ? arena.getPlugin() : MobArenav2.getInstance();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!dropped.isValid() || dropped.isDead() || ticks >= 160 || !player.isOnline()) {
                    dropped.remove();
                    activeOrbs.remove(dropped);
                    cancel();
                    return;
                }

                Location orbLoc = dropped.getLocation();
                Location pLoc = player.getLocation().add(0, 1.0, 0);

                if (orbLoc.getWorld().equals(pLoc.getWorld())) {
                    double dist = orbLoc.distance(pLoc);

                    // Hiệu ứng hạt linh hồn xoay quanh
                    orbLoc.getWorld().spawnParticle(Particle.SCULK_SOUL, orbLoc, 2, 0.1, 0.1, 0.1, 0.02);

                    // Cơ chế hút từ tính tự động (Soul Magnet) trong 12 block
                    if (dist <= 12.0) {
                        Vector pullDir = pLoc.toVector().subtract(orbLoc.toVector()).normalize().multiply(0.75);
                        dropped.setVelocity(pullDir);
                    }

                    // Khi chạm vào người chơi (hoặc khoảng cách <= 1.5 block)
                    if (dist <= 1.5) {
                        dropped.remove();
                        activeOrbs.remove(dropped);

                        AttributeInstance maxHpAttr = player.getAttribute(AttributeUtils.MAX_HEALTH);
                        double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 20.0;
                        player.setHealth(Math.min(maxHp, player.getHealth() + 4.0));

                        // Cấp khiên vàng 2.0 (tối đa 10 HP)
                        double curAbs = player.getAbsorptionAmount();
                        player.setAbsorptionAmount(Math.min(10.0, curAbs + 2.0));

                        // [v8.1.0 SATURATION BUFF]: Bão hòa thức ăn tức thì khi hấp thụ linh hồn
                        player.setFoodLevel(Math.min(20, player.getFoodLevel() + 3));
                        player.setSaturation(Math.min(20.0f, player.getSaturation() + 5.0f));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 40, 0, false, false));

                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, false, false));
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.6f);
                        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1.8, 0), 2, 0.2, 0.2, 0.2, 0);

                        // Milestone Lv.5: Thánh Quang Sét Đánh mỗi 10 hạt
                        if (level >= 5) {
                            NamespacedKey sKey = new NamespacedKey(plugin, KEY_SOULS_COUNT);
                            int count = player.getPersistentDataContainer().getOrDefault(sKey, PersistentDataType.INTEGER, 0) + 1;
                            if (count >= 10) {
                                player.getPersistentDataContainer().set(sKey, PersistentDataType.INTEGER, 0);
                                triggerDivineSmite(player, arena);
                            } else {
                                player.getPersistentDataContainer().set(sKey, PersistentDataType.INTEGER, count);
                            }
                        }

                        cancel();
                        return;
                    }
                }
                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void triggerDivineSmite(Player player, Arena arena) {
        LivingEntity lowestMob = null;
        double lowestHp = Double.MAX_VALUE;

        for (Entity e : player.getNearbyEntities(15.0, 10.0, 15.0)) {
            if (e instanceof LivingEntity mob && mob.hasMetadata("MA_MOB") && !mob.isDead()) {
                if (mob.getHealth() < lowestHp) {
                    lowestHp = mob.getHealth();
                    lowestMob = mob;
                }
            }
        }

        if (lowestMob != null) {
            lowestMob.getWorld().strikeLightningEffect(lowestMob.getLocation());
            lowestMob.damage(25.0, player);
            player.sendMessage("§b§l[SOUL MAGNETISM] §eDivine Smite triggered! 25.0 True DMG strike on " + lowestMob.getName() + "!");
            player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.5f);
        }
    }

    @Override
    public void onReset(Player player, Arena arena) {
        for (Item item : activeOrbs) {
            if (item.isValid()) item.remove();
        }
        activeOrbs.clear();
        if (player != null) {
            MobArenav2 plugin = (arena != null && arena.getPlugin() != null) ? arena.getPlugin() : MobArenav2.getInstance();
            player.getPersistentDataContainer().remove(new NamespacedKey(plugin, KEY_SOULS_COUNT));
        }
    }
}
