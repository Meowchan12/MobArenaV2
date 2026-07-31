package vn.meowchan12.mobarenav2.utils;

import net.md_5.bungee.api.ChatColor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {

    // Matches &#RRGGBB, #RRGGBB, and <#RRGGBB>
    private static final Pattern HEX_PATTERN = Pattern.compile("(?:&|<)?#([a-fA-F0-9]{6})>?");

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
     * Strips all color codes from text (for plain text comparisons)
     */
    public static String stripColor(String text) {
        if (text == null) return null;
        return ChatColor.stripColor(colorize(text));
    }
}
