package vn.meowchan12.mobarenav2.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import vn.meowchan12.mobarenav2.MobArenav2;

import java.util.UUID;

public class PlayerConnectionListener implements Listener {

    private final MobArenav2 plugin;

    public PlayerConnectionListener(MobArenav2 plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // 1. Exit setup mode securely if the admin leaves abruptly
        if (plugin.getSetupManager() != null && plugin.getSetupManager().isInSetupMode(player)) {
            plugin.getSetupManager().exitSetupMode(player);
        }

        // [v6.2 FIX] Memory Leak #3: Clean up edit session if admin disconnects while in edit mode
        if (plugin.getSetupManager() != null) {
            plugin.getSetupManager().removeEditSession(player);
        }

        // 2. ANTI-DUPE: Clear pending state if they disconnect during the 3s countdown
        if (plugin.getInventoryBackupManager() != null && plugin.getInventoryBackupManager().isPendingJoin(uuid)) {
            plugin.getInventoryBackupManager().removePending(uuid);
        }

        // 3. If the player quits while in the arena (Alive or Spectator)
        // The system will force a Leave, clean states, and trigger restoreBackup() safely.
        if (plugin.getArenaManager().isPlaying(player) || plugin.getArenaManager().isSpectating(player)) {
            plugin.getArenaManager().handlePlayerDisconnect(player);
        }

        // [v6.2 FIX] Memory Leak #1: Unload shop data from RAM (save to disk first)
        if (plugin.getShopManager() != null) {
            plugin.getShopManager().unloadPlayerData(uuid);
        }

        // [v6.2 FIX] Memory Leak #2: Clean up GUIListener static maps
        vn.meowchan12.mobarenav2.listener.GUIListener.pendingEffectAction.remove(uuid);
        vn.meowchan12.mobarenav2.listener.GUIListener.pendingEffectClass.remove(uuid);
        vn.meowchan12.mobarenav2.listener.GUIListener.completedUpgradeSelection.remove(uuid);

        // [v6.4 FIX] I/O Optimization: Clean up user data cache
        if (plugin.getUserDataManager() != null) {
            plugin.getUserDataManager().unloadData(uuid);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // [v6.4 FIX] I/O Optimization: Preload user data async to avoid lag spikes later
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (plugin.getUserDataManager() != null) {
                plugin.getUserDataManager().loadData(player.getUniqueId());
            }
        });

        // CRASH RECOVERY: Check if the player has an emergency backup file
        // If they do, and they are not actively in an arena, restore it immediately.
        if (plugin.getInventoryBackupManager() != null && plugin.getInventoryBackupManager().hasBackup(player.getUniqueId())) {
            if (!plugin.getArenaManager().isPlaying(player)) {

                // Wash away any residual arena items/effects
                player.getInventory().clear();
                plugin.getArenaManager().resetPlayerState(player);

                // Refund Survival Inventory
                plugin.getInventoryBackupManager().restoreBackup(player);

                plugin.getLogger().info("[MobArenav2] Restored inventory for " + player.getName() + " after an unexpected disconnect/crash.");
                player.sendMessage("§a[MobArenav2] Server detected an unsafe disconnect. Your survival inventory has been safely restored!");
            }
        }
    }
}