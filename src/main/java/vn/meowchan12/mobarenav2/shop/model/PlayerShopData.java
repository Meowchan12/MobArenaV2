package vn.meowchan12.mobarenav2.shop.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerShopData {

    private final UUID playerUUID;
    private final Map<String, Integer> itemLevels = new HashMap<>();
    private int rerollTickets = 0;
    private String equippedUltimate = null;

    public PlayerShopData(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public UUID getUuid() { return playerUUID; }
    public Map<String, Integer> getItemLevels() { return itemLevels; }
    public Map<String, Integer> getPurchasedItemLevels() { return itemLevels; }

    public int getItemLevel(String itemId) {
        return itemLevels.getOrDefault(itemId, 0);
    }

    public void setItemLevel(String itemId, int level) {
        itemLevels.put(itemId, level);
    }

    public void incrementItemLevel(String itemId) {
        setItemLevel(itemId, getItemLevel(itemId) + 1);
    }

    public int getRerollTickets() {
        return rerollTickets;
    }

    public void setRerollTickets(int rerollTickets) {
        this.rerollTickets = Math.max(0, rerollTickets);
    }

    public void addRerollTickets(int amount) {
        this.rerollTickets = Math.max(0, this.rerollTickets + amount);
    }

    public boolean useRerollTicket() {
        if (rerollTickets > 0) {
            rerollTickets--;
            return true;
        }
        return false;
    }

    public boolean hasPinPermit() {
        if (getItemLevel("upgrade_pin_permit") > 0) return true;
        for (Map.Entry<String, Integer> e : itemLevels.entrySet()) {
            if (e.getValue() > 0 && e.getKey().toLowerCase().contains("pin_permit")) return true;
        }
        return false;
    }

    public boolean hasHephaestusLicense() {
        if (getItemLevel("hephaestus_license") > 0 || getItemLevel("casino_hephaestus_license") > 0) return true;
        for (Map.Entry<String, Integer> e : itemLevels.entrySet()) {
            if (e.getValue() > 0 && e.getKey().toLowerCase().contains("hephaestus")) return true;
        }
        return false;
    }

    public String getEquippedUltimate() {
        return equippedUltimate;
    }

    public void setEquippedUltimate(String equippedUltimate) {
        this.equippedUltimate = equippedUltimate;
    }
}
