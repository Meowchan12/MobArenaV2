package vn.meowchan12.mobarenav2.listener;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;

import java.util.List;
import java.util.UUID;

public class ArenaPlayerDeathListener implements Listener {

    private final MobArenav2 plugin;
    private final NamespacedKey bossKey;
    private final vn.meowchan12.mobarenav2.boss.BossCounterMeta counterMeta;

    public ArenaPlayerDeathListener(MobArenav2 plugin) {
        this.plugin = plugin;
        this.bossKey = new NamespacedKey(plugin, "arena_boss_id");
        this.counterMeta = new vn.meowchan12.mobarenav2.boss.BossCounterMeta(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
        if (arena == null || !arena.isRunning()) return;

        // Spectators are completely immune to all arena damage
        if (arena.getSpectators().contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (player.getHealth() - event.getFinalDamage() <= 0) {

            NamespacedKey dmKey = new NamespacedKey(plugin, "ma_death_mark");
            boolean hasDeathMark = false;

            if (player.getPersistentDataContainer().has(dmKey, PersistentDataType.LONG)) {
                long expiry = player.getPersistentDataContainer().get(dmKey, PersistentDataType.LONG);
                if (System.currentTimeMillis() <= expiry) {
                    hasDeathMark = true;
                } else {
                    player.getPersistentDataContainer().remove(dmKey);
                }
            }

            boolean hasTotem = false;
            if (player.getInventory().getItemInMainHand().getType() == org.bukkit.Material.TOTEM_OF_UNDYING ||
                    player.getInventory().getItemInOffHand().getType() == org.bukkit.Material.TOTEM_OF_UNDYING) {
                hasTotem = true;
            }

            boolean isAntiTotem = false;
            if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent edbe) {
                org.bukkit.entity.Entity damager = edbe.getDamager();
                org.bukkit.entity.LivingEntity bossEntity = null;
                if (damager instanceof org.bukkit.entity.LivingEntity le) {
                    bossEntity = le;
                } else if (damager instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof org.bukkit.entity.LivingEntity le) {
                    bossEntity = le;
                }
                if (bossEntity != null) {
                    isAntiTotem = counterMeta.shouldBypassTotem(bossEntity, player);
                }
            }

            if (hasTotem && !hasDeathMark && !isAntiTotem) {
                return;
            }

            if (hasTotem && (hasDeathMark || isAntiTotem)) {
                if (isAntiTotem) {
                    player.sendMessage("§4§l[SOUL SHATTER] §cThe Boss shattered your Totem of Undying!");
                } else {
                    player.sendMessage("§4☠ The Death Mark has shattered your Totem! Goodbye!");
                }
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_BREAK_BLOCK, 1.0f, 0.5f);
            }

            event.setCancelled(true);
            player.getPersistentDataContainer().remove(dmKey);

            // [v7.2 FIX] Immediately clear all player effects on death
            plugin.getArenaManager().clearAllPlayerEffects(player);
            plugin.getArenaManager().handlePlayerDeath(player, arena);

            // [ĐÃ SỬA] Bug #4: Tắt Fly Mode của Spectator để chống người chơi bay lượn trên không gian Arena
            if (arena.getSpectators().contains(player.getUniqueId())) {
                player.setAllowFlight(false);
                player.setFlying(false);
            }

            if (arena.isRunning() && arena.getSpectators().contains(player.getUniqueId())) {
                player.sendTitle("§c§lYOU DIED", "§7You are now spectating.", 10, 50, 20);

                TextComponent msg = new TextComponent("§8[MobArenav2] §7You have fallen. ");
                TextComponent leaveBtn = new TextComponent("§c§l[ LEAVE ARENA ]");
                leaveBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ma leave"));
                leaveBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§cClick to safely leave the arena")));
                msg.addExtra(leaveBtn);

                player.spigot().sendMessage(msg);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
        if (arena != null && arena.isRunning()) {
            plugin.getArenaManager().clearAllPlayerEffects(player);
            if (!arena.getSpectators().contains(player.getUniqueId())) {
                plugin.getArenaManager().handlePlayerDeath(player, arena);
            }
            // [v7.4.0 BUG 2 FIX]: Auto-respawn to eliminate lingering red death screen
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && player.isDead()) {
                    try {
                        player.spigot().respawn();
                    } catch (Throwable ignored) {}
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // [v7.4.0 BUG 2 FIX]: Route respawn to original pre-join location if leaving or died
        Location targetLoc = plugin.getArenaManager().getAndRemovePendingRespawnLocation(player.getUniqueId());
        if (targetLoc != null && targetLoc.getWorld() != null) {
            targetLoc.getWorld().getChunkAt(targetLoc).load(true);
            event.setRespawnLocation(targetLoc);
            plugin.getLogger().info("[MobArenav2] Safely routed respawn for " + player.getName() + " to pre-join location.");
        }

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
        if (arena != null) {
            plugin.getArenaManager().clearAllPlayerEffects(player);
            plugin.getArenaManager().resetPlayerState(player);
        }
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        org.bukkit.entity.LivingEntity entity = event.getEntity();

        // [v8.0.5 OPTIMIZATION]: Direct O(1) metadata arena lookup avoids O(N*M) full-server entity scans
        if (!entity.hasMetadata("MA_MOB")) return;
        var metaList = entity.getMetadata("MA_MOB");
        if (metaList.isEmpty()) return;
        String arenaName = metaList.get(0).asString();
        if ("STANDALONE".equals(arenaName)) return;

        Arena arena = plugin.getArenaManager().getArena(arenaName);
        if (arena == null || !arena.isRunning()) return;

        boolean allowDrop = arena.getConfig().getBoolean("settings.mob-drop",
                arena.getConfig().getBoolean("settings.rules.mob-drop", false));

        if (!allowDrop) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }

        if (entity.getKiller() != null) {
            ArenaPlayerProfile profile = arena.getPlayerProfiles().get(entity.getKiller().getUniqueId());
            if (profile != null) {
                profile.addKill();

                // [v8.2.2 FIX]: Credit Boss Slain stat to killer if the slain entity is a boss
                NamespacedKey arenaBossKey = new NamespacedKey(plugin, "arena_boss_id");
                NamespacedKey maBossKey = new NamespacedKey(plugin, "ma_boss_id");
                boolean isBoss = entity.hasMetadata("MA_BOSS")
                        || entity.getPersistentDataContainer().has(arenaBossKey, PersistentDataType.STRING)
                        || entity.getPersistentDataContainer().has(maBossKey, PersistentDataType.STRING);
                if (isBoss) {
                    UUID killerUUID = entity.getKiller().getUniqueId();
                    if (!entity.hasMetadata("MA_BOSS_CREDITED_" + killerUUID)) {
                        entity.setMetadata("MA_BOSS_CREDITED_" + killerUUID, new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                        profile.addBossKill();
                    }
                }
            }
        }

        if (entity.getPersistentDataContainer().has(bossKey, PersistentDataType.STRING)) {
            String bossId = entity.getPersistentDataContainer().get(bossKey, PersistentDataType.STRING);
            ConfigurationSection bossConfig = plugin.getBossManager().getBossesConfig().getConfigurationSection(bossId);

            if (bossConfig != null && bossConfig.contains("loot-supply")) {
                String supplyId = bossConfig.getString("loot-supply");
                List<ItemStack> loots = plugin.getSupplyManager().getSupplyItems(supplyId);

                Location deathLoc = entity.getLocation();

                deathLoc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, deathLoc, 100, 1.0, 1.0, 1.0, 0.5);
                deathLoc.getWorld().playSound(deathLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.0f);

                for (ItemStack item : loots) {
                    if (item != null) {
                        deathLoc.getWorld().dropItemNaturally(deathLoc, item);
                    }
                }
            }
        }
    }
}