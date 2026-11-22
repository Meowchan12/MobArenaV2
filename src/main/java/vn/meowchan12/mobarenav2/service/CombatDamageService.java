package vn.meowchan12.mobarenav2.service;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;

/**
 * [v7.4.0 COMBAT REWORK]: Centralized Layered Damage Service
 * Ensures Absorption hearts (Golden Apples, Phantom Barrier) absorb damage first.
 * Ensures Resistance effect & Shop Damage Reduction mitigate armor-piercing damage.
 * Mitigates True Damage for high-tier defensive builds.
 */
public class CombatDamageService {

    /**
     * Applies layered true damage or armor-piercing damage to a player.
     *
     * @param player Target player
     * @param damage Incoming raw damage amount
     * @param isPureTrueDamage true if pure true damage, false if armor-piercing
     * @param damager Damager entity (can be null)
     * @param arena Current arena instance
     * @param plugin MobArenav2 plugin instance
     * @return remaining damage actually deducted from red health
     */
    @SuppressWarnings({"deprecation", "removal"})
    public static double applyLayeredDamage(Player player, double damage, boolean isPureTrueDamage, LivingEntity damager, Arena arena, MobArenav2 plugin) {
        if (player == null || !player.isOnline() || damage <= 0) return 0.0;
        if (arena != null && arena.getSpectators().contains(player.getUniqueId())) return 0.0;

        // ---------------------------------------------------------------------
        // 1. MITIGATION PHASE
        // ---------------------------------------------------------------------
        if (!isPureTrueDamage) {
            // Armor-Piercing damage: Bypasses Armor/Toughness BUT STILL MITIGATED by Resistance & DR%
            if (player.hasPotionEffect(PotionEffectType.RESISTANCE)) {
                PotionEffect eff = player.getPotionEffect(PotionEffectType.RESISTANCE);
                int amp = (eff != null) ? eff.getAmplifier() + 1 : 1;
                double resistMult = Math.max(0.0, 1.0 - (amp * 0.20));
                damage *= resistMult;
            }

            if (arena != null) {
                ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());
                if (profile != null) {
                    double dmgRed = profile.getUpgradeValue("DAMAGE_REDUCTION", plugin);
                    if (dmgRed > 0) {
                        double redFactor = Math.max(0.20, 1.0 - (dmgRed / 100.0));
                        damage *= redFactor;
                    }
                }
            }
        } else {
            // Pure True Damage: High-tier Iron Skin (Lv.4+) grants 25% True Damage Mitigation
            if (arena != null) {
                ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());
                if (profile != null && profile.getUpgradeLevel("iron_skin") >= 4) {
                    damage *= 0.75;
                }
            }
        }

        if (damage <= 0) return 0.0;

        // ---------------------------------------------------------------------
        // 2. ABSORPTION PHASE (Shields / Golden Hearts absorb first!)
        // ---------------------------------------------------------------------
        double absorption = player.getAbsorptionAmount();
        if (absorption > 0) {
            if (absorption >= damage) {
                player.setAbsorptionAmount(Math.max(0.0, absorption - damage));
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.2f);
                player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, player.getLocation().add(0, 1, 0), 6, 0.2, 0.4, 0.2, 0.05);
                return 0.0; // Red health untouched!
            } else {
                damage -= absorption;
                player.setAbsorptionAmount(0.0);
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BREAK, 0.9f, 0.9f);
                player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.1);
            }
        }

        // ---------------------------------------------------------------------
        // 3. RED HEALTH & LETHAL CHECK PHASE
        // ---------------------------------------------------------------------
        double currentHp = player.getHealth();
        double newHp = currentHp - damage;

        if (newHp <= 0) {
            // Check Totem of Undying
            boolean hasTotem = player.getInventory().getItemInMainHand().getType() == org.bukkit.Material.TOTEM_OF_UNDYING
                    || player.getInventory().getItemInOffHand().getType() == org.bukkit.Material.TOTEM_OF_UNDYING;

            vn.meowchan12.mobarenav2.boss.BossCounterMeta counterMeta = new vn.meowchan12.mobarenav2.boss.BossCounterMeta(plugin);
            boolean isAntiTotem = damager != null && counterMeta.shouldBypassTotem(damager, player);

            NamespacedKey dmKey = new NamespacedKey(plugin, "ma_death_mark");
            boolean hasDeathMark = player.getPersistentDataContainer().has(dmKey, PersistentDataType.LONG)
                    && System.currentTimeMillis() <= player.getPersistentDataContainer().get(dmKey, PersistentDataType.LONG);

            if (hasTotem && !hasDeathMark && !isAntiTotem) {
                if (player.getInventory().getItemInMainHand().getType() == org.bukkit.Material.TOTEM_OF_UNDYING) {
                    player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() - 1);
                } else {
                    player.getInventory().getItemInOffHand().setAmount(player.getInventory().getItemInOffHand().getAmount() - 1);
                }
                player.playEffect(org.bukkit.EntityEffect.TOTEM_RESURRECT);
                player.setHealth(1.0);
                player.sendMessage("§a§l[TOTEM] §eYour Totem of Undying saved you from fatal damage!");
                return 0.0;
            } else if (hasTotem && isAntiTotem) {
                player.sendMessage("§4§l[SOUL SHATTER] §cThe Boss shattered your Totem of Undying!");
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 0.5f);
                player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation().add(0, 1, 0), 15);
            }

            // Fatal: Delegate to Arena death handler cleanly
            if (arena != null) {
                plugin.getArenaManager().clearAllPlayerEffects(player);
                plugin.getArenaManager().handlePlayerDeath(player, arena);
            } else {
                player.setHealth(0.0);
            }
            return damage;
        } else {
            player.setHealth(newHp);
            player.playHurtAnimation(0.0f);
            return damage;
        }
    }
}
