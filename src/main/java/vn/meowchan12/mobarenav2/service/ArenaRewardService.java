package vn.meowchan12.mobarenav2.service;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [v8.1.0]: Arena Reward Service.
 * Safely stashes arena rewards during waves and executes guaranteed payouts upon match conclusion.
 */
public class ArenaRewardService {

    private final MobArenav2 plugin;

    public static class StashedReward {
        private double vaultMoney = 0.0;
        private int exp = 0;
        private int casinoChips = 0;
        private final List<ItemStack> items = new ArrayList<>();
        private final List<String> commands = new ArrayList<>();

        public void addMoney(double amount) { this.vaultMoney += amount; }
        public void addExp(int amount) { this.exp += amount; }
        public void addChips(int amount) { this.casinoChips += amount; }
        public void addItem(ItemStack is) { if (is != null) this.items.add(is.clone()); }
        public void addCommand(String cmd) { if (cmd != null) this.commands.add(cmd); }

        public double getVaultMoney() { return vaultMoney; }
        public int getExp() { return exp; }
        public int getChips() { return casinoChips; }
        public List<ItemStack> getItems() { return items; }
        public List<String> getCommands() { return commands; }
    }

    private final Map<UUID, StashedReward> stashes = new ConcurrentHashMap<>();

    public ArenaRewardService(MobArenav2 plugin) {
        this.plugin = plugin;
    }

    public StashedReward getStash(UUID uuid) {
        return stashes.computeIfAbsent(uuid, k -> new StashedReward());
    }

    public void recordWaveReward(UUID uuid, double money, int exp, int chips) {
        StashedReward reward = getStash(uuid);
        reward.addMoney(money);
        reward.addExp(exp);
        reward.addChips(chips);
    }

    /**
     * Executes safe payout of all stashed rewards to the player.
     */
    public void payoutStashedRewards(Player player, Arena arena, boolean isVictory) {
        if (player == null || !player.isOnline()) return;
        StashedReward stash = stashes.remove(player.getUniqueId());
        if (stash == null) return;

        // Payout Vault
        if (stash.getVaultMoney() > 0 && plugin.getEconomy() != null) {
            plugin.getEconomy().depositPlayer(player, stash.getVaultMoney());
            player.sendMessage("§a[Reward] §eReceived $" + String.format("%.2f", stash.getVaultMoney()) + " §afrom your arena achievements!");
        }

        // Payout Exp
        if (stash.getExp() > 0) {
            player.giveExp(stash.getExp());
        }

        // Payout Casino Chips
        if (stash.getChips() > 0) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "casino give " + player.getName() + " " + stash.getChips());
            player.sendMessage("§6[Reward] §eReceived " + stash.getChips() + " Casino Chips!");
        }

        // Payout Items
        for (ItemStack item : stash.getItems()) {
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }

        // Execute reward commands
        for (String cmd : stash.getCommands()) {
            String parsed = cmd.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }

        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
    }

    public void clearStash(UUID uuid) {
        stashes.remove(uuid);
    }
}
