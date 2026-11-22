package vn.meowchan12.mobarenav2.upgrade.fusion;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.upgrade.handler.ultimate.*;
import vn.meowchan12.mobarenav2.utils.ColorUtils;

import java.util.*;

/**
 * [v8.3.0]: Ultimate Fusion Manager.
 * Orchestrates recipe registration, eligibility detection, slot compression (-1 slot freed),
 * telemetry/state inheritance, and interactive draft notifications.
 */
public class FusionManager {

    public record UltimateRecipe(
            String id,
            String displayName,
            Material material,
            String comp1,
            String comp2,
            String type,
            UpgradeHandler handler,
            List<String> description
    ) {}

    private final MobArenav2 plugin;
    private final Map<String, UltimateRecipe> recipes = new LinkedHashMap<>();

    public FusionManager(MobArenav2 plugin) {
        this.plugin = plugin;
        registerDefaultRecipes();
    }

    private void registerDefaultRecipes() {
        // 1. Blood God's Feast (Vampiric Strike + Slaughterhouse Engine)
        registerRecipe(new UltimateRecipe(
                "ultimate_blood_god_feast",
                "&#AA0000&l✦ [ULTIMATE] BLOOD GOD'S FEAST ✦",
                Material.NETHERITE_SWORD,
                "vampiric_strike",
                "slaughterhouse",
                "ULTIMATE_BLOOD_GOD_FEAST",
                new UltimateBloodGodFeastUpgrade(),
                Arrays.asList(
                        "&7&m----------------------------------------",
                        "&cRequires: &eMax Vampiric Strike &f+ &eMax Slaughterhouse",
                        "&6&lMERGED ULTIMATE POWERS:",
                        "&a✔ Inherits 100% of Slaughterhouse kill scaling & boss bonus!",
                        "&a✔ 25% True Lifesteal on all damage dealt (No Upper Cap).",
                        "&a✔ Slaying enemies summons a Blood Vortex (AoE Slow & Damage).",
                        "&b✔ Frees +1 Upgrade Slot upon fusion!",
                        "&7&m----------------------------------------"
                )
        ));

        // 2. Longinus Cleave (Extended Reach + Warrior's Might)
        registerRecipe(new UltimateRecipe(
                "ultimate_longinus_cleave",
                "&#00FFAA&l✦ [ULTIMATE] LONGINUS CLEAVE ✦",
                Material.TRIDENT,
                "extended_reach",
                "warrior_might",
                "ULTIMATE_LONGINUS_CLEAVE",
                new UltimateLonginusCleaveUpgrade(),
                Arrays.asList(
                        "&7&m----------------------------------------",
                        "&cRequires: &eMax Extended Reach &f+ &eMax Warrior's Might",
                        "&6&lMERGED ULTIMATE POWERS:",
                        "&a✔ +1.8m interaction attack reach (Paper Native).",
                        "&a✔ Basic attacks trigger a 360° Sweeping Cleave (5m radius).",
                        "&a✔ Deals 100% full weapon damage to all cleave targets.",
                        "&b✔ Frees +1 Upgrade Slot upon fusion!",
                        "&7&m----------------------------------------"
                )
        ));

        // 3. Citadel of Retribution (Super Aegis + Thorns Aura)
        registerRecipe(new UltimateRecipe(
                "ultimate_citadel_retribution",
                "&#55FF55&l✦ [ULTIMATE] CITADEL OF RETRIBUTION ✦",
                Material.SHIELD,
                "super_aegis",
                "thorns_aura",
                "ULTIMATE_CITADEL_RETRIBUTION",
                new UltimateCitadelRetributionUpgrade(),
                Arrays.asList(
                        "&7&m----------------------------------------",
                        "&cRequires: &eMax Super Aegis &f+ &eMax Thorns Aura",
                        "&6&lMERGED ULTIMATE POWERS:",
                        "&a✔ Absorbs 100% fatal lethal damage (2s Invulnerability).",
                        "&a✔ Reflects 150% of damage taken as AoE True Damage (8m).",
                        "&a✔ Stuns attacking enemies for 1.5 seconds on strike.",
                        "&b✔ Frees +1 Upgrade Slot upon fusion!",
                        "&7&m----------------------------------------"
                )
        ));

        // 4. Death Scythe (Super Executioner + Critical Strike)
        registerRecipe(new UltimateRecipe(
                "ultimate_death_scythe",
                "&#FF0055&l✦ [ULTIMATE] DEATH SCYTHE ✦",
                Material.NETHERITE_HOE,
                "super_executioner",
                "critical_strike",
                "ULTIMATE_DEATH_SCYTHE",
                new UltimateDeathScytheUpgrade(),
                Arrays.asList(
                        "&7&m----------------------------------------",
                        "&cRequires: &eMax Super Executioner &f+ &eMax Critical Strike",
                        "&6&lMERGED ULTIMATE POWERS:",
                        "&a✔ Instantly executes mobs & bosses below 20% Health!",
                        "&a✔ Critical strikes deal 350% total damage.",
                        "&a✔ Lethal strikes trigger a Death Nova explosion.",
                        "&b✔ Frees +1 Upgrade Slot upon fusion!",
                        "&7&m----------------------------------------"
                )
        ));

        // 5. Adamantine Colossus (Titan Heart + Iron Skin)
        registerRecipe(new UltimateRecipe(
                "ultimate_adamantine_colossus",
                "&#FFAA00&l✦ [ULTIMATE] ADAMANTINE COLOSSUS ✦",
                Material.NETHERITE_CHESTPLATE,
                "titan_heart",
                "iron_skin",
                "ULTIMATE_ADAMANTINE_COLOSSUS",
                new UltimateAdamantineColossusUpgrade(),
                Arrays.asList(
                        "&7&m----------------------------------------",
                        "&cRequires: &eMax Titan Heart &f+ &eMax Iron Skin",
                        "&6&lMERGED ULTIMATE POWERS:",
                        "&a✔ Permanently grants +40 Max Health in this arena match.",
                        "&a✔ 35% Flat Damage Mitigation against all damage sources.",
                        "&a✔ 100% Knockback Resistance (Unmovable Fortress).",
                        "&b✔ Frees +1 Upgrade Slot upon fusion!",
                        "&7&m----------------------------------------"
                )
        ));

        // 6. Godspeed Tempest (Swift Wind + Adrenaline Rush)
        registerRecipe(new UltimateRecipe(
                "ultimate_godspeed_tempest",
                "&#FFFF55&l✦ [ULTIMATE] GODSPEED TEMPEST ✦",
                Material.FEATHER,
                "swift_wind",
                "adrenaline_rush",
                "ULTIMATE_GODSPEED_TEMPEST",
                new UltimateGodspeedTempestUpgrade(),
                Arrays.asList(
                        "&7&m----------------------------------------",
                        "&cRequires: &eMax Swift Wind &f+ &eMax Adrenaline Rush",
                        "&6&lMERGED ULTIMATE POWERS:",
                        "&a✔ +60% Permanent Movement Speed boost.",
                        "&a✔ Below 40% HP: Triggers Godspeed Mode (+50% attack speed,",
                        "   invulnerability frames during sprints, and debuff immunity).",
                        "&b✔ Frees +1 Upgrade Slot upon fusion!",
                        "&7&m----------------------------------------"
                )
        ));

        // 7. Golden Necromancer (Soul Harvester + Midas Touch)
        registerRecipe(new UltimateRecipe(
                "ultimate_golden_necromancer",
                "&#FFD700&l✦ [ULTIMATE] GOLDEN NECROMANCER ✦",
                Material.GOLD_BLOCK,
                "soul_harvester",
                "midas_touch",
                "ULTIMATE_GOLDEN_NECROMANCER",
                new UltimateGoldenNecromancerUpgrade(),
                Arrays.asList(
                        "&7&m----------------------------------------",
                        "&cRequires: &eMax Soul Harvester &f+ &eMax Midas Touch",
                        "&6&lMERGED ULTIMATE POWERS:",
                        "&a✔ Harvested souls grant +2% damage & drop +$500 bonus cash.",
                        "&a✔ 30% chance on kill to summon Golden Skeletons to fight for you.",
                        "&b✔ Frees +1 Upgrade Slot upon fusion!",
                        "&7&m----------------------------------------"
                )
        ));

        // 8. Celestial Cataclysm (Super Nova + Elemental Infusion)
        registerRecipe(new UltimateRecipe(
                "ultimate_celestial_cataclysm",
                "&#FF55FF&l✦ [ULTIMATE] CELESTIAL CATACLYSM ✦",
                Material.NETHER_STAR,
                "super_nova",
                "elemental_infusion",
                "ULTIMATE_CELESTIAL_CATACLYSM",
                new UltimateCelestialCataclysmUpgrade(),
                Arrays.asList(
                        "&7&m----------------------------------------",
                        "&cRequires: &eMax Super Nova &f+ &eMax Elemental Infusion",
                        "&6&lMERGED ULTIMATE POWERS:",
                        "&a✔ Every 5s, calls down Celestial Meteors on up to 8 priority targets.",
                        "&a✔ Meteor impacts and basic attacks unleash Chain Lightning.",
                        "&b✔ Frees +1 Upgrade Slot upon fusion!",
                        "&7&m----------------------------------------"
                )
        ));
    }

