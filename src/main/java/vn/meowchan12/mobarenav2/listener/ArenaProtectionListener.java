package vn.meowchan12.mobarenav2.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

import java.util.Arrays;
import java.util.List;

public class ArenaProtectionListener implements Listener {

    private final MobArenav2 plugin;

    private final List<Material> blockedUtilityBlocks = Arrays.asList(
            Material.CRAFTING_TABLE, Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL,
            Material.ENCHANTING_TABLE, Material.BREWING_STAND, Material.LOOM,
            Material.CARTOGRAPHY_TABLE, Material.SMITHING_TABLE, Material.GRINDSTONE,
            Material.STONECUTTER, Material.BARREL, Material.HOPPER, Material.DROPPER, Material.DISPENSER
    );

    public ArenaProtectionListener(MobArenav2 plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (plugin.getInventoryBackupManager() != null && plugin.getInventoryBackupManager().isPendingJoin(player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage("§c[MobArenav2] You cannot move items while preparing to join the arena!");
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (plugin.getInventoryBackupManager() != null && plugin.getInventoryBackupManager().isPendingJoin(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
        if (arena != null) {
            if (arena.getSpectators().contains(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }

            if (arena.isRunning() && event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                if (blockedUtilityBlocks.contains(event.getClickedBlock().getType())) {
                    event.setCancelled(true);
                    player.sendMessage("§c[MobArenav2] You cannot use utility blocks like Crafting Tables or Furnaces during combat!");
                }
            }
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        if (plugin.getInventoryBackupManager() != null && plugin.getInventoryBackupManager().isPendingJoin(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage("§c[MobArenav2] You cannot drop items while preparing to join the arena!");
            return;
        }

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
        if (arena != null && arena.isRunning()) {
            boolean allowDrop = arena.getConfig().getBoolean("settings.rules.item-drop", false);
            if (!allowDrop) {
                event.setCancelled(true);
                player.sendMessage("§c[MobArenav2] You cannot drop items in this arena!");
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (plugin.getSetupManager().isInSetupMode(player)) return;

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
        if (arena != null && arena.isRunning()) {

            // [FIX BUG #2]: Quét số nguyên và kiểm tra cả block trái + phải của Rương
            if (event.getBlock().getType() == Material.CHEST) {
                for (Location supplyLoc : arena.getActiveSupplies()) {
                    if ((supplyLoc.getBlockX() == event.getBlock().getX() || supplyLoc.getBlockX() + 1 == event.getBlock().getX()) &&
                            supplyLoc.getBlockY() == event.getBlock().getY() &&
                            supplyLoc.getBlockZ() == event.getBlock().getZ()) {

                        event.setCancelled(true);
                        player.sendMessage("§c[MobArenav2] Supply chests are indestructible! Just open it!");
                        return;
                    }
                }
            }

            boolean allowBreak = arena.getConfig().getBoolean("settings.rules.block-break", false);
            if (!allowBreak) event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getSetupManager().isInSetupMode(event.getPlayer())) return;

        Arena arena = plugin.getArenaManager().getArenaByPlayer(event.getPlayer());
        if (arena != null && arena.isRunning()) {
            boolean allowPlace = arena.getConfig().getBoolean("settings.rules.block-place", false);
            if (!allowPlace) event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (plugin.getSetupManager().isInSetupMode(player)) return;

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
        if (arena != null) {
            boolean allowAllCmds = arena.getConfig().getBoolean("settings.rules.allow-commands", false);
            if (allowAllCmds) return;

            String cmd = event.getMessage().toLowerCase();
            List<String> whitelist = arena.getConfig().getStringList("settings.rules.command-whitelist");
            boolean isAllowed = false;

            if (cmd.startsWith("/ma ") || cmd.equals("/ma")) {
                isAllowed = true;
            } else {
                for (String allowedCmd : whitelist) {
                    if (cmd.startsWith(allowedCmd.toLowerCase())) {
                        isAllowed = true;
                        break;
                    }
                }
            }

            if (!isAllowed) {
                event.setCancelled(true);
                player.sendMessage("§c[MobArenav2] This command cannot be used while inside the arena! Please use /ma leave to exit.");
            }
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
        if (arena == null) return;

        Location to = event.getTo();
        if (to == null) return;
        PlayerTeleportEvent.TeleportCause cause = event.getCause();

        if (cause == PlayerTeleportEvent.TeleportCause.COMMAND || cause == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            boolean isValidDest = false;
            if (isSameLocationSafe(to, arena.getLobby())) isValidDest = true;
            else if (isSameLocationSafe(to, arena.getSpectatorSpawn())) isValidDest = true;
            else if (isSameLocationSafe(to, arena.getArenaSpawn())) isValidDest = true;
            else if (arena.containsLocation(to)) isValidDest = true;

            if (!isValidDest) {
                event.setCancelled(true);
                player.sendMessage("§c[MobArenav2] Teleportation has been blocked! You must type §e/ma leave §cto safely exit the arena.");
            }
        }

        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            if (arena.isRunning() && !arena.containsLocation(to)) {
                event.setCancelled(true);
                player.sendMessage("§c[MobArenav2] You cannot use an Ender Pearl to escape the combat boundaries!");
            }
        }
    }

    private boolean isSameLocationSafe(Location loc1, Location loc2) {
        if (loc1 == null || loc2 == null || loc1.getWorld() != loc2.getWorld()) return false;
        return loc1.distanceSquared(loc2) < 5.0;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM ||
                event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }

        for (Arena arena : plugin.getArenaManager().getAllArenas()) {
            if (arena.containsLocation(event.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onEntityCombust(EntityCombustEvent event) {
        if (event instanceof EntityCombustByEntityEvent || event instanceof EntityCombustByBlockEvent) {
            return;
        }
        for (Arena arena : plugin.getArenaManager().getAllArenas()) {
            if (arena.isRunning() && arena.getActiveMobs().contains(event.getEntity())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPvP(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;

        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof org.bukkit.entity.AreaEffectCloud cloud && cloud.getSource() instanceof Player p) {
            attacker = p;
        }

        if (attacker != null && !attacker.equals(victim)) {
            Arena arena = plugin.getArenaManager().getArenaByPlayer(victim);
            if (arena != null && plugin.getArenaManager().getArenaByPlayer(attacker) == arena) {

                boolean allowPvP = arena.getConfig().getBoolean("settings.rules.player-pvp", false);
                if (!allowPvP) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerSplashPvP(PotionSplashEvent event) {
        if (event.getEntity().getShooter() instanceof Player attacker) {
            Arena arena = plugin.getArenaManager().getArenaByPlayer(attacker);
            if (arena != null && !arena.getConfig().getBoolean("settings.rules.player-pvp", false)) {
                event.getAffectedEntities().removeIf(entity ->
                        entity instanceof Player &&
                                !entity.equals(attacker) &&
                                plugin.getArenaManager().getArenaByPlayer((Player) entity) == arena
                );
            }
        }
    }
}