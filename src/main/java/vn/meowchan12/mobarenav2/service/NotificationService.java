package vn.meowchan12.mobarenav2.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.utils.ColorUtils;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [v8.1.0]: 3-Tier UX Notification Service.
 * Manages Chat, Title/Subtitle, and Actionbar feeds with anti-spam rate limiting.
 */
public class NotificationService {

    private final MobArenav2 plugin;
    private final Map<UUID, Long> lastActionbarTime = new ConcurrentHashMap<>();
    private final Map<String, Long> lastBroadcastTime = new ConcurrentHashMap<>();

    public NotificationService(MobArenav2 plugin) {
        this.plugin = plugin;
    }

    /**
     * Tier 1: Actionbar HUD update (throttled to avoid flicker).
     */
    public void sendActionbar(Player player, String message) {
        if (player == null || !player.isOnline()) return;
        long now = System.currentTimeMillis();
        long last = lastActionbarTime.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 250) return; // 250ms throttle

        lastActionbarTime.put(player.getUniqueId(), now);
        Component comp = ColorUtils.toAdventure(message);
        player.sendActionBar(comp);
    }

    /**
     * Tier 2: Large Title and Subtitle announcement.
     */
    public void sendTitle(Player player, String titleText, String subtitleText, int fadeIn, int stay, int fadeOut) {
        if (player == null || !player.isOnline()) return;
        Component main = ColorUtils.toAdventure(titleText != null ? titleText : "");
        Component sub = ColorUtils.toAdventure(subtitleText != null ? subtitleText : "");
        Title.Times times = Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L));
        player.showTitle(Title.title(main, sub, times));
    }

    /**
     * Broadcast Title and Subtitle to all arena participants.
     */
    public void broadcastArenaTitle(Arena arena, String titleText, String subtitleText, Sound sound) {
        if (arena == null) return;
        for (UUID uuid : arena.getActivePlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                sendTitle(p, titleText, subtitleText, 10, 50, 15);
                if (sound != null) {
                    p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
                }
            }
        }
    }

    /**
     * Tier 3: Chat Broadcast with spam cooldown per key.
     */
    public void broadcastThrottled(Arena arena, String key, String message, long cooldownMillis) {
        if (arena == null || message == null) return;
        long now = System.currentTimeMillis();
        String comboKey = arena.getName() + ":" + key;
        long last = lastBroadcastTime.getOrDefault(comboKey, 0L);
        if (now - last < cooldownMillis) return;

        lastBroadcastTime.put(comboKey, now);
        arena.broadcastMessage(message);
    }
}
