package vn.meowchan12.mobarenav2.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Witch;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;
import vn.meowchan12.mobarenav2.utils.ColorUtils;

import java.util.*;

/**
 * [v8.3.0]: Support Mob Service.
 * Manages tactical priority-target support witches:
 * 1. Healer Witch: Green glow outline, 1.25x scale, 64m aura healing 5% Max HP to mobs/bosses.
 * 2. Buffer Witch (War Witch): Red glow outline, 1.25x scale, 48m aura stacking Strength (No Cap) every 5s.
 * Fully native to Paper 1.21.4 with zero memory leaks and automatic lifecycle cleanup.
 */
public class SupportMobService {

    public enum SupportType {
        HEALER,
        BUFFER
    }

    private static final String TEAM_HEALER = "MA_HEAL_WITCH";
    private static final String TEAM_BUFFER = "MA_BUFF_WITCH";

    private final MobArenav2 plugin;
    private final NamespacedKey supportTypeKey;
    private final NamespacedKey witchTeamEntryKey;

    private Team healerTeam;
    private Team bufferTeam;
    private BukkitTask auraTask;

    // Tick counters for periodic pulses (1s = 20 ticks)
    private int globalTick = 0;

    public SupportMobService(MobArenav2 plugin) {
        this.plugin = plugin;
        this.supportTypeKey = new NamespacedKey(plugin, "ma_support_type");
        this.witchTeamEntryKey = new NamespacedKey(plugin, "ma_support_entry");
        initScoreboardTeams();
        startAuraTask();
    }

    private void initScoreboardTeams() {
        try {
            Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();

            healerTeam = sb.getTeam(TEAM_HEALER);
            if (healerTeam == null) {
                healerTeam = sb.registerNewTeam(TEAM_HEALER);
            }
            healerTeam.color(NamedTextColor.GREEN);

            bufferTeam = sb.getTeam(TEAM_BUFFER);
            if (bufferTeam == null) {
                bufferTeam = sb.registerNewTeam(TEAM_BUFFER);
            }
            bufferTeam.color(NamedTextColor.RED);
        } catch (Exception ex) {
            plugin.getLogger().warning("[MobArenav2] Failed to initialize Support Witch scoreboard glow teams: " + ex.getMessage());
        }
    }

    public boolean isSupportMobKey(String mobKey) {
        if (mobKey == null) return false;
        String lower = mobKey.trim().toLowerCase();
        return lower.equals("healer_witch") || lower.equals("buffer_witch") ||
               lower.equals("healer") || lower.equals("buffer") ||
               lower.equals("war_witch");
    }

    public SupportType parseType(String mobKey) {
        if (mobKey == null) return SupportType.HEALER;
        String lower = mobKey.trim().toLowerCase();
        if (lower.contains("buff") || lower.contains("war")) {
            return SupportType.BUFFER;
        }
        return SupportType.HEALER;
    }

