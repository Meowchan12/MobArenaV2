package vn.meowchan12.mobarenav2.upgrade.handler.ultimate;

import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

/**
 * [v8.3.0 ULTIMATE UPGRADE]: Blood God's Feast
 * - Fuses: Vampiric Strike (Max) + Slaughterhouse Engine (Max)
 * - Retains 100% of accumulated Slaughterhouse kills and Boss hits bonus.
 * - 25% True Lifesteal on all strikes (no upper cap).
 * - Slaying enemies summons a Blood Vortex that slows and damages nearby mobs.
 */
public class UltimateBloodGodFeastUpgrade implements UpgradeHandler {

    private final NamespacedKey bossBonusKey;
    private final NamespacedKey bossHitKey;

    public UltimateBloodGodFeastUpgrade() {
        MobArenav2 plugin = MobArenav2.getInstance();
        this.bossBonusKey = new NamespacedKey(plugin, "ma_slaughter_boss_bonus");
        this.bossHitKey = new NamespacedKey(plugin, "ma_slaughter_boss_hits");
    }

    @Override
    public String getType() {
        return "ULTIMATE_BLOOD_GOD_FEAST";
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena) {
        if (level <= 0 || player == null || arena == null) return;

        ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());
        if (profile == null) return;

        // 1. Inherited Slaughterhouse Scaling (Kills + Boss hits)
        int bossBonus;
        if (victim != null && (victim.hasMetadata("MA_BOSS") || victim.hasMetadata("BOSS"))) {
            int hits = player.getPersistentDataContainer().getOrDefault(bossHitKey, PersistentDataType.INTEGER, 0) + 1;
            int currentBonus = player.getPersistentDataContainer().getOrDefault(bossBonusKey, PersistentDataType.INTEGER, 0);

            if (hits >= 20) {
                player.getPersistentDataContainer().set(bossHitKey, PersistentDataType.INTEGER, 0);
                currentBonus++;
                player.getPersistentDataContainer().set(bossBonusKey, PersistentDataType.INTEGER, currentBonus);
            } else {
                player.getPersistentDataContainer().set(bossHitKey, PersistentDataType.INTEGER, hits);
            }
            bossBonus = currentBonus;
        } else {
            bossBonus = player.getPersistentDataContainer().getOrDefault(bossBonusKey, PersistentDataType.INTEGER, 0);
        }

        int totalPercent = profile.getKills() + bossBonus;
        if (totalPercent > 0) {
            double multiplier = 1.0 + (totalPercent / 100.0);
            event.setDamage(event.getDamage() * multiplier);
        }

        // 2. 25% True Lifesteal (No Upper Cap)
        double finalDamage = event.getDamage();
        double healAmount = finalDamage * 0.25;
        if (player.hasMetadata("MA_MORTAL_WOUND")) {
            healAmount *= 0.20; // Mortal Wound -80% healing received
        }

        AttributeInstance maxHpAttr = player.getAttribute(AttributeUtils.MAX_HEALTH);
        double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 20.0;
        player.setHealth(Math.min(maxHp, player.getHealth() + healAmount));

        // Crimson particle sweep
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1.8, 0), 2, 0.3, 0.3, 0.3, 0);
    }

    @Override
    public void onKill(Player player, LivingEntity victim, int level, Arena arena) {
        if (level <= 0 || player == null || victim == null) return;

        // Summon Blood Vortex at victim location
        org.bukkit.Location deathLoc = victim.getLocation();
        deathLoc.getWorld().playSound(deathLoc, Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 1.5f);

        Particle.DustOptions bloodDust = new Particle.DustOptions(Color.fromRGB(180, 0, 0), 1.5f);
        deathLoc.getWorld().spawnParticle(Particle.DUST, deathLoc.add(0, 0.5, 0), 30, 1.5, 0.3, 1.5, 0.1, bloodDust);

        // Slow and damage mobs in 5 blocks
        for (org.bukkit.entity.LivingEntity nearby : deathLoc.getWorld().getNearbyLivingEntities(deathLoc, 5.0)) {
            if (nearby instanceof Player) continue;
            if (arena.getActiveMobs().contains(nearby) || nearby.hasMetadata("MA_MOB")) {
                nearby.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, false, false));
                nearby.damage(10.0, player);
            }
        }
    }
}
