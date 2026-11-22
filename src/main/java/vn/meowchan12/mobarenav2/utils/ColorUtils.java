package vn.meowchan12.mobarenav2.utils;

import net.md_5.bungee.api.ChatColor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {

    // Matches &#RRGGBB, §#RRGGBB, #RRGGBB, and <#RRGGBB>
    private static final Pattern HEX_PATTERN = Pattern.compile("(?:[§&]|<)?#([a-fA-F0-9]{6})>?");

    /**
     * Translates color codes including:
     * - Standard Minecraft color codes (&a, &b, etc.)
     * - Hex RGB colors: &#FF5555, #FF5555, <#FF5555>
     */
    public static String colorize(String text) {
        if (text == null) return null;

        // Process hex colors
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            String replacement = ChatColor.of("#" + hex).toString();
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);

        // Process standard color codes
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    /**
     * Converts a string with legacy or hex colors into an Adventure Component with explicit italic control.
     * In Minecraft, item meta display names and lore default to italic unless TextDecoration.ITALIC is set to false.
     */
    public static net.kyori.adventure.text.Component toAdventure(String text, boolean italic) {
        if (text == null) return net.kyori.adventure.text.Component.empty();
        net.kyori.adventure.text.Component comp = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(colorize(text));
        return net.kyori.adventure.text.Component.empty().decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, italic).append(comp);
    }

    /**
     * Converts a string with legacy or hex colors into an Adventure Component.
     * Defaults to non-italic (italic: false) to prevent Minecraft's default italics in item names and lore.
     */
    public static net.kyori.adventure.text.Component toAdventure(String text) {
        return toAdventure(text, false);
    }

    /**
     * Strips all color codes from text (for plain text comparisons)
     */
    public static String stripColor(String text) {
        if (text == null) return null;
        return ChatColor.stripColor(colorize(text));
    }
}
