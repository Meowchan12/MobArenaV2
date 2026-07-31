package vn.meowchan12.mobarenav2.shop.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import vn.meowchan12.mobarenav2.MobArenav2;

import java.lang.reflect.Method;
import java.util.UUID;

public class PlayerPointsHook {

    private final MobArenav2 plugin;
    private Object ppAPI;
    private Method lookMethod;
    private Method takeMethod;
    private Method giveMethod;
    private boolean isEnabled = false;

    public PlayerPointsHook(MobArenav2 plugin) {
        this.plugin = plugin;
        setupPlayerPoints();
    }

    private void setupPlayerPoints() {
        Plugin ppPlugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (ppPlugin != null) {
            try {
                Method getApiMethod = ppPlugin.getClass().getMethod("getAPI");
                this.ppAPI = getApiMethod.invoke(ppPlugin);
                if (this.ppAPI != null) {
                    this.lookMethod = ppAPI.getClass().getMethod("look", UUID.class);
                    this.takeMethod = ppAPI.getClass().getMethod("take", UUID.class, int.class);
                    this.giveMethod = ppAPI.getClass().getMethod("give", UUID.class, int.class);
                    this.isEnabled = true;
                    plugin.getLogger().info("[MobArenav2] Successfully hooked into PlayerPoints API via Reflection!");
                }
            } catch (Throwable e) {
                plugin.getLogger().info("[MobArenav2] PlayerPoints plugin found. Will use Command/PAPI integration.");
                this.isEnabled = false;
            }
        }
    }

    public boolean hasEnoughPoints(Player player, int amount) {
        if (!isEnabled || ppAPI == null || lookMethod == null) return false;
        try {
            int points = (int) lookMethod.invoke(ppAPI, player.getUniqueId());
            return points >= amount;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean takePoints(Player player, int amount) {
        if (!isEnabled || ppAPI == null || takeMethod == null) return false;
        try {
            return (boolean) takeMethod.invoke(ppAPI, player.getUniqueId(), amount);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean givePoints(Player player, int amount) {
        if (!isEnabled || ppAPI == null || giveMethod == null) return false;
        try {
            return (boolean) giveMethod.invoke(ppAPI, player.getUniqueId(), amount);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isEnabled() {
        return isEnabled;
    }
}
