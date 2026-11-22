package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [v8.2.0 NEW UPGRADE]: Phantom Decoy (STEALTH_TAUNT)
 * - Lối chơi: Đánh lừa AI quái vật, giải nguy khẩn cấp khi bị quây bắt.
 * - Khi nhận đòn đánh khiến máu tụt xuống dưới 30% (Cooldown: 30s đến 18s theo cấp độ):
 *   + Người chơi tàng hình hoàn toàn trong 2.5 giây (quái vật mất mục tiêu).
 *   + Triệu hồi 1 Phân Thân Ảo Ảnh tại chỗ để thu hút (Taunt) quái vật xung quanh.
 * - Tier Milestone (Lv.5): Khi phân thân biến mất, phát nổ khói mù gây 40.0 AoE Damage và Mù (Blindness) quái vật trong 4 giây!
 */
public class PhantomDecoyUpgrade implements UpgradeHandler {

    private final MobArenav2 plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public PhantomDecoyUpgrade(MobArenav2 plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getType() {
        return "STEALTH_TAUNT";
    }

    @Override
    public void onDefend(EntityDamageEvent event, Player player, LivingEntity damager, int level, Arena arena) {
        if (level <= 0 || player == null || !player.isOnline()) return;

        AttributeInstance maxHpAttr = player.getAttribute(AttributeUtils.MAX_HEALTH);
        double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 20.0;
        double remainingHp = player.getHealth() - event.getFinalDamage();

        // Kích hoạt khi máu sau khi dính đòn < 30% Max HP
        if (remainingHp <= (maxHp * 0.30) && remainingHp > 0) {
            long now = System.currentTimeMillis();
            long cd = Math.max(18000L, (33 - level * 3) * 1000L); // Lv.1: 30s, Lv.5: 18s
            long lastUsed = cooldowns.getOrDefault(player.getUniqueId(), 0L);
            if (now - lastUsed < cd) return;

            cooldowns.put(player.getUniqueId(), now);

            // 1. Tàng hình và mất mục tiêu
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 50, 0, false, false));
            player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.2f);
            player.sendMessage("§e§l[PHANTOM DECOY] §aStealth activated! A decoy phantom is distracting your foes!");

            Location decoyLoc = player.getLocation().clone();

            // 2. Triệu hồi ArmorStand phân thân ảo ảnh
            ArmorStand decoy = decoyLoc.getWorld().spawn(decoyLoc, ArmorStand.class, stand -> {
                stand.setCustomName("§e§lPHANTOM DECOY");
                stand.setCustomNameVisible(true);
                stand.setArms(true);
                stand.setBasePlate(false);
                stand.setGravity(false);
                stand.setInvulnerable(true);

                // Trang bị bản sao giáp người chơi
                ItemStack helmet = player.getInventory().getHelmet();
                stand.getEquipment().setHelmet(helmet != null ? helmet.clone() : new ItemStack(Material.IRON_HELMET));
                ItemStack chest = player.getInventory().getChestplate();
                stand.getEquipment().setChestplate(chest != null ? chest.clone() : new ItemStack(Material.IRON_CHESTPLATE));
                ItemStack mainHand = player.getInventory().getItemInMainHand();
                stand.getEquipment().setItemInMainHand(mainHand != null ? mainHand.clone() : new ItemStack(Material.IRON_SWORD));
            });

            // 3. Chuyển hướng quái vật xung quanh nhắm vào decoy
            for (Entity near : decoyLoc.getWorld().getNearbyEntities(decoyLoc, 10.0, 5.0, 10.0)) {
                if (near instanceof Mob mob && mob.hasMetadata("MA_MOB")) {
                    mob.setTarget(decoy);
                }
            }

            // 4. Hẹn giờ 50 ticks (2.5s) biến mất & phát nổ (Lv.5)
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (decoy.isValid()) {
                        if (level >= 5 && decoy.getWorld() != null) {
                            decoy.getWorld().spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, decoy.getLocation().add(0, 1.0, 0), 20, 0.4, 0.4, 0.4, 0.05);
                            decoy.getWorld().playSound(decoy.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.2f);

                            for (Entity near : decoy.getWorld().getNearbyEntities(decoy.getLocation(), 4.5, 3.0, 4.5)) {
                                if (near instanceof LivingEntity mob && mob.hasMetadata("MA_MOB") && !mob.isDead()) {
                                    mob.damage(40.0, player);
                                    mob.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
                                }
                            }
                        }
                        decoy.remove();
                    }
                }
            }.runTaskLater(plugin, 50L);
        }
    }

    @Override
    public void onReset(Player player, Arena arena) {
        if (player != null) {
            cooldowns.remove(player.getUniqueId());
        }
    }
}
