package vn.meowchan12.mobarenav2.shop.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ShopHolder implements InventoryHolder {

    private final String categoryId; // null for Main Shop menu, categoryId for Category Shop submenus

    public ShopHolder(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public boolean isMainShop() {
        return categoryId == null;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
