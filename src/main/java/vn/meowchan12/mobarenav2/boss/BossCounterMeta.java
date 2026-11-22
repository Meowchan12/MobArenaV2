package vn.meowchan12.mobarenav2.boss;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

import java.util.concurrent.ThreadLocalRandom;

/**
 * [v7.0] Handles all anti-meta counter mechanics for bosses.
 * Prevents cheese strategies (Mace oneshot, Elytra kiting, Ranged spam, Totem abuse).
 * 
 * Each counter mechanic is configurable per-boss in bosses.yml under "counter-meta" section.
 * Memory-safe: No persistent state stored; all checks are event-driven.
 */
public class BossCounterMeta {

    private final MobArenav2 plugin;

    public BossCounterMeta(MobArenav2 plugin) {
        this.plugin = plugin;
    }

    /**
     * Main entry point: processes all counter-meta checks when a player attacks a boss.
     * Called from the combat listener BEFORE damage is applied.
     * 
     * @return true if the attack should be cancelled entirely
     */
    public boolean processCounterMeta(EntityDamageByEntityEvent event, Player attacker, LivingEntity boss, Arena arena) {
        NamespacedKey bossIdKey = new NamespacedKey(plugin, "arena_boss_id");
        if (!boss.getPersistentDataContainer().has(bossIdKey, PersistentDataType.STRING)) return false;

        String bossId = boss.getPersistentDataContainer().get(bossIdKey, PersistentDataType.STRING);
        ConfigurationSection bossCfg = plugin.getBossManager().getBossesConfig().getConfigurationSection(bossId);
        if (bossCfg == null) return false;

        ConfigurationSection counterCfg = bossCfg.getConfigurationSection("counter-meta");
        if (counterCfg == null) return false;

        // 1. Damage Cap
        if (counterCfg.contains("damage-cap")) {
            double cap = counterCfg.getDouble("damage-cap", 200.0);
            if (event.getDamage() > cap) {
                event.setDamage(cap);
            }
        }

        // 2. Mace Counter (Kinetic Reflection)
        if (counterCfg.getBoolean("anti-mace.enabled", false)) {
            if (processMaceCounter(event, attacker, boss, counterCfg.getConfigurationSection("anti-mace"))) {
                return true;
            }
        }

        // 3. Ranged Counter (Mirror of Vengeance)
        if (counterCfg.getBoolean("anti-ranged.enabled", false)) {
            if (processRangedCounter(event, attacker, boss, counterCfg.getConfigurationSection("anti-ranged"))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Processes Elytra counter mechanics. Called from a tick-based check.
     * Detects players gliding near the boss and punishes them.
     */
    public void processElytraCounter(Player player, LivingEntity boss, Arena arena) {
        NamespacedKey bossIdKey = new NamespacedKey(plugin, "arena_boss_id");
        if (!boss.getPersistentDataContainer().has(bossIdKey, PersistentDataType.STRING)) return;

        String bossId = boss.getPersistentDataContainer().get(bossIdKey, PersistentDataType.STRING);
        ConfigurationSection bossCfg = plugin.getBossManager().getBossesConfig().getConfigurationSection(bossId);
        if (bossCfg == null) return;

        ConfigurationSection counterCfg = bossCfg.getConfigurationSection("counter-meta.anti-elytra");
        if (counterCfg == null || !counterCfg.getBoolean("enabled", false)) return;

        double radius = counterCfg.getDouble("radius", 50.0);
        if (player.getLocation().distance(boss.getLocation()) > radius) return;

        if (!player.isGliding()) return;

        // Gravity Crush: Unequip elytra, strike lightning, fall damage
        PlayerInventory inv = player.getInventory();
        ItemStack chestplate = inv.getChestplate();
        if (chestplate != null && chestplate.getType() == Material.ELYTRA) {
            // Damage the elytra severely
            short maxDurability = chestplate.getType().getMaxDurability();
            if (maxDurability > 0 && chestplate instanceof org.bukkit.inventory.meta.Damageable) {
                // Set durability to 1
                org.bukkit.inventory.meta.ItemMeta meta = chestplate.getItemMeta();
                if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                    damageable.setDamage(maxDurability - 1);
                    chestplate.setItemMeta(meta);
                }
            }

            // Unequip elytra to inventory or drop
            inv.setChestplate(null);
            if (inv.firstEmpty() != -1) {
                inv.addItem(chestplate);
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), chestplate);
            }
        }

        // Strike lightning
        player.getWorld().strikeLightningEffect(player.getLocation());
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.8f);

        // Cancel gliding and apply fall
        player.setGliding(false);
        player.setVelocity(new Vector(0, -2.0, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 4, false, false));

        // Direct true damage
        double damage = counterCfg.getDouble("damage", 15.0);
        double newHp = player.getHealth() - damage;
        if (newHp <= 0) {
            player.damage(9999.0);
        } else {
            player.setHealth(newHp);
        }

