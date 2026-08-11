# ⚔️ MobArenav2 — Advanced Roguelike Mob Arena Engine

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21-brightgreen.svg?style=for-the-badge&logo=minecraft)
![API](https://img.shields.io/badge/Paper--API-1.21--R0.1-blue.svg?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-orange.svg?style=for-the-badge)
![Build Status](https://img.shields.io/badge/Build-Passing-success.svg?style=for-the-badge)

**MobArenav2** is a next-generation, high-performance Minecraft Paper plugin engineered to transform traditional mob arenas into thrilling, highly configurable **Roguelike survival battlegrounds**. 

Featuring modern Hex/RGB colors, 23+ dynamic Boss Skills, true armor penetration mechanics, per-arena base health systems, persistent stat shops, and class slot limits, **MobArenav2** offers an unmatched PvE experience for modern Minecraft server administrators and developers.

---

## 📋 Table of Contents

- [1. Overview & Highlights](#1-overview--highlights)
- [2. Prerequisites & Quick Start](#2-prerequisites--quick-start)
- [3. Core Gameplay Systems](#3-core-gameplay-systems)
  - [3.1 Wave Types & Wave Scaling](#31-wave-types--wave-scaling)
  - [3.2 Per-Arena Base Health System](#32-per-arena-base-health-system)
  - [3.3 Ranged Combat & True Damage Penetration](#33-ranged-combat--true-damage-penetration)
  - [3.4 Endless Mode & Player Voting](#34-endless-mode--player-voting)
- [4. Boss Engine & 23 Custom Boss Skills](#4-boss-engine--23-custom-boss-skills)
  - [4.1 Boss Spawning & Target Selection](#41-boss-spawning--target-selection)
  - [4.2 Custom Skill Registry](#42-custom-skill-registry)
- [5. Roguelike Upgrade System](#5-roguelike-upgrade-system)
  - [5.1 Trigger Modes (Interval, Specific Waves, Stage-Based)](#51-trigger-modes-interval-specific-waves-stage-based)
  - [5.2 Class Slot Limits & Permissions](#52-class-slot-limits--permissions)
- [6. Persistent `/ma shop` System](#6-persistent-ma-shop-system)
  - [6.1 Multi-Currency Support](#61-multi-currency-support)
  - [6.2 Advanced Combat Stats](#62-advanced-combat-stats)
  - [6.3 Integrated Shop Guide Menu](#63-integrated-shop-guide-menu)
- [7. Complete Configuration Guide (Deep Dive)](#7-complete-configuration-guide-deep-dive)
  - [7.1 Configuration Directory Overview](#71-configuration-directory-overview)
  - [7.2 `settings.yml` (Master Plugin Settings & Messages)](#72-settingsyml-master-plugin-settings--messages)
  - [7.3 `arenas/<name>.yml` (Wave Setup, Priority & Endless Mode)](#73-arenasnameyml-wave-setup-priority--endless-mode)
  - [7.4 Mob Attribute Parser & Equipment Syntax Guide](#74-mob-attribute-parser--equipment-syntax-guide)
  - [7.5 Boss Setup & Calling Boss Waves (`bosses.yml`)](#75-boss-setup--calling-boss-waves-bossesyml)
  - [7.6 Rewards System Setup (Normal & Endless Rewards)](#76-rewards-system-setup-normal--endless-rewards)
  - [7.7 `classes.yml` & Class Setup Instructions (`/ma setupclass`)](#77-classesyml--class-setup-instructions-ma-setupclass)
  - [7.8 Admin Safety, Performance & Caution Guidelines](#78-admin-safety-performance--caution-guidelines)
  - [7.9 `upgrades.yml` & `shops/` Configuration](#79-upgradesyml--shops-configuration)
  - [7.10 `supply.yml` & In-Game GUI Supply Editor](#710-supplyyml--in-game-gui-supply-editor)
- [8. Commands & Permissions Reference](#8-commands--permissions-reference)
- [9. Architecture & Performance Optimizations](#9-architecture--performance-optimizations)
- [10. Troubleshooting & FAQ](#10-troubleshooting--faq)
- [11. Credits & Support](#11-credits--support)

---

## 1. Overview & Highlights

### Why MobArenav2?
Traditional mob arena plugins rely on basic wave loops that quickly become repetitive. **MobArenav2** completely overhauls the experience with RPG progression, true armor penetration mechanics, and dynamic boss AI:

- **🎨 Full Hex/RGB Color Support**: Gorgeous `&#RRGGBB`, `#RRGGBB`, and `<#RRGGBB>` formatting across all chat messages, GUI titles, boss bars, and action bars.
- **🛡️ Totem-Proof Base Health**: Set per-arena or per-class max health via Bukkit Attributes rather than temporary potion effects. Players retain their bonus health even after popping a Totem of Undying.
- **⚡ Smart True Damage & Penetration**: Counters overpowered Protection IV Netherite armor with configurable armor penetration and true damage ratios.
- **👹 23 Dynamic Boss Skills**: From absolute freezing ice cages and Saitama punches to soul drains, shadow clones, and meteor showers.
- **🔮 Roguelike Mid-Game Upgrades**: Players pick cards between waves to stack critical strikes, lifesteal absorption, cleave shockwaves, and elemental lightning strikes.
- **🏪 Persistent Shop Infrastructure**: Players purchase permanent HP, Dodge, Cooldown Reduction, Armor, and Toughness bonuses across sessions using Vault, PlayerPoints, or custom commands.
- **⚡ Zero-Lag Engine**: Automatic projectile purging, safe chunk loading, isolated inventory backups, and syntax error firewall protection.

---

## 2. Prerequisites & Quick Start

### Server Requirements
- **Minecraft Server**: Paper, Purpur, or Spigot (Version **1.21** or higher).
- **Java Environment**: **Java 21** or **Java 25**.
- **Required Dependencies**:
  - [Vault](https://www.spigotmc.org/resources/vault.34315/) (Required for economy integration).
- **Optional Dependencies**:
  - [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) (For expansion placeholders).
  - [PlayerPoints](https://www.spigotmc.org/resources/playerpoints.80746/) (For alternative shop currency).
  - [MythicMobs](https://www.spigotmc.org/resources/mythicmobs.21708/) (For custom MythicMob spawning).

### Quick Installation Step-by-Step
1. Download the latest `MobArenav2-5.8.jar` release.
2. Place the file inside your server's `plugins/` directory.
3. Start or restart your server to extract default configuration files.
4. Set up your first arena using in-game setup commands:
   ```bash
   /ma create arena1
   /ma setup arena1
   ```
5. Use the setup tool (Blaze Rod) to mark Arena Point 1 (P1), Point 2 (P2), Lobby, Arena Spawn, and Mob Spawnpoints.
6. Type `/ma setupclass arena1` to configure class selection chests in the lobby.

---

## 3. Core Gameplay Systems

### 3.1 Wave Types & Wave Scaling
MobArenav2 supports 4 distinct wave types defined in arena YAML files:
- `mob`: Standard combat wave spawning configurable mob groups.
- `swarm`: Applies `mob-multiplier` to spawn massive mob hordes.
- `boss`: Dedicated Boss Wave triggering custom boss mechanics, custom spawn messages, and boss bars.
- `supply`: Peaceful loot wave spawning physical double chests filled with supply items for a limited duration.
- `bonus`: High-reward bonus wave granting bonus Vault money and points.

### 3.2 Per-Arena Base Health System
In vanilla Minecraft, applying health via Potion Effects (Health Boost) causes players to lose their extra hearts as soon as a **Totem of Undying** pops. 
MobArenav2 resolves this by injecting base attributes directly into `Attribute.GENERIC_MAX_HEALTH`:
- Set `base-health: 40.0` to grant all players 20 hearts upon joining.
- Set `base-health-per-class` to customize health per class (e.g., Tank: 60.0 HP, Mage: 30.0 HP).
- Stackable with persistent `/ma shop` health upgrades.

### 3.3 Ranged Combat & True Damage Penetration
Mob arrows are enhanced to ensure ranged mobs (Skeletons, Pillagers, Piglins) remain dangerous in late-game:
- **Arrow Velocity & Damage**: Base arrow damage increases by +55% with a +4.5% wave scaling factor. Arrow velocity is boosted by +30%.
- **Boss Arrow Penetration**: Bosses with `arrow-penetration: 0.40` deal 40% of their arrow damage directly as **True Damage**, bypassing all armor and enchantments.

### 3.4 Endless Mode & Player Voting
When players reach the maximum wave defined in `max-waves`, a 30-second GUI vote opens:
- If the majority votes **YES**, the arena enters **Endless Mode** with scaled mob HP/Damage multipliers.
- If the majority votes **NO**, players safely exit, receive accumulated Vault rewards, and return to the lobby.

---

## 4. Boss Engine & 23 Custom Boss Skills

### 4.1 Boss Spawning & Target Selection
Bosses can be configured with `spawn-random-player: true`. When enabled, the plugin scans all active players in the arena, picks a random target, and spawns the boss safely on solid ground near that player to prevent out-of-bounds spawning.

### 4.2 Custom Skill Registry

Below is the complete reference of all **23 Boss Skills** built into MobArenav2:

| Skill ID | Type Name | Description / Mechanics |
| :--- | :--- | :--- |
| `earthquake` | `AREA_DAMAGE` | Ground slam dealing AoE damage with **90% True Damage Armor Penetration**. |
| `earthquake_smash` | `EARTHQUAKE_SMASH` | Boss leaps high into the air and smashes down, knocking up players with 90% True Damage. |
| `saitama_punch` | `SAITAMA_PUNCH` | Devastating melee strike dealing high damage with **90% True Damage Armor Penetration**. |
| `boss_freeze` | `FREEZE` | Traps players in **Blue Ice** blocks, applying 35-40% Max HP True Damage & debuffs. |
| `absolute_defense` | `INVULNERABLE` | Grants complete invulnerability to the boss for a configured duration. |
| `dash_and_ranged` | `DASH_AND_RANGED` | Boss dashes multiple times at players, enters invulnerable stance, and fires True Damage lasers. |
| `spammer_bow` | `SPAMMER_BOW` | Relentless arrow barrage creating impact explosions dealing 80% True Damage. |
| `soul_drain` | `SOUL_DRAIN` | Drains life force from all nearby players (% Max HP) and heals the boss. |
| `shadow_clone` | `SHADOW_CLONE` | Spawns multiple temporary shadow mob clones to distract players. |
| `cursed_ground` | `CURSED_GROUND` | Curses the floor beneath players, dealing tick damage and applying Wither/Slowness. |
| `berserker_charge` | `BERSERKER_CHARGE` | Charges directly at the furthest player, dealing impact damage and knocking back obstacles. |
| `void_prison` | `VOID_PRISON` | Traps a targeted player inside a **Barrier Block Cage** with ticking void damage. |
| `super_arrow` | `SUPER_ARROW_DAMAGE` | Empowers boss arrows with flaming explosion tags for a set duration. |
| `ultimate_arrow` | `ULTIMATE_ARROW_DAMAGE` | Calls down rain of ultimate arrows targeting multiple players with AoE status effects. |
| `knockbacker` | `KNOCKBACKER` | Releases a sonic boom shockwave dealing True Damage and knocking back nearby players. |
| `death_mark` | `DEATH_MARK` | Marks a random player, disabling their Totems of Undying for a set time. |
| `magnetic_pull` | `MAGNETIC_PULL` | Pulls all players within radius towards the boss and applies Blindness. |
| `toxic_nova` | `TOXIC_NOVA` | Expands a growing ring of witch particles dealing damage per tick and Wither. |
| `minion_call` | `MINION_CALL` | Summons buffed minion mobs (e.g. Vindicators) with a temporary lifespan. |
| `lightning_strike` | `LIGHTNING` | Strikes lightning on multiple random active players in the arena. |
| `gravity_pull` | `PULL` | Strong gravitational pull dragging players directly to the boss. |
| `meteor_shower` | `METEOR_SHOWER` | Launches exploding fireballs down from the sky onto player coordinates. |
| `teleport_strike` | `TELEPORT_STRIKE` | Teleports behind the furthest player and executes a surprise strike. |

---

## 5. Roguelike Upgrade System

### 5.1 Trigger Modes
The Roguelike Upgrade System offers 3 flexible trigger modes configured in `settings.yml`, `upgrades.yml`, or per-arena:
1. **`INTERVAL`**: Opens the Upgrade GUI every $X$ waves (e.g. `trigger-every-waves: 5`).
2. **`SPECIFIC_WAVES`**: Opens only on specific wave numbers (e.g. `specific-waves: [5, 10, 15, 20]`).
3. **`STAGE_BASED`**: Opens based on custom wave ranges (e.g. Stage 1: waves 1-10 every 5 waves; Stage 2: waves 11-30 every 3 waves).

### 5.2 Class Slot Limits & Permissions
Players are limited in how many distinct upgrade types they can hold at once:
- **Default Limit**: `default-max-slots: 3`.
- **Per-Class Override**: Define custom limits in YAML (`class-slots: Tank: 4, Mage: 2`).
- **Permission Override**: Grant `mobarenav2.upgradeslot.X` (where X is 1–10) to give VIP players extra slots.

---

## 6. Persistent `/ma shop` System

### 6.1 Multi-Currency Support
Items in `/ma shop` can be configured with 3 payment methods:
- `VAULT`: Uses Vault economy money.
- `PLAYERPOINTS`: Uses PlayerPoints plugin balance.
- `CUSTOM_COMMAND`: Uses custom console commands and PlaceholderAPI checks.

### 6.2 Advanced Combat Stats
The shop allows players to purchase permanent upgrades that persist across sessions:
- **Max Health (`GENERIC_MAX_HEALTH`)**: Increases max health.
- **Armor (`GENERIC_ARMOR`)**: Increases base armor.
- **Armor Toughness (`GENERIC_ARMOR_TOUGHNESS`)**: Reduces effectiveness of high-damage hits.
- **Dodge (`CUSTOM_DODGE`)**: Adds up to +1.2% dodge chance in combat.
- **Cooldown Reduction (`CUSTOM_COOLDOWN_REDUCE`)**: Reduces ability cooldowns.
- **Lifesteal Boost (`CUSTOM_LIFESTEAL_BOOST`)**: Increases lifesteal trigger chance.
- **Crit Boost (`CUSTOM_CRIT_BOOST`)**: Increases critical strike chance.

### 6.3 Integrated Shop Guide Menu
Clicking the **Shop Guide** item (Book icon at slot 50) in `/ma shop` opens an interactive guide explaining how special abilities, currencies, and attribute upgrades operate.

---

## 7. Complete Configuration Guide (Deep Dive)

### 7.1 Configuration Directory Overview
When MobArenav2 loads, it extracts the following configuration directory inside `plugins/MobArenav2/`:

```
plugins/MobArenav2/
├── settings.yml          # Global settings, scoreboard, system messages, upgrade system defaults
├── classes.yml           # Combat class definitions, item syntax, armor, and potion buffs
├── bosses.yml            # Custom mob boss stats, gear, and skill assignments
├── bossskill.yml         # Mechanical configurations & tuning for all 23 Boss Skills
├── upgrades.yml          # Card pool definitions for mid-game Roguelike upgrades
├── supply.yml            # Supply drop chest loot tables (Managed via in-game GUI)
├── arenas/               # Directory containing individual arena YAML files (e.g. default.yml, arena1.yml)
├── shops/                # Directory containing shop category files (base_stats.yml, advanced_stats.yml...)
└── userdata/             # Player persistent shop level data
```

---

### 7.2 `settings.yml` (Master Plugin Settings & Messages)

`settings.yml` controls core plugin behavior, chat prefixes, scoreboard placeholders, Endless Mode GUI titles, and global Roguelike upgrade defaults.

```yaml
# ==========================================
# GENERAL SETTINGS
# ==========================================
prefix: "&#FF5555[&lMobArena V2&r&#FF5555] &r" # Supports Hex/RGB &#RRGGBB format

# ==========================================
# SCOREBOARD CONFIGURATION
# ==========================================
scoreboard:
  enabled: true
  title: "&#FF5555&lᴍᴏʙᴀʀᴇɴᴀ"
  lines:
    - "&7&m-------------------"
    - "&fᴀʀᴇɴᴀ: &e%arena%"
    - "&fsᴛᴀᴛᴜs: &a%players% Alive &7| &c%dead% Dead"
    - "&fᴍᴏʙ ʀᴇᴍᴀɪɴɪɴɢ: &c%mobs%"
    - "&fɴᴇxᴛ ᴡᴀᴠᴇ: &e%next_wave%"
    - "&fɴᴇxᴛ sᴜᴘᴘʟʏ ᴡᴀᴠᴇ: &b%next_supply%"
    - "&cɴᴇxᴛ ʙᴏss ᴡᴀᴠᴇ: &4%next_boss%"
    - "&7&m-------------------"

# ==========================================
# ROGUELIKE UPGRADE SYSTEM CONFIGURATION
# ==========================================
upgrade-system:
  enabled: true
  mode: "INTERVAL" # Options: "INTERVAL", "SPECIFIC_WAVES", "STAGE_BASED"
  trigger-every-waves: 5
  specific-waves: [5, 10, 15, 20]
  default-max-slots: 3
  class-slots:
    Tank: 4
    Warrior: 3
    Archer: 3
    Mage: 2
```

---

### 7.3 `arenas/<name>.yml` (Wave Setup, Priority & Endless Mode)

Each file in `plugins/MobArenav2/arenas/` represents an isolated arena instance. MobArenav2 uses an advanced **Smart Wave Priority Engine** allowing wave ranges, exact wave lists, and endless loops.

```yaml
name: "stage3"
settings:
  enabled: true
  required-arena: "stage2" # Required arena clear before unlocking
  max-waves: 100            # Waves before Endless Mode voting opens

  base-health: 40.0
  base-health-per-class:
    Tank: 60.0
    Archer: 35.0

  player-limits:
    min-players: 1
    max-players: 4

  entry-fee:
    enabled: false
    type: VAULT
    amount: 100.0

  rules:
    mob-drop: false
    player-pvp: false
    block-break: false
    block-place: false
    item-drop: true

  wave-conditions:
    clear-mob-before-next-wave: true
    clear-boss-before-next-wave: true
    next-wave: 6.0

# ==========================================
# SMART WAVE PRIORITY SYSTEM
# ==========================================
# Priority Rules:
# - Priority 1: Highest (Bosses & Supply Drops - Always overrides standard waves)
# - Priority 2: Swarm Events & Special Mini-Boss Hordes
# - Priority 3: Standard Phase Mob Waves (Wave Ranges 1-4, 5-9...)
# - Priority 4+: Endless Mode Loops (Waves 101+)
# ==========================================
waves:
  # --- Priority 1: Supply Drop Wave ---
  supply_drop_10:
    waves: 10
    priority: 1
    wave_types: supply
    supply-id: supply15
    duration: 30.0

  # --- Priority 1: Boss Wave ---
  boss_stage3_1:
    waves: 15
    priority: 1
    wave_types: boss
    mobs:
      titan_of_the_depths: 1

  # --- Priority 2: Swarm Event ---
  swarm_stage3_1:
    waves: 12
    priority: 2
    wave_types: swarm
    mob-multiplier: 2.5
    maxhp-bonus: x1.5
    general-effect: speed:2
    mobs:
      cave_spider: 15
      silverfish: 15

  # --- Priority 3: Standard Wave Range (Waves 1 to 4) ---
  stage3_phase1:
    waves: 1-4
    priority: 3
    wave_types: mob
    maxhp-bonus: x2.5
    damage-bonus: x2.5
    general-effect: speed:2
    mobs:
      zombie:
        amount: 3
        equip:
          - diamond_sword:1 sharpness:5 knockback:1 %100
          - diamond_chestplate:1 protection:4 thorns:2 %100
      skeleton:
        amount: 3
        equip:
          - bow:1 power:6 punch:2 flame:1 %100
          - diamond_helmet:1 protection:4 %100
      spider: 2
      MythicMob_DemonLord: 1 # Supports MythicMobs integration!

  # --- Priority 4: Endless Mode Chaos Loop (Waves 101+) ---
  endless_stage3_chaos:
    waves: 101+
    priority: 4
    after-game: true # Triggers during Endless Mode
    wave_types: mob
    maxhp-bonus: x15.0
    damage-bonus: x10.0
    movement-speed: x2.0
    general-effect: speed:3, strength:4, resistance:4
    mobs:
      warden: 5
      wither_skeleton:
        amount: 25
        equip:
          - netherite_sword:1 sharpness:7 fire_aspect:3 %100
          - netherite_chestplate:1 protection:6 %100
```

#### Wave Range Syntax Options:
- `waves: 10`: Single exact wave.
- `waves: 1-5`: Wave range from wave 1 to wave 5.
- `waves: 3, 7, 12`: Comma-separated exact wave numbers.
- `waves: 21+` or `waves: 101+`: Endless open-ended loop starting from wave 21 until infinity.

---

### 7.4 Mob Attribute Parser & Equipment Syntax Guide

MobAttributes in MobArenav2 are parsed by `MobAttributeParser.java`:

#### 1. Mob Attribute Modifiers (`maxhp-bonus`, `damage-bonus`, `movement-speed`...):
- `maxhp-bonus`: `+10` (adds 10 HP) or `x2.5` (multiplies base HP by 2.5). Modifies `Attribute.GENERIC_MAX_HEALTH` and fills health to max.
- `damage-bonus`: `+5` or `x2.0`. Modifies `Attribute.GENERIC_ATTACK_DAMAGE` and injects NBT tag `ma_damage_bonus`.
- `movement-speed`: `x1.5` or `+0.1`. Modifies `Attribute.GENERIC_MOVEMENT_SPEED`.
- `explosion-damage`: `x3.0` or `+2.0`. Modifies Creeper explosion radius directly via `creeper.setExplosionRadius()`.
- `bow-speed`: Injects `bow_speed_bonus` NBT tag to accelerate arrow projectile speed.
- `general-effect` / `effect`: Potion effect syntax `"effect_name:amplifier, effect_name:amplifier"` (e.g. `speed:2, resistance:1`). Applied with infinite duration `Integer.MAX_VALUE`.

#### 2. Mob Equipment Parsing Syntax:
```yaml
equip:
  - "material:amount enchant_name:level;enchant_name:level %chance"
```
- **Example 1**: `- "diamond_sword:1 sharpness:5 knockback:1 %100"` (100% chance to equip Diamond Sword with Sharpness V & Knockback I).
- **Example 2**: `- "netherite_chestplate:1 protection:6 thorns:4 %50"` (50% chance to equip Netherite Chestplate).
- **Example 3**: `- "shield:1 unbreaking:5 OFF_HAND %100"` (Equips Shield directly into mob **Off-Hand** slot!).

#### Automatic Slot Detection Logic:
- Ends with `_HELMET` -> Helmet Slot
- Ends with `_CHESTPLATE` -> Chestplate Slot
- Ends with `_LEGGINGS` -> Leggings Slot
- Ends with `_BOOTS` -> Boots Slot
- Contains `OFF_HAND` -> Off-Hand Slot
- Default -> Main Hand Slot

*(Note: MobArenav2 automatically sets mob item drop chance to `0.0f` for all slots to prevent mobs from dropping arena gear upon death!)*

---

### 7.5 Boss Setup & Calling Boss Waves (`bosses.yml`)

#### Defining a Custom Boss in `bosses.yml`:
```yaml
eclipse_huntsman:
  type: SKELETON
  Boss_displayname: "&#00FFFF&lEclipse Huntsman"
  Bossbar: true
  nametagshow: true
  HP: 400.0                      # Multi-alias support: HP / hp / max-hp
  Damage: 10.0                    # Multi-alias support: Damage / damage
  ranged-damage-multiplier: 2.5 # Multiplies bow arrow damage by 2.5x
  arrow-penetration: 0.40        # 40% of arrow damage bypasses armor as true damage
  spawn-random-player: true     # Spawns boss safely near a random active arena player
  spawn-message: "&#00FFFF&l⚠ An arrow from the shadows... The Eclipse Huntsman has locked on! ⚠"
  spawn-sound: "ENTITY_SKELETON_HORSE_DEATH"
  effect-bonus: "speed:2, jump_boost:2"
  Equip:
    - "bow:1 power:5;punch:2 %100"
    - "chainmail_helmet:1 %100"
  skill:                          # Active skill pool from bossskill.yml
    - "dash_and_ranged"
    - "spammer_bow"
    - "super_arrow"
  enrage-skill:                   # Enrage skills triggered when HP < 50%
    - "ultimate_arrow"
```

---

### 7.6 Rewards System Setup (Normal & Endless Rewards)

The `rewards:` section in arena YAML files distributes money and commands upon wave completion:

```yaml
rewards:
  # Recurring reward during normal game (every 5 normal waves)
  every 5 normal:
    - vault:2000000.0
    - cmd(give %player% diamond 5)

  # Fixed single wave reward (Wave 85)
  '85':
    - vault:8000000.0

  # Recurring Endless Mode reward (every 3 endless waves)
  every 3 endless:
    - 'after-game: true' # Deferred: Paid out safely after the match ends
    - vault:2000000.0
    - cmd(eco give %player% 500000)
```

#### Reward Types:
- `vault:<amount>`: Direct Vault economy money payout.
- `cmd(<command>)`: Executes console command with `%player%`, `%arena%`, `%wave%` placeholders.
- `'after-game: true'`: Defers reward payout until the player exits the match to prevent mid-combat inventory clutter.

---

### 7.7 `classes.yml` & Class Setup Instructions (`/ma setupclass`)

#### `classes.yml` Example:
```yaml
classes:
  Knight:
    limit: -1
    items: "netherite_sword:1 sharpness:5;unbreaking:3, golden_apple:16, cooked_beef:64"
    armor: "netherite_helmet:1 protection:4, netherite_chestplate:1 protection:4, netherite_leggings:1 protection:4, netherite_boots:1 protection:4"
    effects: "health_boost:4, resistance:0"

  Juggernaut:
    limit: 1
    allowed-arenas: ["stage2", "boss_room"]
    items: "netherite_axe:1 sharpness:6, shield:1 unbreaking:5, enchanted_golden_apple:3"
    armor: "netherite_helmet:1 protection:5, netherite_chestplate:1 protection:5, netherite_leggings:1 protection:5, netherite_boots:1 protection:5"
    effects: "slowness:1, resistance:2, health_boost:9"
```

#### 📜 Step-by-Step Guide for In-Game Class Setup (`/ma setupclass`):
1. **Execute Command**: Type `/ma setupclass <arena_name>` (Alias: `/ma classsetup <arena_name>`).
2. **Receive Setup Tool**: The administrator receives a special **Class Setup Wand** in their hand.
3. **Link Class Chests/Signs**:
   - Go to your Arena Lobby.
   - Right-click or Shift-Right-Click a **Chest** or **Sign**.
   - Type the class name (e.g. `Knight`, `Archer`, `Juggernaut`) in chat when prompted.
4. **Player Usage**: Players in the lobby can right-click the linked sign or chest to preview items and select their class!

---

### 7.8 Admin Safety, Performance & Caution Guidelines

> [!CAUTION]
> **🚨 Infinite Mob Multipliers Warning**: Setting `mob-multiplier: 10.0` with 30 mob entries will attempt to spawn **300 entities simultaneously**, which will crash low-end server CPUs. Keep `mob-multiplier` between `1.5` and `3.0`.

> [!WARNING]
> **⚡ Overpowered True Damage Penetration**: Setting `arrow-penetration: 1.0` (100%) or `armor-penetration: 1.0` on a boss with 50 damage will 1-shot any player regardless of Protection IV Netherite armor. Maintain penetration ratios between `0.30` and `0.50` for balanced gameplay.

> [!NOTE]
> **🛡️ Bukkit Max Health Ceiling**: Vanilla Spigot/Paper caps `GENERIC_MAX_HEALTH` at `2048.0` HP. If you configure a Boss with 5000 HP, ensure you raise `settings.attribute.maxHealth.max` in `spigot.yml` first!

---

### 7.9 `upgrades.yml` & `shops/` Configuration

#### 1. Roguelike Upgrades Config (`upgrades.yml`)
The `upgrades.yml` file contains the logic for mid-game roguelike upgrades (cards presented between waves). It defines trigger modes, max slots, and the stats of every single upgrade card.

```yaml
settings:
  enabled: true
  mode: "INTERVAL" # Options: "INTERVAL", "SPECIFIC_WAVES", "STAGE_BASED"
  trigger-every-waves: 5
  class-slots:
    Tank: 4
    Mage: 2

upgrades:
  critical_strike:
    display-name: '&#FFAA00&lCritical Strike &8[Lv.%current_level%/%max_level%]'
    material: GOLDEN_SWORD
    weight: 35             # Higher weight = Higher chance to appear in GUI
    slot: 2                # Fixed slot position in Upgrade GUI
    type: CRITICAL_CHANCE  # Hardcoded upgrade logic type
    max-level: 5
    base-value: 12.0       # 12% at Lv 1
    increment: 6.0         # +6% per level up
    lore:
      - '&fCurrent Chance: &c%current_value%%'
      - '&fNext Level: &c%next_value%%'
```

#### 2. Persistent Attribute Shops (`shops/` Directory)
All `.yml` files inside `plugins/MobArenav2/shops/` load dynamically to create categorized GUIs for players to permanently upgrade attributes using economy plugins.

```yaml
category:
  id: "advanced_stats"
  display-name: "&#FFAA00&lADVANCED COMBAT STATS"
  material: DIAMOND_SWORD
  slot: 24

items:
  perm_dodge:
    category: "advanced_stats"
    slot: 20
    display-name: "&#FFAA00&lPhantom Reflexes &8[+0.2% Dodge]"
    material: FEATHER
    price-type: VAULT          # Supports: VAULT, PLAYERPOINTS, CUSTOM_COMMAND
    base-cost: 250000.0
    cost-multiplier: 2.0
    max-level: 6
    attribute: CUSTOM_DODGE
    base-value: 0.2
    increment: 0.2
    output-actions:
      - "message: &a[MobArena Shop] Upgraded Phantom Reflexes to Level %level%!"
      - "sound: ENTITY_ENDERMAN_TELEPORT 1.0 2.0"
```

---

### 7.10 `supply.yml` & In-Game GUI Supply Editor

Do **NOT** edit `supply.yml` manually! `supply.yml` stores raw serialized NBT item stacks.

#### Managing Supply Drops In-Game:
1. `/ma supply create <id>` (Creates a new supply drop loot table ID, e.g. `chest_boss`).
2. `/ma supply edit <id>` (Opens an interactive virtual inventory GUI. Place any custom NBT items, weapons, potions inside, then press ESC to save automatically).
3. `/ma supply list` (Lists all registered supply chest IDs).

---

## 8. Commands & Permissions Reference

| Command | Permission | Access | Description |
| :--- | :--- | :--- | :--- |
| `/ma join [arena]` | *None* | Player | Joins a MobArena instance. |
| `/ma leave` | *None* | Player | Safely leaves the current arena. |
| `/ma class` | *None* | Player | Opens class selection menu. |
| `/ma shop` | *None* | Player | Opens persistent attribute shop GUI. |
| `/ma vote <yes\|no>` | *None* | Player | Casts vote for Endless Mode. |
| `/ma top` / `/ma stats` | *None* | Player | Displays leaderboard & stats. |
| `/ma setup [arena]` | `mobarenav2.admin` | Admin | Enters wand setup mode. |
| `/ma create <name>` | `mobarenav2.admin` | Admin | Creates a new arena file. |
| `/ma setpoint1` / `/ma setpoint2` | `mobarenav2.admin` | Admin | Sets region boundaries. |
| `/ma setlobby` / `/ma setspawn` | `mobarenav2.admin` | Admin | Sets lobby & player spawn. |
| `/ma addsupply` | `mobarenav2.admin` | Admin | Adds a supply drop location. |
| `/ma setupclass [arena]` | `mobarenav2.admin` | Admin | Opens class setup wand tool. |
| `/ma forceend <arena>` | `mobarenav2.admin` | Admin | Forcefully ends an arena match. |
| `/ma reload` | `mobarenav2.admin` | Admin | Reloads all configuration files. |
| *N/A* | `mobarenav2.bypass.arenalock` | Admin | Bypasses arena unlock requirements. |
| *N/A* | `mobarenav2.upgradeslot.X` | Admin/VIP | Grants $X$ upgrade slots (1-10). |

---

## 9. Architecture & Performance Optimizations

MobArenav2 is designed for production servers carrying 100+ players:
- **`InventoryBackupManager`**: Safely serializes player inventories to disk before matches, preventing inventory loss during unexpected crashes.
- **Projectile Purging**: Scans and clears stuck arrows and tridents between wave transitions.
- **Syntax Error Firewall**: Validates YAML file syntax in RAM before saving to disk to prevent file wiping upon syntax errors.
- **Idempotent Attributes**: Recalculates stats upon joining or buying items without duplicating attribute modifiers.

---

## 10. Troubleshooting & FAQ

### Q: Why do hearts disappear when a player pops a Totem of Undying?
**A**: Ensure you configure `base-health` in your arena YAML file or `settings.yml`. MobArenav2 sets base max health using Bukkit Attributes (`GENERIC_MAX_HEALTH`) rather than potion effects.

### Q: Why are new configuration options missing in my existing YAML files?
**A**: Spigot/Paper does not overwrite existing configuration files on disk. To apply new config defaults, delete the old file (or add the missing section manually as shown in the Configuration Guide).

### Q: Why isn't Vault giving money rewards after match completion?
**A**: Ensure Vault and a compatible economy plugin (e.g. EssentialsX Economy) are installed and enabled on your server.

---

## 11. Credits & Support

- **Author & Developer**: Meowchan12
- **Core Platform**: PaperMC & Bukkit APIs
- **License**: MIT License

For bug reports, feature requests, or support, please open an Issue on GitHub or contact us through our official support channel. Happy arena battles! ⚔️
