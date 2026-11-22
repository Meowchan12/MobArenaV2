package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

/**
 * Interface nền tảng cho mọi kỹ năng của Boss trong MobArenav2.
 * Mỗi kỹ năng (BossSkill) sẽ là một class độc lập triển khai interface này,
 * tự đóng gói logic tính toán sát thương, hiệu ứng hạt, âm thanh và vật lý.
 */
public interface BossSkill {

    /**
     * Tên định danh của loại kỹ năng (khớp với trường 'type' trong bossskill.yml).
     * Ví dụ: "FREEZE", "AREA_DAMAGE", "METEOR_SHOWER", "SAITAMA_PUNCH", ...
     *
     * @return Chuỗi định danh loại kỹ năng (thường viết HOA)
     */
    String getType();

    /**
     * Thực thi kỹ năng khi Boss tung chiêu trong đấu trường hoặc sandbox ngoài đấu trường.
     *
     * @param plugin      Instance plugin chính
     * @param arena       Đấu trường đang diễn ra trận đấu (hoặc null nếu triệu hồi test ở ngoài)
     * @param boss        Thực thể Boss đang thi triển chiêu thức
     * @param skillConfig Phần cấu hình của kỹ năng tương ứng từ bossskill.yml
     */
    void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection skillConfig);

    /**
     * Phương thức tiện ích an toàn giúp lấy danh sách người chơi mục tiêu trong bán kính,
     * tự động thích ứng hoàn hảo cho cả trường hợp Boss trong Arena lẫn Boss triệu hồi độc lập ở ngoài.
     */
    default java.util.List<org.bukkit.entity.Player> getNearbyTargets(Arena arena, LivingEntity boss, double radius) {
        if (arena != null) {
            return arena.getPlayersAsObjects().stream()
                    .filter(p -> p != null && p.isOnline() && !arena.getSpectators().contains(p.getUniqueId()))
                    .filter(p -> boss.getWorld().equals(p.getWorld()) && p.getLocation().distanceSquared(boss.getLocation()) <= radius * radius)
                    .collect(java.util.stream.Collectors.toList());
        }
        if (boss != null && boss.getWorld() != null) {
            return new java.util.ArrayList<>(boss.getWorld().getNearbyPlayers(boss.getLocation(), radius));
        }
        return java.util.Collections.emptyList();
    }
}
