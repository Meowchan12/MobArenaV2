package vn.meowchan12.mobarenav2.arena;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import vn.meowchan12.mobarenav2.MobArenav2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClassSetupGUI {

    public static void open(Player player, MobArenav2 plugin, String className) {
        Inventory gui = Bukkit.createInventory(null, 54, Component.text("Setup Class: " + className));

        // 1. Tải Giáp (Armor) vào Hàng 1
        ItemStack[] armor = plugin.getClassManager().getClassArmor(className);
        if (armor[3] != null) gui.setItem(0, armor[3]);
        if (armor[2] != null) gui.setItem(1, armor[2]);
        if (armor[1] != null) gui.setItem(2, armor[1]);
        if (armor[0] != null) gui.setItem(3, armor[0]);

        // 2. Tải Items vào Hàng 2 -> 5
        List<ItemStack> items = plugin.getClassManager().getClassItems(className);
        int slot = 9;
        for (ItemStack item : items) {
            if (slot > 44) break;
            gui.setItem(slot++, item);
        }

        // 3. Tải Dữ liệu Giới hạn (Limit)
        int currentLimit = plugin.getClassManager().getClassLimit(className);
        ItemStack limitBtn = new ItemStack(Material.ANVIL);
        ItemMeta limitMeta = limitBtn.getItemMeta();
        limitMeta.setDisplayName("§e§lClass Limit");
        limitMeta.setLore(Arrays.asList(
                "§7Current Limit: §a" + currentLimit,
                "§fLeft-Click: §a+1",
                "§fRight-Click: §c-1",
                "§7(Set to -1 for Unlimited)"
        ));
        limitBtn.setItemMeta(limitMeta);
        gui.setItem(45, limitBtn);

        // 4. Tải Dữ liệu Upgrade Slots
        int currentUpgSlots = plugin.getUpgradeManager().getConfig().getInt("settings.class-slots." + className, plugin.getUpgradeManager().getConfig().getInt("settings.default-max-slots", 3));
        ItemStack upgBtn = new ItemStack(Material.NETHER_STAR);
        ItemMeta upgMeta = upgBtn.getItemMeta();
        upgMeta.setDisplayName("§d§lUpgrade Slots");
        upgMeta.setLore(Arrays.asList(
                "§7Current Slots: §a" + currentUpgSlots,
                "§fLeft-Click: §a+1",
                "§fRight-Click: §c-1"
        ));
        upgBtn.setItemMeta(upgMeta);
        gui.setItem(46, upgBtn);

        // 5. Nút Hướng dẫn Allowed Arenas
        ItemStack arenaBtn = new ItemStack(Material.MAP);
        ItemMeta arenaMeta = arenaBtn.getItemMeta();
        arenaMeta.setDisplayName("§b§lAllowed Arenas");
        arenaMeta.setLore(Arrays.asList(
                "§7To restrict this class to specific arenas,",
                "§7please edit the §f'allowed-arenas' §7list directly",
                "§7in the §fclasses.yml §7file."
        ));
        arenaBtn.setItemMeta(arenaMeta);
        gui.setItem(47, arenaBtn);

        // 6. [MỚI] Nút Quản lý Hiệu ứng (Effects)
        ItemStack effBtn = new ItemStack(Material.POTION);
        ItemMeta effMeta = effBtn.getItemMeta();
        effMeta.setDisplayName("§b§lEffect Manager");
        List<String> effLore = new ArrayList<>();
        String effs = plugin.getConfigManager().getClasses().getString("classes." + className + ".effects", "");
        effLore.add("§7Current Effects:");
        if (effs.isEmpty()) {
            effLore.add(" §cNone");
        } else {
            for (String e : effs.split(",")) {
                effLore.add(" §a• " + e.trim());
            }
        }
        effLore.add("");
        effLore.add("§fLeft/Middle-Click: §aAdd / Edit Effect");
        effLore.add("§fRight-Click: §cRemove Effect");
        effMeta.setLore(effLore);
        effBtn.setItemMeta(effMeta);
        gui.setItem(48, effBtn);

        // 7. [MỚI] Nút Tự Động Bơm Máu Đầu Game
        boolean heal = plugin.getConfigManager().getClasses().getBoolean("classes." + className + ".heal-on-start", true);
        ItemStack healBtn = new ItemStack(heal ? Material.ENCHANTED_GOLDEN_APPLE : Material.APPLE);
        ItemMeta healMeta = healBtn.getItemMeta();
        healMeta.setDisplayName("§c§lHeal on Start");
        healMeta.setLore(Arrays.asList(
                "§7Current: " + (heal ? "§aEnabled" : "§cDisabled"),
                "",
                "§7Fully restores health when",
                "§7the player selects this class.",
                "§7(Useful when using Health Boost)",
                "",
                "§fClick to toggle!"
        ));
        healBtn.setItemMeta(healMeta);
        gui.setItem(49, healBtn);

        // 8. Nút SAVE
        ItemStack saveBtn = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta saveMeta = saveBtn.getItemMeta();
        saveMeta.setDisplayName("§a§lSave & Apply");
        saveMeta.setLore(Arrays.asList("§7Click to save this loadout!"));
        saveBtn.setItemMeta(saveMeta);
        gui.setItem(53, saveBtn);

        player.openInventory(gui);
    }
}