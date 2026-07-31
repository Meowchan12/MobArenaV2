package vn.meowchan12.mobarenav2.shop.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerShopData {

    private final UUID playerUUID;
    private final Map<String, Integer> itemLevels = new HashMap<>();

    public PlayerShopData(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public Map<String, Integer> getItemLevels() { return itemLevels; }

    public int getItemLevel(String itemId) {
        return itemLevels.getOrDefault(itemId, 0);
    }

    public void setItemLevel(String itemId, int level) {
        itemLevels.put(itemId, level);
    }

    public void incrementItemLevel(String itemId) {
        setItemLevel(itemId, getItemLevel(itemId) + 1);
    }
}