    public void setupSupportWitch(Witch witch, Arena arena, String mobKey, int currentWave) {
        if (witch == null || !witch.isValid()) return;

        SupportType type = parseType(mobKey);
        witch.setPersistent(true);
        witch.setRemoveWhenFarAway(false);

        ConfigurationSection settingsSec = plugin.getConfigManager() != null && plugin.getConfigManager().getSettings() != null ?
                plugin.getConfigManager().getSettings().getConfigurationSection("support-mobs") : null;

        String configPrefix = (type == SupportType.HEALER) ? "healer_witch" : "buffer_witch";
        ConfigurationSection witchConfig = (settingsSec != null) ? settingsSec.getConfigurationSection(configPrefix) : null;

        // 1. Display Name & Glowing
        String defaultName = (type == SupportType.HEALER) ? "&#55FF55&l[Healer Witch]" : "&#FF5555&l[War Witch]";
        String displayName = (witchConfig != null) ? witchConfig.getString("display-name", defaultName) : defaultName;
        witch.setCustomName(ColorUtils.colorize(displayName));
        witch.setCustomNameVisible(true);
        witch.setGlowing(true);

        // 2. Scoreboard Team Glow Color
        String entry = witch.getUniqueId().toString();
        try {
            if (type == SupportType.HEALER && healerTeam != null) {
                healerTeam.addEntry(entry);
            } else if (type == SupportType.BUFFER && bufferTeam != null) {
                bufferTeam.addEntry(entry);
            }
        } catch (Exception ignored) {}

        // 3. Native Scale (Paper 1.20.5+ / 1.21.4 Attribute.SCALE)
        double scaleValue = (witchConfig != null) ? witchConfig.getDouble("scale", 1.25) : 1.25;
        try {
            AttributeInstance scaleAttr = witch.getAttribute(Attribute.SCALE);
            if (scaleAttr != null) {
                scaleAttr.setBaseValue(scaleValue);
            }
        } catch (Throwable ignored) {}

        // 4. Wave-Scaled Health Formula: HP = min(base-hp + (wave - 1) * hp-per-wave, max-hp-cap)
        double baseHp = (witchConfig != null) ? witchConfig.getDouble("base-hp", 80.0) : 80.0;
        double hpPerWave = (witchConfig != null) ? witchConfig.getDouble("hp-per-wave", 6.0) : 6.0;
        double maxHpCap = (witchConfig != null) ? witchConfig.getDouble("max-hp-cap", 400.0) : 400.0;

        int waveNumber = Math.max(1, currentWave);
        double calculatedHp = Math.min(maxHpCap, baseHp + (waveNumber - 1) * hpPerWave);

        AttributeInstance maxHpAttr = witch.getAttribute(AttributeUtils.MAX_HEALTH);
        if (maxHpAttr != null) {
            maxHpAttr.setBaseValue(calculatedHp);
            witch.setHealth(calculatedHp);
        }

        // 5. Persistent Tags & Metadata
        witch.getPersistentDataContainer().set(supportTypeKey, PersistentDataType.STRING, type.name());
        witch.getPersistentDataContainer().set(witchTeamEntryKey, PersistentDataType.STRING, entry);
        witch.setMetadata("MA_SUPPORT_TYPE", new FixedMetadataValue(plugin, type.name()));
        witch.setMetadata("MA_SUPPORT_MOB", new FixedMetadataValue(plugin, true));

        // Announce Arrival to arena players
        if (arena != null) {
            String alert = (type == SupportType.HEALER) ?
                    "&a&l⚠ [HIGH PRIORITY] &eA &a&lHealer Witch &ehas appeared! Eliminate her before she heals the horde!" :
                    "&c&l⚠ [HIGH PRIORITY] &eA &c&lWar Witch &ehas appeared! Slay her before mob strength stacks out of control!";
            arena.broadcastMessage(ColorUtils.colorize(alert));
            witch.getWorld().playSound(witch.getLocation(), Sound.ENTITY_WITCH_AMBIENT, 1.2f, 0.7f);
        }
    }

    private void startAuraTask() {
        if (auraTask != null) auraTask.cancel();

        auraTask = new BukkitRunnable() {
            @Override
            public void run() {
                globalTick++;
                tickSupportAuras();
            }
        }.runTaskTimer(plugin, 20L, 20L); // Ticks every 1 second (20 ticks)
    }

    private void tickSupportAuras() {
        if (plugin.getArenaManager() == null) return;

        for (Arena arena : plugin.getArenaManager().getAllArenas()) {
            if (!arena.isRunning() || arena.getActiveMobs().isEmpty()) continue;

            List<LivingEntity> mobsSnapshot = new ArrayList<>(arena.getActiveMobs());
            for (LivingEntity mob : mobsSnapshot) {
                if (mob == null || !mob.isValid() || mob.isDead()) continue;
                if (!mob.hasMetadata("MA_SUPPORT_MOB")) continue;

                String typeStr = mob.hasMetadata("MA_SUPPORT_TYPE") ?
                        mob.getMetadata("MA_SUPPORT_TYPE").get(0).asString() : null;

                if ("HEALER".equalsIgnoreCase(typeStr)) {
                    tickHealerAura(mob, arena);
                } else if ("BUFFER".equalsIgnoreCase(typeStr)) {
                    tickBufferAura(mob, arena);
                }
            }
        }
    }

