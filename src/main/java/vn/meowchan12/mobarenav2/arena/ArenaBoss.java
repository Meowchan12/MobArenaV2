package vn.meowchan12.mobarenav2.arena;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.boss.BossCounterMeta;
import vn.meowchan12.mobarenav2.boss.BossPhaseManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents a boss entity within an Arena.
 * Manages the boss bar, skill cooldowns, phase transitions, and counter-meta mechanics.
 * [v7.0] Added multi-phase support, elytra counter tick, and reward tracking integration.
 */
public class ArenaBoss {

    private final MobArenav2 plugin;
    private final Arena arena;
    private LivingEntity entity;
    private final UUID bossUUID; // [FIX BUG #2] Use UUID as source of truth
    private final String displayName;
    private final String bossConfigId; // [v7.0] The config key in bosses.yml

    private BossBar bossBar;

    private final Map<String, Integer> maxCooldowns = new HashMap<>();
    private final Map<String, Integer> currentCooldowns = new HashMap<>();

    private int globalSkillCooldown = 0; // [v6.6] Boss skill queue / global cooldown

    private final List<String> enrageSkills = new ArrayList<>();
    private boolean hasEnraged = false;

    // [v7.0] Multi-Phase Boss System
    private final BossPhaseManager phaseManager;
    private List<String> activePhaseSkills = null;

    // [v7.0] Counter-Meta (Elytra check tick)
    private final BossCounterMeta counterMeta;

    // [v8.3.1]: Telegraphed Crushing Blow Mechanic
    private final boolean hasCrushingBlow;
    private int crushingBlowCooldown;
    private boolean isChargingCrushingBlow = false;

    // [v8.4.0 RACE-CONDITION FIX]: Track exact wave number this boss belongs to
    private final int spawnedWave;

    public ArenaBoss(MobArenav2 plugin, Arena arena, LivingEntity entity, String displayName,
                     boolean showBossBar, boolean showNameTag, List<String> skills,
                     List<String> enrageSkillsData, String bossConfigId) {
        this.plugin = plugin;
        this.arena = arena;
        this.entity = entity;
        this.bossUUID = entity.getUniqueId();
        this.displayName = displayName != null ? displayName.replace("&", "\u00a7") : "Boss";
        this.bossConfigId = bossConfigId;
        this.spawnedWave = (arena != null) ? arena.getCurrentWave() : -1;

        // [v8.3.1]: Check if boss has crushing-blow enabled
        org.bukkit.NamespacedKey cbKey = new org.bukkit.NamespacedKey(plugin, "ma_boss_crushing_blow");
        this.hasCrushingBlow = entity.getPersistentDataContainer().has(cbKey, org.bukkit.persistence.PersistentDataType.BYTE);
        double cbInitMin = getDoubleSetting("crushing-blow.initial-delay-min", 8.0);
        double cbInitMax = getDoubleSetting("crushing-blow.initial-delay-max", 12.0);
        int cbInitMinTicks = (int)(cbInitMin * 20);
        int cbInitMaxTicks = Math.max(cbInitMinTicks, (int)(cbInitMax * 20));
        this.crushingBlowCooldown = cbInitMinTicks + ThreadLocalRandom.current().nextInt(Math.max(1, cbInitMaxTicks - cbInitMinTicks + 1));

        // [v7.0] Initialize phase manager
        ConfigurationSection bossCfg = plugin.getBossManager().getBossesConfig().getConfigurationSection(bossConfigId);
        this.phaseManager = new BossPhaseManager(plugin, arena, entity, bossCfg);
        this.counterMeta = new BossCounterMeta(plugin);

        if (showNameTag) {
            this.entity.setCustomName(this.displayName);
            this.entity.setCustomNameVisible(true);
        }

        if (showBossBar) {
            this.bossBar = Bukkit.createBossBar(this.displayName, BarColor.GREEN, BarStyle.SEGMENTED_10);
            if (arena != null) {
                for (Player p : arena.getPlayersAsObjects()) {
                    this.bossBar.addPlayer(p);
                }
            } else if (entity.getWorld() != null) {
                for (Player p : entity.getWorld().getNearbyPlayers(entity.getLocation(), 50.0)) {
                    this.bossBar.addPlayer(p);
                }
            }
        }

        if (skills != null) {
            for (String skillData : skills) {
                String[] parts = skillData.split(":");
                String skillId = parts[0].trim();
                int cooldownTicks = 20 * 20;

                if (parts.length == 2) {
                    try {
                        cooldownTicks = (int) (Double.parseDouble(parts[1].trim()) * 20);
                    } catch (NumberFormatException ignored) {}
                } else {
                    ConfigurationSection skillCfg = plugin.getBossManager().getSkillsConfig().getConfigurationSection(skillId);
                    if (skillCfg != null && skillCfg.contains("cooldown")) {
                        cooldownTicks = skillCfg.getInt("cooldown") * 20;
                    }
                }

                maxCooldowns.put(skillId, cooldownTicks);
                currentCooldowns.put(skillId, ThreadLocalRandom.current().nextInt(60, 160));
            }
        }

        if (enrageSkillsData != null) {
            this.enrageSkills.addAll(enrageSkillsData);
        }

        // [v7.0] Start reward tracking
        if (plugin.getBossRewardManager() != null) {
            plugin.getBossRewardManager().startTracking(bossUUID);
        }

        startTask();
    }

