package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bộ quản lý và điều phối trung tâm cho toàn bộ kỹ năng của Boss trong MobArenav2.
 * Cho phép tra cứu O(1), đăng ký linh hoạt theo Strategy Pattern và điều phối thi triển kỹ năng
 * tới từng BossSkill tương ứng một cách độc lập và an toàn, xóa bỏ hoàn toàn switch-case monolithic.
 */
public class BossSkillRegistry {

    private final MobArenav2 plugin;
    private final Map<String, BossSkill> skills = new ConcurrentHashMap<>();

    public BossSkillRegistry(MobArenav2 plugin) {
        this.plugin = plugin;
        registerDefaultSkills();
    }

    /**
     * Tự động đăng ký toàn bộ kỹ năng Boss mặc định của plugin.
     */
    public void registerDefaultSkills() {
        registerSkill(new FreezeSkill());
        registerSkill(new AreaDamageSkill());
        registerSkill(new PullSkill());
        registerSkill(new LightningSkill());
        registerSkill(new InvulnerableSkill());
        registerSkill(new SuperArrowSkill());
        registerSkill(new UltimateArrowSkill());
        registerSkill(new KnockbackerSkill());
        registerSkill(new DeathMarkSkill());
        registerSkill(new MagneticPullSkill());
        registerSkill(new EarthquakeSmashSkill());
        registerSkill(new ToxicNovaSkill());
        registerSkill(new MinionCallSkill());
        registerSkill(new DashAndRangedSkill());
        registerSkill(new InvisTeleporterSkill());
        registerSkill(new SpammerBowSkill());
        registerSkill(new SaitamaPunchSkill());
        registerSkill(new LifestealAuraSkill());
        registerSkill(new SoulDrainSkill());
        registerSkill(new ShadowCloneSkill());
        registerSkill(new CursedGroundSkill());
        registerSkill(new BerserkerChargeSkill());
        registerSkill(new VoidPrisonSkill());
        registerSkill(new MeteorShowerSkill());
        registerSkill(new TeleportStrikeSkill());
        registerSkill(new EnrageSkill());
        registerSkill(new SummonSkill());
        registerSkill(new ShadowWarpSkill());
        registerSkill(new VertigoPulseSkill());
        registerSkill(new ChronoStasisSkill());
        registerSkill(new AbyssalTetherSkill());

        // [v8.3.0]: Tactical Boss Skills
        registerSkill(new ShieldBreakBurstSkill());
        registerSkill(new TelegraphedSlamSkill());
        registerSkill(new ChainOfFateSkill());
        registerSkill(new MortalGroundSkill());
        registerSkill(new BloodFrenzySkill());

        // Aliases hỗ trợ tương thích ngược cấu hình YAML
        skills.put("SHIELD_BREAK", getSkill("SHIELD_BREAK_BURST"));
        skills.put("DPS_CHECK", getSkill("SHIELD_BREAK_BURST"));
        skills.put("RED_ZONE_SLAM", getSkill("TELEGRAPHED_SLAM"));
        skills.put("CHAINS", getSkill("CHAIN_OF_FATE"));
        skills.put("MORTAL_ZONE", getSkill("MORTAL_GROUND"));
        skills.put("TELEPORT", getSkill("SHADOW_WARP"));
        skills.put("WARP", getSkill("SHADOW_WARP"));
        skills.put("CONFUSION_PULSE", getSkill("VERTIGO_PULSE"));
        skills.put("TIME_WARP", getSkill("CHRONO_STASIS"));
        skills.put("SOUL_TETHER", getSkill("ABYSSAL_TETHER"));
        if (hasSkill("FREEZE")) {
            skills.put("BOSS_FREEZE", getSkill("FREEZE"));
            skills.put("FREEZE_AURA", getSkill("FREEZE"));
        }
        if (hasSkill("LIGHTNING")) {
            skills.put("LIGHTNING_STRIKE", getSkill("LIGHTNING"));
        }
        if (hasSkill("PULL")) {
            skills.put("PULL_PLAYERS", getSkill("PULL"));
            skills.put("GRAVITY_VOID", getSkill("PULL"));
        }
        if (hasSkill("AREA_DAMAGE")) {
            skills.put("EARTHQUAKE", getSkill("AREA_DAMAGE"));
            skills.put("EARTHQUAKE_SLAM", getSkill("AREA_DAMAGE"));
        }
        if (hasSkill("INVULNERABLE")) {
            skills.put("GOD_MODE", getSkill("INVULNERABLE"));
            skills.put("ABSOLUTE_DEFENSE", getSkill("INVULNERABLE"));
        }
    }

