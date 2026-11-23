package vn.meowchan12.mobarenav2.shop.ultimate;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.utils.ParticleUtils;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.shop.model.PlayerShopData;
import vn.meowchan12.mobarenav2.shop.model.ShopItem;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [v7.3 REWORK]: Ultimate Skill Manager
 * Handles activation, cooldowns with CUSTOM_COOLDOWN_REDUCE, and combat execution
 * for Ultimates purchased from /ma shop (Shadow Dash, Rallying Beacon, Stasis Aegis).
 */
public class UltimateManager {

    private final MobArenav2 plugin;
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> stasisImmunePlayers = new HashSet<>();

    public UltimateManager(MobArenav2 plugin) {
        this.plugin = plugin;
    }

    public boolean isStasisImmune(Player player) {
        return player != null && stasisImmunePlayers.contains(player.getUniqueId());
    }

    /**
     * Determines which ultimate is available and triggers it.
     */
    public boolean tryTriggerUltimate(Player player, Arena arena) {
        if (player == null || arena == null || !arena.isRunning()) return false;

        PlayerShopData shopData = (plugin.getShopManager() != null) ? plugin.getShopManager().getPlayerData(player.getUniqueId()) : null;
        if (shopData == null) return false;

        boolean hasStasis = shopData.getItemLevel("stasis_aegis_ultimate") > 0 || player.hasPermission("mobarenav2.ultimate.stasaegis");
        boolean hasBeacon = shopData.getItemLevel("rallying_beacon_ultimate") > 0 || player.hasPermission("mobarenav2.ultimate.rallyingbeacon");
        boolean hasDash = shopData.getItemLevel("shadow_dash_ultimate") > 0 || player.hasPermission("mobarenav2.ultimate.shadowdash");

        if (!hasStasis && !hasBeacon && !hasDash) {
            return false;
        }

        // [v7.5.2 SINGLE EQUIPPED ULTIMATE LOADOUT]:
        String equipped = shopData.getEquippedUltimate();
        if (equipped == null) {
            if (hasDash) equipped = "shadow_dash_ultimate";
            else if (hasBeacon) equipped = "rallying_beacon_ultimate";
            else if (hasStasis) equipped = "stasis_aegis_ultimate";
            if (equipped != null) {
                shopData.setEquippedUltimate(equipped);
                if (plugin.getShopManager() != null) {
                    plugin.getShopManager().savePlayerDataToDisk(player.getUniqueId());
                }
            }
        }

        String chosen = null;
        String ultName = "";
        long remainingCd = 0;

        if (equipped != null) {
            switch (equipped.toLowerCase()) {
                case "shadow_dash_ultimate" -> {
                    if (hasDash) {
                        ultName = "Shadow Dash";
                        remainingCd = getRemainingCooldown(player.getUniqueId(), "dash");
                        if (remainingCd <= 0) chosen = "dash";
                    }
                }
                case "rallying_beacon_ultimate" -> {
                    if (hasBeacon) {
                        ultName = "Rallying Beacon";
                        remainingCd = getRemainingCooldown(player.getUniqueId(), "beacon");
                        if (remainingCd <= 0) chosen = "beacon";
                    }
                }
                case "stasis_aegis_ultimate" -> {
                    if (hasStasis) {
                        ultName = "Stasis Aegis";
                        remainingCd = getRemainingCooldown(player.getUniqueId(), "stasis");
                        if (remainingCd <= 0) chosen = "stasis";
                    }
                }
            }
        }

        if (chosen == null) {
            if (remainingCd > 0) {
                long secondsLeft = (remainingCd + 999) / 1000;
                player.sendActionBar(Component.text("§c[" + ultName + ": Cooldown " + secondsLeft + "s]"));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
            }
            return false;
        }

        // Apply cooldown reduction from Shop (CUSTOM_COOLDOWN_REDUCE, configurable cap)
        double maxCdReduction = plugin.getConfig().getDouble("shop.ultimates.max-cooldown-reduction", 0.25);
        double cdReduction = (plugin.getShopManager() != null) ? plugin.getShopManager().getCustomStatBonus(player.getUniqueId(), "CUSTOM_COOLDOWN_REDUCE") : 0.0;
        double reductionFactor = 1.0 - Math.min(maxCdReduction, cdReduction / 100.0);

        switch (chosen) {
            case "dash" -> {
                ShopItem dashItem = (plugin.getShopManager() != null) ? plugin.getShopManager().getShopItem("shadow_dash_ultimate") : null;
                int baseCd = (dashItem != null) ? dashItem.getIntSetting("cooldown", 25) : 25;
                long actualCd = Math.round(baseCd * reductionFactor);
                setCooldown(player.getUniqueId(), "dash", actualCd);
                castShadowDash(player, arena, actualCd, dashItem);
                return true;
            }
            case "beacon" -> {
                ShopItem beaconItem = (plugin.getShopManager() != null) ? plugin.getShopManager().getShopItem("rallying_beacon_ultimate") : null;
                int baseCd = (beaconItem != null) ? beaconItem.getIntSetting("cooldown", 45) : 45;
                long actualCd = Math.round(baseCd * reductionFactor);
                setCooldown(player.getUniqueId(), "beacon", actualCd);
                castRallyingBeacon(player, arena, actualCd, beaconItem);
                return true;
            }
            case "stasis" -> {
                ShopItem stasisItem = (plugin.getShopManager() != null) ? plugin.getShopManager().getShopItem("stasis_aegis_ultimate") : null;
                int baseCd = (stasisItem != null) ? stasisItem.getIntSetting("cooldown", 60) : 60;
                long actualCd = Math.round(baseCd * reductionFactor);
                setCooldown(player.getUniqueId(), "stasis", actualCd);
                castStasisAegis(player, arena, actualCd, stasisItem);
                return true;
            }
        }

        return false;
    }

