package vn.meowchan12.mobarenav2.service;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.MobFactory;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * [v8.1.0]: Master Mob Spawner Service.
 * Handles mob batch spawning, elite mutation affixes, swarm surges, and siege AI mechanics.
 */
public class MobSpawnerService {

    public enum WaveType {
        NORMAL,
        SWARM,
        ELITE_MUTATION,
        BOSS
    }

    private final MobArenav2 plugin;
    private final MobFactory mobFactory;

    public MobSpawnerService(MobArenav2 plugin) {
        this.plugin = plugin;
        this.mobFactory = new MobFactory(plugin);
    }

    public LivingEntity spawnMob(Arena arena, String mobConfigKey, Location spawnLoc, WaveType waveType) {
        if (arena == null || spawnLoc == null || mobConfigKey == null) return null;

        LivingEntity entity = mobFactory.spawnArenaMob(arena, mobConfigKey, spawnLoc, null, null);
        if (entity == null) return null;

        // Apply Wave-Specific Archetype Modifiers
        switch (waveType) {
            case SWARM -> applySwarmModifier(entity);
            case ELITE_MUTATION -> applyEliteAffixes(entity);
            default -> {}
        }

        return entity;
    }

    public void applyWaveArchetype(LivingEntity entity, Arena arena, String waveTypeStr) {
        if (entity == null) return;
        WaveType wt = parseWaveType(waveTypeStr);
        switch (wt) {
            case SWARM -> applySwarmModifier(entity);
            case ELITE_MUTATION -> applyEliteAffixes(entity);
            default -> {}
        }
    }

    public WaveType parseWaveType(String str) {
        if (str == null) return WaveType.NORMAL;
        String s = str.trim().toUpperCase();
        if (s.contains("SWARM")) return WaveType.SWARM;
        if (s.contains("ELITE")) return WaveType.ELITE_MUTATION;
        if (s.contains("BOSS")) return WaveType.BOSS;
        return WaveType.NORMAL;
    }

    private void applySwarmModifier(LivingEntity entity) {
        // Swarm mobs have slightly lower HP but high movement speed
        AttributeInstance maxHp = entity.getAttribute(AttributeUtils.MAX_HEALTH);
        if (maxHp != null) {
            maxHp.setBaseValue(Math.max(10.0, maxHp.getBaseValue() * 0.70));
            entity.setHealth(maxHp.getBaseValue());
        }
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
        entity.setMetadata("MA_SWARM", new FixedMetadataValue(plugin, true));
    }

    private void applyEliteAffixes(LivingEntity entity) {
        String[] possibleAffixes = {"SHIELDED", "SPEEDSTER", "EXPLOSIVE", "BERSERK", "CORRUPT"};
        String chosenAffix = possibleAffixes[ThreadLocalRandom.current().nextInt(possibleAffixes.length)];

        entity.setMetadata("MA_ELITE_AFFIX", new FixedMetadataValue(plugin, chosenAffix));
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "ma_elite_affix"), PersistentDataType.STRING, chosenAffix);

        // Customize according to affix
        switch (chosenAffix) {
            case "SHIELDED" -> {
                entity.setCustomName("§b[Shielded] §f" + (entity.getCustomName() != null ? entity.getCustomName() : entity.getName()));
                entity.setCustomNameVisible(true);
                entity.setAbsorptionAmount(20.0);
            }
            case "SPEEDSTER" -> {
                entity.setCustomName("§e[Speedster] §f" + (entity.getCustomName() != null ? entity.getCustomName() : entity.getName()));
                entity.setCustomNameVisible(true);
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2, false, true));
            }
            case "EXPLOSIVE" -> {
                entity.setCustomName("§c[Explosive] §f" + (entity.getCustomName() != null ? entity.getCustomName() : entity.getName()));
                entity.setCustomNameVisible(true);
            }
            case "BERSERK" -> {
                entity.setCustomName("§4[Berserk] §f" + (entity.getCustomName() != null ? entity.getCustomName() : entity.getName()));
                entity.setCustomNameVisible(true);
                entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1, false, true));
            }
            case "CORRUPT" -> {
                entity.setCustomName("§5[Corrupt] §f" + (entity.getCustomName() != null ? entity.getCustomName() : entity.getName()));
                entity.setCustomNameVisible(true);
            }
        }
    }
}
