package vn.meowchan12.mobarenav2.upgrade;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.handler.*;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bộ quản lý và điều phối trung tâm cho toàn bộ Roguelike Upgrades trong MobArenav2.
 * Cho phép tra cứu O(1), đăng ký linh hoạt theo Strategy Pattern và điều phối sự kiện
 * tới từng UpgradeHandler tương ứng một cách an toàn, độc lập.
 */
public class UpgradeRegistry {

    private final MobArenav2 plugin;
    private final Map<String, UpgradeHandler> handlers = new ConcurrentHashMap<>();

    // Anti-recursion thread guards to prevent cascading proc loops (e.g. Cleave -> Attack -> Cleave)
    private static final ThreadLocal<Boolean> IS_DISPATCHING_ATTACK = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> IS_DISPATCHING_DEFEND = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> IS_DISPATCHING_KILL = ThreadLocal.withInitial(() -> false);

    /**
     * Checks if any upgrade handler is actively executing on the current thread.
     * Prevents secondary proc damage from triggering further offensive or defensive upgrades.
     */
    public static boolean isDispatchingAny() {
        return IS_DISPATCHING_ATTACK.get() || IS_DISPATCHING_DEFEND.get() || IS_DISPATCHING_KILL.get();
    }

    public UpgradeRegistry(MobArenav2 plugin) {
        this.plugin = plugin;
        registerDefaultHandlers();
    }

