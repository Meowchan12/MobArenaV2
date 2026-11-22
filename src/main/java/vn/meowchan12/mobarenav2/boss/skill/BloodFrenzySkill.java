package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;
import vn.meowchan12.mobarenav2.utils.ColorUtils;

/**
 * [v8.3.0 TACTICAL BOSS SKILL]: BLOOD_FRENZY (Execute Phase Mechanic)
 * - Triggers when Boss HP drops below 30% or when cast directly.
 * - Boss gains Speed II and unholy lifesteal (restores 1.0% Max HP on every basic attack landed).
 * - Creates a race against the clock to burst the Boss down.
 */
public class BloodFrenzySkill implements BossSkill {

    public static final String META_BLOOD_FRENZY = "MA_BLOOD_FRENZY";

    @Override
    public String getType() {
        return "BLOOD_FRENZY";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection skillConfig) {
        if (boss == null || !boss.isValid() || boss.isDead()) return;
        if (boss.hasMetadata(META_BLOOD_FRENZY)) return; // Already enraged

        double lifestealPercent = (skillConfig != null) ? skillConfig.getDouble("lifesteal-percent", 1.0) : 1.0;
        int speedAmp = (skillConfig != null) ? skillConfig.getInt("speed-amplifier", 1) : 1;
        int strAmp = (skillConfig != null) ? skillConfig.getInt("strength-amplifier", 1) : 1;

        boss.setMetadata(META_BLOOD_FRENZY, new FixedMetadataValue(plugin, lifestealPercent));

        // Buffs
        boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, speedAmp, false, false));
        boss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, strAmp, false, false));

        String bossName = boss.getCustomName() != null ? boss.getCustomName() : boss.getName();
        String alert = ColorUtils.colorize("&4&l☠ [BLOOD FRENZY] " + bossName + " &centered an unholy blood frenzy!\n" +
                "&cAttacks now heal 1.0% Max HP! &e&lBURST THE BOSS DOWN IMMEDIATELY!");

        if (arena != null) arena.broadcastMessage(alert);

        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.7f);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.8f, 0.5f);

        // Persistent blood aura effect loop
        new BukkitRunnable() {
            final Particle.DustOptions redDust = new Particle.DustOptions(Color.fromRGB(200, 0, 0), 1.6f);

            @Override
            public void run() {
                if (!boss.isValid() || boss.isDead() || !boss.hasMetadata(META_BLOOD_FRENZY)) {
                    cancel();
                    return;
                }

                Location loc = boss.getLocation().add(0, 1.0, 0);
                loc.getWorld().spawnParticle(Particle.DUST, loc, 6, 0.6, 0.8, 0.6, 0.05, redDust);
            }
        }.runTaskTimer(plugin, 4L, 4L);
    }
}
