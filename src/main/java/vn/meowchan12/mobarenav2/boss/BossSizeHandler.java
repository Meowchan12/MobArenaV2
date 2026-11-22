package vn.meowchan12.mobarenav2.boss;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import vn.meowchan12.mobarenav2.MobArenav2;

/**
 * [v7.0] Handles boss visual size scaling using GENERIC_SCALE attribute.
 * Supports preset sizes (mini, normal, large, huge) and custom float values.
 * Uses reflection for cross-version compatibility (1.20.5+ / 1.21.2+).
 */
public class BossSizeHandler {

    private static Attribute SCALE_ATTRIBUTE = null;

    static {
        try {
            // Try 1.21.2+ (SCALE without prefix)
            SCALE_ATTRIBUTE = (Attribute) Attribute.class.getDeclaredField("SCALE").get(null);
        } catch (Exception e) {
            try {
                // Try 1.20.5-1.21.1 (GENERIC_SCALE)
                SCALE_ATTRIBUTE = (Attribute) Attribute.class.getDeclaredField("GENERIC_SCALE").get(null);
            } catch (Exception ignored) {
                // Not available on this server version
            }
        }
    }

    /**
     * Parses the 'size' key from a boss config section and applies the scale attribute.
     * Supported values: "mini" (0.5), "normal" (1.0), "large" (2.0), "huge" (3.5), or a custom float like "1.75".
     *
     * @param entity    The boss entity to scale
     * @param bossConfig The boss configuration section from bosses.yml
     * @param plugin    The plugin instance for logging
     */
    public static void applySize(LivingEntity entity, ConfigurationSection bossConfig, MobArenav2 plugin) {
        if (bossConfig == null || !bossConfig.contains("size")) return;

        String sizeStr = bossConfig.getString("size", "normal").trim().toLowerCase();
        double scale = parseSize(sizeStr);

        if (scale <= 0 || scale == 1.0) return; // Skip if normal or invalid

        if (SCALE_ATTRIBUTE == null) {
            plugin.getLogger().warning("[MobArenav2] SCALE attribute not available. Boss size scaling requires Minecraft 1.20.5+.");
            return;
        }

        try {
            AttributeInstance scaleAttr = entity.getAttribute(SCALE_ATTRIBUTE);
            if (scaleAttr != null) {
                scaleAttr.setBaseValue(scale);
                plugin.getLogger().info("[MobArenav2] Applied boss size scale: " + scale + "x");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[MobArenav2] Failed to apply boss size: " + e.getMessage());
        }
    }

    /**
     * Converts a size string to a numeric scale value.
     */
    private static double parseSize(String sizeStr) {
        return switch (sizeStr) {
            case "mini" -> 0.5;
            case "small" -> 0.75;
            case "normal" -> 1.0;
            case "large" -> 2.0;
            case "huge" -> 3.5;
            default -> {
                // Try parsing as custom number
                try {
                    double val = Double.parseDouble(sizeStr);
                    yield Math.max(0.1, Math.min(16.0, val)); // Clamp between 0.1x and 16x
                } catch (NumberFormatException e) {
                    yield 1.0; // Default to normal
                }
            }
        };
    }

    /**
     * Gets the scale value for a given size string (used for damage calculations).
     */
    public static double getScale(String sizeStr) {
        if (sizeStr == null) return 1.0;
        return parseSize(sizeStr.trim().toLowerCase());
    }
}