    /**
     * Đăng ký toàn bộ 25 UpgradeHandler mặc định của hệ thống.
     */
    public void registerDefaultHandlers() {
        registerHandler(new WarriorMightUpgrade());
        registerHandler(new CriticalStrikeUpgrade());
        registerHandler(new AdrenalineRushUpgrade());
        registerHandler(new TitanHeartUpgrade());
        registerHandler(new IronSkinUpgrade());
        registerHandler(new PhantomBarrierUpgrade());
        registerHandler(new VampiricStrikeUpgrade());
        registerHandler(new SoulHarvesterUpgrade());
        registerHandler(new SwiftWindUpgrade());
        registerHandler(new MidasTouchUpgrade());
        registerHandler(new BerserkersPactUpgrade());
        registerHandler(new GamblersDiceUpgrade());
        registerHandler(new InstantHealUpgrade());
        registerHandler(new BloodlustUpgrade());
        registerHandler(new SecondWindUpgrade());
        registerHandler(new ThornsAuraUpgrade());
        registerHandler(new ElementalInfusionUpgrade());
        registerHandler(new ArmorPiercingUpgrade());
        registerHandler(new SoulOrbUpgrade());
        registerHandler(new SuperExecutionerUpgrade());
        registerHandler(new SuperAegisUpgrade());
        registerHandler(new SuperOverclockUpgrade());
        registerHandler(new SuperNovaUpgrade());
        registerHandler(new SlaughterhouseEngineUpgrade());
        registerHandler(new MirrorOfRetributionUpgrade());
        registerHandler(new HephaestusBlessingUpgrade(plugin));
        registerHandler(new ExtendedReachUpgrade());
        registerHandler(new PredatorEyeUpgrade());
        registerHandler(new AlchemicalQuiverUpgrade());
        registerHandler(new GravitySingularityUpgrade(plugin));
        registerHandler(new PhantomDecoyUpgrade(plugin));
        registerHandler(new SoulTetherUpgrade());
        registerHandler(new PotionEffectUpgrade());
        registerHandler(new CommandUpgrade());

        // Aliases hỗ trợ tương thích ngược và cấu hình linh hoạt
        if (hasHandler("CRITICAL_CHANCE")) handlers.put("CRITICAL_STRIKE", getHandler("CRITICAL_CHANCE"));
        if (hasHandler("ECO_MULTIPLIER")) handlers.put("MIDAS_TOUCH", getHandler("ECO_MULTIPLIER"));
        if (hasHandler("GLASS_CANNON")) handlers.put("BERSERKERS_PACT", getHandler("GLASS_CANNON"));
        if (hasHandler("MAX_HEALTH")) handlers.put("TITAN_HEART", getHandler("MAX_HEALTH"));
        if (hasHandler("DAMAGE_REDUCTION")) handlers.put("IRON_SKIN", getHandler("DAMAGE_REDUCTION"));
        if (hasHandler("WAVE_SHIELD")) handlers.put("PHANTOM_BARRIER", getHandler("WAVE_SHIELD"));
        if (hasHandler("THORNS_AURA")) {
            handlers.put("SPIKE_RETALIATION", getHandler("THORNS_AURA"));
            handlers.put("QUILL_BURST", getHandler("THORNS_AURA"));
        }
        if (hasHandler("ELEMENTAL_INFUSION")) handlers.put("ELEMENTAL_STRIKE", getHandler("ELEMENTAL_INFUSION"));
        if (hasHandler("ARMOR_PIERCING")) handlers.put("ARMOR_SHREDDER", getHandler("ARMOR_PIERCING"));
        if (hasHandler("HASTE_EFFECT")) {
            handlers.put("FRENZY_STRIKES", getHandler("HASTE_EFFECT"));
            handlers.put("ATTACK_SPEED_BOOST", getHandler("HASTE_EFFECT"));
            handlers.put("ADRENALINE_RUSH", getHandler("HASTE_EFFECT"));
        }
        if (hasHandler("SOUL_ORB")) {
            handlers.put("SOUL_MAGNETISM", getHandler("SOUL_ORB"));
            handlers.put("SOUL_COLLECTOR", getHandler("SOUL_ORB"));
        }
        if (hasHandler("HEAL_ON_KILL")) {
            handlers.put("SOUL_HARVESTER", getHandler("HEAL_ON_KILL"));
        }
        if (hasHandler("SPEED_BOOST")) {
            handlers.put("SWIFT_WIND", getHandler("SPEED_BOOST"));
        }
        if (hasHandler("SUPER_KILL_SCALING")) {
            handlers.put("SLAUGHTERHOUSE_ENGINE", getHandler("SUPER_KILL_SCALING"));
            handlers.put("SUPER_SLAUGHTERHOUSE", getHandler("SUPER_KILL_SCALING"));
        }
        if (hasHandler("PREDATOR_EYE")) handlers.put("EXECUTE_AMPLIFIER", getHandler("PREDATOR_EYE"));
        if (hasHandler("BOW_POTION_INFUSION")) handlers.put("ALCHEMICAL_QUIVER", getHandler("BOW_POTION_INFUSION"));
        if (hasHandler("CROWD_CONTROL_VORTEX")) handlers.put("GRAVITY_SINGULARITY", getHandler("CROWD_CONTROL_VORTEX"));
        if (hasHandler("STEALTH_TAUNT")) handlers.put("PHANTOM_DECOY", getHandler("STEALTH_TAUNT"));
        if (hasHandler("DAMAGE_SHARING_LINK")) handlers.put("SOUL_TETHER", getHandler("DAMAGE_SHARING_LINK"));
        if (hasHandler("EXTENDED_REACH")) {
            handlers.put("ATTACK_RANGE", getHandler("EXTENDED_REACH"));
            handlers.put("LONG_REACH", getHandler("EXTENDED_REACH"));
        }
        if (hasHandler("SUPER_TRUE_REFLECT")) {
            handlers.put("MIRROR_OF_RETRIBUTION", getHandler("SUPER_TRUE_REFLECT"));
            handlers.put("SUPER_RETRIBUTION", getHandler("SUPER_TRUE_REFLECT"));
        }
        if (hasHandler("SUPER_EXECUTE")) {
            handlers.put("SUPER_EXECUTIONER", getHandler("SUPER_EXECUTE"));
        }
        if (hasHandler("SUPER_REVIVE")) {
            handlers.put("SUPER_AEGIS", getHandler("SUPER_REVIVE"));
        }
        if (hasHandler("SUPER_AUTO_STRIKE")) {
            handlers.put("SUPER_NOVA", getHandler("SUPER_AUTO_STRIKE"));
        }
        if (hasHandler("HEPHAESTUS_BLESSING")) {
            handlers.put("SUPER_HEPHAESTUS", getHandler("HEPHAESTUS_BLESSING"));
            handlers.put("HEPHAESTUS", getHandler("HEPHAESTUS_BLESSING"));
        }
    }

    /**
     * Đăng ký một UpgradeHandler mới vào hệ thống.
     *
     * @param handler Bộ xử lý nâng cấp cần đăng ký
     */
    public void registerHandler(UpgradeHandler handler) {
        if (handler == null || handler.getType() == null) return;
        handlers.put(handler.getType().toUpperCase(), handler);
    }

