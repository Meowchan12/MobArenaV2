package vn.meowchan12.mobarenav2.upgrade.handler;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;

import java.util.concurrent.ThreadLocalRandom;

/**
 * [v8.0.0 SUPER UPGRADE]: Hephaestus' Blessing (Phước Lành Thần Rèn)
 * Siêu nâng cấp quý hiếm (Mythic/Super) mở khóa từ Shop.
 * Khi tiêu diệt quái, có tỷ lệ (2% ở Lv.1 đến 30% ở Lv.5) rơi ra Mảnh Rèn Thần Bí.
 * Mảnh kim loại bay về phía người chơi và phục hồi độ bền cho TOÀN BỘ trang bị:
 * Giáp đang mặc, vũ khí chính/phụ và toàn bộ vật phẩm trong túi đồ!
 */
public class HephaestusBlessingUpgrade implements UpgradeHandler {

    private final MobArenav2 plugin;

    public HephaestusBlessingUpgrade(MobArenav2 plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getType() {
        return "HEPHAESTUS_BLESSING";
    }

    @Override
    public void onKill(Player player, LivingEntity victim, int level, Arena arena) {
        if (level <= 0 || player == null || !player.isOnline()) return;

        // Tỷ lệ kích hoạt: Lv.1=2%, Lv.2=7%, Lv.3=14%, Lv.4=22%, Lv.5=30%
        double chance = switch (level) {
            case 1 -> 0.02;
            case 2 -> 0.07;
            case 3 -> 0.14;
            case 4 -> 0.22;
            default -> 0.30;
        };

        if (ThreadLocalRandom.current().nextDouble() > chance) return;

        // Visual Mảnh Rèn Thần Thánh (Display Entity bay về phía người chơi)
        org.bukkit.Location spawnLoc = victim != null ? victim.getLocation().add(0, 0.8, 0) : player.getLocation();
        try {
            ItemDisplay display = spawnLoc.getWorld().spawn(spawnLoc, ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(Material.NETHERITE_SCRAP));
                d.setBillboard(Display.Billboard.CENTER);
                d.setGlowing(true);
            });

            new BukkitRunnable() {
                int tick = 0;
                @Override
                public void run() {
                    if (!player.isOnline() || display.isDead() || tick > 15) {
                        display.remove();
                        this.cancel();
                        return;
                    }
                    org.bukkit.Location targetLoc = player.getLocation().add(0, 1.0, 0);
                    Vector dir = targetLoc.toVector().subtract(display.getLocation().toVector());
                    if (dir.lengthSquared() > 0.1) {
                        display.teleport(display.getLocation().add(dir.normalize().multiply(0.65)));
                    }
                    display.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, display.getLocation(), 2, 0.1, 0.1, 0.1, 0.02);
                    tick++;
                }
            }.runTaskTimer(plugin, 1L, 1L);
        } catch (Exception ignored) {}

        // Âm thanh đe búa thần rèn & hiệu ứng hạt
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.8f);
        player.getWorld().spawnParticle(Particle.SCRAPE, player.getLocation().add(0, 1.2, 0), 15, 0.3, 0.4, 0.3, 0.05);

        // Hồi phục độ bền cho TOÀN BỘ trang bị: Giáp đang mặc, tay chính, tay phụ, kho đồ
        int repairPoints = 20;
        boolean repairedAnything = false;

        // 1. Giáp
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (repairItem(armor, repairPoints)) repairedAnything = true;
        }
        // 2. Tay chính & tay phụ
        if (repairItem(player.getInventory().getItemInMainHand(), repairPoints)) repairedAnything = true;
        if (repairItem(player.getInventory().getItemInOffHand(), repairPoints)) repairedAnything = true;
        // 3. Toàn bộ kho đồ cá nhân
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (repairItem(item, repairPoints)) repairedAnything = true;
        }

        // Actionbar thông báo & ghi nhận tiến trình
        player.sendActionBar(vn.meowchan12.mobarenav2.utils.ColorUtils.toAdventure("&6&l⚒ Hephaestus' Blessing: All equipment durability repaired! ⚒"));

        ArenaPlayerProfile profile = arena != null ? arena.getPlayerProfiles().get(player.getUniqueId()) : null;
        if (profile != null) {
            profile.addHephaestusRepairs(1);
        }
    }

    private boolean repairItem(ItemStack item, int amount) {
        if (item == null || item.getType().isAir()) return false;
        if (item.getItemMeta() instanceof Damageable dmg) {
            if (dmg.hasDamage()) {
                int newDamage = Math.max(0, dmg.getDamage() - amount);
                dmg.setDamage(newDamage);
                item.setItemMeta(dmg);
                return true;
            }
        }
        return false;
    }
}
