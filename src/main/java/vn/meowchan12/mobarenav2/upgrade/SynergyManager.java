package vn.meowchan12.mobarenav2.upgrade;

import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;
import vn.meowchan12.mobarenav2.utils.ColorUtils;

import java.util.*;

/**
 * [v8.1.0]: Roguelike Synergy & Fusion System.
 * Detects synergistic upgrade combinations and grants legendary combo effects.
 */
public class SynergyManager {

    public record Synergy(String id, String name, String description, List<String> requiredUpgrades) {}

    private final MobArenav2 plugin;
    private final List<Synergy> synergies = new ArrayList<>();

    public SynergyManager(MobArenav2 plugin) {
        this.plugin = plugin;
        registerDefaults();
    }

    private void registerDefaults() {
        synergies.add(new Synergy(
                "immortal_valhalla",
                "&#FF5555&l✦ VALHALLA BERSERKER ✦",
                "Berserker's Pact + Titan Heart: Gain +50% ATK & Resistance III when HP < 30%.",
                Arrays.asList("berserkers_pact", "titan_heart")
        ));
        synergies.add(new Synergy(
                "vampiric_bloodrush",
                "&#AA0000&l✦ VAMPIRIC BLOODRUSH ✦",
                "Vampiric Strike + Bloodlust: Lifesteal heals +50% more & grants Speed II on kills.",
                Arrays.asList("vampiric_strike", "bloodlust")
        ));
        synergies.add(new Synergy(
                "thorny_fortress",
                "&#55FF55&l✦ THORNY FORTRESS ✦",
                "Thorns Aura + Iron Skin: Thorns reflect +50% extra damage and stun attackers.",
                Arrays.asList("thorns_aura", "iron_skin")
        ));
        synergies.add(new Synergy(
                "hellfire_tempest",
                "&#FFAA00&l✦ HELLFIRE TEMPEST ✦",
                "Super Nova + Elemental Infusion: Orbital strikes trigger fiery chain explosions.",
                Arrays.asList("super_nova", "elemental_infusion")
        ));
        synergies.add(new Synergy(
                "midas_harvester",
                "&#FFD700&l✦ MIDAS HARVESTER ✦",
                "Soul Harvester + Midas Touch: Each soul harvested yields +$100 bonus cash.",
                Arrays.asList("soul_harvester", "midas_touch")
        ));
    }

    public List<Synergy> getActiveSynergies(ArenaPlayerProfile profile) {
        List<Synergy> active = new ArrayList<>();
        if (profile == null) return active;

        for (Synergy syn : synergies) {
            boolean allPresent = true;
            for (String req : syn.requiredUpgrades()) {
                if (profile.getUpgradeLevel(req) <= 0) {
                    allPresent = false;
                    break;
                }
            }
            if (allPresent) {
                active.add(syn);
            }
        }
        return active;
    }

    public void checkAndNotifyNewSynergies(Player player, ArenaPlayerProfile profile, Arena arena) {
        if (player == null || profile == null) return;

        List<Synergy> current = getActiveSynergies(profile);
        for (Synergy syn : current) {
            String tag = "synergy_notified_" + syn.id();
            if (!player.hasMetadata(tag)) {
                player.setMetadata(tag, new org.bukkit.metadata.FixedMetadataValue(plugin, true));

                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.5f);
                player.sendMessage(" ");
                player.sendMessage(ColorUtils.colorize("&#FFAA00&l⚡ [UPGRADE SYNERGY UNLOCKED] ⚡"));
                player.sendMessage(ColorUtils.colorize(syn.name()));
                player.sendMessage(ColorUtils.colorize("&f" + syn.description()));
                player.sendMessage(" ");
            }
        }
    }
}
