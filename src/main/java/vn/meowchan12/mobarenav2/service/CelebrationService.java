package vn.meowchan12.mobarenav2.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

import java.time.Duration;
import java.util.UUID;

/**
 * Service handling victory celebrations, milestone wave fanfare audio,
 * titles, subtitles, and safe non-damaging fireworks.
 */
public class CelebrationService {

    private final MobArenav2 plugin;

    public CelebrationService(MobArenav2 plugin) {
        this.plugin = plugin;
    }

    private String colorize(String text) {
        if (text == null) return "";
        return text.replace("&", "§");
    }

    public void celebrateWaveClear(Arena arena, int wave) {
        if (arena == null) return;
        FileConfiguration settings = plugin.getConfigManager().getSettings();
        if (settings == null) return;

        int interval = settings.getInt("celebrations.milestone-waves.interval", 5);
        if (interval > 0 && wave % interval != 0) {
            return;
        }

        String titleRaw = settings.getString("celebrations.milestone-waves.title", "&6&l★ WAVE %wave% CLEARED! ★")
                .replace("%wave%", String.valueOf(wave));
        String subRaw = settings.getString("celebrations.milestone-waves.subtitle", "&eKeep fighting, warriors!")
                .replace("%wave%", String.valueOf(wave));

        Component titleComp = LegacyComponentSerializer.legacySection().deserialize(colorize(titleRaw));
        Component subComp = LegacyComponentSerializer.legacySection().deserialize(colorize(subRaw));
        Title title = Title.title(titleComp, subComp, Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2000), Duration.ofMillis(500)));

        boolean spawnFw = settings.getBoolean("celebrations.milestone-waves.fireworks", true);

        for (UUID uuid : arena.getActivePlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.showTitle(title);
                playFanfareSequence(player, false);
                if (spawnFw) {
                    spawnCosmeticFirework(player.getLocation(), Color.LIME, Color.YELLOW);
                }
            }
        }
    }

    public void celebrateVictory(Arena arena) {
        if (arena == null) return;
        FileConfiguration settings = plugin.getConfigManager().getSettings();
        if (settings == null) return;

        String titleRaw = settings.getString("celebrations.victory.title", "&#FFAA00&l★ VICTORY ACHIEVED! ★")
                .replace("%arena%", arena.getName());
        String subRaw = settings.getString("celebrations.victory.subtitle", "&#55FF55&lAll waves conquered! You are the Arena Champion!")
                .replace("%arena%", arena.getName());

        Component titleComp = LegacyComponentSerializer.legacySection().deserialize(colorize(titleRaw));
        Component subComp = LegacyComponentSerializer.legacySection().deserialize(colorize(subRaw));
        Title title = Title.title(titleComp, subComp, Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000)));

        String broadcastFormat = settings.getString("celebrations.victory.broadcast", "&6[MobArenav2] &eTeam &f%players% &ehas conquered arena &b%arena%&e!");
        if (broadcastFormat != null && !broadcastFormat.isEmpty()) {
            StringBuilder playersStr = new StringBuilder();
            for (UUID uuid : arena.getActivePlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    if (playersStr.length() > 0) playersStr.append(", ");
                    playersStr.append(p.getName());
                }
            }
            String broadcast = colorize(broadcastFormat
                    .replace("%players%", playersStr.toString())
                    .replace("%arena%", arena.getName()));
            Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(broadcast));
        }

        int fwCount = settings.getInt("celebrations.victory.firework-count", 5);

        for (UUID uuid : arena.getActivePlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.showTitle(title);
                playFanfareSequence(player, true);
                spawnMultipleFireworks(player.getLocation(), fwCount);
            }
        }
    }

    private void playFanfareSequence(Player player, boolean isVictory) {
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                switch (step) {
                    case 0:
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
                        break;
                    case 1:
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.26f);
                        break;
                    case 2:
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
                        break;
                    case 3:
                        if (isVictory) {
                            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                        } else {
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.8f);
                        }
                        cancel();
                        return;
                }
                step++;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    private void spawnCosmeticFirework(Location loc, Color primary, Color fade) {
        if (loc.getWorld() == null) return;
        Firework fw = (Firework) loc.getWorld().spawnEntity(loc.clone().add(0, 1.5, 0), EntityType.FIREWORK_ROCKET);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.setPower(1);
        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(primary)
                .withFade(fade)
                .flicker(true)
                .trail(true)
                .build());
        fw.setFireworkMeta(meta);
        fw.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "ma_cosmetic_fw"), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
    }

    private void spawnMultipleFireworks(Location baseLoc, int count) {
        new BukkitRunnable() {
            int fired = 0;

            @Override
            public void run() {
                if (fired >= count || baseLoc.getWorld() == null) {
                    cancel();
                    return;
                }
                double offsetX = (Math.random() - 0.5) * 4.0;
                double offsetZ = (Math.random() - 0.5) * 4.0;
                Location spawnLoc = baseLoc.clone().add(offsetX, 0, offsetZ);
                Color[] colors = {Color.RED, Color.YELLOW, Color.GREEN, Color.AQUA, Color.PURPLE, Color.ORANGE};
                Color c1 = colors[(int) (Math.random() * colors.length)];
                Color c2 = colors[(int) (Math.random() * colors.length)];
                spawnCosmeticFirework(spawnLoc, c1, c2);
                fired++;
            }
        }.runTaskTimer(plugin, 0L, 8L);
    }
}
