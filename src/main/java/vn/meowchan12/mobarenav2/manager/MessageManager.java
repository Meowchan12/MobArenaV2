package vn.meowchan12.mobarenav2.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.utils.ColorUtils;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/**
 * [v8.1.0]: Centralized Localization and Message Management Service.
 * Handles MiniMessage/Hex color decoding, prefix injection, placeholders,
 * ActionBars, Titles, and safe fallbacks.
 */
public class MessageManager {

    private final MobArenav2 plugin;
    private FileConfiguration messagesConfig;
    private File messagesFile;
    private String prefix = "§b§l[MobArena] §f";

    public MessageManager(MobArenav2 plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // Merge missing default keys from jar resource
        try (InputStream defStream = plugin.getResource("messages.yml")) {
            if (defStream != null) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
                messagesConfig.setDefaults(defConfig);
            }
        } catch (Exception ignored) {}

        this.prefix = ColorUtils.colorize(messagesConfig.getString("prefix", "&#00FFFF&l[MobArena] &f"));
    }

    public void reload() {
        load();
    }

    public String getPrefix() {
        return prefix;
    }

    public String getRaw(String key, String def) {
        String msg = messagesConfig.getString(key);
        if (msg == null) msg = def;
        return msg != null ? ColorUtils.colorize(msg) : "";
    }

    public String get(String key, String def) {
        String raw = getRaw(key, def);
        return prefix + raw;
    }

    public String format(String key, String def, Map<String, String> placeholders) {
        String msg = get(key, def);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                msg = msg.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return msg;
    }

    public String formatRaw(String key, String def, Map<String, String> placeholders) {
        String msg = getRaw(key, def);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                msg = msg.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return msg;
    }

    public void sendMessage(CommandSender sender, String key, String def, Map<String, String> placeholders) {
        if (sender == null) return;
        sender.sendMessage(format(key, def, placeholders));
    }

    public void sendMessage(CommandSender sender, String key, String def) {
        sendMessage(sender, key, def, Collections.emptyMap());
    }

    public void sendRawMessage(CommandSender sender, String key, String def, Map<String, String> placeholders) {
        if (sender == null) return;
        sender.sendMessage(formatRaw(key, def, placeholders));
    }

    public void sendActionBar(Player player, String key, String def, Map<String, String> placeholders) {
        if (player == null || !player.isOnline()) return;
        String raw = formatRaw(key, def, placeholders);
        Component comp = LegacyComponentSerializer.legacySection().deserialize(raw);
        player.sendActionBar(comp);
    }

    public void sendActionBar(Player player, String key, String def) {
        sendActionBar(player, key, def, Collections.emptyMap());
    }

    public void sendTitle(Player player, String titleKey, String titleDef, String subKey, String subDef, Map<String, String> placeholders, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        if (player == null || !player.isOnline()) return;
        String titleStr = formatRaw(titleKey, titleDef, placeholders);
        String subStr = formatRaw(subKey, subDef, placeholders);

        Component titleComp = LegacyComponentSerializer.legacySection().deserialize(titleStr);
        Component subComp = LegacyComponentSerializer.legacySection().deserialize(subStr);

        Title title = Title.title(
                titleComp,
                subComp,
                Title.Times.times(
                        Duration.ofMillis(fadeInTicks * 50L),
                        Duration.ofMillis(stayTicks * 50L),
                        Duration.ofMillis(fadeOutTicks * 50L)
                )
        );
        player.showTitle(title);
    }

    public void broadcast(Arena arena, String key, String def, Map<String, String> placeholders) {
        if (arena == null) return;
        String formatted = format(key, def, placeholders);
        for (Player p : arena.getPlayersAsObjects()) {
            p.sendMessage(formatted);
        }
    }

    public void broadcast(Arena arena, String key, String def) {
        broadcast(arena, key, def, Collections.emptyMap());
    }
}
