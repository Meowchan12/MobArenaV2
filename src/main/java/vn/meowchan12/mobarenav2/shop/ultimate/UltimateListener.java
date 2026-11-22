package vn.meowchan12.mobarenav2.shop.ultimate;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

public class UltimateListener implements Listener {

    private final MobArenav2 plugin;
    private final UltimateManager ultimateManager;

    public UltimateListener(MobArenav2 plugin, UltimateManager ultimateManager) {
        this.plugin = plugin;
        this.ultimateManager = ultimateManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        // Must be in an active running arena
        if (!plugin.getArenaManager().isPlaying(player)) return;

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
        if (arena == null || !arena.isRunning()) return;

        // Player must be holding a combat item or empty hand
        ItemStack item = player.getInventory().getItemInMainHand();
        Material mat = item.getType();
        boolean isCombatItem = mat.name().endsWith("_SWORD") || mat.name().endsWith("_AXE")
                || mat == Material.BOW || mat == Material.CROSSBOW || mat == Material.TRIDENT
                || mat == Material.SHIELD || mat == Material.AIR;

        if (!isCombatItem) return;

        boolean triggered = ultimateManager.tryTriggerUltimate(player, arena);
        if (triggered) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStasisDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (ultimateManager.isStasisImmune(player)) {
                event.setCancelled(true);
            }
        }
    }
}
