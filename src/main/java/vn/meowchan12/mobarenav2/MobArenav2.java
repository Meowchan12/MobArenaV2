package vn.meowchan12.mobarenav2;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.UpgradeGUI;
import vn.meowchan12.mobarenav2.command.ArenaCommand;
import vn.meowchan12.mobarenav2.listener.ArenaCombatListener;
import vn.meowchan12.mobarenav2.listener.ArenaMobCombatListener;
import vn.meowchan12.mobarenav2.listener.ArenaPlayerDeathListener;
import vn.meowchan12.mobarenav2.listener.ArenaProtectionListener;
import vn.meowchan12.mobarenav2.listener.GUIListener;
import vn.meowchan12.mobarenav2.listener.MobAIListener;
import vn.meowchan12.mobarenav2.listener.PlayerConnectionListener;
import vn.meowchan12.mobarenav2.listener.SetupListener;
import vn.meowchan12.mobarenav2.listener.SupplyListener;
import vn.meowchan12.mobarenav2.manager.*;
import vn.meowchan12.mobarenav2.service.CelebrationService;
import vn.meowchan12.mobarenav2.service.UpgradeRerollService;

public final class MobArenav2 extends JavaPlugin {

    private static MobArenav2 instance;
    private Economy econ = null;

    private ConfigManager configManager;
    private ArenaManager arenaManager;
    private SetupManager setupManager;
    private ClassManager classManager;
    private UpgradeManager upgradeManager;
    private InventoryBackupManager inventoryBackupManager;
    private UserDataManager userDataManager;
    private SupplyManager supplyManager;
    private WaveManager waveManager;
    private BossManager bossManager;
    private vn.meowchan12.mobarenav2.boss.BossRewardManager bossRewardManager;
    private vn.meowchan12.mobarenav2.shop.manager.ShopManager shopManager;
    private CelebrationService celebrationService;
    private UpgradeRerollService upgradeRerollService;
    private vn.meowchan12.mobarenav2.service.MobSpawnerService mobSpawnerService;
    private vn.meowchan12.mobarenav2.service.ArenaRewardService arenaRewardService;
    private vn.meowchan12.mobarenav2.service.NotificationService notificationService;
    private vn.meowchan12.mobarenav2.upgrade.SynergyManager synergyManager;
    private vn.meowchan12.mobarenav2.boss.skill.BossSkillRegistry bossSkillRegistry;
    private vn.meowchan12.mobarenav2.upgrade.UpgradeRegistry upgradeRegistry;
    private vn.meowchan12.mobarenav2.mob.MobRegistry mobRegistry;
    private vn.meowchan12.mobarenav2.shop.ultimate.UltimateManager ultimateManager;
    private vn.meowchan12.mobarenav2.database.DatabaseManager databaseManager;
    private vn.meowchan12.mobarenav2.arena.ArenaMemoryManager arenaMemoryManager;
    private vn.meowchan12.mobarenav2.service.SupportMobService supportMobService;
    private vn.meowchan12.mobarenav2.upgrade.fusion.FusionManager fusionManager;
    private MessageManager messageManager;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Khởi tạo Economy (Vault Hook)
        if (!setupEconomy()) {
            getLogger().severe("Disabled due to no Vault dependency found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        try {
            Class.forName("org.bukkit.attribute.Attribute$Builder"); // Available in very new versions, or checking version string
            String version = Bukkit.getVersion();
            if (version.contains("1.21.2") || version.contains("1.21.3") || version.contains("1.21.4")) {
                getLogger().info("==================================================");
                getLogger().info("[MobArenav2] Detected Paper/Spigot 1.21.2+ API.");
                getLogger().info("[MobArenav2] Next-Gen features and dynamic attributes activated!");
                getLogger().info("==================================================");
            }
        } catch (Exception ignored) {}

        // 2. Tải toàn bộ cấu hình
        this.configManager = new ConfigManager(this);
        this.configManager.loadAllConfigs();
        this.messageManager = new MessageManager(this);

        // 2.5 Khởi tạo Database Manager & Auto Migration
        this.databaseManager = new vn.meowchan12.mobarenav2.database.DatabaseManager(this);
        this.databaseManager.init();

        // 3. Khởi tạo các Manager
        this.inventoryBackupManager = new InventoryBackupManager(this);
        this.userDataManager = new UserDataManager(this);
        this.classManager = new ClassManager(this);
        this.upgradeManager = new UpgradeManager(this);
        this.supplyManager = new SupplyManager(this);
        this.bossManager = new BossManager(this);
        this.bossRewardManager = new vn.meowchan12.mobarenav2.boss.BossRewardManager(this);
        this.waveManager = new WaveManager(this);
        this.bossSkillRegistry = new vn.meowchan12.mobarenav2.boss.skill.BossSkillRegistry(this);
        this.upgradeRegistry = new vn.meowchan12.mobarenav2.upgrade.UpgradeRegistry(this);
        this.mobRegistry = new vn.meowchan12.mobarenav2.mob.MobRegistry(this);
        this.celebrationService = new CelebrationService(this);
        this.upgradeRerollService = new UpgradeRerollService(this);
        this.mobSpawnerService = new vn.meowchan12.mobarenav2.service.MobSpawnerService(this);
        this.arenaRewardService = new vn.meowchan12.mobarenav2.service.ArenaRewardService(this);
        this.notificationService = new vn.meowchan12.mobarenav2.service.NotificationService(this);
        this.supportMobService = new vn.meowchan12.mobarenav2.service.SupportMobService(this);
        this.fusionManager = new vn.meowchan12.mobarenav2.upgrade.fusion.FusionManager(this);
        this.synergyManager = new vn.meowchan12.mobarenav2.upgrade.SynergyManager(this);
        this.shopManager = new vn.meowchan12.mobarenav2.shop.manager.ShopManager(this);
        this.ultimateManager = new vn.meowchan12.mobarenav2.shop.ultimate.UltimateManager(this);
        this.arenaMemoryManager = new vn.meowchan12.mobarenav2.arena.ArenaMemoryManager(this);
        this.arenaManager = new ArenaManager(this);
        this.setupManager = new SetupManager();

        // Nạp dữ liệu các Arena hiện có
        this.arenaManager.loadArenas();

        // 4. Đăng ký toàn bộ Event Listeners
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new SetupListener(this, this.setupManager), this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaPlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new SupplyListener(this), this);
        getServer().getPluginManager().registerEvents(new MobAIListener(this, this.arenaManager), this);
        getServer().getPluginManager().registerEvents(new vn.meowchan12.mobarenav2.pipeline.CombatPipeline(this), this);
        getServer().getPluginManager().registerEvents(new UpgradeGUI(this), this);
        getServer().getPluginManager().registerEvents(new vn.meowchan12.mobarenav2.arena.FusionDraftGUI(this), this);
        getServer().getPluginManager().registerEvents(new vn.meowchan12.mobarenav2.arena.UltimatePreviewGUI(this), this);
        getServer().getPluginManager().registerEvents(new vn.meowchan12.mobarenav2.shop.gui.ShopGUI(this, this.shopManager), this);
        getServer().getPluginManager().registerEvents(new vn.meowchan12.mobarenav2.shop.ultimate.UltimateListener(this, this.ultimateManager), this);

