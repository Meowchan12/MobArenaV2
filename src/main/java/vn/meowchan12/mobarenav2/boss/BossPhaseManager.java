package vn.meowchan12.mobarenav2.boss;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.BossSkillExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * [v7.0] Manages multi-phase boss transitions.
 * Each phase is defined in bosses.yml and triggers at a specific HP threshold.
 * When a phase triggers, skills change and transition effects play.
 */
public class BossPhaseManager {

    private final MobArenav2 plugin;
    private final Arena arena;
    private final LivingEntity boss;
    private final List<PhaseData> phases;
    private int currentPhaseIndex = -1;

    public BossPhaseManager(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection bossConfig) {
        this.plugin = plugin;
        this.arena = arena;
        this.boss = boss;
        this.phases = new ArrayList<>();

        loadPhases(bossConfig);
    }

    /**
     * Loads phase definitions from the boss config.
     * Example config:
     * phases:
     *   phase_1:
     *     hp-threshold: 60  # Triggers at 60% HP
     *     skills:
     *       - "earthquake_smash"
     *       - "void_prison"
     *     on-enter-message: "&4&l[PHASE 2] %boss% roars in fury!"
     */
    private void loadPhases(ConfigurationSection bossConfig) {
        if (bossConfig == null || !bossConfig.contains("phases")) return;

        ConfigurationSection phasesSec = bossConfig.getConfigurationSection("phases");
        if (phasesSec == null) return;

        for (String phaseKey : phasesSec.getKeys(false)) {
            ConfigurationSection phaseCfg = phasesSec.getConfigurationSection(phaseKey);
            if (phaseCfg == null) continue;

            int threshold = phaseCfg.getInt("hp-threshold", 50);
            List<String> skills = phaseCfg.getStringList("skills");
            List<String> onEnterSkills = phaseCfg.getStringList("on-enter-skills");
            String message = phaseCfg.getString("on-enter-message", "");
            boolean godMode = phaseCfg.getBoolean("god-mode", false);
            int godModeDuration = phaseCfg.getInt("god-mode-duration", 0);

            phases.add(new PhaseData(phaseKey, threshold, skills, onEnterSkills, message, godMode, godModeDuration));
        }

        // Sort by threshold descending (highest HP threshold first)
        phases.sort(Comparator.comparingInt(PhaseData::threshold).reversed());
    }

    /**
     * Called every tick from ArenaBoss to check if a phase transition should occur.
     * Returns the current phase's skill list, or null if no phases are defined.
     */
    public List<String> checkPhaseTransition(double healthPercent) {
        if (phases.isEmpty()) return null;

        int startIndex = Math.max(0, currentPhaseIndex + 1);
        for (int i = startIndex; i < phases.size(); i++) {
            PhaseData phase = phases.get(i);
            if (healthPercent <= phase.threshold()) {
                currentPhaseIndex = i;
                onPhaseEnter(phase);
                return phase.skills();
            }
        }

        // Return current phase skills
        if (currentPhaseIndex >= 0 && currentPhaseIndex < phases.size()) {
            return phases.get(currentPhaseIndex).skills();
        }
        return null;
    }

    /**
     * Executes transition effects when entering a new phase.
     */
    private void onPhaseEnter(PhaseData phase) {
        String bossName = boss.getCustomName() != null ? boss.getCustomName() : "Boss";

        // Broadcast message
        if (!phase.message().isEmpty()) {
            String msg = phase.message().replace("%boss%", bossName).replace("&", "§");
            arena.broadcastMessage(msg);
        }

        // Play transition effects
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
        boss.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, boss.getLocation().add(0, 1, 0), 3);
        boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation().add(0, 1, 0), 100, 2, 2, 2, 0.1);

        // Execute on-enter skills (one-time skills fired on phase transition)
        for (String skillId : phase.onEnterSkills()) {
            BossSkillExecutor.castSkill(plugin, arena, boss, skillId);
        }

        // Apply god mode if configured
        if (phase.godMode() && phase.godModeDuration() > 0) {
            org.bukkit.NamespacedKey godKey = new org.bukkit.NamespacedKey(plugin, "ma_invulnerable");
            long godExpiry = System.currentTimeMillis() + (phase.godModeDuration() * 1000L);
            boss.getPersistentDataContainer().set(godKey, org.bukkit.persistence.PersistentDataType.LONG, godExpiry);
            boss.setGlowing(true);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (boss.isValid() && !boss.isDead()) {
                    boss.setGlowing(false);
                    boss.getPersistentDataContainer().remove(godKey);
                    boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 1.0f);
                }
            }, phase.godModeDuration() * 20L);
        }

        plugin.getLogger().info("[MobArenav2] Boss '" + bossName + "' entered phase: " + phase.name());
    }

    /**
     * Returns true if phases are defined for this boss.
     */
    public boolean hasPhases() {
        return !phases.isEmpty();
    }

    /**
     * Gets the current phase index.
     */
    public int getCurrentPhaseIndex() {
        return currentPhaseIndex;
    }

    /**
     * Immutable record holding phase configuration data.
     */
    private record PhaseData(
        String name,
        int threshold,
        List<String> skills,
        List<String> onEnterSkills,
        String message,
        boolean godMode,
        int godModeDuration
    ) {}
}