    private void castShadowDash(Player player, Arena arena, long cd, ShopItem item) {
        double velocityMult = (item != null) ? item.getDoubleSetting("dash-velocity", 1.6) : 1.6;
        Vector dir = player.getLocation().getDirection().normalize().multiply(velocityMult);
        dir.setY(0.2);
        player.setVelocity(dir);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PHANTOM_BITE, 0.8f, 1.8f);
        player.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, player.getLocation().add(0, 1, 0), 25, 0.4, 0.4, 0.4, 0.05);
        ParticleUtils.spawnSafeParticle(player.getWorld(), Particle.SOUL_FIRE_FLAME, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.05);

        player.sendActionBar(Component.text("§b§l✔ [SHADOW DASH] §fDashed forward! Stunned nearby enemies! (CD: " + cd + "s)"));

        double radius = (item != null) ? item.getDoubleSetting("radius", 4.5) : 4.5;
        double radiusY = (item != null) ? item.getDoubleSetting("radius-y", 3.0) : 3.0;
        double damage = (item != null) ? item.getDoubleSetting("damage", 15.0) : 15.0;
        int slowTicks = (item != null) ? item.getIntSetting("slowness-ticks", 40) : 40;
        int slowAmp = (item != null) ? item.getIntSetting("slowness-amplifier", 9) : 9;
        int weakTicks = (item != null) ? item.getIntSetting("weakness-ticks", 40) : 40;
        int weakAmp = (item != null) ? item.getIntSetting("weakness-amplifier", 4) : 4;

        BukkitRunnable stunTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Entity e : player.getNearbyEntities(radius, radiusY, radius)) {
                    if (e instanceof LivingEntity mob && mob.hasMetadata("MA_MOB") && !mob.isDead()) {
                        mob.damage(damage, player);
                        mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, slowAmp, false, true));
                        mob.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, weakTicks, weakAmp, false, true));
                        spawnSafeFlash(mob.getWorld(), mob.getLocation().add(0, 1, 0));
                    }
                }
            }
        };
        stunTask.runTaskLater(plugin, 3L);
    }

    private void spawnSafeFlash(org.bukkit.World world, org.bukkit.Location loc) {
        if (world == null || loc == null) return;
        try {
            world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 15, 0.4, 0.4, 0.4, 0.05);
            world.spawnParticle(Particle.CRIT, loc, 10, 0.3, 0.3, 0.3, 0.1);
        } catch (Throwable ignored) {}
    }

    private void castRallyingBeacon(Player player, Arena arena, long cd, ShopItem item) {
        Location beaconLoc = player.getLocation().clone();
        player.getWorld().playSound(beaconLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.2f);
        player.getWorld().playSound(beaconLoc, Sound.ITEM_TOTEM_USE, 0.8f, 1.6f);

        arena.broadcastMessage("§e§l✦ [RALLYING BEACON] §f" + player.getName() + " planted a Holy Banner! (Restoring HP to nearby allies)");
        player.sendActionBar(Component.text("§e§l✔ [RALLYING BEACON] §fHoly Banner Planted! (CD: " + cd + "s)"));

        int totalTicks = (item != null) ? item.getIntSetting("duration-ticks", 200) : 200; // 10s default
        double radius = (item != null) ? item.getDoubleSetting("radius", 6.0) : 6.0;
        double radiusSq = radius * radius;
        double healPercent = (item != null) ? item.getDoubleSetting("heal-percent", 0.05) : 0.05;
        int resTicks = (item != null) ? item.getIntSetting("resistance-ticks", 40) : 40;

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!arena.isRunning() || ticks >= totalTicks) {
                    cancel();
                    return;
                }

                // Holy particle column
                beaconLoc.getWorld().spawnParticle(Particle.END_ROD, beaconLoc.clone().add(0, 1.5, 0), 6, 0.2, 1.0, 0.2, 0.05);
                beaconLoc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, beaconLoc.clone().add(0, 0.2, 0), 8, 1.5, 0.1, 1.5, 0.02);

                // Heal every 20 ticks (1s)
                if (ticks % 20 == 0) {
                    for (Player ally : arena.getPlayersAsObjects()) {
                        if (ally.getLocation().distanceSquared(beaconLoc) <= radiusSq) {
                            AttributeInstance maxHpAttr = ally.getAttribute(AttributeUtils.MAX_HEALTH);
                            double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 20.0;
                            double heal = maxHp * healPercent;
                            ally.setHealth(Math.min(maxHp, ally.getHealth() + heal));
                            ally.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, resTicks, 0, false, false));
                            ally.getWorld().spawnParticle(Particle.HEART, ally.getLocation().add(0, 1.8, 0), 3, 0.2, 0.2, 0.2, 0.05);
                        }
                    }
                }

                ticks += 10;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void castStasisAegis(Player player, Arena arena, long cd, ShopItem item) {
        UUID uuid = player.getUniqueId();
        stasisImmunePlayers.add(uuid);

        int durationTicks = (item != null) ? item.getIntSetting("duration-ticks", 60) : 60; // 3s default
        int durationSec = Math.max(1, durationTicks / 20);

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.7f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.5f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, 99, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, durationTicks, 99, false, false));

        player.sendActionBar(Component.text("§3§l✔ [STASIS AEGIS] §bInvulnerable for " + durationSec + "s! (CD: " + cd + "s)"));

        double burstRadius = (item != null) ? item.getDoubleSetting("burst-radius", 7.0) : 7.0;
        double burstRadiusY = (item != null) ? item.getDoubleSetting("burst-radius-y", 4.0) : 4.0;
        double burstDmg = (item != null) ? item.getDoubleSetting("burst-damage", 20.0) : 20.0;
        double knockback = (item != null) ? item.getDoubleSetting("knockback", 1.4) : 1.4;
        int slowTicks = (item != null) ? item.getIntSetting("slowness-ticks", 80) : 80;
        int slowAmp = (item != null) ? item.getIntSetting("slowness-amplifier", 2) : 2;

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !arena.isRunning()) {
                    stasisImmunePlayers.remove(uuid);
                    cancel();
                    return;
                }

                // Swirling snowflakes
                player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0, 1, 0), 12, 0.5, 0.7, 0.5, 0.05);

                if (ticks >= durationTicks) {
                    stasisImmunePlayers.remove(uuid);

                    // Icy shockwave burst
                    Location loc = player.getLocation();
                    loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc.add(0, 1, 0), 1);
                    loc.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 60, 2.0, 1.0, 2.0, 0.15);
                    player.playSound(loc, Sound.ITEM_SHIELD_BLOCK, 1.2f, 0.6f);

                    for (Entity near : player.getNearbyEntities(burstRadius, burstRadiusY, burstRadius)) {
                        if (near instanceof LivingEntity mob && mob.hasMetadata("MA_MOB") && !mob.isDead()) {
                            Vector knock = mob.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(knockback).setY(0.35);
                            mob.setVelocity(knock);
                            mob.damage(burstDmg, player);
                            mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, slowAmp, false, true));
                        }
                    }

                    player.sendActionBar(Component.text("§b§l[STASIS EXPLOSION] §fIce shockwave detonated!"));
                    cancel();
                    return;
                }

                ticks += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private long getRemainingCooldown(UUID uuid, String skill) {
        Map<String, Long> userCds = cooldowns.get(uuid);
        if (userCds == null) return 0;
        Long expire = userCds.get(skill);
        if (expire == null) return 0;
        long diff = expire - System.currentTimeMillis();
        return Math.max(0, diff);
    }

    private void setCooldown(UUID uuid, String skill, long seconds) {
        cooldowns.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .put(skill, System.currentTimeMillis() + (seconds * 1000L));
    }

    public void cleanupPlayer(UUID uuid) {
        cooldowns.remove(uuid);
        stasisImmunePlayers.remove(uuid);
    }
}