    public void registerRecipe(UltimateRecipe recipe) {
        recipes.put(recipe.id(), recipe);
        if (recipe.handler() != null && plugin.getUpgradeRegistry() != null) {
            plugin.getUpgradeRegistry().registerHandler(recipe.handler());
        }
    }

    public Collection<UltimateRecipe> getAllRecipes() {
        return recipes.values();
    }

    public UltimateRecipe getRecipe(String id) {
        return recipes.get(id);
    }

    public List<UltimateRecipe> getEligibleFusions(ArenaPlayerProfile profile) {
        List<UltimateRecipe> eligible = new ArrayList<>();
        if (profile == null) return eligible;

        for (UltimateRecipe recipe : recipes.values()) {
            if (profile.getUpgradeLevel(recipe.id()) > 0) continue; // Already owned

            int maxLvl1 = getMaxLevel(recipe.comp1());
            int maxLvl2 = getMaxLevel(recipe.comp2());

            int ownedLvl1 = profile.getUpgradeLevel(recipe.comp1());
            int ownedLvl2 = profile.getUpgradeLevel(recipe.comp2());

            if (ownedLvl1 >= maxLvl1 && ownedLvl2 >= maxLvl2) {
                eligible.add(recipe);
            }
        }
        return eligible;
    }

    private int getMaxLevel(String upgradeId) {
        UpgradeData data = plugin.getUpgradeManager().getUpgradeData(upgradeId);
        return (data != null) ? data.maxLevel : 1;
    }

