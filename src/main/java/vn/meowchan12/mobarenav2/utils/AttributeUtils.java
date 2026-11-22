package vn.meowchan12.mobarenav2.utils;

import org.bukkit.attribute.Attribute;

public class AttributeUtils {
    public static Attribute MAX_HEALTH;
    public static Attribute ATTACK_DAMAGE;
    public static Attribute ATTACK_SPEED;
    public static Attribute MOVEMENT_SPEED;
    public static Attribute ARMOR;
    public static Attribute ARMOR_TOUGHNESS;
    public static Attribute ENTITY_INTERACTION_RANGE;
    public static Attribute BLOCK_INTERACTION_RANGE;

    static {
        try {
            // Try 1.21.2+ first (where prefixes were cleaned up)
            MAX_HEALTH = getAttrField("MAX_HEALTH", "GENERIC_MAX_HEALTH");
            ATTACK_DAMAGE = getAttrField("ATTACK_DAMAGE", "GENERIC_ATTACK_DAMAGE");
            ATTACK_SPEED = getAttrField("ATTACK_SPEED", "GENERIC_ATTACK_SPEED");
            MOVEMENT_SPEED = getAttrField("MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED");
            ARMOR = getAttrField("ARMOR", "GENERIC_ARMOR");
            ARMOR_TOUGHNESS = getAttrField("ARMOR_TOUGHNESS", "GENERIC_ARMOR_TOUGHNESS");
            ENTITY_INTERACTION_RANGE = getAttrField("ENTITY_INTERACTION_RANGE", "PLAYER_ENTITY_INTERACTION_RANGE");
            BLOCK_INTERACTION_RANGE = getAttrField("BLOCK_INTERACTION_RANGE", "PLAYER_BLOCK_INTERACTION_RANGE");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Attribute getAttrField(String modernName, String legacyName) {
        try {
            return (Attribute) Attribute.class.getDeclaredField(modernName).get(null);
        } catch (Throwable ignored) {
            try {
                return (Attribute) Attribute.class.getDeclaredField(legacyName).get(null);
            } catch (Throwable ignored2) {
                return null;
            }
        }
    }
}
