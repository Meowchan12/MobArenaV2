package vn.meowchan12.mobarenav2.utils;

import org.bukkit.attribute.Attribute;

public class AttributeUtils {
    public static Attribute MAX_HEALTH;
    public static Attribute ATTACK_DAMAGE;
    public static Attribute MOVEMENT_SPEED;
    public static Attribute ARMOR;
    public static Attribute ARMOR_TOUGHNESS;

    static {
        try {
            // Try 1.21.2+ first (where GENERIC_ prefix was removed)
            MAX_HEALTH = (Attribute) Attribute.class.getDeclaredField("MAX_HEALTH").get(null);
            ATTACK_DAMAGE = (Attribute) Attribute.class.getDeclaredField("ATTACK_DAMAGE").get(null);
            MOVEMENT_SPEED = (Attribute) Attribute.class.getDeclaredField("MOVEMENT_SPEED").get(null);
            ARMOR = (Attribute) Attribute.class.getDeclaredField("ARMOR").get(null);
            ARMOR_TOUGHNESS = (Attribute) Attribute.class.getDeclaredField("ARMOR_TOUGHNESS").get(null);
        } catch (Exception e) {
            try {
                // Fallback to older versions (1.21.1 and below)
                MAX_HEALTH = (Attribute) Attribute.class.getDeclaredField("GENERIC_MAX_HEALTH").get(null);
                ATTACK_DAMAGE = (Attribute) Attribute.class.getDeclaredField("GENERIC_ATTACK_DAMAGE").get(null);
                MOVEMENT_SPEED = (Attribute) Attribute.class.getDeclaredField("GENERIC_MOVEMENT_SPEED").get(null);
                ARMOR = (Attribute) Attribute.class.getDeclaredField("GENERIC_ARMOR").get(null);
                ARMOR_TOUGHNESS = (Attribute) Attribute.class.getDeclaredField("GENERIC_ARMOR_TOUGHNESS").get(null);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}
