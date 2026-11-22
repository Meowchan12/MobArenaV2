package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;

import java.util.concurrent.ThreadLocalRandom;

/**
 * [v8.4.3 DYNAMIC ENGINE]: Quill Burst (Thorns Aura)
 * Reads damage threshold, burst damage, radius, and stun chance from UpgradeData/upgrades.yml.
 */
public class ThornsAuraUpgrade implements UpgradeHandler {

    private static final String KEY_ACCUMULATED_DMG = "ma_quill_acc_dmg";

    @Override
    public String getType() {
        return "THORNS_AURA";
    }

    @Override
    public void onDefend(EntityDamageEvent event, Player player, LivingEntity damager, int level, Arena arena, UpgradeData data) {
        if (level <= 0 || player == null || arena == null) return;

        MobArenav2 plugin = (arena.getPlugin() != null) ? arena.getPlugin() : MobArenav2.getInstance();
        NamespacedKey accKey = new NamespacedKey(plugin, KEY_ACCUMULATED_DMG);

        double accDmg = player.getPersistentDataContainer().getOrDefault(accKey, PersistentDataType.DOUBLE, 0.0);
        accDmg += event.getFinalDamage();

        double triggerThreshold = (data != null) ? data.getDouble("trigger-threshold", 20.0) : 20.0;
        if (accDmg >= triggerThreshold) {
            // Đạt mốc kích hoạt Quill Burst!
            player.getPersistentDataContainer().set(accKey, PersistentDataType.DOUBLE, 0.0);

            double burstDamage = (data != null) ? data.getValueAtLevel(level) : (15.0 + (level * 5.0));
            double radius = (data != null) ? data.getDouble("radius", 4.5) : 4.5;

            player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation().add(0, 0.5, 0), 2);
            player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(0, 0.8, 0), 8, 1.2, 0.2, 1.2, 0.1);
            player.playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.6f);
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.5f);

            for (Entity near : player.getNearbyEntities(radius, radius, radius)) {
                if (near instanceof LivingEntity mob && mob.hasMetadata("MA_MOB") && !mob.isDead()) {
                    mob.damage(burstDamage, player);

                    // Đẩy lùi kẻ địch
                    Vector push = mob.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.4).setY(0.4);
                    mob.setVelocity(push);

                    // Làm chậm
                    mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false));

                    // Milestone: Stun
                    int stunMilestone = (data != null) ? data.getInt("stun-milestone-level", 5) : 5;
                    double stunChance = (data != null) ? data.getDouble("stun-chance", 30.0) : 30.0;
                    if (level >= stunMilestone && ThreadLocalRandom.current().nextDouble(100.0) <= stunChance) {
                        mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 9, false, false));
                        mob.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 4, false, false));
                        spawnSafeFlash(mob.getWorld(), mob.getLocation().add(0, 1.5, 0));
                    }
                }
            }

            player.sendMessage("§6§l[QUILL BURST] §eRetaliatory shockwave unleashed! (§c" + String.format("%.0f", burstDamage) + " AoE Damage§e)");
        } else {
            player.getPersistentDataContainer().set(accKey, PersistentDataType.DOUBLE, accDmg);
        }
    }

    @Override
    public void onDefend(EntityDamageEvent event, Player player, LivingEntity damager, int level, Arena arena) {
        onDefend(event, player, damager, level, arena, null);
    }

    private void spawnSafeFlash(org.bukkit.World world, org.bukkit.Location loc) {
        if (world == null || loc == null) return;
        try {
            world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 15, 0.4, 0.4, 0.4, 0.05);
            world.spawnParticle(Particle.CRIT, loc, 10, 0.3, 0.3, 0.3, 0.1);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onReset(Player player, Arena arena) {
        if (player == null) return;
        MobArenav2 plugin = (arena != null && arena.getPlugin() != null) ? arena.getPlugin() : MobArenav2.getInstance();
        player.getPersistentDataContainer().remove(new NamespacedKey(plugin, KEY_ACCUMULATED_DMG));
    }
}
