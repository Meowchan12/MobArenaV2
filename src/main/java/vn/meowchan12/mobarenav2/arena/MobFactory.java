package vn.meowchan12.mobarenav2.arena;

import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.utils.ArenaMath;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MobFactory {

    private final MobArenav2 plugin;
    private final MobAttributeParser attributeParser;
    private final boolean hasMythicMobs;

    public MobFactory(MobArenav2 plugin) {
        this.plugin = plugin;
        this.attributeParser = new MobAttributeParser(plugin);
        this.hasMythicMobs = Bukkit.getPluginManager().getPlugin("MythicMobs") != null;
    }

    private String colorize(String text) {
        if (text == null) return null;
        Pattern pattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, ChatColor.of("#" + matcher.group(1)).toString());
        }
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', matcher.appendTail(buffer).toString());
    }

    public LivingEntity spawnArenaMob(Arena arena, String mobName, Location loc, ConfigurationSection mobConfig, ConfigurationSection waveConfig) {
        if (loc == null || loc.getWorld() == null) return null;

        // [FIX BUG #5]: Ép Load Chunk chứa tọa độ Spawn để đảm bảo Entity được khởi tạo 100% với đầy đủ dữ liệu
        if (!loc.getChunk().isLoaded()) {
            loc.getChunk().load(true);
        }

        Mob spawnedMob = null;
        boolean isCustomBoss = false;

        // =====================================================================
        // [FIX BUG #1]: CASE-INSENSITIVE BOSS KEY LOOKUP
        // Duyệt qua tất cả key trong bosses.yml và so sánh không phân biệt
        // hoa thường, tránh lỗi "Iron_Behemoth" vs "iron_behemoth"
        // =====================================================================
        ConfigurationSection bossesSec = plugin.getBossManager().getBossesConfig();
        ConfigurationSection bossConfig = null;
        String matchedBossKey = mobName;

        if (bossesSec != null) {
            for (String key : bossesSec.getKeys(false)) {
                if (key.equalsIgnoreCase(mobName)) {
                    bossConfig = bossesSec.getConfigurationSection(key);
                    matchedBossKey = key;
                    break;
                }
            }
        }

        if (bossConfig != null) {
            isCustomBoss = true;

            // =====================================================================
            // [v5.5 FIX]: SPAWN-RANDOM-PLAYER / SPAWN NEAR PLAYER FLOOR SEARCH
            // Scans active players (random if >= 2, single if 1) and spawns boss on safe floor
            // =====================================================================
            boolean spawnNearPlayer = bossConfig.getBoolean("spawn-random-player",
                                      bossConfig.getBoolean("spawn_random_player",
                                      bossConfig.getBoolean("spawn-near-player",
                                      bossConfig.getBoolean("spawn_near_player", false))));

            if (spawnNearPlayer && !arena.getPlayersAsObjects().isEmpty()) {
                List<Player> activePlayers = arena.getPlayersAsObjects();
                Player randomTarget = (activePlayers.size() == 1) ? activePlayers.get(0) :
                        activePlayers.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(activePlayers.size()));
                Location pLoc = randomTarget.getLocation();

                double offsetX = java.util.concurrent.ThreadLocalRandom.current().nextDouble(-4.0, 4.0);
                double offsetZ = java.util.concurrent.ThreadLocalRandom.current().nextDouble(-4.0, 4.0);
                Location searchLoc = pLoc.clone().add(offsetX, 0, offsetZ);

                // Search for safe floor block around player Y level (+2 down to -3)
                int safeY = pLoc.getBlockY();
                boolean foundFloor = false;
                for (int y = pLoc.getBlockY() + 2; y >= pLoc.getBlockY() - 3; y--) {
                    org.bukkit.block.Block b = searchLoc.getWorld().getBlockAt(searchLoc.getBlockX(), y, searchLoc.getBlockZ());
                    if (b.getType().isSolid()) {
                        safeY = y + 1;
                        foundFloor = true;
                        break;
                    }
                }

                if (!foundFloor) {
                    safeY = pLoc.getBlockY();
                }

                Location nearLoc = new Location(pLoc.getWorld(), searchLoc.getX(), safeY, searchLoc.getZ(), pLoc.getYaw(), pLoc.getPitch());

                if (nearLoc.getWorld() != null) {
                    loc = nearLoc;
                }
            }

            // Force load & lock chunk for Boss spawn
            if (loc.getWorld() != null) {
                loc.getChunk().addPluginChunkTicket(plugin);
                loc.getChunk().load(true);
            }

            String entityTypeStr = bossConfig.getString("type", "ZOMBIE").replace(" ", "_").toUpperCase();
            plugin.getLogger().info("[MobArenav2] Spawning Boss '" + matchedBossKey + "' (type: " + entityTypeStr + ") at " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());

            Entity spawnedEntity = null;
            try {
                EntityType type = EntityType.valueOf(entityTypeStr);
                spawnedEntity = loc.getWorld().spawnEntity(loc, type);

                if (!(spawnedEntity instanceof Mob bossEntity)) {
                    // Entity spawned but is NOT a Mob (shouldn't happen for combat mobs)
                    plugin.getLogger().warning("[MobArenav2] Boss '" + matchedBossKey + "' entity type " + entityTypeStr + " is not a Mob! Removing.");
                    if (spawnedEntity != null) spawnedEntity.remove();
                    spawnedEntity = null;
                } else {
                    spawnedMob = bossEntity;

                    // =====================================================================
                    // STEP 1: Tag this entity as a Boss with persistent data
                    // =====================================================================
                    org.bukkit.NamespacedKey bKey = new org.bukkit.NamespacedKey(plugin, "arena_boss_id");
                    bossEntity.getPersistentDataContainer().set(bKey, org.bukkit.persistence.PersistentDataType.STRING, matchedBossKey);

                    // =====================================================================
                    // STEP 2: Apply boss-specific attributes (HP, Damage, Equip, Effects)
                    // =====================================================================
                    try {
                        attributeParser.applyBossAttributes(bossEntity, bossConfig);
                    } catch (Exception attrEx) {
                        plugin.getLogger().warning("[MobArenav2] Failed to apply attributes to Boss '" + matchedBossKey + "': " + attrEx.getMessage());
                    }

                    // =====================================================================
                    // STEP 3: Scale HP based on player count
                    // =====================================================================
                    int playerCount = arena.getActivePlayers().size();
                    double baseHp = bossConfig.getDouble("HP",
                                    bossConfig.getDouble("hp",
                                    bossConfig.getDouble("max-hp", 100.0)));
                    double scaledHp = ArenaMath.calculateBossHealth(baseHp, 1.0, playerCount);

                    if (bossEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                        bossEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(scaledHp);
                        bossEntity.setHealth(Math.min(scaledHp, bossEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()));
                    }

                    // =====================================================================
                    // STEP 4: 1-tick delayed health sync (fixes Iron Golem cracked texture)
                    // =====================================================================
                    final Mob finalBossMob = bossEntity;
                    final double finalHp = scaledHp;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (finalBossMob.isValid() && !finalBossMob.isDead()) {
                            if (finalBossMob.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                                finalBossMob.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(finalHp);
                                finalBossMob.setHealth(Math.min(finalHp, finalBossMob.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()));
                            }
                        }
                    }, 1L);

                    // =====================================================================
                    // STEP 5: Display name, BossBar, Nametag
                    // =====================================================================
                    String dName = colorize(bossConfig.getString("Boss_displayname",
                                            bossConfig.getString("display-name",
                                            bossConfig.getString("displayname", matchedBossKey))));

                    boolean bBar = bossConfig.getBoolean("Bossbar",
                                   bossConfig.getBoolean("bossbar",
                                   bossConfig.getBoolean("show-bossbar", false)));

                    boolean nTag = bossConfig.getBoolean("nametagshow",
                                   bossConfig.getBoolean("nametag",
                                   bossConfig.getBoolean("show-nametag", true)));

                    List<String> skills = bossConfig.getStringList("skill");
                    if (skills.isEmpty()) skills = bossConfig.getStringList("skills");
                    List<String> enrageSkills = bossConfig.getStringList("enrage-skill");
                    if (enrageSkills.isEmpty()) enrageSkills = bossConfig.getStringList("enrage-skills");

                    // =====================================================================
                    // STEP 6: Create ArenaBoss (handles bossbar, skills, enrage, etc.)
                    // =====================================================================
                    new ArenaBoss(plugin, arena, bossEntity, dName, bBar, nTag, skills, enrageSkills);

                    plugin.getLogger().info("[MobArenav2] Boss '" + matchedBossKey + "' spawned successfully! HP=" + scaledHp + " DisplayName=" + dName + " BossBar=" + bBar);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("[MobArenav2] FAILED to spawn Boss '" + matchedBossKey + "' (type: " + entityTypeStr + "): " + e.getMessage());
                e.printStackTrace();
                // If entity was spawned but init failed, remove the orphaned entity
                if (spawnedEntity != null && spawnedEntity.isValid()) {
                    spawnedEntity.remove();
                    plugin.getLogger().warning("[MobArenav2] Removed orphaned boss entity to prevent vanilla mob.");
                }
                spawnedMob = null;
            }
        }

        if (!isCustomBoss && hasMythicMobs) {
            MythicMob mythicMob = MythicBukkit.inst().getMobManager().getMythicMob(mobName).orElse(null);
            if (mythicMob != null) {
                ActiveMob activeMob = mythicMob.spawn(BukkitAdapter.adapt(loc), 1);
                if (activeMob != null && activeMob.getEntity().getBukkitEntity() instanceof Mob m) {
                    spawnedMob = m;
                }
            }
        }

        if (!isCustomBoss && spawnedMob == null) {
            try {
                EntityType type = EntityType.valueOf(mobName.replace(" ", "_").toUpperCase());
                Entity entity = loc.getWorld().spawnEntity(loc, type);

                if (entity instanceof Mob m) {
                    spawnedMob = m;
                } else if (entity instanceof LivingEntity le) {
                    // Fallback for non-Mob LivingEntities
                    if (le instanceof Mob mobEnt) spawnedMob = mobEnt;
                } else if (entity != null) {
                    entity.remove();
                    plugin.getLogger().warning("[MobArenav2] Entity '" + mobName + "' is not a valid combat Mob! Removed.");
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[MobArenav2] Invalid mob type in config: " + mobName);
            }
        }

        if (spawnedMob != null) {
            // [FIX BUG #5]: Khóa tuyệt đối cơ chế "RemoveWhenFarAway" của Vanilla.
            // Quái sẽ KHÔNG BAO GIỜ tự biến mất kể cả khi người chơi chạy xa 100 block.
            spawnedMob.setRemoveWhenFarAway(false);
            spawnedMob.setPersistent(true);

            // Gán Target ban đầu cho người chơi gần nhất trong Arena
            if (!arena.getPlayersAsObjects().isEmpty()) {
                Player nearest = arena.getPlayersAsObjects().get(0);
                spawnedMob.setTarget(nearest);
            }

            // =====================================================================
            // [FIX CRITICAL BUG]: GÁN METADATA "MA_MOB" CHO TẤT CẢ MOB ARENA
            // Metadata này là CHÌA KHÓA để kích hoạt toàn bộ hệ thống
            // =====================================================================
            spawnedMob.setMetadata("MA_MOB", new FixedMetadataValue(plugin, arena.getName()));

            arena.getActiveMobs().add(spawnedMob);
            if (!isCustomBoss && mobConfig != null && waveConfig != null) {
                attributeParser.applyAttributes(spawnedMob, mobConfig, waveConfig);
            }
        }

        return spawnedMob;
    }
}