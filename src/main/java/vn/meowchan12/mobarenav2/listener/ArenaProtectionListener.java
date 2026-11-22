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
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

import java.util.Arrays;
import java.util.List;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;

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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplodeArena(org.bukkit.event.block.BlockExplodeEvent event) {
        if (isInsideAnyArena(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplodeArena(org.bukkit.event.entity.EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isInsideAnyArena(block.getLocation()));
    }

    private boolean isInsideAnyArena(org.bukkit.Location loc) {
        return plugin.getArenaManager().getAllArenas().stream().anyMatch(a -> a.containsLocation(loc));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
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
        } else {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                for (Arena a : plugin.getArenaManager().getAllArenas()) {
                    if (a.containsLocation(event.getClickedBlock().getLocation())) {
                        if (plugin.getSetupManager().isInEditMode(player) && !a.isRunning()) {
                            return; // Allow admin to interact if arena is not running
                        }
                        event.setCancelled(true);
                        player.sendMessage("§c[MobArenav2] You cannot interact with blocks inside an arena! Use /ma arenaedit to modify when the arena is not running.");
                        return;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
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
        } else {
            for (Arena a : plugin.getArenaManager().getAllArenas()) {
                if (a.containsLocation(event.getBlock().getLocation())) {
                    if (plugin.getSetupManager().isInEditMode(player) && !a.isRunning()) {
                        return; // Allow admin to edit if arena is not running
                    }
                    event.setCancelled(true);
                    player.sendMessage("§c[MobArenav2] You cannot break blocks inside an arena! Use /ma arenaedit to modify when it is not running.");
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getSetupManager().isInSetupMode(event.getPlayer())) return;

        Arena arena = plugin.getArenaManager().getArenaByPlayer(event.getPlayer());
        if (arena != null && arena.isRunning()) {
            boolean allowPlace = arena.getConfig().getBoolean("settings.rules.block-place", false);
            if (!allowPlace) event.setCancelled(true);
        } else {
            for (Arena a : plugin.getArenaManager().getAllArenas()) {
                if (a.containsLocation(event.getBlock().getLocation())) {
                    if (plugin.getSetupManager().isInEditMode(event.getPlayer()) && !a.isRunning()) {
                        return; // Allow admin to edit if arena is not running
                    }
                    event.setCancelled(true);
                    event.getPlayer().sendMessage("§c[MobArenav2] You cannot place blocks inside an arena! Use /ma arenaedit to modify when it is not running.");
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (plugin.getSetupManager().isInSetupMode(player)) return;

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
        if (arena != null) {
            boolean allowAllCmds = arena.getConfig().getBoolean("settings.rules.allow-commands", false);
            if (allowAllCmds) return;

            String cmd = event.getMessage().toLowerCase();
            
            // Strip plugin aliases (e.g., /minecraft:tp -> /tp, /bukkit:kill -> /kill)
            if (cmd.startsWith("/")) {
                String[] parts = cmd.substring(1).split(" ");
                if (parts.length > 0 && parts[0].contains(":")) {
                    String[] aliasParts = parts[0].split(":");
                    if (aliasParts.length > 1) {
                        parts[0] = aliasParts[1];
                        cmd = "/" + String.join(" ", parts);
                    }
                }
            }
            
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
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
            boolean allowEnderpearl = arena.getConfig().getBoolean("settings.rules.allow-enderpearl", true);
            if (!allowEnderpearl) {
                event.setCancelled(true);
                player.sendMessage("§c[MobArenav2] Enderpearls are disabled in this arena!");
            }
            return;
        }

        if (cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            event.setCancelled(true);
            player.sendMessage("§c[MobArenav2] Chorus fruit teleportation is disabled in the arena!");
            return;
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
        // [v8.0.5 OPTIMIZATION]: O(1) metadata check eliminates O(N) arena scans for all server entities
        if (event.getEntity().hasMetadata("MA_MOB")) {
            event.setCancelled(true);
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
            Arena victimArena = plugin.getArenaManager().getArenaByPlayer(victim);
            Arena attackerArena = plugin.getArenaManager().getArenaByPlayer(attacker);

            // [v8.0.0 STRICT ISOLATION]: One is in arena and the other is outside, or they are in different arenas
            if (victimArena != attackerArena) {
                if (victimArena != null || attackerArena != null) {
                    event.setCancelled(true);
                    return;
                }
            }

            // Both in the same arena -> Check player-pvp rule
            if (victimArena != null && victimArena.isRunning()) {
                boolean allowPvP = victimArena.getConfig().getBoolean("settings.rules.player-pvp", false);
                if (!allowPvP) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerSplashPvP(PotionSplashEvent event) {
        if (event.getEntity().getShooter() instanceof Player attacker) {
            Arena attackerArena = plugin.getArenaManager().getArenaByPlayer(attacker);

            event.getAffectedEntities().removeIf(entity -> {
                if (entity instanceof Player victim) {
                    Arena victimArena = plugin.getArenaManager().getArenaByPlayer(victim);
                    // Outside player cannot splash arena players; arena player cannot splash outside players
                    if (attackerArena != victimArena) {
                        return true;
                    }
                    // Same arena, check PvP
                    if (attackerArena != null && !attackerArena.getConfig().getBoolean("settings.rules.player-pvp", false)) {
                        return !victim.equals(attacker);
                    }
                }
                return false;
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAreaCloudApply(org.bukkit.event.entity.AreaEffectCloudApplyEvent event) {
        if (event.getEntity().getSource() instanceof Player attacker) {
            Arena attackerArena = plugin.getArenaManager().getArenaByPlayer(attacker);

            event.getAffectedEntities().removeIf(entity -> {
                if (entity instanceof Player victim) {
                    Arena victimArena = plugin.getArenaManager().getArenaByPlayer(victim);
                    if (attackerArena != victimArena) {
                        return true;
                    }
                    if (attackerArena != null && !attackerArena.getConfig().getBoolean("settings.rules.player-pvp", false)) {
                        return !victim.equals(attacker);
                    }
                }
                return false;
            });
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        protectSupplyChests(event.blockList());
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        protectSupplyChests(event.blockList());
    }

    private void protectSupplyChests(List<org.bukkit.block.Block> blockList) {
        blockList.removeIf(block -> {
            if (block.getType() == Material.CHEST) {
                for (Arena arena : plugin.getArenaManager().getAllArenas()) {
                    if (arena.isRunning()) {
                        for (Location supplyLoc : arena.getActiveSupplies()) {
                            if ((supplyLoc.getBlockX() == block.getX() || supplyLoc.getBlockX() + 1 == block.getX()) &&
                                supplyLoc.getBlockY() == block.getY() &&
                                supplyLoc.getBlockZ() == block.getZ()) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        org.bukkit.entity.Item item = event.getItem();
        Location itemLoc = item.getLocation();

        for (Arena arena : plugin.getArenaManager().getAllArenas()) {
            if (arena.containsLocation(itemLoc) || arena.containsLocation(player.getLocation())) {
                if (!arena.getActivePlayers().contains(player.getUniqueId())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    // =====================================================================
    // [v7.5.6]: Spectator Hunger Protection
    // Prevents spectators from starving or losing saturation over time.
    // =====================================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
            if (arena != null && arena.getSpectators().contains(player.getUniqueId())) {
                event.setCancelled(true);
                player.setFoodLevel(20);
                player.setSaturation(20.0f);
            }
        }
    }

    // =====================================================================
    // [v7.5.6]: Gamemode Protection (F3+F4 / /gamemode bypass barrier)
    // Prevents admins & players inside running arenas from toggling gamemode.
    // =====================================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (plugin.getArenaManager().isInternalGameModeSwitch(player.getUniqueId())) {
            return; // Internal plugin-managed switch allowed
        }
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
        if (arena != null && arena.isRunning()) {
            event.setCancelled(true);
            player.sendMessage("§c[MobArenav2] You cannot change game mode while participating in a running arena!");
        }
    }
}