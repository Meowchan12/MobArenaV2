package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Nhốt người chơi vào lồng kết giới Barrier và rút máu theo thời gian.
 */
public class VoidPrisonSkill implements BossSkill {

    @Override
    public String getType() {
        return "VOID_PRISON";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        int vpDuration = config.getInt("duration", 5);
        double vpDamage = config.getDouble("damage", 8.0);
        List<String> vpEffects = config.getStringList("effects");

        List<Player> vpTargets = new ArrayList<>(arena.getPlayersAsObjects());
        if (!vpTargets.isEmpty()) {
            Player victim = vpTargets.get(ThreadLocalRandom.current().nextInt(vpTargets.size()));
            Location vLoc = victim.getLocation().clone();

            victim.sendMessage("§5§l[VOID PRISON] §dYou are trapped! Break free or wait " + vpDuration + "s!");
            victim.playSound(victim.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 1.5f, 0.5f);

            List<Location> barrierBlocks = new ArrayList<>();
            int[][] offsets = {{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,2,0},{1,1,0},{-1,1,0},{0,1,1},{0,1,-1}};
            for (int[] off : offsets) {
                Location bLoc = vLoc.clone().add(off[0], off[1], off[2]);
                if (bLoc.getBlock().getType().isAir()) {
                    bLoc.getBlock().setType(Material.BARRIER);
                    barrierBlocks.add(bLoc);
                }
            }

            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (ticks >= vpDuration * 20 || !victim.isOnline() || !arena.getActivePlayers().contains(victim.getUniqueId())) {
                        for (Location bl : barrierBlocks) {
                            if (bl.getBlock().getType() == Material.BARRIER) {
                                bl.getBlock().setType(Material.AIR);
                            }
                        }
                        this.cancel();
                        return;
                    }

                    if (ticks % 5 == 0) {
                        for (Location bl : barrierBlocks) {
                            bl.getWorld().spawnParticle(Particle.WITCH, bl.clone().add(0.5, 0.5, 0.5), 2, 0.2, 0.2, 0.2, 0);
                        }
                    }

                    if (ticks % 20 == 0 && ticks > 0) {
                        vn.meowchan12.mobarenav2.service.CombatDamageService.applyLayeredDamage(victim, (vpDamage / vpDuration), true, boss, arena, plugin);
                        victim.getWorld().spawnParticle(Particle.SCULK_SOUL, victim.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.02);
                    }
                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);

            for (String eff : vpEffects) {
                String[] data = eff.trim().split(":");
                if (data.length >= 2) {
                    try {
                        PotionEffectType pType = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(data[0].toLowerCase()));
                        if (pType != null) {
                            victim.addPotionEffect(new PotionEffect(pType, Integer.parseInt(data[1]), data.length > 2 ? Integer.parseInt(data[2]) : 0));
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }
}