    private List<Player> getTargetPlayers() {
        if (arena != null) {
            return arena.getPlayersAsObjects();
        }
        if (entity != null && entity.isValid() && entity.getWorld() != null) {
            return new ArrayList<>(entity.getWorld().getNearbyPlayers(entity.getLocation(), 50.0));
        }
        return java.util.Collections.emptyList();
    }

    private void startTask() {
        new BukkitRunnable() {
            int elytraCheckCounter = 0; // [v7.0] Only check elytra every 10 ticks for performance
            int lastThreshold = 1; // 1: >80%, 2: 50-80%, 3: 20-50%, 4: <=20%
            int bossBarNearbyCounter = 0;

            @Override
            public void run() {
                // [FIX BUG #2] Re-fetch entity by UUID if lost due to Chunk unload
                Entity currentEntity = Bukkit.getEntity(bossUUID);
                if (currentEntity instanceof LivingEntity) {
                    entity = (LivingEntity) currentEntity;
                }

                if (entity == null || entity.isDead() || (arena != null && !arena.isRunning())) {
                    onBossDeath();
                    cleanup();
                    this.cancel();
                    return;
                }

                if (!entity.isValid()) {
                    return; // Wait for Chunk to load, do NOT cancel the loop.
                }

                // If standalone boss, update boss bar viewers dynamically
                if (bossBar != null && arena == null) {
                    bossBarNearbyCounter += 5;
                    if (bossBarNearbyCounter >= 20) {
                        bossBarNearbyCounter = 0;
                        List<Player> nearby = getTargetPlayers();
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (nearby.contains(p)) {
                                if (!bossBar.getPlayers().contains(p)) {
                                    bossBar.addPlayer(p);
                                }
                            } else {
                                if (bossBar.getPlayers().contains(p)) {
                                    bossBar.removePlayer(p);
                                }
                            }
                        }
                    }
                }

                double maxHealth = 100.0;
                if (entity.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.MAX_HEALTH) != null) {
                    maxHealth = entity.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.MAX_HEALTH).getValue();
                }
                double health = Math.max(0.0, entity.getHealth());
                double progress = health / maxHealth;