    /**
     * Lấy UpgradeHandler theo type.
     *
     * @param type Tên loại nâng cấp (không phân biệt hoa thường)
     * @return UpgradeHandler tương ứng hoặc null nếu chưa đăng ký
     */
    public UpgradeHandler getHandler(String type) {
        if (type == null) return null;
        return handlers.get(type.toUpperCase());
    }

    /**
     * Kiểm tra xem một loại nâng cấp đã được đăng ký hay chưa.
     *
     * @param type Tên loại nâng cấp
     * @return true nếu đã đăng ký
     */
    public boolean hasHandler(String type) {
        return type != null && handlers.containsKey(type.toUpperCase());
    }

    /**
     * Lấy UpgradeHandler theo type hoặc theo ID của cấu hình.
     *
     * @param data Dữ liệu nâng cấp
     * @return UpgradeHandler tương ứng hoặc null nếu chưa đăng ký
     */
    public UpgradeHandler resolveHandler(UpgradeData data) {
        if (data == null) return null;
        UpgradeHandler handler = getHandler(data.type);
        if (handler == null && data.id != null) {
            handler = getHandler(data.id);
        }
        return handler;
    }

    /**
     * Điều phối sự kiện khi người chơi chọn hoặc thăng cấp nâng cấp trong GUI.
     *
     * @param player   Người chơi nhận nâng cấp
     * @param newLevel Cấp độ mới
     * @param data     Dữ liệu nâng cấp
     * @param arena    Đấu trường hiện tại
     */
    public void dispatchApply(Player player, int newLevel, UpgradeData data, Arena arena) {
        if (player == null || data == null || arena == null) return;
        UpgradeHandler handler = resolveHandler(data);
        if (handler != null) {
            try {
                handler.onApply(player, newLevel, data, arena);
            } catch (Exception e) {
                plugin.getLogger().severe("[MobArenav2] Error applying upgrade " + data.id + " (" + data.type + "): " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Điều phối sự kiện bắt đầu Wave mới cho người chơi.
     * Duyệt qua các nâng cấp mà người chơi đang sở hữu trong hồ sơ và gọi onWaveStart.
     *
     * @param player Người chơi
     * @param arena  Đấu trường hiện tại
     */
    public void dispatchWaveStart(Player player, Arena arena) {
        if (player == null || arena == null) return;
        ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());
        if (profile == null) return;

        for (Map.Entry<String, Integer> entry : profile.getOwnedUpgrades().entrySet()) {
            String upgradeId = entry.getKey();
            int level = entry.getValue();
            if (level <= 0) continue;

            UpgradeData data = plugin.getUpgradeManager().getUpgradeData(upgradeId);
            UpgradeHandler handler = (data != null) ? resolveHandler(data) : getHandler(upgradeId);
            if (handler != null) {
                try {
                    handler.onWaveStart(player, level, arena, data);
                } catch (Exception e) {
                    plugin.getLogger().warning("[MobArenav2] Error in onWaveStart for " + (data != null ? data.type : upgradeId) + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Điều phối sự kiện người chơi gây sát thương lên quái vật hoặc Boss.
     *
     * @param event  Sự kiện EntityDamageByEntityEvent
     * @param player Người chơi tấn công
     * @param victim Quái vật hoặc Boss bị tấn công
     * @param arena  Đấu trường hiện tại
     */
    public void dispatchAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, Arena arena) {
        if (player == null || victim == null || arena == null) return;
        if (IS_DISPATCHING_ATTACK.get() || IS_DISPATCHING_DEFEND.get()) {
            return; // Anti-recursion: Procs cannot proc procs
        }

        IS_DISPATCHING_ATTACK.set(true);
        try {
            ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());
            if (profile == null) return;

            for (Map.Entry<String, Integer> entry : profile.getOwnedUpgrades().entrySet()) {
                String upgradeId = entry.getKey();
                int level = entry.getValue();
                if (level <= 0) continue;

                UpgradeData data = plugin.getUpgradeManager().getUpgradeData(upgradeId);
                UpgradeHandler handler = (data != null) ? resolveHandler(data) : getHandler(upgradeId);
                if (handler != null) {
                    try {
                        handler.onAttack(event, player, victim, level, arena, data);
                    } catch (Exception e) {
                        plugin.getLogger().warning("[MobArenav2] Error in onAttack for " + (data != null ? data.type : upgradeId) + ": " + e.getMessage());
                    }
                }
            }
        } finally {
            IS_DISPATCHING_ATTACK.set(false);
        }
    }

    /**
     * Điều phối sự kiện người chơi chịu đòn từ quái vật hoặc môi trường.
     *
     * @param event   Sự kiện EntityDamageEvent
     * @param player  Người chơi chịu đòn
     * @param damager Kẻ tấn công
     * @param arena   Đấu trường hiện tại
     */
    public void dispatchDefend(EntityDamageEvent event, Player player, LivingEntity damager, Arena arena) {
        if (player == null || arena == null) return;
        if (IS_DISPATCHING_DEFEND.get()) {
            return; // Anti-recursion: Defend procs cannot cascade
        }

        IS_DISPATCHING_DEFEND.set(true);
        try {
            ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());
            if (profile == null) return;

            for (Map.Entry<String, Integer> entry : profile.getOwnedUpgrades().entrySet()) {
                String upgradeId = entry.getKey();
                int level = entry.getValue();
                if (level <= 0) continue;

                UpgradeData data = plugin.getUpgradeManager().getUpgradeData(upgradeId);
                UpgradeHandler handler = (data != null) ? resolveHandler(data) : getHandler(upgradeId);
                if (handler != null) {
                    try {
                        handler.onDefend(event, player, damager, level, arena, data);
                    } catch (Exception e) {
                        plugin.getLogger().warning("[MobArenav2] Error in onDefend for " + (data != null ? data.type : upgradeId) + ": " + e.getMessage());
                    }
                }
            }
        } finally {
            IS_DISPATCHING_DEFEND.set(false);
        }
    }

    /**
     * Điều phối sự kiện người chơi tiêu diệt quái vật hoặc Boss.
     *
     * @param player Người chơi tiêu diệt quái
     * @param victim Quái vật vừa bị hạ gục
     * @param arena  Đấu trường hiện tại
     */
    public void dispatchKill(Player player, LivingEntity victim, Arena arena) {
        if (player == null || victim == null || arena == null) return;
        if (IS_DISPATCHING_KILL.get()) {
            return; // Anti-recursion: Kill procs cannot cascade
        }

        IS_DISPATCHING_KILL.set(true);
        try {
            ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());
            if (profile == null) return;

            for (Map.Entry<String, Integer> entry : profile.getOwnedUpgrades().entrySet()) {
                String upgradeId = entry.getKey();
                int level = entry.getValue();
                if (level <= 0) continue;

                UpgradeData data = plugin.getUpgradeManager().getUpgradeData(upgradeId);
                UpgradeHandler handler = (data != null) ? resolveHandler(data) : getHandler(upgradeId);
                if (handler != null) {
                    try {
                        handler.onKill(player, victim, level, arena, data);
                    } catch (Exception e) {
                        plugin.getLogger().warning("[MobArenav2] Error in onKill for " + (data != null ? data.type : upgradeId) + ": " + e.getMessage());
                    }
                }
            }
        } finally {
            IS_DISPATCHING_KILL.set(false);
        }
    }

    /**
     * Điều phối dọn dẹp sạch sẽ khi người chơi chết, chuyển thành spectator, rời trận hoặc kết thúc arena.
     *
     * @param player Người chơi cần hoàn trả trạng thái
     * @param arena  Đấu trường hiện tại
     */
    public void dispatchReset(Player player, Arena arena) {
        if (player == null) return;
        for (UpgradeHandler handler : new java.util.HashSet<>(handlers.values())) {
            try {
                handler.onReset(player, arena);
            } catch (Exception e) {
                plugin.getLogger().warning("[MobArenav2] Error resetting upgrade handler " + handler.getType() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Lấy toàn bộ các UpgradeHandler đã đăng ký.
     *
     * @return Collection các UpgradeHandler
     */
    public Collection<UpgradeHandler> getRegisteredHandlers() {
        return handlers.values();
    }

    /**
     * Xóa toàn bộ handlers.
     */
    public void clear() {
        handlers.clear();
    }
}
