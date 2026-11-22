package vn.meowchan12.mobarenav2.upgrade.handler.ultimate;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.service.CombatDamageService;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.ColorUtils;

/**
 * [v8.3.0 ULTIMATE UPGRADE]: Citadel of Retribution
 * - Fuses: Super Aegis (Max) + Thorns Aura (Max)
 * - Absorbs 100% of fatal lethal damage, triggering 2s complete invulnerability (Aegis Shield).
 * - Reflects 150% of damage taken as AoE True Damage to all mobs in 8 blocks, stunning attackers for 1.5s.
 */
public class UltimateCitadelRetributionUpgrade implements UpgradeHandler {

    private static final String META_AEGIS_COOLDOWN = "MA_ULTIMATE_AEGIS_CD";

    @Override
    public String getType() {
        return "ULTIMATE_CITADEL_RETRIBUTION";
    }

    @Override
    public void onDefend(org.bukkit.event.entity.EntityDamageEvent event, Player player, LivingEntity attacker, int level, Arena arena) {
        if (level <= 0 || player == null || arena == null) return;

        double incomingDamage = event.getFinalDamage();
        double currentHealth = player.getHealth();

        // 1. Super Aegis Fatal Damage Block
        if (incomingDamage >= currentHealth) {
            long lastTrigger = player.hasMetadata(META_AEGIS_COOLDOWN) ?
                    player.getMetadata(META_AEGIS_COOLDOWN).get(0).asLong() : 0L;

            if (System.currentTimeMillis() - lastTrigger >= 60000L) { // 60s cooldown
                event.setCancelled(true);
                player.setMetadata(META_AEGIS_COOLDOWN, new FixedMetadataValue(MobArenav2.getInstance(), System.currentTimeMillis()));

                // 2s Complete Invulnerability
                player.setNoDamageTicks(40);
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 255, false, false));

                Location loc = player.getLocation();
                loc.getWorld().playSound(loc, Sound.ITEM_TOTEM_USE, 1.2f, 1.0f);
                loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.add(0, 1.0, 0), 40, 0.8, 1.0, 0.8, 0.2);
                player.sendMessage(ColorUtils.colorize("&6&l✦ [CITADEL AEGIS] &fAbsorbed lethal blow! 2s Divine Barrier active!"));
                return;
            }
        }

        // 2. 150% AoE True Damage Retribution Reflect (8m radius)
        if (incomingDamage > 1.0) {
            double reflectAmount = incomingDamage * 1.50;
            Location pLoc = player.getLocation();

            pLoc.getWorld().spawnParticle(Particle.SONIC_BOOM, pLoc.add(0, 1.0, 0), 1);
            pLoc.getWorld().playSound(pLoc, Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.2f);

            for (LivingEntity nearby : pLoc.getWorld().getNearbyLivingEntities(pLoc, 8.0)) {
                if (nearby instanceof Player) continue;
                if (arena.getActiveMobs().contains(nearby) || nearby.hasMetadata("MA_MOB")) {
                    nearby.damage(reflectAmount, player);
                    nearby.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 255, false, false)); // 1.5s Stun
                    nearby.getWorld().spawnParticle(Particle.CRIT, nearby.getLocation().add(0, 1.0, 0), 5);
                }
            }
        }
    }
}
