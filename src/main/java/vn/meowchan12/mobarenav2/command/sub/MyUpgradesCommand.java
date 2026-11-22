package vn.meowchan12.mobarenav2.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;
import vn.meowchan12.mobarenav2.command.SubCommand;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.utils.ColorUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MyUpgradesCommand implements SubCommand {

    @Override
    public String getName() {
        return "myupgrades";
    }

    @Override
    public String getPermission() {
        return "mobarenav2.player.join";
    }

    @Override
    public String getUsage() {
        return "/ma myupgrades [player]";
    }

    @Override
    public String getDescription() {
        return "Inspect active match upgrades and live combat badges";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args, MobArenav2 plugin) {
        Player player = (sender instanceof Player p) ? p : null;
        Player targetPlayer = player;

        if (args.length >= 2) {
            targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null || !targetPlayer.isOnline()) {
                sender.sendMessage("§c[MobArenav2] Player '" + args[1] + "' not found or is offline!");
                return true;
            }
        } else if (player == null) {
            sender.sendMessage("§cConsole must specify a player: /ma myupgrades <player>");
            return true;
        }

        Arena arena = plugin.getArenaManager().getArenaByPlayer(targetPlayer);
        if (arena == null) {
            if (targetPlayer.equals(player)) {
                sender.sendMessage("§c[MobArenav2] You are not currently playing in an active arena match!");
            } else {
                sender.sendMessage("§c[MobArenav2] " + targetPlayer.getName() + " is not currently playing in an active arena match!");
            }
            return true;
        }
        ArenaPlayerProfile profile = arena.getPlayerProfiles().get(targetPlayer.getUniqueId());
        if (profile == null) {
            sender.sendMessage("§c[MobArenav2] No active profile found for this arena!");
            return true;
        }
        int maxSlots = plugin.getUpgradeManager().getMaxSlots(targetPlayer, profile.getSelectedClass(), arena);
        int usedSlots = plugin.getUpgradeManager().getUsedSlots(profile);
        Map<String, Integer> owned = profile.getOwnedUpgrades();

        String title = targetPlayer.equals(player) ? "YOUR" : (targetPlayer.getName().toUpperCase() + "'S");
        sender.sendMessage("§8§m----------------------------------");
        sender.sendMessage("§6§l✦ " + title + " ACTIVE MATCH UPGRADES ✦");
        sender.sendMessage("§7Player: §f" + targetPlayer.getName() + " §8| §7Arena: §e" + arena.getName() + " §8| §7Class: §a" + (profile.getSelectedClass() != null ? profile.getSelectedClass() : "None"));
        sender.sendMessage("§7Upgrade Slots: §b" + usedSlots + "§7/§b" + maxSlots);
        sender.sendMessage("§8§m----------------------------------");
        if (owned == null || owned.isEmpty()) {
            sender.sendMessage("§7  No upgrades selected yet.");
        } else {
            for (Map.Entry<String, Integer> entry : owned.entrySet()) {
                String upgradeId = entry.getKey();
                UpgradeData data = plugin.getUpgradeManager().getUpgradeData(upgradeId);
                int level = entry.getValue();
                int maxLvl = (data != null) ? data.maxLevel : level;

                boolean isUltimate = upgradeId.toLowerCase().startsWith("ultimate_")
                        || (data != null && data.type != null && data.type.toUpperCase().startsWith("ULTIMATE_"));
                boolean isSuper = !isUltimate && (upgradeId.toLowerCase().startsWith("super_")
                        || (data != null && data.type != null && data.type.toUpperCase().startsWith("SUPER_")));

                String name;
                if (data != null && data.displayName != null) {
                    String clean = data.displayName
                            .replace("%current_level%", String.valueOf(level))
                            .replace("%max_level%", String.valueOf(maxLvl))
                            .replaceAll("(?i)\\s*(&#[a-fA-F0-9]{6}|[&§][0-9a-fk-orA-FK-OR])?\\s*\\[Lv[^\\]]*\\]", "")
                            .trim();
                    if (isUltimate && !clean.toUpperCase().contains("[ULTIMATE]")) {
                        clean = "&6&l[ULTIMATE] &f" + clean;
                    } else if (isSuper && !clean.toUpperCase().contains("[SUPER]")) {
                        clean = "&d&l[SUPER] &f" + clean;
                    }
                    name = ColorUtils.colorize(clean);
                } else {
                    String prefix = isUltimate ? "&6&l[ULTIMATE] &f" : (isSuper ? "&d&l[SUPER] &f" : "&f");
                    name = ColorUtils.colorize(prefix + upgradeId);
                }

                String levelText;
                if (isUltimate) {
                    levelText = "§6§l✦ ULTIMATE (MAX)";
                } else if (level >= maxLvl) {
                    levelText = "§eLevel " + level + "§7/§e" + maxLvl + " §6§l(MAX)";
                } else {
                    levelText = "§eLevel " + level + "§7/§e" + maxLvl;
                }

                String dynamicBadge = "";
                String dynamicHoverExtra = "";
                if (upgradeId.equalsIgnoreCase("super_slaughterhouse") || upgradeId.equalsIgnoreCase("ultimate_blood_god_feast")) {
                    dynamicBadge = " §8[§c" + profile.getSlaughterhouseKills() + " Kills / +" + Math.min(9999, profile.getSlaughterhouseKills()) + "% DMG§8]";
                    dynamicHoverExtra = "\n§e⚔ Mobs Eliminated: §c" + profile.getSlaughterhouseKills() + "\n§eCurrent Damage Bonus: §a+" + Math.min(9999, profile.getSlaughterhouseKills()) + "%";
                } else if (upgradeId.equalsIgnoreCase("super_hephaestus")) {
                    dynamicBadge = " §8[§b" + profile.getHephaestusRepairs() + " Repairs§8]";
                    dynamicHoverExtra = "\n§e⚒ Total Repairs Triggered: §b" + profile.getHephaestusRepairs() + " times";
                } else if (upgradeId.equalsIgnoreCase("soul_harvester") || upgradeId.equalsIgnoreCase("ultimate_golden_necromancer")) {
                    dynamicBadge = " §8[§b" + profile.getSoulHarvesterSouls() + " Souls§8]";
                    dynamicHoverExtra = "\n§e☠ Total Souls Harvested: §b" + profile.getSoulHarvesterSouls();
                    if (profile.getMidasGoldEarned() > 0) {
                        dynamicBadge += " §8[§6+$" + (int) profile.getMidasGoldEarned() + "§8]";
                        dynamicHoverExtra += "\n§e💰 Extra Gold Mined: §6+$" + (int) profile.getMidasGoldEarned();
                    }
                } else if (upgradeId.equalsIgnoreCase("midas_touch")) {
                    dynamicBadge = " §8[§6+$" + (int) profile.getMidasGoldEarned() + "§8]";
                    dynamicHoverExtra = "\n§e💰 Extra Gold Mined: §6+$" + (int) profile.getMidasGoldEarned();
                } else if (upgradeId.equalsIgnoreCase("super_retribution") || upgradeId.equalsIgnoreCase("ultimate_citadel_retribution")) {
                    dynamicBadge = " §8[§b" + (int) profile.getReflectedDamageTotal() + " DMG Reflected§8]";
                    dynamicHoverExtra = "\n§e🛡 Reflected True Damage: §b" + (int) profile.getReflectedDamageTotal() + " DMG";
                }

                String bullet = isUltimate ? " §6✦ " : (isSuper ? " §d★ " : " §a✔ ");
                net.md_5.bungee.api.chat.BaseComponent[] components = net.md_5.bungee.api.chat.TextComponent.fromLegacyText(ColorUtils.colorize(bullet + name + " §7- " + levelText + dynamicBadge));
                if (data != null && data.lore != null && !data.lore.isEmpty()) {
                    List<String> coloredLore = new ArrayList<>(data.getFormattedLore(level));
                    if (!dynamicHoverExtra.isEmpty()) {
                        coloredLore.add(ColorUtils.colorize(dynamicHoverExtra));
                    }
                    net.md_5.bungee.api.chat.HoverEvent hover = new net.md_5.bungee.api.chat.HoverEvent(
                            net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                            new net.md_5.bungee.api.chat.ComponentBuilder(String.join("\n", coloredLore)).create()
                    );
                    for (net.md_5.bungee.api.chat.BaseComponent comp : components) {
                        comp.setHoverEvent(hover);
                    }
                }
                if (player != null) {
                    player.spigot().sendMessage(components);
                } else {
                    sender.sendMessage(ColorUtils.colorize(" §a✔ " + name + " §7- " + levelText + dynamicBadge));
                }
            }
        }
        sender.sendMessage("§8§m----------------------------------");
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args, MobArenav2 plugin) {
        if (args.length == 2) {
            List<String> list = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    list.add(p.getName());
                }
            }
            return list;
        }
        return Collections.emptyList();
    }
}