    private void tickHealerAura(LivingEntity witch, Arena arena) {
        // Pulses every 4 seconds
        if (globalTick % 4 != 0) return;

        double radius = 64.0;
        double healPercent = 0.05; // 5% Max HP

        Location witchLoc = witch.getLocation();
        Collection<LivingEntity> nearby = witchLoc.getWorld().getNearbyLivingEntities(witchLoc, radius);

        int healedCount = 0;
        for (LivingEntity target : nearby) {
            if (target == null || !target.isValid() || target.isDead()) continue;

            // STRICT FILTER: NEVER HEAL PLAYERS!
            if (target instanceof Player) continue;

            // Must be an arena mob or boss
            if (!isArenaTarget(target, arena)) continue;

            AttributeInstance maxHpAttr = target.getAttribute(AttributeUtils.MAX_HEALTH);
            if (maxHpAttr == null) continue;

            double maxHp = maxHpAttr.getValue();
            double curHp = target.getHealth();
            if (curHp >= maxHp) continue; // Already full health

            double healAmount = maxHp * healPercent;
            target.setHealth(Math.min(maxHp, curHp + healAmount));
            healedCount++;

            // Visual heal feedback on targets
            target.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, target.getLocation().add(0, 1.0, 0), 4, 0.3, 0.4, 0.3, 0.05);
        }

        if (healedCount > 0) {
            // Witch aura pulse particle ring & subtle sound
            witch.getWorld().spawnParticle(Particle.HEART, witchLoc.add(0, 2.0, 0), 6, 0.5, 0.5, 0.5, 0.05);
            witch.getWorld().playSound(witchLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
        }
    }

    private void tickBufferAura(LivingEntity witch, Arena arena) {
        // Pulses every 5 seconds
        if (globalTick % 5 != 0) return;

        double radius = 48.0;
        int durationTicks = 200; // 10 seconds (fades after witch is killed)

        Location witchLoc = witch.getLocation();
        Collection<LivingEntity> nearby = witchLoc.getWorld().getNearbyLivingEntities(witchLoc, radius);

        int buffedCount = 0;
        for (LivingEntity target : nearby) {
            if (target == null || !target.isValid() || target.isDead()) continue;

            // STRICT FILTER: NEVER BUFF PLAYERS!
            if (target instanceof Player) continue;

            // Must be an arena mob or boss
            if (!isArenaTarget(target, arena)) continue;

            // Stack Strength: Strength I -> Strength II -> Strength III... (NO CAP!)
            int nextAmp = 0;
            PotionEffect currentEff = target.getPotionEffect(PotionEffectType.STRENGTH);
            if (currentEff != null) {
                nextAmp = currentEff.getAmplifier() + 1;
            }

            target.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, durationTicks, nextAmp, false, true));
            buffedCount++;

            // Red particle on buffed target
            target.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, target.getLocation().add(0, 1.2, 0), 2, 0.2, 0.3, 0.2, 0.02);
        }

        if (buffedCount > 0) {
            // Buffer witch aura pulse: Red particle burst
            try {
                Particle.DustOptions redDust = new Particle.DustOptions(Color.fromRGB(255, 30, 30), 1.6f);
                witch.getWorld().spawnParticle(Particle.DUST, witchLoc.add(0, 1.5, 0), 25, 1.2, 0.8, 1.2, redDust);
            } catch (Throwable ignored) {
                witch.getWorld().spawnParticle(Particle.FLAME, witchLoc.add(0, 1.5, 0), 15, 0.5, 0.5, 0.5, 0.05);
            }
            witch.getWorld().playSound(witchLoc, Sound.ENTITY_BLAZE_SHOOT, 0.7f, 0.75f);
        }
    }

    private boolean isArenaTarget(LivingEntity entity, Arena arena) {
        if (entity.hasMetadata("MA_MOB") || entity.hasMetadata("MA_BOSS") || entity.hasMetadata("BOSS")) {
            return true;
        }
        if (arena.getActiveMobs().contains(entity)) {
            return true;
        }
        return false;
    }

    public void cleanUpEntity(LivingEntity entity) {
        if (entity == null) return;
        try {
            String entry = entity.getUniqueId().toString();
            if (healerTeam != null) healerTeam.removeEntry(entry);
            if (bufferTeam != null) bufferTeam.removeEntry(entry);
        } catch (Exception ignored) {}
    }

    public void shutdown() {
        if (auraTask != null) {
            auraTask.cancel();
            auraTask = null;
        }
        try {
            if (healerTeam != null) {
                for (String entry : new HashSet<>(healerTeam.getEntries())) {
                    healerTeam.removeEntry(entry);
                }
                healerTeam.unregister();
                healerTeam = null;
            }
            if (bufferTeam != null) {
                for (String entry : new HashSet<>(bufferTeam.getEntries())) {
                    bufferTeam.removeEntry(entry);
                }
                bufferTeam.unregister();
                bufferTeam = null;
            }
        } catch (Exception ignored) {}
    }
}
