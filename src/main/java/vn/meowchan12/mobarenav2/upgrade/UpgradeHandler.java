package vn.meowchan12.mobarenav2.upgrade;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;

/**
 * Interface chuẩn cho mọi Roguelike Upgrade trong MobArenav2.
 * Mỗi Upgrade sẽ là một class độc lập triển khai interface này, tự quản lý
 * các mốc sự kiện trong vòng đời của người chơi mà không gây phân mảnh hay spaghetti code.
 */
public interface UpgradeHandler {

    /**
     * Tên định danh của loại Upgrade (khớp với trường 'type' trong upgrades.yml).
     * Ví dụ: "DAMAGE_MULTIPLIER", "CRITICAL_CHANCE", "WAVE_SHIELD", "LIFESTEAL", ...
     *
     * @return Chuỗi định danh loại nâng cấp (thường viết HOA)
     */
    String getType();

    /**
     * Kích hoạt khi người chơi click chọn nâng cấp hoặc nâng cấp thăng cấp trong GUI.
     * Dùng để cập nhật thuộc tính thực thể hoặc các buff tức thì.
     *
     * @param player   Người chơi nhận nâng cấp
     * @param newLevel Cấp độ mới của nâng cấp (1, 2, 3...)
     * @param data     Dữ liệu cấu hình của nâng cấp từ upgrades.yml
     * @param arena    Đấu trường hiện tại
     */
    default void onApply(Player player, int newLevel, UpgradeData data, Arena arena) {}

    /**
     * Kích hoạt khi một Wave mới bắt đầu trong đấu trường.
     * Dùng cho các nâng cấp hồi khiên, nạp tim vàng đầu wave (Phantom Barrier, Titan Heart Lv.5...).
     *
     * @param player Người chơi sở hữu nâng cấp
     * @param level  Cấp độ hiện tại của nâng cấp
     * @param arena  Đấu trường hiện tại
     */
    default void onWaveStart(Player player, int level, Arena arena) {}

    default void onWaveStart(Player player, int level, Arena arena, UpgradeData data) {
        onWaveStart(player, level, arena);
    }

    /**
     * Kích hoạt khi người chơi tấn công và gây sát thương lên quái vật hoặc Boss.
     * Dùng cho các nâng cấp tăng % sát thương, bạo kích, hút máu, bào giáp, shockwave...
     *
     * @param event  Sự kiện EntityDamageByEntityEvent
     * @param player Người chơi tấn công
     * @param victim Quái vật hoặc Boss bị tấn công
     * @param level  Cấp độ hiện tại của nâng cấp
     * @param arena  Đấu trường hiện tại
     */
    default void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena) {}

    default void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena, UpgradeData data) {
        onAttack(event, player, victim, level, arena);
    }

    /**
     * Kích hoạt khi người chơi nhận sát thương từ quái vật, Boss hoặc đạn bay.
     * Dùng cho các nâng cấp giảm sát thương, phản đòn giáp gai, khiên khẩn cấp (Second Wind, Iron Skin...).
     *
     * @param event   Sự kiện EntityDamageEvent
     * @param player  Người chơi chịu đòn
     * @param damager Kẻ tấn công (có thể là quái vật hoặc null nếu sát thương môi trường)
     * @param level   Cấp độ hiện tại của nâng cấp
     * @param arena   Đấu trường hiện tại
     */
    default void onDefend(EntityDamageEvent event, Player player, LivingEntity damager, int level, Arena arena) {}

    default void onDefend(EntityDamageEvent event, Player player, LivingEntity damager, int level, Arena arena, UpgradeData data) {
        onDefend(event, player, damager, level, arena);
    }

    /**
     * Kích hoạt khi người chơi tiêu diệt một quái vật hoặc Boss.
     * Dùng cho các nâng cấp hồi máu khi diệt quái, rơi linh hồn, tích stack cuồng nộ (Soul Harvester, Bloodlust, Soul Orb...).
     *
     * @param player Người chơi tiêu diệt quái
     * @param victim Quái vật vừa bị hạ gục
     * @param level  Cấp độ hiện tại của nâng cấp
     * @param arena  Đấu trường hiện tại
     */
    default void onKill(Player player, LivingEntity victim, int level, Arena arena) {}

    default void onKill(Player player, LivingEntity victim, int level, Arena arena, UpgradeData data) {
        onKill(player, victim, level, arena);
    }

    /**
     * Kích hoạt khi người chơi chết, chuyển sang Spectator, gõ /ma leave, hoặc khi đấu trường kết thúc.
     * Dùng để dọn dẹp sạch sẽ toàn bộ tasks lặp, huỷ buff tạm thời, hoàn trả thuộc tính Vanilla, chống rò rỉ (Zero Leakage).
     *
     * @param player Người chơi cần hoàn trả trạng thái
     * @param arena  Đấu trường hiện tại
     */
    default void onReset(Player player, Arena arena) {}
}