    public boolean fuse(Player player, ArenaPlayerProfile profile, UltimateRecipe recipe, Arena arena) {
        if (player == null || profile == null || recipe == null) return false;

        // Verify eligibility again
        int maxLvl1 = getMaxLevel(recipe.comp1());
        int maxLvl2 = getMaxLevel(recipe.comp2());
        if (profile.getUpgradeLevel(recipe.comp1()) < maxLvl1 || profile.getUpgradeLevel(recipe.comp2()) < maxLvl2) {
            player.sendMessage(ColorUtils.colorize("&c[MobArenav2] You do not meet the Max-Level requirements for this fusion!"));
            return false;
        }

        // Reset component upgrade handlers
        if (plugin.getUpgradeRegistry() != null) {
            UpgradeData d1 = plugin.getUpgradeManager().getUpgradeData(recipe.comp1());
            UpgradeHandler h1 = plugin.getUpgradeRegistry().resolveHandler(d1);
            if (h1 != null) {
                try { h1.onReset(player, arena); } catch (Exception ignored) {}
            }

            UpgradeData d2 = plugin.getUpgradeManager().getUpgradeData(recipe.comp2());
            UpgradeHandler h2 = plugin.getUpgradeRegistry().resolveHandler(d2);
            if (h2 != null) {
                try { h2.onReset(player, arena); } catch (Exception ignored) {}
            }
        }

        // Execute Fusion: Remove comp1 and comp2, add recipe.id at level 1
        profile.getOwnedUpgrades().remove(recipe.comp1());
        profile.getOwnedUpgrades().remove(recipe.comp2());
        profile.addUpgradeLevel(recipe.id());

        // Apply newly created ultimate upgrade handler
        if (recipe.handler() != null) {
            UpgradeData ultData = plugin.getUpgradeManager().getUpgradeData(recipe.id());
            try {
                recipe.handler().onApply(player, 1, ultData, arena);
            } catch (Exception e) {
                plugin.getLogger().warning("[MobArenav2] Error triggering onApply for fused ultimate " + recipe.id() + ": " + e.getMessage());
            }
        }

        // Audio & Visual feedback
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.5f);

        String comp1Name = getDisplayName(recipe.comp1());
        String comp2Name = getDisplayName(recipe.comp2());

        player.sendMessage(" ");
        player.sendMessage(ColorUtils.colorize("&6&l✦ [ULTIMATE FUSION SUCCESSFUL] ✦"));
        player.sendMessage(ColorUtils.colorize("&aMerged &e" + comp1Name + " &aand &e" + comp2Name));
        player.sendMessage(ColorUtils.colorize("&ainto &f" + recipe.displayName()));
        player.sendMessage(ColorUtils.colorize("&b&l✔ Slot Compression: +1 Upgrade Slot has been freed!"));
        player.sendMessage(" ");

        return true;
    }

    public void checkAndSendFusionPrompt(Player player, ArenaPlayerProfile profile) {
        if (player == null || profile == null) return;

        List<UltimateRecipe> eligible = getEligibleFusions(profile);
        if (eligible.isEmpty()) return;

        // Interactive Click-to-Fuse Chat Link
        TextComponent prefix = new TextComponent(ColorUtils.colorize("&6&l[FUSION] &eYour upgrades are ready to combine! "));
        TextComponent button = new TextComponent(ColorUtils.colorize("&a&l&n[CLICK HERE TO FUSE]&r"));
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ColorUtils.colorize("&eClick to open the Fusion Draft Menu\n&7Fusing compresses 2 cards into 1, freeing 1 slot!")).create()));
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ma fuse"));

        prefix.addExtra(button);
        player.spigot().sendMessage(prefix);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
    }

    private String getDisplayName(String upgradeId) {
        UpgradeData data = plugin.getUpgradeManager().getUpgradeData(upgradeId);
        if (data == null || data.displayName == null) return upgradeId;
        String name = data.displayName
                .replace("%current_level%", String.valueOf(data.maxLevel))
                .replace("%max_level%", String.valueOf(data.maxLevel));
        name = name.replaceAll("(?i)\\s*(&#[a-fA-F0-9]{6}|[&§][0-9a-fk-orA-FK-OR])?\\s*\\[Lv[^\\]]*\\]", "");
        return ColorUtils.colorize(name.trim());
    }
}
