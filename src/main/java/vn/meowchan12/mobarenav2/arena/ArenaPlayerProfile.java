package vn.meowchan12.mobarenav2.arena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.manager.UpgradeManager;
import vn.meowchan12.mobarenav2.upgrade.UpgradeDraftType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ArenaPlayerProfile {

    private final UUID playerUUID;
    private double accumulatedVaultMoney = 0.0;
    private final List<String> pendingCommands = new ArrayList<>();

    // ONLY save location here. Inventory is now handled by InventoryBackupManager safely.
    private Location originalLocation;

    private boolean isReady = false;
    private String selectedClass = null;
    private ArenaScoreboard scoreboard;
    private int kills = 0;
    private int coins = 0;
    private int currentWaveRerolls = 0;
    private final Deque<UpgradeDraftType> pendingUpgradeQueue = new ArrayDeque<>();
    private int pinnedUpgradeSlot = -1; // -1 means none pinned, slots 11, 13, 15
    private int remainingPins = 10;
    private int remainingBanishes = 10;
    private final Set<String> banishedUpgrades = new HashSet<>();

    // Realtime combat telemetry & dynamic upgrade stat tracking
    private double damageDealt = 0.0;
    private double damageTaken = 0.0;
    private int bossKills = 0;

    private int slaughterhouseKills = 0;
    private int hephaestusRepairs = 0;
    private int soulHarvesterSouls = 0;
    private double midasGoldEarned = 0.0;
    private double reflectedDamageTotal = 0.0;
    private double titanHeartShieldAbsorbed = 0.0;

    // [v8.2.0]: Over-cap / Limit Break bonus stats (Maxed Out upgrades)
    private double overcapDamageBonusPercent = 0.0;
    private double overcapFlatTrueDmg = 0.0;
    private double overcapBonusSpeed = 0.0;
    private int superUpgradesChosen = 0;
    private int pityRollCounter = 0;

    private final Map<String, Integer> activeUpgrades = new HashMap<>();
    private final Map<String, Integer> matchUpgradesHistory = new java.util.LinkedHashMap<>();

    public ArenaPlayerProfile(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public boolean isReady() { return isReady; }
    public void setReady(boolean ready) { this.isReady = ready; }
    public String getSelectedClass() { return selectedClass; }
    public void setSelectedClass(String selectedClass) { this.selectedClass = selectedClass; }
    public ArenaScoreboard getScoreboard() { return scoreboard; }
    public void setScoreboard(ArenaScoreboard scoreboard) { this.scoreboard = scoreboard; }
    public Location getOriginalLocation() { return originalLocation; }
    public int getKills() { return kills; }
    public void addKill() { this.kills++; }

    // --- [MỚI] Hỗ trợ hiển thị danh sách kỹ năng QoL ---
    public Map<String, Integer> getOwnedUpgrades() {
        return activeUpgrades;
    }

    public int getUpgradeLevel(String upgradeId) {
        return activeUpgrades.getOrDefault(upgradeId, 0);
    }

    public void addUpgradeLevel(String upgradeId) {
        int newLvl = getUpgradeLevel(upgradeId) + 1;
        activeUpgrades.put(upgradeId, newLvl);
        matchUpgradesHistory.put(upgradeId, newLvl);
    }

    public Map<String, Integer> getMatchUpgradesHistory() {
        return matchUpgradesHistory;
    }

    public void removeCoins(int amount) {
        this.coins = Math.max(0, this.coins - amount);
    }
    public int getCurrentWaveRerolls() { return currentWaveRerolls; }
    public void incrementRerolls() { this.currentWaveRerolls++; }
    public void resetWaveRerolls() {
        this.currentWaveRerolls = 0;
        clearPinnedUpgrade();
    }

    public int getPinnedUpgradeSlot() { return pinnedUpgradeSlot; }
    public void setPinnedUpgradeSlot(int slot) { this.pinnedUpgradeSlot = slot; }
    public void clearPinnedUpgrade() { this.pinnedUpgradeSlot = -1; }

    public int getPendingUpgradeChoices() { return pendingUpgradeQueue.size(); }

    public void setPendingUpgradeChoices(int count) {
        pendingUpgradeQueue.clear();
        for (int i = 0; i < Math.max(0, count); i++) {
            pendingUpgradeQueue.addLast(UpgradeDraftType.NORMAL);
        }
    }

    public void addPendingUpgradeChoices(int count, UpgradeDraftType draftType) {
        UpgradeDraftType resolved = (draftType != null) ? draftType : UpgradeDraftType.NORMAL;
        for (int i = 0; i < Math.max(0, count); i++) {
            pendingUpgradeQueue.addLast(resolved);
        }
    }

    public void addPendingUpgradeChoices(int count, boolean isBossReward) {
        addPendingUpgradeChoices(count, isBossReward ? UpgradeDraftType.BOSS : UpgradeDraftType.NORMAL);
    }

    public UpgradeDraftType peekPendingDraftType() {
        UpgradeDraftType next = pendingUpgradeQueue.peekFirst();
        return (next != null) ? next : UpgradeDraftType.NORMAL;
    }

    public boolean isPendingBossReward() {
        return peekPendingDraftType() == UpgradeDraftType.BOSS;
    }

    public boolean isPendingSwarmReward() {
        return peekPendingDraftType() == UpgradeDraftType.SWARM;
    }

    public void decrementPendingUpgradeChoices() {
        pendingUpgradeQueue.pollFirst();
    }

    public double getDamageDealt() { return damageDealt; }
    public void addDamageDealt(double damage) { this.damageDealt += Math.max(0, damage); }

    public double getDamageTaken() { return damageTaken; }
    public void addDamageTaken(double damage) { this.damageTaken += Math.max(0, damage); }

    public int getBossKills() { return bossKills; }
    public void addBossKill() { this.bossKills++; }

    public int getRemainingPins() { return remainingPins; }
    public boolean usePin() {
        if (remainingPins > 0) {
            remainingPins--;
            return true;
        }
        return false;
    }

    public int getRemainingBanishes() { return remainingBanishes; }
    public boolean banishUpgrade(String upgradeId) {
        if (remainingBanishes > 0 && upgradeId != null) {
            remainingBanishes--;
            banishedUpgrades.add(upgradeId.toLowerCase());
            return true;
        }
        return false;
    }
    public boolean isBanished(String upgradeId) {
        return upgradeId != null && banishedUpgrades.contains(upgradeId.toLowerCase());
    }
    public Set<String> getBanishedUpgrades() { return banishedUpgrades; }

    public int getSlaughterhouseKills() { return slaughterhouseKills; }
    public void addSlaughterhouseKill() { this.slaughterhouseKills++; }

    public int getHephaestusRepairs() { return hephaestusRepairs; }
    public void addHephaestusRepairs(int amount) { this.hephaestusRepairs += amount; }

    public int getSoulHarvesterSouls() { return soulHarvesterSouls; }
    public void addSoulHarvesterSouls(int amount) { this.soulHarvesterSouls += amount; }

    public double getMidasGoldEarned() { return midasGoldEarned; }
    public void addMidasGoldEarned(double amount) { this.midasGoldEarned += amount; }

    public double getReflectedDamageTotal() { return reflectedDamageTotal; }
    public void addReflectedDamage(double amount) { this.reflectedDamageTotal += amount; }

    public double getTitanHeartShieldAbsorbed() { return titanHeartShieldAbsorbed; }
    public void addTitanHeartShieldAbsorbed(double amount) { this.titanHeartShieldAbsorbed += amount; }

    // [v8.2.0]: Over-cap stats and Pity/Super tracker
    public double getOvercapDamageBonusPercent() { return overcapDamageBonusPercent; }
    public void addOvercapDamageBonusPercent(double amount) { this.overcapDamageBonusPercent += amount; }

    public double getOvercapFlatTrueDmg() { return overcapFlatTrueDmg; }
    public void addOvercapFlatTrueDmg(double amount) { this.overcapFlatTrueDmg += amount; }

    public double getOvercapBonusSpeed() { return overcapBonusSpeed; }
    public void addOvercapBonusSpeed(double amount) { this.overcapBonusSpeed += amount; }

    public int getSuperUpgradesChosen() { return superUpgradesChosen; }
    public void incrementSuperUpgradesChosen() { this.superUpgradesChosen++; }

    public int getPityRollCounter() { return pityRollCounter; }
    public void incrementPityRollCounter() { this.pityRollCounter++; }
    public void resetPityRollCounter() { this.pityRollCounter = 0; }

    public void clearUpgrades() {
        activeUpgrades.clear();
        clearPinnedUpgrade();
        this.pendingUpgradeQueue.clear();
        this.overcapDamageBonusPercent = 0.0;
        this.overcapFlatTrueDmg = 0.0;
        this.overcapBonusSpeed = 0.0;
        this.superUpgradesChosen = 0;
        this.pityRollCounter = 0;
    }

    public double getUpgradeValue(String type, MobArenav2 plugin) {
        double total = 0.0;
        for (Map.Entry<String, Integer> entry : activeUpgrades.entrySet()) {
            UpgradeManager.UpgradeData data = plugin.getUpgradeManager().getUpgradeData(entry.getKey());
            if (data != null) {
                boolean matches = data.type.equalsIgnoreCase(type)
                        || (type.equalsIgnoreCase("CRITICAL_STRIKE") && data.type.equalsIgnoreCase("CRITICAL_CHANCE"))
                        || (type.equalsIgnoreCase("CRITICAL_CHANCE") && data.type.equalsIgnoreCase("CRITICAL_STRIKE"));
                if (matches) {
                    total += data.baseValue + (entry.getValue() - 1) * data.increment;
                }
            }
        }
        return total;
    }

    // ==========================================
    // SECURE LOCATION SAVING
    // ==========================================
    public void saveOriginalLocation(Player player) {
        // Only save if it's the FIRST time (When initially joining).
        // Absolutely do not overwrite if they are already in a match/spectating!
        if (this.originalLocation != null) return;
        this.originalLocation = player.getLocation();
    }

    public void cacheReward(String rewardString) {
        if (rewardString == null || rewardString.isEmpty()) return;
        String[] rewards = rewardString.split(",");
        for (String rew : rewards) {
            rew = rew.trim();
            if (rew.toLowerCase().startsWith("vault:")) {
                try {
                    double baseAmount = Double.parseDouble(rew.split(":")[1]);
                    // [v7.3 REWORK]: Midas Touch Multiplier (+30% -> +120%)
                    int midasLvl = getUpgradeLevel("midas_touch");
                    double multiplier = vn.meowchan12.mobarenav2.upgrade.handler.MidasTouchUpgrade.getMultiplier(midasLvl);
                    accumulatedVaultMoney += baseAmount * multiplier;
                } catch (NumberFormatException ignored) {}
            } else if (rew.toLowerCase().startsWith("cmd(")) {
                int start = rew.indexOf("(") + 1;
                int end = rew.lastIndexOf(")");
                if (start > 0 && end > start) {
                    pendingCommands.add(rew.substring(start, end));
                }
            }
        }
    }

    public void distributeRewards(MobArenav2 plugin) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null) return;
        boolean receivedAnything = false;

        if (accumulatedVaultMoney > 0 && plugin.getEconomy() != null) {
            plugin.getEconomy().depositPlayer(player, accumulatedVaultMoney);

            java.text.DecimalFormat df = new java.text.DecimalFormat("#,###");
            player.sendMessage("§a[MobArenav2] §7You received §e$" + df.format(accumulatedVaultMoney) + " §7for your efforts!");
            receivedAnything = true;
        }

        for (String cmd : pendingCommands) {
            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
            receivedAnything = true;
        }

        if (receivedAnything) {
            player.sendMessage("§a[MobArenav2] §7All accumulated rewards have been added to your account.");
        }

        accumulatedVaultMoney = 0.0;
        pendingCommands.clear();
        clearUpgrades();
        resetWaveRerolls();
    }
}