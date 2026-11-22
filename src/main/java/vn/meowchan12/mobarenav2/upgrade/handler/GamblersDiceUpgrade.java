package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * [v7.3 REWORK]: Gambler's Dice
 * High stakes roll: 30% Jackpot ($10,000,000 Vault) vs 70% Curse (50% HP True Damage + Debuffs).
 */
public class GamblersDiceUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "RANDOM_EVENT";
    }

    @Override
    public void onApply(Player player, int newLevel, UpgradeData data, Arena arena) {
        if (data.eventsSection == null) return;

        double roll = ThreadLocalRandom.current().nextDouble(100.0);
        double currentSum = 0;
        for (String eventKey : data.eventsSection.getKeys(false)) {
            ConfigurationSection ev = data.eventsSection.getConfigurationSection(eventKey);
            if (ev == null) continue;

            currentSum += ev.getDouble("chance", 0.0);
            if (roll <= currentSum) {
                // 1. Command execution (e.g. Vault eco give)
                if (ev.contains("command")) {
                    String cmd = ev.getString("command", "").replace("%player%", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }

                // 2. Percent or True damage handling
                if (ev.contains("percent-damage") || ev.contains("true-damage")) {
                    double currentHp = player.getHealth();
                    double damage = 0.0;

                    if (ev.contains("percent-damage")) {
                        double pct = ev.getDouble("percent-damage", 50.0);
                        damage = currentHp * (pct / 100.0);
                        double minDmg = ev.getDouble("min-damage", 10.0);
                        damage = Math.max(damage, minDmg);
                    } else if (ev.contains("true-damage")) {
                        damage = ev.getDouble("true-damage", 5.0);
                    }

                    double newHp = currentHp - damage;
                    if (newHp <= 0.0) {
                        // Safe death handling through Arena Manager
                        player.setHealth(0.5);
                        player.damage(1000.0); // Clean death trigger
                    } else {
                        player.setHealth(newHp);
                        player.damage(0.1); // Damage flash without knockback
                    }
                }

                // 3. Lightning effect
                if (ev.getBoolean("lightning", false)) {
                    player.getWorld().strikeLightningEffect(player.getLocation());
                }

                // 4. Sounds
                if (ev.contains("sound")) {
                    try {
                        String sName = ev.getString("sound");
                        if (sName != null) {
                            Sound s = Sound.valueOf(sName.toUpperCase());
                            player.playSound(player.getLocation(), s, 1.0f, 1.0f);
                        }
                    } catch (Exception ignored) {}
                }

                // 5. Particles
                if (ev.contains("particles")) {
                    try {
                        String pName = ev.getString("particles");
                        if (pName != null) {
                            Particle p = Particle.valueOf(pName.toUpperCase());
                            player.getWorld().spawnParticle(p, player.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.15);
                        }
                    } catch (Exception ignored) {}
                }

                // 6. Potion effects (Curse)
                if (ev.contains("potion-effects")) {
                    List<String> pList = ev.getStringList("potion-effects");
                    for (String potStr : pList) {
                        String[] parts = potStr.split(" ");
                        if (parts.length >= 3) {
                            try {
                                PotionEffectType pType = PotionEffectType.getByName(parts[0].toUpperCase());
                                int duration = Integer.parseInt(parts[1]);
                                int amplifier = Integer.parseInt(parts[2]);
                                if (pType != null) {
                                    player.addPotionEffect(new PotionEffect(pType, duration, amplifier, false, true));
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }

                // 7. Player Message
                if (ev.contains("message")) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', ev.getString("message")));
                }

                // 8. Broadcast message
                if (ev.contains("broadcast")) {
                    String bMsg = ev.getString("broadcast", "").replace("%player%", player.getName());
                    Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', bMsg));
                }

                break;
            }
        }
    }
}
