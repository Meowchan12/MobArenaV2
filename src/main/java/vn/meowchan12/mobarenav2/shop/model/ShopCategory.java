package vn.meowchan12.mobarenav2.shop.model;

import org.bukkit.Material;
import java.util.List;

public class ShopCategory {

    private final String id;
    private final String displayName;
    private final Material material;
    private final int slot;
    private final List<String> lore;

    public ShopCategory(String id, String displayName, Material material, int slot, List<String> lore) {
        this.id = id;
        this.displayName = displayName;
        this.material = material != null ? material : Material.CHEST;
        this.slot = slot;
        this.lore = lore;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Material getMaterial() { return material; }
    public int getSlot() { return slot; }
    public List<String> getLore() { return lore; }
}
