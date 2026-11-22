package vn.meowchan12.mobarenav2.listener;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ClassSetupGUI;
import vn.meowchan12.mobarenav2.arena.UpgradeGUI;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GUIListener implements Listener {

    private final MobArenav2 plugin;

    public static final Map<UUID, String> pendingEffectAction = new HashMap<>();
    public static final Map<UUID, String> pendingEffectClass = new HashMap<>();

    // [MỚI] Danh sách ghi chú người chơi đã hoàn thành việc chọn Upgrade
    public static final Set<UUID> completedUpgradeSelection = new HashSet<>();

    public GUIListener(MobArenav2 plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (pendingEffectAction.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            String action = pendingEffectAction.remove(player.getUniqueId());
            String className = pendingEffectClass.remove(player.getUniqueId());
            String input = event.getMessage().trim();

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (input.equalsIgnoreCase("cancel")) {
                    player.sendMessage("§c[MobArenav2] Action cancelled.");
                    ClassSetupGUI.open(player, plugin, className);
                    return;
                }

                FileConfiguration config = plugin.getConfigManager().getClasses();
                String path = "classes." + className + ".effects";
                String currentEffects = config.getString(path, "");

                List<String> effList = new ArrayList<>();
                if (currentEffects != null && !currentEffects.isEmpty()) {
                    for (String e : currentEffects.split(",")) {
                        effList.add(e.trim());
                    }
                }

                if (action.equals("ADD")) {
                    String[] parts = input.split(":");
                    if (parts.length < 2) {
                        player.sendMessage("§c[MobArenav2] Invalid format! Use EFFECT:LEVEL (e.g., SPEED:1).");
                        ClassSetupGUI.open(player, plugin, className);
                        return;
                    }
                    String effName = parts[0].toUpperCase();
                    effList.removeIf(e -> e.toUpperCase().startsWith(effName + ":"));
                    effList.add(input.toUpperCase());
                    player.sendMessage("§a[MobArenav2] Successfully saved effect: §e" + input.toUpperCase());

                } else if (action.equals("REMOVE")) {
                    String effName = input.toUpperCase();
                    boolean removed = effList.removeIf(e -> e.toUpperCase().startsWith(effName + ":") || e.toUpperCase().equals(effName));
                    if (removed) {
                        player.sendMessage("§a[MobArenav2] Successfully removed effect: §e" + effName);
                    } else {
                        player.sendMessage("§c[MobArenav2] Effect '" + effName + "' not found.");
                    }
                }

                config.set(path, effList.isEmpty() ? null : String.join(", ", effList));
                try {
                    config.save(new File(plugin.getDataFolder(), "classes.yml"));
                    plugin.getClassManager().loadClasses();
                } catch (Exception ignored) {}

                ClassSetupGUI.open(player, plugin, className);
            });
        }
    }

    // ==========================================
    // VÁ LỖI TẮT MENU UPGRADE BẰNG NÚT ESC (BUG #4)
    // ==========================================
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (vn.meowchan12.mobarenav2.arena.UpgradeGUI.isUpgradeSelectionTitle(title, plugin)) {
            if (!completedUpgradeSelection.contains(player.getUniqueId())) {
                player.sendActionBar(vn.meowchan12.mobarenav2.utils.ColorUtils.toAdventure("&e&l[⚡ Upgrade paused: Use &a&l/ma upgrade &e&lanytime to claim!]"));
            } else {
                completedUpgradeSelection.remove(player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        if (title.contains("UPGRADE DIRECTORY")) {
            event.setCancelled(true);
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.BARRIER) {
                player.closeInventory();
            }
            return;
        }

        if (title.contains("Match Details:") || title.contains("Chi Tiết Trận Đấu:")) {
            event.setCancelled(true);
            if (event.getCurrentItem() != null && (event.getCurrentItem().getType() == Material.BARRIER || event.getCurrentItem().getType() == Material.ARROW)) {
                player.closeInventory();
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            return;
        }

        if (title.contains("Select your Class")) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || !clickedItem.hasItemMeta()) return;

            boolean isAdminBypass = player.hasPermission("mobarenav2.bypass.class") || player.hasPermission("mobarenav2.admin");
            if (clickedItem.getType() == Material.BARRIER && !isAdminBypass) {
                player.sendMessage("§c[MobArenav2] This class is restricted or full!");
                return;
            }

            NamespacedKey key = new NamespacedKey(plugin, "class_id");
            if (clickedItem.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                String className = clickedItem.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
                boolean success = plugin.getClassManager().equipClass(player, className);
                if (success) {
                    player.closeInventory();
                    player.sendMessage("§eType §a/ma ready §ewhen you are prepared to fight!");
                }
            }
        }

        else if (title.contains("Endless Mode") || title.contains("Vote")) {
            event.setCancelled(true);
            Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
            if (arena == null || !arena.isVoting()) {
                player.closeInventory();
                return;
            }
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || !clickedItem.hasItemMeta()) return;

            NamespacedKey voteKey = new NamespacedKey(plugin, vn.meowchan12.mobarenav2.arena.VoteGUI.VOTE_PDC_KEY);
            String choice = clickedItem.getItemMeta().getPersistentDataContainer().get(voteKey, PersistentDataType.STRING);

            if ("yes".equalsIgnoreCase(choice) || clickedItem.getType() == Material.EMERALD_BLOCK || clickedItem.getType() == Material.LIME_WOOL || event.getRawSlot() == 11) {
                plugin.getWaveManager().castVote(player, arena, true);
                player.closeInventory();
            } else if ("no".equalsIgnoreCase(choice) || clickedItem.getType() == Material.REDSTONE_BLOCK || clickedItem.getType() == Material.RED_WOOL || event.getRawSlot() == 15) {
                plugin.getWaveManager().castVote(player, arena, false);
                player.closeInventory();
            }
        }

        else if (title.contains("Setup Class:")) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                player.sendMessage("§c[MobArenav2] Please drag and drop items manually instead of shift-clicking!");
                return;
            }

            int slot = event.getRawSlot();
            if ((slot >= 0 && slot <= 44) || slot >= 54) {
                return;
            }

            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || !clickedItem.hasItemMeta()) return;

            Inventory topInv = event.getView().getTopInventory();
            String className = ChatColor.stripColor(title).split(":")[1].replace("]", "").trim();

            if (slot == 45) {
                try {
                    String loreLine = ChatColor.stripColor(clickedItem.getItemMeta().getLore().get(0));
                    int currentLimit = Integer.parseInt(loreLine.split(":")[1].trim());
                    if (event.isLeftClick()) currentLimit++;
                    else if (event.isRightClick()) currentLimit--;
                    if (currentLimit < -1) currentLimit = -1;

                    ItemMeta meta = clickedItem.getItemMeta();
                    meta.setLore(Arrays.asList(
                            "§7Current Limit: §a" + currentLimit,
                            "§fLeft-Click: §a+1",
                            "§fRight-Click: §c-1",
                            "§7(Set to -1 for Unlimited)"
                    ));
                    clickedItem.setItemMeta(meta);
                    topInv.setItem(slot, clickedItem);
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                } catch (Exception ignored) {}
            }

            if (slot == 46) {
                try {
                    String loreLine = ChatColor.stripColor(clickedItem.getItemMeta().getLore().get(0));
                    int currentUpg = Integer.parseInt(loreLine.split(":")[1].trim());
                    if (event.isLeftClick()) currentUpg++;
                    else if (event.isRightClick()) currentUpg--;
                    if (currentUpg < 0) currentUpg = 0;

                    ItemMeta meta = clickedItem.getItemMeta();
                    meta.setLore(Arrays.asList(
                            "§7Current Slots: §a" + currentUpg,
                            "§fLeft-Click: §a+1",
                            "§fRight-Click: §c-1"
                    ));
                    clickedItem.setItemMeta(meta);
                    topInv.setItem(slot, clickedItem);
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                } catch (Exception ignored) {}
            }

            if (slot == 48) {
                saveLoadoutSilently(plugin, topInv, className);

                if (event.isLeftClick() || event.getClick() == org.bukkit.event.inventory.ClickType.MIDDLE) {
                    pendingEffectAction.put(player.getUniqueId(), "ADD");
                    pendingEffectClass.put(player.getUniqueId(), className);
                    player.closeInventory();
                    player.sendMessage("§a[MobArenav2] §bType the effect to ADD or EDIT in chat.");
                    player.sendMessage("§7Format: §fEFFECT_NAME:LEVEL §7(Example: SPEED:1)");
                    player.sendMessage("§cType 'cancel' to abort.");
                } else if (event.isRightClick()) {
                    pendingEffectAction.put(player.getUniqueId(), "REMOVE");
                    pendingEffectClass.put(player.getUniqueId(), className);
                    player.closeInventory();
                    player.sendMessage("§c[MobArenav2] §bType the effect name to REMOVE in chat.");
                    player.sendMessage("§7Example: SPEED");
                    player.sendMessage("§cType 'cancel' to abort.");
                }
            }

            if (slot == 49) {
                boolean isCurrentlyEnabled = clickedItem.getType() == Material.ENCHANTED_GOLDEN_APPLE;
                boolean newState = !isCurrentlyEnabled;

                clickedItem.setType(newState ? Material.ENCHANTED_GOLDEN_APPLE : Material.APPLE);
                ItemMeta meta = clickedItem.getItemMeta();
                meta.setLore(Arrays.asList(
                        "§7Current: " + (newState ? "§aEnabled" : "§cDisabled"),
                        "",
                        "§7Fully restores health when",
                        "§7the player selects this class.",
                        "§7(Useful when using Health Boost)",
                        "",
                        "§fClick to toggle!"
                ));
                clickedItem.setItemMeta(meta);
                topInv.setItem(slot, clickedItem);
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
            }

            if (slot == 53) {
                try {
                    saveLoadoutSilently(plugin, topInv, className);
                    player.closeInventory();
                    player.sendMessage("§a[MobArenav2] Successfully saved Class loadout for: §e" + className);
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                } catch (Exception ex) {
                    player.sendMessage("§c[MobArenav2] An error occurred while saving! Please check the console.");
                    ex.printStackTrace();
                }
            }
        }
    }

    private void saveLoadoutSilently(MobArenav2 plugin, Inventory topInv, String className) {
        try {
            String limitLore = ChatColor.stripColor(topInv.getItem(45).getItemMeta().getLore().get(0));
            int limit = Integer.parseInt(limitLore.split(":")[1].trim());

            String upgLore = ChatColor.stripColor(topInv.getItem(46).getItemMeta().getLore().get(0));
            int upgSlots = Integer.parseInt(upgLore.split(":")[1].trim());

            boolean healOnStart = topInv.getItem(49) != null && topInv.getItem(49).getType() == Material.ENCHANTED_GOLDEN_APPLE;

            ItemStack[] armor = new ItemStack[4];
            armor[3] = topInv.getItem(0);
            armor[2] = topInv.getItem(1);
            armor[1] = topInv.getItem(2);
            armor[0] = topInv.getItem(3);

            List<ItemStack> items = new ArrayList<>();
            for (int i = 9; i <= 44; i++) {
                ItemStack it = topInv.getItem(i);
                if (it != null && it.getType() != Material.AIR) items.add(it);
            }

            if (items.isEmpty()) items.add(new ItemStack(Material.WOODEN_SWORD));

            plugin.getClassManager().saveClassSetup(className, armor, items, limit, healOnStart);
            plugin.getUpgradeManager().saveClassSlotLimit(className, upgSlots);
        } catch (Exception ignored) {}
    }
}