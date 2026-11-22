package vn.meowchan12.mobarenav2.service;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;

import java.util.UUID;

/**
 * [v8.1.0]: Master Wave State Machine Engine.
 * Responsible strictly for tick pacing, state transitions, and coordination between
 * MobSpawnerService, ArenaRewardService, and NotificationService.
 */
public class WaveEngine {

    public enum EngineState {
        IDLE,
        INTERMISSION,
        ACTIVE_COMBAT,
        UPGRADE_SELECTION,
        VOTING,
        FINISHED
    }

    private final MobArenav2 plugin;
    private final Arena arena;
    private final MobSpawnerService spawnerService;
    private final ArenaRewardService rewardService;
    private final NotificationService notificationService;

    private EngineState state = EngineState.IDLE;
    private BukkitTask engineTask;

    private int tickCounter = 0;
    private int intermissionSecondsLeft = 5;
    private int activeWaveSeconds = 0;

    public WaveEngine(MobArenav2 plugin, Arena arena, MobSpawnerService spawnerService, ArenaRewardService rewardService, NotificationService notificationService) {
        this.plugin = plugin;
        this.arena = arena;
        this.spawnerService = spawnerService;
        this.rewardService = rewardService;
        this.notificationService = notificationService;
    }

    public void start() {
        if (engineTask != null) engineTask.cancel();
        state = EngineState.INTERMISSION;
        intermissionSecondsLeft = 5;

        engineTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!arena.isRunning() || arena.getActivePlayers().isEmpty()) {
                    stop();
                    return;
                }
                tick();
            }
        }.runTaskTimer(plugin, 10L, 10L); // 10 ticks = 0.5s cadence
    }

    public void stop() {
        if (engineTask != null) {
            engineTask.cancel();
            engineTask = null;
        }
        state = EngineState.FINISHED;
    }

    private void tick() {
        tickCounter++;
        switch (state) {
            case INTERMISSION -> tickIntermission();
            case UPGRADE_SELECTION -> tickUpgradeSelection();
            case ACTIVE_COMBAT -> tickActiveCombat();
            case VOTING -> tickVoting();
            default -> {}
        }
    }

    private void tickIntermission() {
        if (tickCounter % 2 == 0) { // Once per second
            intermissionSecondsLeft--;
            for (UUID uuid : arena.getActivePlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    notificationService.sendActionbar(p, "&e⚔ Next Wave starting in &6&l" + intermissionSecondsLeft + "s&e...");
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f);
                }
            }

            if (intermissionSecondsLeft <= 0) {
                transitionToCombat();
            }
        }
    }

    private void tickUpgradeSelection() {
        boolean allChosen = true;
        for (UUID uuid : arena.getActivePlayers()) {
            ArenaPlayerProfile profile = arena.getPlayerProfiles().get(uuid);
            if (profile != null && profile.getPendingUpgradeChoices() > 0) {
                allChosen = false;
                break;
            }
        }

        if (allChosen || tickCounter >= 60) { // 30s timeout
            arena.setUpgradePhase(false);
            state = EngineState.INTERMISSION;
            intermissionSecondsLeft = 5;
            arena.broadcastMessage("§aAll upgrades selected! Next wave in 5 seconds.");
        }
    }

    private void tickActiveCombat() {
        if (tickCounter % 2 == 0) {
            activeWaveSeconds++;
        }
        arena.getActiveMobs().removeIf(mob -> mob == null || !mob.isValid() || mob.isDead());
        int mobsRemaining = arena.getActiveMobs().size();

        for (UUID uuid : arena.getActivePlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                notificationService.sendActionbar(p, "&c⚔ Wave &e#" + arena.getCurrentWave() + " &7| &fMobs Alive: &c&l" + mobsRemaining);
            }
        }

        if (mobsRemaining == 0 && arena.getSpawnQueue().isEmpty()) {
            transitionToWaveCleared();
        }
    }

    private void tickVoting() {
        // Handled via VoteGUI
    }

    private void transitionToCombat() {
        state = EngineState.ACTIVE_COMBAT;
        activeWaveSeconds = 0;
        arena.setCurrentWave(arena.getCurrentWave() + 1);

        notificationService.broadcastArenaTitle(arena, "&6&lWAVE " + arena.getCurrentWave(), "&7Defend against the incoming horde!", Sound.ITEM_GOAT_HORN_SOUND_0);
    }

    private void transitionToWaveCleared() {
        notificationService.broadcastArenaTitle(arena, "&a&lWAVE CLEARED!", "&eUpgrade drafting available!", Sound.UI_TOAST_CHALLENGE_COMPLETE);
        state = EngineState.UPGRADE_SELECTION;
        tickCounter = 0;
    }

    public EngineState getState() { return state; }
    public void setState(EngineState state) { this.state = state; }
}