                if (bossBar != null) {
                    bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
                    String hpText = String.format("%.1f", health);
                    String maxHpText = String.format("%.1f", maxHealth);

                    int currentThreshold;
                    String statusIcon;
                    if (progress > 0.8) {
                        currentThreshold = 1;
                        bossBar.setColor(BarColor.GREEN);
                        statusIcon = "\u00a7a\u2764";
                        if (bossBar.hasFlag(org.bukkit.boss.BarFlag.DARKEN_SKY)) bossBar.removeFlag(org.bukkit.boss.BarFlag.DARKEN_SKY);
                        if (bossBar.hasFlag(org.bukkit.boss.BarFlag.CREATE_FOG)) bossBar.removeFlag(org.bukkit.boss.BarFlag.CREATE_FOG);
                    } else if (progress > 0.5) {
                        currentThreshold = 2;
                        bossBar.setColor(BarColor.BLUE);
                        statusIcon = "\u00a7b\ud83d\udee1";
                        if (bossBar.hasFlag(org.bukkit.boss.BarFlag.DARKEN_SKY)) bossBar.removeFlag(org.bukkit.boss.BarFlag.DARKEN_SKY);
                        if (bossBar.hasFlag(org.bukkit.boss.BarFlag.CREATE_FOG)) bossBar.removeFlag(org.bukkit.boss.BarFlag.CREATE_FOG);
                    } else if (progress > 0.2) {
                        currentThreshold = 3;
                        bossBar.setColor(BarColor.YELLOW);
                        statusIcon = "\u00a7e\u26a1";
                        if (bossBar.hasFlag(org.bukkit.boss.BarFlag.DARKEN_SKY)) bossBar.removeFlag(org.bukkit.boss.BarFlag.DARKEN_SKY);
                        if (bossBar.hasFlag(org.bukkit.boss.BarFlag.CREATE_FOG)) bossBar.removeFlag(org.bukkit.boss.BarFlag.CREATE_FOG);
                    } else {
                        currentThreshold = 4;
                        bossBar.setColor(BarColor.RED);
                        statusIcon = "\u00a7c\u2620";
                        if (!bossBar.hasFlag(org.bukkit.boss.BarFlag.DARKEN_SKY)) bossBar.addFlag(org.bukkit.boss.BarFlag.DARKEN_SKY);
                        if (!bossBar.hasFlag(org.bukkit.boss.BarFlag.CREATE_FOG)) bossBar.addFlag(org.bukkit.boss.BarFlag.CREATE_FOG);
                    }

                    // Threshold transition sound & notification cues
                    if (currentThreshold != lastThreshold) {
                        if (currentThreshold == 3 && lastThreshold < 3) {
                            for (Player p : getTargetPlayers()) {
                                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                                p.sendMessage("\u00a7e\u26a1 \u00a7c[BOSS ALERT] \u00a7e" + displayName + " \u00a7ehas entered Phase 2! Prepare yourselves!");
                            }
                        } else if (currentThreshold == 4 && lastThreshold < 4) {
                            for (Player p : getTargetPlayers()) {
                                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
                                p.sendMessage("\u00a74\u2620 \u00a7l[DESPERATION] \u00a7c" + displayName + " \u00a7cis near death! Extreme danger, strike now!");
                            }
                        }
                        lastThreshold = currentThreshold;
                    }

                    // Show phase info in boss bar if multi-phase
                    String phaseInfo = "";
                    if (phaseManager.hasPhases()) {
                        int pIdx = Math.max(0, phaseManager.getCurrentPhaseIndex());
                        phaseInfo = " \u00a77[\u00a7ePhase " + (pIdx + 1) + "\u00a77]";
                    } else if (currentThreshold == 4) {
                        phaseInfo = " \u00a74[DESPERATION]";
                    }
                    bossBar.setTitle(statusIcon + " " + displayName + " \u00a77- \u00a7c" + hpText + "\u00a77/\u00a7c" + maxHpText + " HP" + phaseInfo);
                }

                // [v7.0] Check phase transitions
                double healthPercent = progress * 100.0;
                List<String> phaseSkills = phaseManager.checkPhaseTransition(healthPercent);
                if (phaseSkills != null && !phaseSkills.equals(activePhaseSkills)) {
                    activePhaseSkills = phaseSkills;
                    // Rebuild cooldowns from phase skills
                    currentCooldowns.clear();
                    maxCooldowns.clear();
                    double initMinSec = getDoubleSetting("initial-skill-delay-min", 2.0);
                    double initMaxSec = getDoubleSetting("initial-skill-delay-max", 5.0);
                    int initMinTicks = (int)(initMinSec * 20);
                    int initMaxTicks = Math.max(initMinTicks, (int)(initMaxSec * 20));

                    for (String skillId : activePhaseSkills) {
                        int cooldownTicks = 20 * 20;
                        ConfigurationSection skillCfg = plugin.getBossManager().getSkillsConfig().getConfigurationSection(skillId);
                        if (skillCfg != null && skillCfg.contains("cooldown")) {
                            cooldownTicks = skillCfg.getInt("cooldown") * 20;
                        }
                        maxCooldowns.put(skillId, cooldownTicks);
                        currentCooldowns.put(skillId, ThreadLocalRandom.current().nextInt(initMinTicks, Math.max(initMinTicks + 1, initMaxTicks + 1)));
                    }
                }

                // Legacy enrage (only if no phases defined)
                double enrageThreshold = getDoubleSetting("enrage-threshold", 0.30);
                if (!phaseManager.hasPhases() && !hasEnraged && progress <= enrageThreshold) {
                    hasEnraged = true;
                    for (String eSkill : enrageSkills) {
                        BossSkillExecutor.castSkill(plugin, arena, entity, eSkill);
                    }
                }