        player.sendMessage("§4§l[GRAVITY CRUSH] §cYour Elytra has been ripped off! No flying allowed near this boss!");
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation(), 30, 1, 1, 1, 0.1);
    }

    /**
     * Processes Totem counter (Soul Shatter).
     * Returns true if the totem should be bypassed.
     */
    public boolean shouldBypassTotem(LivingEntity boss, Player player) {
        NamespacedKey bossIdKey = new NamespacedKey(plugin, "arena_boss_id");
        if (!boss.getPersistentDataContainer().has(bossIdKey, PersistentDataType.STRING)) return false;

        String bossId = boss.getPersistentDataContainer().get(bossIdKey, PersistentDataType.STRING);
        ConfigurationSection bossCfg = plugin.getBossManager().getBossesConfig().getConfigurationSection(bossId);
        if (bossCfg == null) return false;

        return bossCfg.getBoolean("counter-meta.anti-totem.enabled", false);
    }

    // ==========================================
    // PRIVATE COUNTER IMPLEMENTATIONS
    // ==========================================

    /**
     * Mace Counter: Kinetic Reflection
     * High fall-damage attacks (Mace smash) are reflected back with multiplied damage.
     */
    private boolean processMaceCounter(EntityDamageByEntityEvent event, Player attacker, LivingEntity boss, ConfigurationSection cfg) {
        if (cfg == null) return false;

        double threshold = cfg.getDouble("damage-threshold", 100.0);
        double reflectMultiplier = cfg.getDouble("reflect-multiplier", 10.0);
        boolean disarm = cfg.getBoolean("disarm", true);

        // Check if the attack exceeds the threshold (likely a Mace smash)
        if (event.getDamage() <= threshold) return false;

        // Check if player is holding a mace
        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        boolean isMace = mainHand.getType() == Material.MACE;

        // Also check for high fall distance as additional indicator
        boolean isHighFall = attacker.getFallDistance() > 5.0;

        if (!isMace && !isHighFall) return false;

        // Reflect damage
        double reflectedDamage = event.getDamage() * (reflectMultiplier / 100.0);
        event.setCancelled(true);

        // Apply reflected damage as true damage to attacker
        double newHp = attacker.getHealth() - reflectedDamage;
        if (newHp <= 0) {
            attacker.damage(9999.0);
        } else {
            attacker.setHealth(newHp);
        }

        // Disarm: knock weapon out of hand
        if (disarm && isMace) {
            attacker.getInventory().setItemInMainHand(null);
            attacker.getWorld().dropItemNaturally(attacker.getLocation(), mainHand);
        }

        // Effects
        attacker.getWorld().playSound(attacker.getLocation(), Sound.ITEM_SHIELD_BREAK, 2.0f, 0.5f);
        attacker.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, attacker.getLocation().add(0, 1, 0), 1);
        attacker.sendMessage("§4§l[KINETIC REFLECTION] §cYour Mace attack was reflected back at you!");

        // Knockback the attacker away
        Vector knockback = attacker.getLocation().toVector().subtract(boss.getLocation().toVector()).normalize().multiply(2.0).setY(1.0);
        attacker.setVelocity(knockback);

        return true;
    }

    /**
     * Ranged Counter: Mirror of Vengeance
     * Projectile attacks are reflected back at the attacker with multiplied damage.
     */
    private boolean processRangedCounter(EntityDamageByEntityEvent event, Player attacker, LivingEntity boss, ConfigurationSection cfg) {
        if (cfg == null) return false;
        if (!(event.getDamager() instanceof Projectile)) return false;

        double reflectMultiplier = cfg.getDouble("reflect-multiplier", 3.0);
        double pullRange = cfg.getDouble("pull-range", 30.0);

        event.setCancelled(true);

        // Reflect damage
        double reflectedDamage = event.getDamage() * reflectMultiplier;
        double newHp = attacker.getHealth() - reflectedDamage;
        if (newHp <= 0) {
            attacker.damage(9999.0);
        } else {
            attacker.setHealth(newHp);
        }

        // Pull player if too far
        double distance = attacker.getLocation().distance(boss.getLocation());
        if (distance > pullRange) {
            Vector pull = boss.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize().multiply(2.5);
            attacker.setVelocity(pull);
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false));
            attacker.sendMessage("§5§l[ROOTS OF THE ABYSS] §dVines dragged you toward the boss!");
        }

        // Effects
        attacker.getWorld().playSound(attacker.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.5f, 0.5f);
        attacker.getWorld().spawnParticle(Particle.CRIT, attacker.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
        attacker.sendMessage("§4§l[MIRROR OF VENGEANCE] §cYour projectile was deflected!");

        return true;
    }
}
