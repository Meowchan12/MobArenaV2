package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;

import java.util.concurrent.ThreadLocalRandom;

/**
 * [v7.3.1 HOTFIX]: Midas Touch
 * Khắc phục triệt để lỗi Placebo. Nhân +30% -> +120% toàn bộ tiền thưởng Vault tích lũy.
 * Milestone Lv.4: 15% tỉ lệ rơi thỏi vàng chứa bonus $25 - $50 tiền mặt trực tiếp khi diệt quái.
 */
public class MidasTouchUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "ECO_MULTIPLIER";
    }

    @Override
    public void onKill(Player player, LivingEntity victim, int level, Arena arena) {
        if (level >= 4 && player != null && victim != null) {
            if (ThreadLocalRandom.current().nextDouble(100.0) <= 15.0) {
                double bonusCash = ThreadLocalRandom.current().nextInt(25, 51);
                MobArenav2 plugin = (arena != null && arena.getPlugin() != null) ? arena.getPlugin() : MobArenav2.getInstance();
                if (plugin != null && plugin.getEconomy() != null) {
                    plugin.getEconomy().depositPlayer(player, bonusCash);
                }
                victim.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, victim.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.05);
                player.playSound(victim.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.8f);
                player.sendActionBar(net.kyori.adventure.text.Component.text("§6§l[MIDAS GOLD] §f+$" + (int) bonusCash + " bonus cash!"));
            }
        }
    }

    public static double getMultiplier(int level) {
        if (level <= 0) return 1.0;
        return 1.0 + (level * 30.0 / 100.0);
    }
}
