package vn.meowchan12.mobarenav2.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;
import vn.meowchan12.mobarenav2.arena.MobFactory;
import vn.meowchan12.mobarenav2.arena.VoteGUI;
import vn.meowchan12.mobarenav2.utils.ArenaMath;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class WaveManager {

    private final MobArenav2 plugin;
    private final MobFactory mobFactory;

    public WaveManager(MobArenav2 plugin) {
        this.plugin = plugin;
        this.mobFactory = new MobFactory(plugin);
    }

    public void startArena(Arena arena) {
        arena.setRunning(true);
        arena.setMatchStartTime(System.currentTimeMillis());
        arena.setCurrentWave(0);
        arena.setVoting(false);
        arena.setEndlessMode(false);
        arena.getEndlessWaveQueue().clear();

        if (!arena.getActiveSupplies().isEmpty()) {
            for (Location oldLoc : arena.getActiveSupplies()) {
                Block left = oldLoc.getBlock();
                Block right = oldLoc.clone().add(1, 0, 0).getBlock();
                if (left.getState() instanceof Chest c) c.getInventory().clear();
                if (right.getState() instanceof Chest c) c.getInventory().clear();
                left.setType(Material.AIR);
                right.setType(Material.AIR);
            }
            arena.getActiveSupplies().clear();
        }

        arena.broadcastMessage("§c§lThe arena has started! Prepare yourself!");

        new BukkitRunnable() {
            int halfSecondTicks = 0;
            int waveActiveHalfSeconds = 0;
            double nextWaveDelay = 6.0;
            boolean isWaiting = true;

            int lastMobCount = -1;
            int ticksSinceLastKill = 0;
            int spawnCooldown = 0;

            String currentWaveType = "mob";
            boolean currentWaveIsBoss = false;
            double currentSupplyDuration = 30.0;

            int lightningTimer = 0;

            @Override
            public void run() {
                if (!arena.isRunning() || arena.getActivePlayers().isEmpty()) {
                    arena.endArena(plugin);
                    this.cancel();
                    return;
                }

                FileConfiguration config = arena.getConfig();

                if (arena.isVoting()) {
                    arena.setVoteTicks(arena.getVoteTicks() + 1);
                    if (arena.getVoteTicks() >= 60) {
                        concludeVote(arena);
                    }
                    for (UUID uuid : arena.getActivePlayers()) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) p.sendActionBar(vn.meowchan12.mobarenav2.utils.ColorUtils.toAdventure("&b⏳ Waiting for votes... &e&l" + (30 - (arena.getVoteTicks() / 2)) + "s"));
                    }
                    return;
                }

                arena.getActiveMobs().removeIf(mob -> mob == null || !mob.isValid() || mob.isDead());

                // --- STAGGERED SPAWN QUEUE PROCESSING ---
                if (!arena.getSpawnQueue().isEmpty()) {
                    int toSpawn = Math.min(5, arena.getSpawnQueue().size());
                    for (int i = 0; i < toSpawn; i++) {
                        Runnable task = arena.getSpawnQueue().poll();
                        if (task != null) task.run();
                    }
                }

                // --- WAVE STATE MACHINE (MUTUALLY EXCLUSIVE STATES) ---
                if (arena.isUpgradePhase()) {
                    // [STATE 1: UPGRADE SELECTION PHASE]
                    // Combat is completely paused; wait until all players select their cards or 30s timeout expires.
                    arena.incrementUpgradePhaseTicks();
                    boolean allFinished = true;
                    for (UUID uuid : arena.getActivePlayers()) {
                        ArenaPlayerProfile prof = arena.getPlayerProfiles().get(uuid);
                        if (prof != null && prof.getPendingUpgradeChoices() > 0) {
                            allFinished = false;
                            break;
                        }
                    }

                    if (allFinished || arena.getUpgradePhaseTicks() >= 60) {
                        arena.setUpgradePhase(false);
                        isWaiting = true;
                        halfSecondTicks = 0;
                        nextWaveDelay = 5.0; // 5-second preparation countdown before next wave spawns
                        arena.broadcastMessage("§aAll upgrades selected! Next wave in 5 seconds.");
                        for (UUID uuid : arena.getActivePlayers()) {
                            Player p = Bukkit.getPlayer(uuid);
                            if (p != null && p.isOnline()) {
                                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                            }
                        }
                    }
                } else if (isWaiting) {
                    // [STATE 2: NEXT WAVE COUNTDOWN]
                    if (halfSecondTicks >= nextWaveDelay * 2) {
                        isWaiting = false;
                        halfSecondTicks = 0;
                        waveActiveHalfSeconds = 0;

                        lightningTimer = 0;
                        lastMobCount = -1;
                        ticksSinceLastKill = 0;
                        spawnCooldown = 6;

                        arena.setCurrentWave(arena.getCurrentWave() + 1);
                        // [v8.4.0 RACE-CONDITION FIX]: Always clear stale boss flag before spawning a new wave
                        arena.setBossDefeatedInWave(false);

                        ConfigurationSection targetWave = arena.getWaveConfig(arena.getCurrentWave());
                        currentWaveType = (targetWave != null) ? targetWave.getString("wave_types", "mob").toLowerCase() : "mob";
                        currentWaveIsBoss = currentWaveType.equals("boss") || (targetWave != null && targetWave.getBoolean("is-boss", false));

                        if (currentWaveType.equals("supply") || currentWaveType.equals("bonus")) {
                            currentSupplyDuration = (targetWave != null) ? targetWave.getDouble("duration", 30.0) : 30.0;
                        }

                        spawnWave(arena, arena.getCurrentWave(), targetWave, currentWaveType);
                    }
                } else {
                    // [STATE 3: ACTIVE WAVE COMBAT]
                    waveActiveHalfSeconds++;
                    if (spawnCooldown > 0) spawnCooldown--;

                    boolean shouldAdvance = false;

                    if (!currentWaveType.equals("supply") && !currentWaveType.equals("bonus")) {
                        int currentMobCount = arena.getActiveMobs().size();

                        ConfigurationSection lightningSec = config.getConfigurationSection("settings.hardcore-mechanics.lightning-strike");
                        if (lightningSec == null) {
                            lightningSec = config.getConfigurationSection("hardcore-mechanics.lightning-strike");
                        }

                        boolean lightningEnabled = lightningSec != null && lightningSec.getBoolean("enabled", false);
                        int lightningInterval = (lightningSec != null ? lightningSec.getInt("interval", 15) : 15) * 2;

                        if (lightningEnabled) {
                            lightningTimer++;
                            if (lightningTimer >= lightningInterval) {
                                lightningTimer = 0;
                                triggerLightningStrike(arena, lightningSec);
                            }
                        }

                        if (lastMobCount == -1 || currentMobCount < lastMobCount) {
                            lastMobCount = currentMobCount;
                            ticksSinceLastKill = 0;
                        } else {
                            ticksSinceLastKill++;
                        }

                        if (ticksSinceLastKill == 120 && currentMobCount > 0) {
                            arena.broadcastMessage("§e[MobArenav2] The remaining monsters are hiding! They are now glowing.");
                            for (org.bukkit.entity.LivingEntity mob : arena.getActiveMobs()) {
                                if (mob != null && mob.isValid()) {
                                    mob.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
                                }
                            }
                        }

                        boolean requireClear = config.getBoolean("settings.wave-conditions.clear-mob-before-next-wave", true);
                        boolean requireClearBoss = config.getBoolean("settings.wave-conditions.clear-mob-before-next-wave-boss", true);
                        boolean forceClearBoss = config.getBoolean("settings.wave-conditions.clear-boss-before-next-wave", true);
                        double delayRaw = config.getDouble("settings.wave-conditions.next-wave", 6.0);

                        if (spawnCooldown == 0 && currentMobCount == 0) {
                            shouldAdvance = true;
                        }

                        if (!requireClear && waveActiveHalfSeconds >= delayRaw * 2) {
                            if (currentWaveIsBoss && forceClearBoss && currentMobCount > 0) {
                                shouldAdvance = false;
                            } else if (currentWaveIsBoss && requireClearBoss && currentMobCount > 0) {
                                shouldAdvance = false;
                            } else {
                                shouldAdvance = true;
                            }
                        }
                    } else {
                        if (waveActiveHalfSeconds >= currentSupplyDuration * 2) {
                            shouldAdvance = true;

                            if (!arena.getActiveSupplies().isEmpty()) {
                                for (Location loc : arena.getActiveSupplies()) {
                                    Block leftBlock = loc.getBlock();
                                    Block rightBlock = loc.clone().add(1, 0, 0).getBlock();

                                    if (leftBlock.getState() instanceof Chest chest) chest.getInventory().clear();
                                    if (rightBlock.getState() instanceof Chest chest) chest.getInventory().clear();

                                    leftBlock.setType(Material.AIR);
                                    rightBlock.setType(Material.AIR);
                                }
                                arena.getActiveSupplies().clear();
                                arena.broadcastMessage("§c[MobArenav2] The Supply Drop has vanished!");
                            }
                        }
                    }

                    // KHI WAVE KẾT THÚC VÀ CHUẨN BỊ CHUYỂN SANG WAVE MỚI
                    if (shouldAdvance) {
                        // [TỐI ƯU HÓA TẠI ĐÂY] Quét và dọn dẹp mũi tên còn kẹt trên sân
                        clearProjectiles(arena);
                        clearDroppedItems(arena);

                        distributeWaveRewards(arena, arena.getCurrentWave());
                        for (Player p : arena.getPlayersAsObjects()) {
                            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                        }

                        int maxWaves = config.getInt("settings.max-waves", 20);
                        if (arena.getCurrentWave() >= maxWaves && !arena.isEndlessMode()) {
                            startVote(arena);
                            return;
                        }

                        for (UUID u : arena.getActivePlayers()) {
                            ArenaPlayerProfile pr = arena.getPlayerProfiles().get(u);
                            if (pr != null) pr.resetWaveRerolls();
                        }

                        if (plugin.getCelebrationService() != null) {
                            plugin.getCelebrationService().celebrateWaveClear(arena, arena.getCurrentWave());
                        }

                        boolean wasBossWave = arena.hasBossDefeatedInWave() || currentWaveType.equals("boss") || currentWaveIsBoss;
                        arena.setBossDefeatedInWave(false);

                        boolean wasSwarmWave = !wasBossWave && currentWaveType.equals("swarm");
                        boolean isSupplyOrBonus = currentWaveType.equals("supply") || currentWaveType.equals("bonus");
                        boolean upgradeEnabled = plugin.getUpgradeManager().isUpgradeSystemEnabled(arena);
                        boolean isStandardUpgradeWave = upgradeEnabled && !isSupplyOrBonus
                                && plugin.getUpgradeManager().isUpgradeWave(arena, arena.getCurrentWave());
                        boolean stackWithInterval = plugin.getUpgradeManager().isStackWithInterval(arena);

                        int bossGrants = 0;
                        int swarmGrants = 0;
                        int normalGrants = 0;

                        if (upgradeEnabled && !isSupplyOrBonus) {
                            if (wasBossWave) {
                                bossGrants = plugin.getUpgradeManager().getWaveDraftRule(arena, vn.meowchan12.mobarenav2.upgrade.UpgradeDraftType.BOSS).grantCount;
                            } else if (wasSwarmWave) {
                                swarmGrants = plugin.getUpgradeManager().getWaveDraftRule(arena, vn.meowchan12.mobarenav2.upgrade.UpgradeDraftType.SWARM).grantCount;
                            }

                            if (isStandardUpgradeWave && ((!wasBossWave && !wasSwarmWave) || stackWithInterval)) {
                                normalGrants = plugin.getUpgradeManager().getWaveDraftRule(arena, vn.meowchan12.mobarenav2.upgrade.UpgradeDraftType.NORMAL).grantCount;
                            }
                        }

                        int grantUpgrades = bossGrants + swarmGrants + normalGrants;

                        boolean isFinalWave = arena.getCurrentWave() >= maxWaves && !arena.isEndlessMode();

                        // [v7.5.5 UPGRADE PHASE]: Do not count down if players have upgrade choices to claim!
                        if (grantUpgrades > 0 && !arena.isVoting()) {
                            arena.setUpgradePhase(true);
                            isWaiting = false; // Wave timer paused!
                            halfSecondTicks = 0;

                            String bannerTag;
                            if (bossGrants > 0) {
                                bannerTag = "&#FFAA00&l✦ BOSS WAVE " + arena.getCurrentWave() + " CLEARED! ✦";
                            } else if (swarmGrants > 0) {
                                bannerTag = "&#FF55FF&l✦ SWARM WAVE " + arena.getCurrentWave() + " CLEARED! ✦";
                            } else {
                                bannerTag = "&#00FFFF&l✦ WAVE " + arena.getCurrentWave() + " CLEARED! ✦";
                            }

                            net.md_5.bungee.api.chat.TextComponent waveMsg = new net.md_5.bungee.api.chat.TextComponent();
                            for (net.md_5.bungee.api.chat.BaseComponent c : net.md_5.bungee.api.chat.TextComponent.fromLegacyText(vn.meowchan12.mobarenav2.utils.ColorUtils.colorize(bannerTag + " &f✦ &e["))) {
                                waveMsg.addExtra(c);
                            }

                            for (net.md_5.bungee.api.chat.BaseComponent c : net.md_5.bungee.api.chat.TextComponent.fromLegacyText(vn.meowchan12.mobarenav2.utils.ColorUtils.colorize("&#FFAA00&l[CHOOSE BUFF (" + grantUpgrades + ")]"))) {
                                c.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/ma upgrade"));
                                c.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.ComponentBuilder("§a§lClick to open your Upgrade Selection GUI!").create()));
                                waveMsg.addExtra(c);
                            }

                            for (net.md_5.bungee.api.chat.BaseComponent c : net.md_5.bungee.api.chat.TextComponent.fromLegacyText(vn.meowchan12.mobarenav2.utils.ColorUtils.colorize(" &f| "))) {
                                waveMsg.addExtra(c);
                            }

                            for (net.md_5.bungee.api.chat.BaseComponent c : net.md_5.bungee.api.chat.TextComponent.fromLegacyText(vn.meowchan12.mobarenav2.utils.ColorUtils.colorize("&#55FF55&l[VIEW BUFFS]"))) {
                                c.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/ma myupgrades"));
                                c.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.ComponentBuilder("§b§lClick to inspect your active upgrades!").create()));
                                waveMsg.addExtra(c);
                            }

                            for (net.md_5.bungee.api.chat.BaseComponent c : net.md_5.bungee.api.chat.TextComponent.fromLegacyText(vn.meowchan12.mobarenav2.utils.ColorUtils.colorize("&e]"))) {
                                waveMsg.addExtra(c);
                            }

                            for (Player ap : arena.getPlayersAsObjects()) {
                                ap.spigot().sendMessage(waveMsg);
                            }

                            for (UUID uuid : arena.getActivePlayers()) {
                                ArenaPlayerProfile profile = arena.getPlayerProfiles().get(uuid);
                                if (profile != null) {
                                    if (bossGrants > 0) {
                                        profile.addPendingUpgradeChoices(bossGrants, vn.meowchan12.mobarenav2.upgrade.UpgradeDraftType.BOSS);
                                    }
                                    if (swarmGrants > 0) {
                                        profile.addPendingUpgradeChoices(swarmGrants, vn.meowchan12.mobarenav2.upgrade.UpgradeDraftType.SWARM);
                                    }
                                    if (normalGrants > 0) {
                                        profile.addPendingUpgradeChoices(normalGrants, vn.meowchan12.mobarenav2.upgrade.UpgradeDraftType.NORMAL);
                                    }
                                }
                                Player p = Bukkit.getPlayer(uuid);
                                if (p != null && p.isOnline()) {
                                    if (bossGrants > 0) {
                                        p.sendTitle("§6§l✦ BOSS WAVE CLEARED! ✦", "§eClaim " + grantUpgrades + " Victory Upgrade" + (grantUpgrades > 1 ? "s" : "") + "!", 10, 50, 15);
                                        p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1.0f);
                                    } else if (swarmGrants > 0) {
                                        p.sendTitle("§d§l✦ SWARM WAVE CLEARED! ✦", "§eClaim " + grantUpgrades + " Swarm Upgrade" + (grantUpgrades > 1 ? "s" : "") + "!", 10, 50, 15);
                                        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.2f, 1.2f);
                                    }
                                    vn.meowchan12.mobarenav2.arena.UpgradeGUI.open(p, plugin);
                                }
                            }
                        } else {
                            arena.setUpgradePhase(false);
                            isWaiting = true;
                            halfSecondTicks = 0;
                            nextWaveDelay = config.getDouble("settings.wave-conditions.next-wave", 6.0);
                            arena.broadcastMessage("§aWave cleared! Next wave in " + nextWaveDelay + " seconds.");
                        }
                    }
                }

                for (UUID uuid : arena.getPlayerProfiles().keySet()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        ArenaPlayerProfile profile = arena.getPlayerProfiles().get(uuid);

                        if (profile != null && profile.getScoreboard() != null) {
                            if (halfSecondTicks % 2 == 0) profile.getScoreboard().tick();
                            profile.getScoreboard().update(arena);
                        }

                        if (!arena.getSpectators().contains(uuid)) {
                            if (arena.isUpgradePhase()) {
                                p.sendActionBar(vn.meowchan12.mobarenav2.utils.ColorUtils.toAdventure("&b&l✦ UPGRADE PHASE ✦ &e&lChoose your buffs! Next wave paused..."));
                            } else if (isWaiting) {
                                double timeLeft = Math.max(0.0, nextWaveDelay - (halfSecondTicks / 2.0));
                                p.sendActionBar(vn.meowchan12.mobarenav2.utils.ColorUtils.toAdventure("&e&l⏳ Next wave in &f&l" + String.format("%.1f", timeLeft) + " &e&lseconds..."));
                            } else if (currentWaveType.equals("supply") || currentWaveType.equals("bonus")) {
                                double timeLeft = Math.max(0.0, currentSupplyDuration - (waveActiveHalfSeconds / 2.0));
                                p.sendActionBar(vn.meowchan12.mobarenav2.utils.ColorUtils.toAdventure("&b&l🎁 Loot the chest! Drops items in: &e&l" + String.format("%.1f", timeLeft) + "s"));

                                for (Location loc : arena.getActiveSupplies()) {
                                    if (loc != null) {
                                        for (double y = 0.5; y <= 15.0; y += 1.0) {
                                            loc.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, loc.clone().add(0.5, y, 0.5), 1, 0.1, 0.1, 0.1, 0.01);
                                            loc.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, loc.clone().add(1.5, y, 0.5), 1, 0.1, 0.1, 0.1, 0.01);
                                        }

                                        org.bukkit.Particle.DustOptions glowingBox = new org.bukkit.Particle.DustOptions(org.bukkit.Color.AQUA, 1.5F);
                                        double[] edgesX = {0.0, 2.0};
                                        double[] edgesYZ = {0.0, 1.0};
                                        for (double x : edgesX) {
                                            for (double y : edgesYZ) {
                                                for (double z : edgesYZ) {
                                                    loc.getWorld().spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(x, y, z), 2, glowingBox);
                                                }
                                            }
                                        }

                                        loc.getWorld().spawnParticle(org.bukkit.Particle.FLAME, loc.clone().add(1.0, 0.5, 0.5), 10, 0.5, 0.3, 0.3, 0.05);
                                    }
                                }
                            } else {
                                // [v8.2.0 ZERO-OVERHEAD COMBAT HUD]: Replaces redundant Mobs Remaining with Live Match Timer & DPS
                                int maxWaves = arena.getConfig() != null ? arena.getConfig().getInt("settings.max-waves", 20) : 20;
                                String waveStr = arena.isEndlessMode() ? (arena.getCurrentWave() + " &8(Endless)") : (arena.getCurrentWave() + "&7/&b" + maxWaves);
                                ArenaPlayerProfile prof = arena.getPlayerProfiles().get(p.getUniqueId());
                                int pKills = (prof != null) ? prof.getKills() : 0;
                                long elapsedSec = arena.getElapsedDurationSeconds();
                                int dps = (prof != null && elapsedSec > 0) ? (int)(prof.getDamageDealt() / elapsedSec) : 0;

                                String hud = String.format("&6⏱ %s  &8│  &eWave %s  &8│  &c⚔ %d Kills  &8│  &a💥 %,d DPS",
                                        arena.getElapsedTimeString(), waveStr, pKills, dps);
                                p.sendActionBar(vn.meowchan12.mobarenav2.utils.ColorUtils.toAdventure(hud));
                            }
                        }
                    }
                }

                if (isWaiting) halfSecondTicks++;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    // ==========================================
    // [v8.2.0 TỐI ƯU HÓA]: DỌN SẠCH MŨI TÊN & VẬT PHẨM (ArenaMemoryManager)
    // ==========================================
    private void clearProjectiles(Arena arena) {
        if (plugin.getArenaMemoryManager() != null) {
            plugin.getArenaMemoryManager().cleanupWaveLeftovers(arena);
            plugin.getArenaMemoryManager().purgeStaleMobs(arena);
            return;
        }
        Location p1 = arena.getP1();
        Location p2 = arena.getP2();
        if (p1 == null || p2 == null || p1.getWorld() == null) return;

        double minX = Math.min(p1.getX(), p2.getX());
        double minY = Math.min(p1.getY(), p2.getY());
        double minZ = Math.min(p1.getZ(), p2.getZ());
        double maxX = Math.max(p1.getX(), p2.getX());
        double maxY = Math.max(p1.getY(), p2.getY());
        double maxZ = Math.max(p1.getZ(), p2.getZ());

        double centerX = (minX + maxX) / 2.0;
        double centerY = (minY + maxY) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0;
        double rx = Math.max(1.0, (maxX - minX) / 2.0);
        double ry = Math.max(1.0, (maxY - minY) / 2.0);
        double rz = Math.max(1.0, (maxZ - minZ) / 2.0);
        Location center = new Location(p1.getWorld(), centerX, centerY, centerZ);

        for (org.bukkit.entity.Entity entity : p1.getWorld().getNearbyEntities(center, rx, ry, rz)) {
            if (entity instanceof org.bukkit.entity.Projectile) {
                entity.remove();
            }
        }
    }

    private void clearDroppedItems(Arena arena) {
        Location p1 = arena.getP1();
        Location p2 = arena.getP2();
        if (p1 == null || p2 == null || p1.getWorld() == null) return;

        double minX = Math.min(p1.getX(), p2.getX());
        double minY = Math.min(p1.getY(), p2.getY());
        double minZ = Math.min(p1.getZ(), p2.getZ());
        double maxX = Math.max(p1.getX(), p2.getX());
        double maxY = Math.max(p1.getY(), p2.getY());
        double maxZ = Math.max(p1.getZ(), p2.getZ());

        double centerX = (minX + maxX) / 2.0;
        double centerY = (minY + maxY) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0;
        double rx = Math.max(1.0, (maxX - minX) / 2.0);
        double ry = Math.max(1.0, (maxY - minY) / 2.0);
        double rz = Math.max(1.0, (maxZ - minZ) / 2.0);
        Location center = new Location(p1.getWorld(), centerX, centerY, centerZ);

        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            for (org.bukkit.entity.Entity entity : p1.getWorld().getNearbyEntities(center, rx, ry, rz)) {
                if (entity != null && entity.isValid() && entity instanceof org.bukkit.entity.Item) {
                    entity.remove();
                }
            }
        });
    }

    private void triggerLightningStrike(Arena arena, ConfigurationSection lightningSec) {
        Location p1 = arena.getP1();
        Location p2 = arena.getP2();

        List<Player> validTargets = new ArrayList<>();

        if (p1 != null && p2 != null) {
            double minX = Math.min(p1.getX(), p2.getX());
            double maxX = Math.max(p1.getX(), p2.getX());
            double minZ = Math.min(p1.getZ(), p2.getZ());
            double maxZ = Math.max(p1.getZ(), p2.getZ());

            for (Player p : arena.getPlayersAsObjects()) {
                Location loc = p.getLocation();
                if (loc.getX() >= minX && loc.getX() <= maxX && loc.getZ() >= minZ && loc.getZ() <= maxZ) {
                    validTargets.add(p);
                }
            }
        } else {
            validTargets.addAll(arena.getPlayersAsObjects());
        }

        if (validTargets.isEmpty() || lightningSec == null) return;

        int minStrikes = lightningSec.getInt("strikes-per-wave.min", 1);
        int maxStrikes = lightningSec.getInt("strikes-per-wave.max", 3);
        int strikes = ThreadLocalRandom.current().nextInt(minStrikes, maxStrikes + 1);

        double minDmg = lightningSec.getDouble("damage.min", 5.0);
        double maxDmg = lightningSec.getDouble("damage.max", 15.0);
        double explosionRadius = lightningSec.getDouble("damage.radius", 4.0);
        double radiusSquared = explosionRadius * explosionRadius;

        for (int i = 0; i < strikes; i++) {
            Player targetPlayer = validTargets.get(ThreadLocalRandom.current().nextInt(validTargets.size()));

            double silentSec = lightningSec.getDouble("phases.silent-aim", 1.0);
            double trackMinSec = lightningSec.getDouble("phases.tracking-min", 1.0);
            double trackMaxSec = lightningSec.getDouble("phases.tracking-max", 2.0);
            double lockSec = lightningSec.getDouble("phases.lock-on", 0.5);

            int silentScanTicks = (int) (silentSec * 20);
            int trackingTicks = ThreadLocalRandom.current().nextInt((int)(trackMinSec * 20), (int)(trackMaxSec * 20) + 1);
            int lockOnTicks = (int) (lockSec * 20);

            int totalTicks = silentScanTicks + trackingTicks + lockOnTicks;
            int startWarningTick = silentScanTicks;
            int startLockOnTick = silentScanTicks + trackingTicks;

            new BukkitRunnable() {
                int tick = 0;
                Location strikeLoc = targetPlayer.getLocation().clone();

                @Override
                public void run() {
                    if (!arena.isRunning() || !targetPlayer.isOnline()) {
                        this.cancel();
                        return;
                    }

                    Location currentLoc = targetPlayer.getLocation().clone();

                    if (tick < startWarningTick) {
                        strikeLoc = currentLoc;
                    }
                    else if (tick < startLockOnTick) {
                        strikeLoc = currentLoc;
                        drawCircle(strikeLoc, explosionRadius, org.bukkit.Particle.FLAME);

                        if (tick % 10 == 0) {
                            targetPlayer.sendActionBar(net.kyori.adventure.text.Component.text("§c⚠ YOU ARE TARGETED BY LIGHTNING STRIKE! KEEP MOVING! ⚠"));
                            targetPlayer.playSound(strikeLoc, org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.0f);
                        }
                    }
                    else if (tick < totalTicks) {
                        if (tick == startLockOnTick) {
                            targetPlayer.playSound(strikeLoc, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 2.0f);
                            targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 10, 1, false, false));
                        }
                        drawCircle(strikeLoc, explosionRadius, org.bukkit.Particle.SOUL_FIRE_FLAME);
                    }

                    if (tick >= totalTicks) {
                        strikeLoc.getWorld().strikeLightningEffect(strikeLoc);
                        double damage = ThreadLocalRandom.current().nextDouble(minDmg, maxDmg);

                        for (Player p : arena.getPlayersAsObjects()) {
                            if (p.getLocation().distanceSquared(strikeLoc) <= radiusSquared) {
                                double newHealth = p.getHealth() - damage;

                                if (newHealth <= 0) {
                                    plugin.getArenaManager().handlePlayerDeath(p, arena);
                                    p.sendMessage("§c⚡ You were struck down by the LIGHTNING Strike!");
                                } else {
                                    p.setHealth(newHealth);
                                    p.sendMessage("§c⚡ You were hit by the LIGHTNING Strike for " + String.format("%.1f", damage) + " true damage!");
                                }
                            }
                        }

                        this.cancel();
                        return;
                    }

                    tick += 2;
                }

                private void drawCircle(Location center, double radius, org.bukkit.Particle particle) {
                    for (int degree = 0; degree < 360; degree += 20) {
                        double radians = Math.toRadians(degree);
                        double x = radius * Math.cos(radians);
                        double z = radius * Math.sin(radians);
                        center.getWorld().spawnParticle(particle, center.clone().add(x, 0.1, z), 1, 0, 0, 0, 0);
                    }
                }
            }.runTaskTimer(plugin, 0L, 2L);
        }
    }

    private void startVote(Arena arena) {
        arena.setVoting(true);
        arena.setVoteTicks(0);
        arena.getActiveVotes().clear();

        if (plugin.getCelebrationService() != null) {
            plugin.getCelebrationService().celebrateVictory(arena);
        }

        arena.broadcastMessage("§e§l[MobArenav2] You have conquered the final wave!");
        arena.broadcastMessage("§bThe Voting Phase has begun. Do you want to continue to ENDLESS MODE?");

        for (UUID uuid : arena.getActivePlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) VoteGUI.open(p, plugin);
        }
    }

    public void castVote(Player player, Arena arena, boolean continueEndless) {
        if (!arena.getActiveVotes().containsKey(player.getUniqueId())) {
            arena.getActiveVotes().put(player.getUniqueId(), continueEndless);
            String choice = continueEndless ? "§aCONTINUE" : "§cSTOP";
            player.sendMessage("§e[MobArenav2] You voted to " + choice + "§e.");

            if (arena.getActiveVotes().size() >= arena.getActivePlayers().size()) {
                concludeVote(arena);
            }
        }
    }

    public void forceSkipWave(Arena arena, int targetWave) {
        if (!arena.isRunning()) return;

        for (org.bukkit.entity.LivingEntity mob : new ArrayList<>(arena.getActiveMobs())) {
            if (mob != null && mob.isValid()) mob.remove();
        }
        arena.getActiveMobs().clear();

        if (!arena.getActiveSupplies().isEmpty()) {
            for (Location loc : arena.getActiveSupplies()) {
                Block leftBlock = loc.getBlock();
                Block rightBlock = loc.clone().add(1, 0, 0).getBlock();

                if (leftBlock.getState() instanceof Chest chest) chest.getInventory().clear();
                if (rightBlock.getState() instanceof Chest chest) chest.getInventory().clear();

                leftBlock.setType(Material.AIR);
                rightBlock.setType(Material.AIR);
            }
            arena.getActiveSupplies().clear();
        }

        // [TỐI ƯU HÓA TẠI ĐÂY] Quét và dọn sạch mũi tên ngay khi Admin ép Skip Wave
        clearProjectiles(arena);

        arena.setCurrentWave(targetWave);
        arena.broadcastMessage("§c§l[MobArenav2] Admin forced the arena to skip to Wave " + targetWave + "!");

        ConfigurationSection targetWaveConfig = arena.getWaveConfig(targetWave);
        String waveType = (targetWaveConfig != null) ? targetWaveConfig.getString("wave_types", "mob").toLowerCase() : "mob";

        spawnWave(arena, targetWave, targetWaveConfig, waveType);
    }

    private void concludeVote(Arena arena) {
        if (!arena.isVoting()) return;
        arena.setVoting(false);

        int yes = 0, no = 0;
        for (Boolean v : arena.getActiveVotes().values()) {
            if (v) yes++; else no++;
        }

        arena.broadcastMessage("§e§l--- VOTE RESULTS ---");
        arena.broadcastMessage("§aContinue (Yes): " + yes);
        arena.broadcastMessage("§cStop (No): " + no);

        if (yes > no || (yes == no && yes > 0)) {
            arena.broadcastMessage("§a§lThe majority voted YES! Welcome to ENDLESS MODE!");
            arena.setEndlessMode(true);

            for (UUID uuid : arena.getActivePlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.closeInventory();
                    ArenaPlayerProfile profile = arena.getPlayerProfiles().get(uuid);
                    if (profile != null && profile.getPendingUpgradeChoices() > 0) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (p.isOnline() && arena.isRunning()) {
                                vn.meowchan12.mobarenav2.arena.UpgradeGUI.open(p, plugin);
                            }
                        }, 10L);
                    }
                }
            }
        } else {
            arena.broadcastMessage("§c§lThe players chose to STOP. The arena is now ending.");
            arena.endArena(plugin);
        }
    }

    private void spawnWave(Arena arena, int waveNumber, ConfigurationSection targetWave, String waveType) {
        try {
            if (waveType.equalsIgnoreCase("supply") || waveType.equalsIgnoreCase("bonus")) {

                if (!arena.getActiveSupplies().isEmpty()) {
                    for (Location oldLoc : arena.getActiveSupplies()) {
                        Block left = oldLoc.getBlock();
                        Block right = oldLoc.clone().add(1, 0, 0).getBlock();
                        if (left.getState() instanceof Chest c) c.getInventory().clear();
                        if (right.getState() instanceof Chest c) c.getInventory().clear();
                        left.setType(Material.AIR);
                        right.setType(Material.AIR);
                    }
                    arena.getActiveSupplies().clear();
                }

                arena.broadcastMessage("§b§lA Supply Drop has arrived!");
                String supplyId = (targetWave != null) ? targetWave.getString("supply-id") : null;

                if (supplyId != null) {
                    List<ItemStack> items = plugin.getSupplyManager().getSupplyItems(supplyId);
                    if (items.isEmpty()) {
                        arena.broadcastMessage("§c⚠ [Warning] Admin configured an invalid supply ID: " + supplyId + "!");
                        plugin.getLogger().warning("Arena " + arena.getName() + " failed to drop supply. ID '" + supplyId + "' is empty or invalid!");
                        return;
                    }

                    Location loc = getSafeSupplyLocation(arena);
                    Block leftBlock = loc.getBlock();
                    Block rightBlock = loc.clone().add(1, 0, 0).getBlock();

                    leftBlock.setType(Material.CHEST);
                    rightBlock.setType(Material.CHEST);

                    org.bukkit.block.data.type.Chest leftChestData = (org.bukkit.block.data.type.Chest) leftBlock.getBlockData();
                    org.bukkit.block.data.type.Chest rightChestData = (org.bukkit.block.data.type.Chest) rightBlock.getBlockData();

                    leftChestData.setType(org.bukkit.block.data.type.Chest.Type.LEFT);
                    rightChestData.setType(org.bukkit.block.data.type.Chest.Type.RIGHT);

                    leftChestData.setFacing(BlockFace.NORTH);
                    rightChestData.setFacing(BlockFace.NORTH);

                    leftBlock.setBlockData(leftChestData);
                    rightBlock.setBlockData(rightChestData);

                    if (leftBlock.getState() instanceof Chest chest) {
                        for (ItemStack item : items) {
                            if (item != null) chest.getInventory().addItem(item);
                        }
                        arena.getActiveSupplies().add(loc);

                        arena.broadcastMessage("§b[Supply Drop] §eA chest has safely landed at §bX: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: " + loc.getBlockZ() + "§e!");
                        arena.broadcastMessage("§c⚠ Loot it quickly before it erupts! ⚠");

                        Location fireworkLoc = loc.clone().add(1.0, 1.0, 0.5);
                        if (fireworkLoc.getWorld() != null) {
                            fireworkLoc.getWorld().spawn(fireworkLoc, org.bukkit.entity.Firework.class, fw -> {
                                org.bukkit.inventory.meta.FireworkMeta fwm = fw.getFireworkMeta();
                                if (fwm != null) {
                                    fwm.addEffect(org.bukkit.FireworkEffect.builder()
                                            .withColor(org.bukkit.Color.AQUA, org.bukkit.Color.WHITE)
                                            .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                                            .withTrail()
                                            .withFlicker()
                                            .build());
                                    fwm.setPower(1);
                                    fw.setFireworkMeta(fwm);
                                }
                            });
                        }
                        for (Player p : arena.getPlayersAsObjects()) {
                            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 0.5f, 2.0f);
                        }
                    }
                }
                return;
            }

            if (waveType.equalsIgnoreCase("boss") || (targetWave != null && targetWave.getBoolean("is-boss", false))) {
                arena.broadcastMessage("§4§l⚠ BOSS WAVE " + waveNumber + " HAS ARRIVED! ⚠");
                for (Player p : arena.getPlayersAsObjects()) {
                    p.sendTitle("§4§lBOSS WAVE!", "§cPrepare for battle!", 10, 70, 20);
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
                }
            } else if (waveType.equalsIgnoreCase("swarm")) {
                arena.broadcastMessage("§6§l⚠ SWARM WAVE " + waveNumber + " DETECTED! ⚠");
                for (Player p : arena.getPlayersAsObjects()) {
                    p.sendTitle("§6§lSWARM WAVE!", "§eOverwhelming numbers incoming!", 10, 70, 20);
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_SILVERFISH_AMBIENT, 1.0f, 1.2f);
                }
            } else if (waveType.equalsIgnoreCase("elite_mutation") || waveType.equalsIgnoreCase("elite")) {
                arena.broadcastMessage("§5§l⚠ ELITE MUTATION WAVE " + waveNumber + "! ⚠");
                for (Player p : arena.getPlayersAsObjects()) {
                    p.sendTitle("§5§lELITE MUTATION!", "§dDangerous mutated affixes detected!", 10, 70, 20);
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.5f);
                }
            } else {
                arena.broadcastMessage("§e§lWave " + waveNumber + " has begun!");
                for (Player p : arena.getPlayersAsObjects()) {
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.5f, 1.5f);
                }
            }

            // [v7.3 DECOUPLED]: Điều phối onWaveStart cho người chơi sở hữu Upgrade (Phantom Barrier, Titan Heart...)
            if (plugin.getUpgradeRegistry() != null) {
                for (Player p : arena.getPlayersAsObjects()) {
                    plugin.getUpgradeRegistry().dispatchWaveStart(p, arena);
                }
            }

            int playerCount = arena.getActivePlayers().size();

            if (targetWave != null) {
                if (targetWave.contains("mobs")) {
                    ConfigurationSection mobsSection = targetWave.getConfigurationSection("mobs");
                    if (mobsSection != null) {
                        double globalMultiplier = waveType.equalsIgnoreCase("swarm") ? targetWave.getDouble("mob-multiplier", 1.0) : 1.0;

                        for (String mobName : mobsSection.getKeys(false)) {
                            // =====================================================================
                            // [FIX BUG #1]: Case-insensitive boss key validation
                            // Phải duyệt key thay vì dùng .contains() để tránh lệch hoa/thường
                            // =====================================================================
                            boolean isBossKey = false;
                            for (String bossKey : plugin.getBossManager().getBossesConfig().getKeys(false)) {
                                if (bossKey.equalsIgnoreCase(mobName)) {
                                    isBossKey = true;
                                    break;
                                }
                            }
                            boolean isSupportMob = plugin.getSupportMobService() != null && plugin.getSupportMobService().isSupportMobKey(mobName);
                            if (!isBossKey && !isSupportMob) {
                                try {
                                    org.bukkit.entity.EntityType.valueOf(mobName.toUpperCase());
                                } catch (Exception ex) {
                                    plugin.getLogger().warning("[MobArenav2] Skipping invalid mob/boss '" + mobName + "' at Wave " + waveNumber + " in arena " + arena.getName() + "!");
                                    continue;
                                }
                            }

                            ConfigurationSection mobSpecificConfig = mobsSection.getConfigurationSection(mobName);
                            int baseAmount = 1;
                            if (mobSpecificConfig != null && mobSpecificConfig.contains("amount")) {
                                baseAmount = mobSpecificConfig.getInt("amount", 1);
                            } else if (mobsSection.isInt(mobName)) {
                                baseAmount = mobsSection.getInt(mobName);
                            }

                            int amountToSpawn = ArenaMath.calculateSpawnAmount(baseAmount, playerCount, globalMultiplier);
                            for (int i = 0; i < amountToSpawn; i++) {
                                arena.getSpawnQueue().add(() -> {
                                    org.bukkit.entity.LivingEntity spawned = mobFactory.spawnArenaMob(arena, mobName, getSmartSpawnLocation(arena), mobSpecificConfig, targetWave);
                                    if (spawned != null && plugin.getMobSpawnerService() != null) {
                                        plugin.getMobSpawnerService().applyWaveArchetype(spawned, arena, waveType);
                                    }
                                });
                            }
                        }
                    }
                } else {
                    for (String mobName : targetWave.getKeys(false)) {
                        if (mobName.equals("waves") || mobName.equals("priority") || mobName.equals("is-boss") || mobName.equals("wave_types") || mobName.equals("wave") || mobName.equals("general-effect") || mobName.equals("maxhp-bonus") || mobName.equals("damage-bonus") || mobName.equals("movement-speed") || mobName.equals("explosion-damage") || mobName.equals("bow-speed") || mobName.equals("after-game") || mobName.equals("supply-id") || mobName.equals("duration")) continue;

                        // [v5.5 FIX]: Also check BossManager for valid boss names
                        boolean isBossKey = false;
                        for (String bossKey : plugin.getBossManager().getBossesConfig().getKeys(false)) {
                            if (bossKey.equalsIgnoreCase(mobName)) {
                                isBossKey = true;
                                break;
                            }
                        }
                        boolean isSupportMob = plugin.getSupportMobService() != null && plugin.getSupportMobService().isSupportMobKey(mobName);
                        if (!isBossKey && !isSupportMob) {
                            try {
                                org.bukkit.entity.EntityType.valueOf(mobName.toUpperCase());
                            } catch (Exception ex) {
                                plugin.getLogger().warning("[MobArenav2] Skipping invalid mob '" + mobName + "' at Wave " + waveNumber + " in arena " + arena.getName() + "!");
                                continue;
                            }
                        }

                        int baseAmount = targetWave.getInt(mobName, 1);
                        int amountToSpawn = ArenaMath.calculateSpawnAmount(baseAmount, playerCount, 1.0);

                        for (int i = 0; i < amountToSpawn; i++) {
                            arena.getSpawnQueue().add(() -> {
                                org.bukkit.entity.LivingEntity spawned = mobFactory.spawnArenaMob(arena, mobName, getSmartSpawnLocation(arena), null, targetWave);
                                if (spawned != null && plugin.getMobSpawnerService() != null) {
                                    plugin.getMobSpawnerService().applyWaveArchetype(spawned, arena, waveType);
                                }
                            });
                        }
                    }
                }
            } else {
                int amountToSpawn = ArenaMath.calculateSpawnAmount(5, playerCount, 1.0);
                for (int i = 0; i < amountToSpawn; i++) {
                    arena.getSpawnQueue().add(() -> mobFactory.spawnArenaMob(arena, "ZOMBIE", getSmartSpawnLocation(arena), null, null));
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().severe("[MobArenav2] Critical runtime error occurred while spawning Wave " + waveNumber + " in arena " + arena.getName() + "!");
            ex.printStackTrace();
        }
    }

    private void distributeWaveRewards(Arena arena, int wave) {
        try {
            FileConfiguration config = arena.getConfig();
            if (config == null || !config.contains("rewards")) return;
            ConfigurationSection rewardsSection = config.getConfigurationSection("rewards");
            if (rewardsSection == null) return;

            List<String> rewardsToGive = new ArrayList<>();

            for (String key : rewardsSection.getKeys(false)) {
                boolean match = false;

                if (key.equals(String.valueOf(wave))) {
                    match = true;
                } else if (key.toLowerCase().startsWith("every ")) {
                    try {
                        String[] parts = key.split(" ");
                        if (parts.length >= 2) {
                            int interval = Integer.parseInt(parts[1].trim());
                            if (interval > 0 && wave % interval == 0) {
                                match = true;
                            }
                        }
                    } catch (NumberFormatException ignored) {}
                }

                if (match && rewardsSection.isList(key)) {
                    List<String> rawList = rewardsSection.getStringList(key);
                    boolean isAfterGame = false;
                    List<String> cleanRewards = new ArrayList<>();

                    for (String line : rawList) {
                        String cleanLine = line.trim().toLowerCase();
                        if (cleanLine.equals("after-game: true") || cleanLine.equals("\"after-game: true\"")) {
                            isAfterGame = true;
                        } else if (cleanLine.equals("after-game: false") || cleanLine.equals("\"after-game: false\"")) {
                            isAfterGame = false;
                        } else {
                            if (!cleanLine.startsWith("vault:") && !cleanLine.startsWith("cmd(")) {
                                plugin.getLogger().warning("[MobArenav2] Unknown reward syntax in arena " + arena.getName() + ": '" + line + "'. Expected 'vault:X' or 'cmd(...)'.");
                            }
                            cleanRewards.add(line);
                        }
                    }

                    if (arena.isEndlessMode() == isAfterGame) {
                        rewardsToGive.addAll(cleanRewards);
                    }
                }
            }

            if (rewardsToGive.isEmpty()) return;
            String rewardString = String.join(",", rewardsToGive);

            for (UUID uuid : arena.getActivePlayers()) {
                ArenaPlayerProfile profile = arena.getPlayerProfiles().get(uuid);
                if (profile != null) profile.cacheReward(rewardString);

                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.sendActionBar(vn.meowchan12.mobarenav2.utils.ColorUtils.toAdventure("&a✦ Wave " + wave + " Reward secured & stashed! ✦"));
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("[MobArenav2] Failed to distribute rewards for Wave " + wave + " in arena " + arena.getName() + " due to a configuration error.");
        }
    }

    private Location getSmartSpawnLocation(Arena arena) {
        List<Location> allSpawns = arena.getSpawnpoints();
        if (allSpawns == null || allSpawns.isEmpty()) return arena.getArenaSpawn();

        List<Location> validSpawns = new ArrayList<>();
        double minDistanceSq = Math.pow(arena.getSpawnMinDistance(), 2);

        for (Location spawn : allSpawns) {
            boolean isValid = true;
            for (Player player : arena.getPlayersAsObjects()) {
                if (spawn.distanceSquared(player.getLocation()) < minDistanceSq) {
                    isValid = false;
                    break;
                }
            }
            if (isValid) validSpawns.add(spawn);
        }

        if (validSpawns.isEmpty()) {
            return allSpawns.get(ThreadLocalRandom.current().nextInt(allSpawns.size()));
        }
        return validSpawns.get(ThreadLocalRandom.current().nextInt(validSpawns.size()));
    }

    private Location getSafeSupplyLocation(Arena arena) {
        List<Location> supplyPoints = arena.getSupplypoints();

        if (supplyPoints != null && !supplyPoints.isEmpty()) {
            return supplyPoints.get(ThreadLocalRandom.current().nextInt(supplyPoints.size()));
        }

        plugin.getLogger().warning("[MobArenav2] Arena " + arena.getName() + " does not have Supply Points set! Dropping at Arena Spawn.");
        return arena.getArenaSpawn();
    }
}