    /**
     * Đăng ký một BossSkill mới vào hệ thống.
     *
     * @param skill Kỹ năng Boss cần đăng ký
     */
    public void registerSkill(BossSkill skill) {
        if (skill == null || skill.getType() == null) return;
        skills.put(skill.getType().toUpperCase(), skill);
    }

    /**
     * Lấy BossSkill theo type.
     *
     * @param type Tên loại kỹ năng (không phân biệt hoa thường)
     * @return BossSkill tương ứng hoặc null nếu chưa đăng ký
     */
    public BossSkill getSkill(String type) {
        if (type == null) return null;
        return skills.get(type.toUpperCase());
    }

    /**
     * Kiểm tra xem một loại kỹ năng đã được đăng ký hay chưa.
     *
     * @param type Tên loại kỹ năng
     * @return true nếu đã có trong registry
     */
    public boolean hasSkill(String type) {
        return type != null && skills.containsKey(type.toUpperCase());
    }

    /**
     * Thực thi kỹ năng của Boss.
     * Tự động xử lý thông báo chiêu thức (custom message hoặc default message)
     * và chuyển giao cho BossSkill tương ứng thực hiện logic.
     *
     * @param arena       Đấu trường đang diễn ra trận đấu
     * @param boss        Thực thể Boss đang thi triển
     * @param skillId     ID của kỹ năng (tên key trong bossskill.yml)
     * @param skillConfig Cấu hình của kỹ năng từ bossskill.yml
     */
    public void executeSkill(Arena arena, LivingEntity boss, String skillId, ConfigurationSection skillConfig) {
        if (boss == null || boss.isDead() || (arena != null && !arena.isRunning()) || skillConfig == null) return;

        String type = skillConfig.getString("type", "").toUpperCase();
        String skillName = skillConfig.getString("display-name", skillId);
        String bossName = boss.getCustomName() != null ? boss.getCustomName() : "Boss";

        // 1. Phát thông báo chiêu thức (Chỉ broadcast chiêu thức Ultimate hoặc có custom message để tránh spam chat)
        boolean isUltimate = skillConfig.getBoolean("ultimate", false)
                || type.equalsIgnoreCase("BOSS_FREEZE")
                || type.equalsIgnoreCase("METEOR_SHOWER")
                || skillId.toLowerCase().contains("ultimate")
                || skillId.toLowerCase().contains("enrage");

        if (skillConfig.contains("message")) {
            String customMsg = skillConfig.getString("message");
            if (customMsg != null && !customMsg.isEmpty()) {
                String formatted = customMsg.replace("%boss%", bossName).replace("%skill%", skillName);
                String colored = ChatColor.translateAlternateColorCodes('&', formatted);
                if (arena != null) {
                    arena.broadcastMessage(colored);
                } else if (boss.getWorld() != null) {
                    for (Player p : boss.getWorld().getNearbyPlayers(boss.getLocation(), 40.0)) {
                        p.sendMessage(colored);
                    }
                }
            }
        } else if (isUltimate) {
            String ultMsg = "§4§l⚠ [BOSS ULTIMATE] §c" + bossName + " §eis casting §4" + skillName + "§e! TAKE COVER!";
            if (arena != null) {
                arena.broadcastMessage(ultMsg);
                for (Player p : arena.getPlayersAsObjects()) {
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.7f);
                }
            } else if (boss.getWorld() != null) {
                for (Player p : boss.getWorld().getNearbyPlayers(boss.getLocation(), 40.0)) {
                    p.sendMessage(ultMsg);
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.7f);
                }
            }
        }

        // 2. Tra cứu và thực thi kỹ năng qua Strategy
        BossSkill skill = getSkill(type);
        if (skill != null) {
            try {
                skill.execute(plugin, arena, boss, skillConfig);
            } catch (Exception e) {
                plugin.getLogger().severe("[MobArenav2] Error executing boss skill '" + skillId + "' (" + type + "): " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            plugin.getLogger().warning("[MobArenav2] Unregistered Boss Skill type: '" + type + "' for skillId: " + skillId);
        }
    }

    /**
     * Lấy toàn bộ các BossSkill đã đăng ký.
     *
     * @return Danh sách BossSkill
     */
    public Collection<BossSkill> getRegisteredSkills() {
        return skills.values();
    }

    /**
     * Xóa toàn bộ kỹ năng đã đăng ký.
     */
    public void clear() {
        skills.clear();
    }
}