        // 5. Đăng ký Command chính
        ArenaCommand arenaCmd = new ArenaCommand(this);
        if (getCommand("ma") != null) {
            getCommand("ma").setExecutor(arenaCmd);
            getCommand("ma").setTabCompleter(arenaCmd);
        }
        if (getCommand("mobarena") != null) {
            getCommand("mobarena").setExecutor(arenaCmd);
            getCommand("mobarena").setTabCompleter(arenaCmd);
        }

        // --------------------------------------------------
        // 6. ĐĂNG KÝ PLACEHOLDERAPI EXPANSION
        // --------------------------------------------------
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ArenaExpansion(this).register();
            getLogger().info("[MobArenav2] Successfully hooked and registered PlaceholderAPI expansion!");
        } else {
            getLogger().warning("[MobArenav2] PlaceholderAPI not found! Fallback to standalone mode.");
        }

        // 7. Thông báo khởi động thành công
        getServer().getConsoleSender().sendMessage(Component.text("[MobArenav2] Successfully enabled - Ready for Battle!").color(NamedTextColor.GREEN));
    }

    @Override
    public void onDisable() {
        // Đảm bảo dọn dẹp và kết thúc an toàn mọi Arena đang chạy dở khi server tắt hoặc reload
        for (Arena arena : arenaManager.getAllArenas()) {
            if (arena.isRunning()) {
                arena.endArena(this);
            }
        }
        if (upgradeRegistry != null) {
            upgradeRegistry.clear();
        }
        if (bossSkillRegistry != null) {
            bossSkillRegistry.clear();
        }
        if (mobRegistry != null) {
            mobRegistry.clear();
        }
        if (supportMobService != null) {
            supportMobService.shutdown();
        }
        if (userDataManager != null) {
            userDataManager.saveAllDirtyNow();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getServer().getConsoleSender().sendMessage(Component.text("[MobArenav2] Safely disabled and all arenas cleared.").color(NamedTextColor.RED));
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    // ==========================================
    // GETTERS CHO CÁC MANAGER
    // ==========================================
    public static MobArenav2 getInstance() { return instance; }
    public Economy getEconomy() { return econ; }
    public ConfigManager getConfigManager() { return configManager; }
    public ArenaManager getArenaManager() { return arenaManager; }
    public SetupManager getSetupManager() { return setupManager; }
    public ClassManager getClassManager() { return classManager; }
    public UpgradeManager getUpgradeManager() { return upgradeManager; }
    public InventoryBackupManager getInventoryBackupManager() { return inventoryBackupManager; }
    public UserDataManager getUserDataManager() { return userDataManager; }
    public SupplyManager getSupplyManager() { return supplyManager; }
    public WaveManager getWaveManager() { return waveManager; }
    public BossManager getBossManager() { return bossManager; }
    public vn.meowchan12.mobarenav2.boss.BossRewardManager getBossRewardManager() { return bossRewardManager; }
    public vn.meowchan12.mobarenav2.shop.manager.ShopManager getShopManager() { return shopManager; }
    public CelebrationService getCelebrationService() { return celebrationService; }
    public UpgradeRerollService getUpgradeRerollService() { return upgradeRerollService; }
    public vn.meowchan12.mobarenav2.service.MobSpawnerService getMobSpawnerService() { return mobSpawnerService; }
    public vn.meowchan12.mobarenav2.service.ArenaRewardService getArenaRewardService() { return arenaRewardService; }
    public vn.meowchan12.mobarenav2.service.NotificationService getNotificationService() { return notificationService; }
    public vn.meowchan12.mobarenav2.upgrade.SynergyManager getSynergyManager() { return synergyManager; }
    public vn.meowchan12.mobarenav2.boss.skill.BossSkillRegistry getBossSkillRegistry() { return bossSkillRegistry; }
    public vn.meowchan12.mobarenav2.upgrade.UpgradeRegistry getUpgradeRegistry() { return upgradeRegistry; }
    public vn.meowchan12.mobarenav2.mob.MobRegistry getMobRegistry() { return mobRegistry; }
    public vn.meowchan12.mobarenav2.shop.ultimate.UltimateManager getUltimateManager() { return ultimateManager; }
    public vn.meowchan12.mobarenav2.database.DatabaseManager getDatabaseManager() { return databaseManager; }
    public vn.meowchan12.mobarenav2.arena.ArenaMemoryManager getArenaMemoryManager() { return arenaMemoryManager; }
    public vn.meowchan12.mobarenav2.service.SupportMobService getSupportMobService() { return supportMobService; }
    public vn.meowchan12.mobarenav2.upgrade.fusion.FusionManager getFusionManager() { return fusionManager; }
    public MessageManager getMessageManager() { return messageManager; }
}