package vn.meowchan12.mobarenav2.upgrade.handler.ultimate;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.ColorUtils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * [v8.3.0 ULTIMATE UPGRADE]: Golden Necromancer
 * - Fuses: Soul Harvester (Max) + Midas Touch (Max)
 * - Harvested souls grant +2% damage and yield +$500 bonus cash.
 * - 30% chance on kill to summon Golden Skeletons to fight for you for 15 seconds.
 */
public class UltimateGoldenNecromancerUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "ULTIMATE_GOLDEN_NECROMANCER";
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena) {
        if (level <= 0 || player == null || arena == null) return;

        ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());
        if (profile == null) return;

        // Soul Damage scaling (+2% per kill)
        int souls = profile.getKills();
        double bonusMultiplier = 1.0 + (souls * 0.02);
        event.setDamage(event.getDamage() * bonusMultiplier);
    }

    @Override
    public void onKill(Player player, LivingEntity victim, int level, Arena arena) {
        if (level <= 0 || player == null || victim == null || arena == null) return;

        // +$500 bonus cash per kill
        if (MobArenav2.getInstance().getEconomy() != null) {
            MobArenav2.getInstance().getEconomy().depositPlayer(player, 500.0);
        }

        // 30% Chance to summon a Golden Skeleton Minion for 15s
        if (ThreadLocalRandom.current().nextDouble() <= 0.30) {
            Location spawnLoc = victim.getLocation();
            if (spawnLoc.getWorld() == null) return;

            Skeleton minion = (Skeleton) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.SKELETON);
            minion.setCustomName(ColorUtils.colorize("&#FFD700&l[Golden Guard] &f" + player.getName()));
            minion.setCustomNameVisible(true);
            minion.setGlowing(true);

            // Give Golden Gear
            if (minion.getEquipment() != null) {
                minion.getEquipment().setHelmet(new ItemStack(Material.GOLDEN_HELMET));
                minion.getEquipment().setChestplate(new ItemStack(Material.GOLDEN_CHESTPLATE));
                minion.getEquipment().setItemInMainHand(new ItemStack(Material.GOLDEN_SWORD));
            }

            minion.setMetadata("MA_SUMMONED_MINION", new FixedMetadataValue(MobArenav2.getInstance(), player.getUniqueId().toString()));

            // Despawn after 15s
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (minion.isValid()) {
                        minion.getWorld().spawnParticle(Particle.POOF, minion.getLocation().add(0, 1.0, 0), 10);
                        minion.remove();
                    }
                }
            }.runTaskLater(MobArenav2.getInstance(), 300L);

            player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1.0f, 1.5f);
        }
    }
}
