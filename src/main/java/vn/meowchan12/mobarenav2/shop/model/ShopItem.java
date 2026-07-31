package vn.meowchan12.mobarenav2.shop.model;

import org.bukkit.Material;
import java.util.List;

public class ShopItem {

    private final String id;
    private final String categoryId;
    private final int slot;
    private final String displayName;
    private final Material material;
    private final List<String> lore;

    // Pricing & Limits
    private final String priceType; // VAULT, PLAYERPOINTS, CUSTOM_COMMAND
    private final double baseCost;
    private final double costMultiplier; // Scale factor per level (e.g., 1.5x)
    private final int maxLevel;

    // Stat/Perk values
    private final String attributeName; // e.g. GENERIC_MAX_HEALTH
    private final double baseValue;
    private final double incrementValue;

    // Input Requirement Check (for Custom Casino/PAPI)
    private final String inputCheckPlaceholder;
    private final double inputCheckRequiredValue;
    private final String inputCheckFailMessage;
    private final String inputTakeCommand;

    // Output Actions (commands, permissions, sounds)
    private final List<String> outputActions;

    public ShopItem(String id, String categoryId, int slot, String displayName, Material material, List<String> lore,
                    String priceType, double baseCost, double costMultiplier, int maxLevel,
                    String attributeName, double baseValue, double incrementValue,
                    String inputCheckPlaceholder, double inputCheckRequiredValue, String inputCheckFailMessage,
                    String inputTakeCommand, List<String> outputActions) {
        this.id = id;
        this.categoryId = categoryId;
        this.slot = slot;
        this.displayName = displayName;
        this.material = material != null ? material : Material.STONE;
        this.lore = lore;
        this.priceType = priceType != null ? priceType.toUpperCase() : "VAULT";
        this.baseCost = baseCost;
        this.costMultiplier = costMultiplier > 0 ? costMultiplier : 1.5;
        this.maxLevel = Math.max(1, maxLevel);
        this.attributeName = attributeName;
        this.baseValue = baseValue;
        this.incrementValue = incrementValue;
        this.inputCheckPlaceholder = inputCheckPlaceholder;
        this.inputCheckRequiredValue = inputCheckRequiredValue;
        this.inputCheckFailMessage = inputCheckFailMessage;
        this.inputTakeCommand = inputTakeCommand;
        this.outputActions = outputActions;
    }

    public String getId() { return id; }
    public String getCategoryId() { return categoryId; }
    public int getSlot() { return slot; }
    public String getDisplayName() { return displayName; }
    public Material getMaterial() { return material; }
    public List<String> getLore() { return lore; }
    public String getPriceType() { return priceType; }
    public double getBaseCost() { return baseCost; }
    public double getCostMultiplier() { return costMultiplier; }
    public int getMaxLevel() { return maxLevel; }
    public String getAttributeName() { return attributeName; }
    public double getBaseValue() { return baseValue; }
    public double getIncrementValue() { return incrementValue; }
    public String getInputCheckPlaceholder() { return inputCheckPlaceholder; }
    public double getInputCheckRequiredValue() { return inputCheckRequiredValue; }
    public String getInputCheckFailMessage() { return inputCheckFailMessage; }
    public String getInputTakeCommand() { return inputTakeCommand; }
    public List<String> getOutputActions() { return outputActions; }

    public double calculateCostForLevel(int targetLevel) {
        if (targetLevel <= 1) return baseCost;
        return baseCost * Math.pow(costMultiplier, targetLevel - 1);
    }

    public double calculateStatForLevel(int level) {
        if (level <= 0) return 0.0;
        return baseValue + (level - 1) * incrementValue;
    }
}