                if (globalSkillCooldown > 0) {
                    globalSkillCooldown -= 5;
                }

                // [v8.3.1]: Check Crushing Blow trigger (dynamic cooldown when in melee range)
                if (hasCrushingBlow && !isChargingCrushingBlow) {
                    if (crushingBlowCooldown > 0) {
                        crushingBlowCooldown -= 5;
                    } else if (globalSkillCooldown <= 0) {
                        Player nearbyTarget = null;
                        for (Player p : getTargetPlayers()) {
                            if (p.isOnline() && !p.isDead() && p.getLocation().getWorld() == entity.getWorld()
                                    && p.getLocation().distanceSquared(entity.getLocation()) <= 49.0) { // 7 blocks
                                nearbyTarget = p;
                                break;
                            }
                        }

                        if (nearbyTarget != null) {
                            isChargingCrushingBlow = true;
                            double cbMinSec = getDoubleSetting("crushing-blow.cooldown-min", 14.0);
                            double cbMaxSec = getDoubleSetting("crushing-blow.cooldown-max", 19.0);
                            int cbMinTicks = (int)(cbMinSec * 20);
                            int cbMaxTicks = Math.max(cbMinTicks, (int)(cbMaxSec * 20));
                            crushingBlowCooldown = cbMinTicks + ThreadLocalRandom.current().nextInt(Math.max(1, cbMaxTicks - cbMinTicks + 1));
                            globalSkillCooldown = 60; // Pause skills during and after
                            vn.meowchan12.mobarenav2.boss.CrushingBlowMechanic.startWindup(plugin, arena, entity, displayName, bossConfigId, () -> {
                                isChargingCrushingBlow = false;
                            });
                        }
                    }
                }

                if (isChargingCrushingBlow) {
                    return; // Pause regular skills while charging heavy strike
                }

                for (String skillId : currentCooldowns.keySet()) {
                    int left = currentCooldowns.get(skillId);
                    if (left > 0) {
                        currentCooldowns.put(skillId, Math.max(0, left - 5));
                    } else if (globalSkillCooldown <= 0) { // [v6.6] Check global queue
                        BossSkillExecutor.castSkill(plugin, arena, entity, skillId);
                        currentCooldowns.put(skillId, maxCooldowns.get(skillId));

                        // Set global skill cooldown randomly between min and max seconds
                        double gMinSec = getDoubleSetting("global-skill-cooldown-min", 3.0);
                        double gMaxSec = getDoubleSetting("global-skill-cooldown-max", 7.0);
                        int gMinTicks = (int)(gMinSec * 20);
                        int gMaxTicks = Math.max(gMinTicks, (int)(gMaxSec * 20));
                        globalSkillCooldown = gMinTicks + ThreadLocalRandom.current().nextInt(Math.max(1, gMaxTicks - gMinTicks + 1));
                    }
                }

                // [v7.0] Elytra counter check (every 10 ticks = 0.5s for performance)
                elytraCheckCounter += 5;
                if (elytraCheckCounter >= 10) {
                    elytraCheckCounter = 0;
                    for (Player p : getTargetPlayers()) {
                        if (p.isGliding()) {
                            counterMeta.processElytraCounter(p, entity, arena);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    /**
     * [v7.0] Called when the boss dies. Distributes rewards based on damage ranking.
     */
    private void onBossDeath() {
        if (entity != null && entity.isDead() && plugin.getBossRewardManager() != null) {
            double maxHealth = 100.0;
            if (entity.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.MAX_HEALTH) != null) {
                maxHealth = entity.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.MAX_HEALTH).getValue();
            }
            plugin.getBossRewardManager().distributeRewards(bossUUID, bossConfigId, maxHealth);
        }

        // [v8.2.2 FIX]: Credit Boss Slain stat to killer and contributing players in the arena
        if (arena != null) {
            boolean anyCredited = false;
            if (entity != null && entity.getKiller() != null) {
                UUID killerUUID = entity.getKiller().getUniqueId();
                ArenaPlayerProfile prof = arena.getPlayerProfiles().get(killerUUID);
                if (prof != null && !entity.hasMetadata("MA_BOSS_CREDITED_" + killerUUID)) {
                    entity.setMetadata("MA_BOSS_CREDITED_" + killerUUID, new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                    prof.addBossKill();
                    anyCredited = true;
                }
            }

            if (plugin.getBossRewardManager() != null) {
                List<Map.Entry<UUID, Double>> ranking = plugin.getBossRewardManager().getDamageRanking(bossUUID);
                for (Map.Entry<UUID, Double> entry : ranking) {
                    if (entry.getValue() > 0) {
                        UUID pUUID = entry.getKey();
                        ArenaPlayerProfile prof = arena.getPlayerProfiles().get(pUUID);
                        if (prof != null && (entity == null || !entity.hasMetadata("MA_BOSS_CREDITED_" + pUUID))) {
                            if (entity != null) entity.setMetadata("MA_BOSS_CREDITED_" + pUUID, new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                            prof.addBossKill();
                            anyCredited = true;
                        }
                    }
                }
            }

            // Fallback: If no direct damage or killer detected (e.g., environmental/void death), credit all active players fighting this boss
            if (!anyCredited) {
                for (UUID pUUID : arena.getActivePlayers()) {
                    ArenaPlayerProfile prof = arena.getPlayerProfiles().get(pUUID);
                    if (prof != null && (entity == null || !entity.hasMetadata("MA_BOSS_CREDITED_" + pUUID))) {
                        if (entity != null) entity.setMetadata("MA_BOSS_CREDITED_" + pUUID, new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                        prof.addBossKill();
                    }
                }
            }
        }

        // [v8.4.0 RACE-CONDITION FIX]: Only flag bossDefeatedInWave while the arena is still actively
        // fighting THIS boss's wave and has NOT already entered the Upgrade Phase or moved to the next wave.
        // Prevents a delayed 5-tick boss heartbeat from leaking a false boss flag into the next normal wave!
        if (arena != null) {
            boolean sameActiveWave = arena.isRunning()
                    && arena.getCurrentWave() == this.spawnedWave
                    && !arena.isUpgradePhase();
            if (sameActiveWave) {
                arena.setBossDefeatedInWave(true);
                arena.broadcastMessage("§6§l[MobArenav2] §eA Boss has fallen! Clear the wave to claim your victory upgrades!");
                for (UUID uuid : arena.getActivePlayers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        p.sendTitle("§6§l✦ BOSS DOWN! ✦", "§eFinish the wave to claim victory upgrades!", 10, 40, 10);
                        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.2f);
                    }
                }
            }
        } else {
            for (Player p : getTargetPlayers()) {
                p.sendMessage("§6§l[MobArenav2] §eStandalone Boss " + displayName + " §ehas been defeated!");
                p.sendTitle("§6§l✦ BOSS DOWN! ✦", "§e" + displayName + " §ehas fallen!", 10, 40, 10);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.2f);
            }
        }
    }

    public void cleanup() {
        if (bossBar != null) {
            bossBar.removeAll();
        }
        // [v7.0] Prevent memory leak if boss despawns without dying
        if (plugin.getBossRewardManager() != null) {
            plugin.getBossRewardManager().stopTracking(bossUUID);
        }
    }

    // [v7.0] Getters
    public UUID getBossUUID() { return bossUUID; }
    public String getBossConfigId() { return bossConfigId; }
    public LivingEntity getEntity() { return entity; }

    // [v8.4.3 DYNAMIC ENGINE]: Configurable Boss AI helpers
    public ConfigurationSection getBossConfig() {
        if (plugin.getBossManager() == null || plugin.getBossManager().getBossesConfig() == null) return null;
        return plugin.getBossManager().getBossesConfig().getConfigurationSection(bossConfigId);
    }

    public ConfigurationSection getBossSettings() {
        if (plugin.getBossManager() == null || plugin.getBossManager().getBossesConfig() == null) return null;
        return plugin.getBossManager().getBossesConfig().getConfigurationSection("settings");
    }

    public double getDoubleSetting(String key, double def) {
        ConfigurationSection bossCfg = getBossConfig();
        if (bossCfg != null && bossCfg.contains(key)) return bossCfg.getDouble(key);
        ConfigurationSection settings = getBossSettings();
        if (settings != null && settings.contains(key)) return settings.getDouble(key);
        return def;
    }

    public int getIntSetting(String key, int def) {
        ConfigurationSection bossCfg = getBossConfig();
        if (bossCfg != null && bossCfg.contains(key)) return bossCfg.getInt(key);
        ConfigurationSection settings = getBossSettings();
        if (settings != null && settings.contains(key)) return settings.getInt(key);
        return def;
    }
}