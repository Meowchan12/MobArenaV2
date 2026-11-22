# ⚔️ MobArenav2 — Next-Generation PvE Arena Engine

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.4-brightgreen.svg?style=for-the-badge&logo=minecraft)
![API](https://img.shields.io/badge/Paper--API-1.21.4--R0.1-blue.svg?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-21%20%7C%2025-orange.svg?style=for-the-badge&logo=openjdk)
![License](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)
![Build Status](https://img.shields.io/badge/Build-v8.4.4--Passing-success.svg?style=for-the-badge)

**MobArenav2** is a high-performance, modular Minecraft Paper/Purpur plugin engineered to revolutionize survival mob arenas into dynamic, scalable, and fully configurable **PvE Battlegrounds**.

Designed from the ground up for modern server networks, **MobArenav2** features an asynchronous SQLite WAL database, decoupled event-driven combat pipelines, multi-phase dynamic boss encounters, dual-tier paginated upgrade directories, fair-draft roll systems, slot-compressing Ultimate Fusions, and over-cap ascendance mechanics.

---

## 📋 Table of Contents

- [1. Feature Highlights](#1-feature-highlights)
- [2. Prerequisites & Quick Start](#2-prerequisites--quick-start)
- [3. Core Arena & Wave Mechanics](#3-core-arena--wave-mechanics)
  - [3.1 Wave Types & Spawning](#31-wave-types--spawning)
  - [3.2 Pre-Match Preparation & 5-Second Countdown](#32-pre-match-preparation--5-second-countdown)
  - [3.3 Centralized Starter Upgrades](#33-centralized-starter-upgrades)
  - [3.4 Tactical Revive System](#34-tactical-revive-system)
  - [3.5 Dynamic Combat HUD Action Bar](#35-dynamic-combat-hud-action-bar)
  - [3.6 Endless Mode & Democratic Voting](#36-endless-mode--democratic-voting)
- [4. Upgrade Engine & Ascendance System](#4-upgrade-engine--ascendance-system)
  - [4.1 100% Data-Driven Combat Mechanics (Zero Hardcode)](#41-100-data-driven-combat-mechanics-zero-hardcode)
  - [4.2 Dual-Tier Paginated Upgrades Directory](#42-dual-tier-paginated-upgrades-directory)
  - [4.3 In-Match Draft Rolls & Pity System](#43-in-match-draft-rolls--pity-system)
  - [4.4 Ultimate Fusion Upgrades & Slot Compression](#44-ultimate-fusion-upgrades--slot-compression)
  - [4.5 Over-Cap Ascendance System (Maxed-Out Pool)](#45-over-cap-ascendance-system-maxed-out-pool)
  - [4.6 Upgrades Catalog](#46-upgrades-catalog)
- [5. Boss Engine & Tactical Mechanics](#5-boss-engine--tactical-mechanics)
  - [5.1 Multi-Phase Bosses & Desperation Scaling](#51-multi-phase-bosses--desperation-scaling)
  - [5.2 Crushing Blow & Counter-Meta Mechanics](#52-crushing-blow--counter-meta-mechanics)
  - [5.3 Anti-Leech Damage Tracking & Co-Op Boss Rewards](#53-anti-leech-damage-tracking--co-op-boss-rewards)
- [6. Persistent `/ma shop` Infrastructure](#6-persistent-ma-shop-infrastructure)
- [7. Interactive GUI Suite](#7-interactive-gui-suite)
- [8. Configuration Files Guide](#8-configuration-files-guide)
- [9. Role-Aware Commands & Permissions Matrix](#9-role-aware-commands--permissions-matrix)
  - [9.1 Player Commands](#91-player-commands)
  - [9.2 Administrator Commands](#92-administrator-commands)
  - [9.3 Permission Hierarchy](#93-permission-hierarchy)
- [10. PlaceholderAPI Integration](#10-placeholderapi-integration)
- [11. Migration & Troubleshooting (v8.4.4)](#11-migration--troubleshooting-v844)

---

## 1. Feature Highlights

- **⚡ Zero-Lag High-Throughput Engine:**
  - SQLite WAL mode (`PRAGMA journal_mode = WAL; PRAGMA synchronous = NORMAL;`) with non-blocking asynchronous background writes.
  - Asynchronous YAML serialization for arena summaries and player records via `CompletableFuture.runAsync()`.
  - Centralized entity and projectile cleaner (`ArenaMemoryManager`) purging arrows, tridents, and loose drops.
- **🛡️ [v8.4.4] Role-Aware Command Suite & Permission Security:**
  - Segregated `/ma help` system delivering tailored command lists for regular players (`/ma help player`) and administrators (`/ma help admin`).
  - Strict permission-gated tab-completion preventing non-admin players from discovering administrative commands or aliases.
  - Native parent permission trees (`mobarenav2.player.*` and `mobarenav2.admin`) in `plugin.yml`.
- **⚙️ [v8.4.3] 100% Data-Driven Upgrade & Boss AI Engine:**
  - Complete elimination of hardcoded combat values: all damage multipliers, cleave radii, knockback resistances, lifesteal caps, and milestones are dynamically read from `upgrades.yml` via `UpgradeData`.
  - Dynamic Boss AI cooldowns, initial delays, and Crushing Blow telegraph parameters read dynamically from `bosses.yml` and `bossskill.yml`.
  - Zero placeholder leaks in `/ma myupgrades` and `MatchDetailGUI`.
- **🧹 [v8.4.2] Pre-Match Reset, Crash Recovery & Starter Upgrades:**
  - 5-second countdown on full lobby ready with automated deep cleanup (removes supply chests, embedded arrows, drops, and untagged mobs).
  - Deterministic pre-lobby world coordinates saved to disk (`<uuid>.yml`) and restored on death or exit.
  - Centralized starter upgrade system in `settings.yml` drafting upgrades upon arena entry.
  - Tactical `/ma revive <player>` restoring 100% combined HP, reapplying class potion effects and active upgrades, plus 3 seconds of invulnerability.
- **🎯 [v8.4.1] Orbital Targeting Engine (Supernova & Celestial Cataclysm):**
  - Supernova: Smart orbital laser targeting up to 6 mobs within 15–18 blocks, 5-second internal cooldown, and boss damage cap.
  - Celestial Cataclysm (Ultimate): Apocalyptic orbital laser targeting up to 8 mobs within 24–28 blocks, 5-second cooldown, and boss damage cap.
- **🔮 [v8.3.0] Ultimate Fusion Upgrades & Slot Compression:**
  - Fuse pairs of Max-Level upgrades into devastating `[Ultimate]` cards.
  - **Slot Compression:** Merging 2 component cards into 1 Ultimate card instantly **frees +1 upgrade slot**!
  - Dedicated **Ultimate Almanac GUI** (`/ma upgrades preview [ultimate]`).
- **🧙‍♀️ Support Witches (Priority Targets):**
  - **Healer Witch:** Green glowing outline, 1.25x scale, 64m aura healing 5% Max HP to mobs & bosses (never heals players!).
  - **War Witch (Buffer):** Red glowing outline, 1.25x scale, 48m aura stacking Strength every 5s.
- **⏱️ Non-Intrusive Combat HUD:**
  - Real-time Action Bar HUD: `§6⏱ %match_time% │ §eWave %wave%/%max% │ §c⚔ %kills% Kills │ §a💥 %dps% DPS`.
  - Sub-microsecond execution (<0.002ms) with zero chunk or entity scan overhead.

---

## 2. Prerequisites & Quick Start

### Server Requirements
- **Server Software:** [Paper](https://papermc.io/), [Purpur](https://purpurmc.org/), or compatible fork running **1.21.4** (Build 1.21.4-R0.1-SNAPSHOT+).
- **Java Runtime:** **Java 21** or **Java 25**.
- **Required Plugins:**
  - [Vault](https://www.spigotmc.org/resources/vault.34315/) + Compatible Economy Plugin (EssentialsX, CMI, etc.).
- **Optional Integrations:**
  - [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) (For expansion placeholders).
  - [PlayerPoints](https://www.spigotmc.org/resources/playerpoints.80746/) (For secondary shop currency).
  - [MythicMobs](https://www.spigotmc.org/resources/mythicmobs.21708/) (For custom Mythic Mob spawns).

### Quick Setup Guide
1. Place `MobArenav2-8.4.4.jar` into your server's `plugins/` directory.
2. Start or restart the server to extract default configurations.
3. Create and set up your arena boundaries:
   ```text
   /ma setup arena1
   ```
4. Set boundary corner points, spectator warp, and save the arena:
   ```text
   /ma setp1 arena1
   /ma setp2 arena1
   /ma setspectator arena1
   /ma savearena arena1
   ```
5. Join and prepare in the lobby:
   ```text
   /ma join arena1
   /ma class
   /ma ready
   ```

---

## 3. Core Arena & Wave Mechanics

### 3.1 Wave Types & Spawning
MobArenav2 defines flexible waves inside `plugins/MobArenav2/arenas/<arena>.yml`:
- **`mob`**: Standard battle wave spawning configured groups of vanilla or custom entities.
- **`swarm`**: Swarm wave applying a `mob-multiplier` (e.g., 2.5x–4.0x) to overwhelm players.
- **`boss`**: Dedicated Boss encounter with custom health scaling, boss bar, skill queue, and phase transitions.
- **`supply`**: Loot wave spawning physical supply chests with consumable supplies, totems, and weapons.
- **`bonus`**: Rapid high-reward wave granting bonus arena coins, money, and score multipliers.

### 3.2 Pre-Match Preparation & 5-Second Countdown
When all joined players toggle `/ma ready`, the arena triggers a **5-second pre-match countdown**:
1. During this countdown, `ArenaRestorer` conducts a deep purge:
   - Destroys lingering supply chests from previous games.
   - Clears embedded arrows, spectral arrows, and tridents from walls and floors.
   - Culls loose item drops.
   - Removes untagged or drifting entities inside arena bounds.
2. Only after deep cleanup completes are players teleported to arena spawns.

### 3.3 Centralized Starter Upgrades
Configured centrally in `settings.yml`:
```yaml
starter-upgrade:
  enabled: true
  upgrade-count: 1
  normal-chance: 70
  super-chance: 20
  fusion-chance: 10
```
Players receive their starter upgrade draft GUI immediately upon teleporting into the arena.

### 3.4 Tactical Revive System (`/ma revive <player>`)
When an administrator revives a fallen player:
- Instantly sets health to 100% of their combined maximum health (Arena base + Class health bonus).
- Re-applies all configured potion effects of their class.
- Restores and reapplies all active upgrades owned prior to death.
- Grants 3 seconds of invulnerability protection.

### 3.5 Dynamic Combat HUD Action Bar
```text
§6⏱ 00:05:42 │ §eWave 12/20 │ §c⚔ 148 Kills │ §a💥 2,450 DPS
```
- **Elapsed Duration:** Displays active match time formatted as `HH:mm:ss`.
- **Wave Counter:** Current wave vs maximum configured wave.
- **Kill Tracker:** Individual player kill count for the active match.
- **Live DPS:** Total damage dealt divided by elapsed seconds, cached and refreshed every 10–20 ticks.

### 3.6 Endless Mode & Democratic Voting
Upon clearing the final wave (`settings.max-waves`), players trigger a 30-second democratic voting phase:
- A GUI prompt asks players to vote **Continue to Endless Mode** or **Conclude Match & Claim Rewards**.
- If approved, the match continues indefinitely with exponential health, damage, and coin scaling.
- If declined or tied, the match concludes cleanly, dispatches rewards, and restores players.

---

## 4. Upgrade Engine & Ascendance System

### 4.1 100% Data-Driven Combat Mechanics (Zero Hardcode)
In MobArenav2 v8.4.3+, all combat calculations are decoupled into `UpgradeData`. 
- Base damage values, linear/percentage increments, milestone activation levels, cleave percentages, and explosion radii are read directly from `upgrades.yml`.
- Editing `upgrades.yml` immediately affects live in-game damage formulas with zero Java recompilation required.

### 4.2 Dual-Tier Paginated Upgrades Directory
Players can inspect available cards anytime via `/ma upgrades`:
- **Standard Upgrades Directory (`/ma upgrades`):** 28 upgrades per page with dynamic category filtering (`ALL`, `OFFENSIVE`, `DEFENSIVE`, `UTILITY`).
- **Super Upgrades Vault:** Displays legendary cards with live match telemetry tracking (milestones, reflected damage, kill counts).

### 4.3 In-Match Draft Rolls & Pity System
- **Boss Victory Guarantee:** Defeating a Boss wave guarantees **1–2 Super Upgrades** in the draft.
- **Pity Counter:** Experiencing 3 consecutive normal drafts without a Super Upgrade guarantees a Super Upgrade in slot 3 on the 4th draft.
- **Lucky Roll:** Slot 3 has an independent **8% lucky chance** to roll a Super Upgrade on standard waves.
- **Diminishing Super Rates:** After obtaining Super Upgrades, subsequent super roll rates diminish fairly (8% ➔ 6% ➔ 4% ➔ 2%).

### 4.4 Ultimate Fusion Upgrades & Slot Compression
When two complementary upgrades reach their Maximum Level, players can combine them into an `[Ultimate]` card via `/ma fuse`:
- **Slot Compression:** Fusing 2 component cards into 1 Ultimate card instantly **frees +1 upgrade slot**!
- **State Inheritance:** Ongoing kill stacks and telemetry (e.g., Slaughterhouse Engine kills) are 100% inherited by the fused card.
- **Preview Almanac:** Players can inspect all fusion recipes anytime via `/ma upgrades preview`.

### 4.5 Over-Cap Ascendance System (Maxed-Out Pool)
When all upgrade slots are filled, the draft rolls choices from the 7-card **Ascendance Pool**:
1. `MAXED_OUT_FULL_REPAIR` (**Master Armorer's Forge**): 100% restores durability to equipped armor, weapons, shields, and inventory bag contents.
2. `MAXED_OUT_OVERPOWER`: Stacks permanent +5% Total Damage.
3. `MAXED_OUT_TRUE_STRIKE`: Stacks permanent +3.0 Flat True Damage.
4. `MAXED_OUT_AGILITY`: Stacks permanent +4% Movement Speed attribute.
5. `MAXED_OUT_HEAL`: Instantly restores 100% HP & Saturation.
6. `MAXED_OUT_BOUNTY`: Grants +5,000 Arena Coins.
7. `MAXED_OUT_BLOOD_SACRIFICE`: High-risk sacrifice of 40% Max HP for +$10,000,000 Server Cash.

### 4.6 Upgrades Catalog

#### Selected Standard Upgrades
- **`warrior_might`:** Increases attack damage and cleaves nearby enemies upon reaching milestone level.
- **`iron_skin`:** Grants bonus Armor, Armor Toughness, and knockback resistance.
- **`critical_strike`:** Grants chance to deal 2.0x normal critical hits and 3.0x mega critical hits.
- **`vampiric_strike`:** Lifesteal siphoning damage into health; overhealing converts to an Absorption shield.
- **`extended_reach`:** Native Paper 1.21.4 `generic.entity_interaction_range` reach expansion (+0.4m per level).
- **`predator_eye`:** Amplified damage against targets below 50% HP.
- **`armor_piercing`:** Armor shredding converting a percentage of physical damage into true damage.
- **`titan_heart`:** Wave-start golden absorption hearts.

#### Super Upgrades
- **`super_nova`:** Orbital laser locking up to 6 targets within 15–18 blocks, 5s internal cooldown, true damage + % max HP damage with boss cap.
- **`super_executioner`:** Instantly executes all monsters and bosses below configured health threshold (default 15% HP) with death thunder and AoE blast.
- **`super_aegis`:** Passive damage reduction; fatal hits trigger 100% heal, 6s invulnerability, and a radial divine shockwave.
- **`super_slaughterhouse`:** Unlimited stacking bonus damage per mob kill.
- **`super_overclock`:** Maximizes attack speed and haste.

#### Ultimate Fusions
- **`ultimate_celestial_cataclysm` (Supernova + Titan Heart):** Orbital laser scanning 24–28 blocks, targeting up to 8 enemies, 5s cooldown, devastating true damage + % max HP damage.
- **`ultimate_longinus_cleave` (Extended Reach + Warrior's Might):** Massive reach with radial supersonic cleaves.
- **`ultimate_aegis_retribution` (Super Aegis + Mirror of Retribution):** Retaliatory shockwave damage storing and reflecting incoming attacks.
- **`ultimate_slaughterhouse_overclock` (Slaughterhouse + Overclock):** High-speed slaughterhouse stacking.

---

## 5. Boss Engine & Tactical Mechanics

### 5.1 Multi-Phase Bosses & Desperation Scaling
Bosses transition through distinct combat phases based on health thresholds (Phase 1, Phase 2, and Desperation Phase below 35% HP), unlocking enhanced attack speeds and aggressive skill rotations.

### 5.2 Crushing Blow & Counter-Meta Mechanics
- **Crushing Blow:** Heavy windup attack preceded by a red particle telegraph circle. Failing to dodge disables shields for 3 seconds and deals heavy damage capped at safe maximum health thresholds.
- **Anti-Elytra Grounding:** Grounding vortex prevents players from abusing Elytra glide inside arena bounds.
- **Anti-Totem Soul Shatter:** Direct true damage spikes bypass totem hoarding.

### 5.3 Anti-Leech Damage Tracking & Co-Op Boss Rewards
- Every fighter dealing damage to a Boss receives +1 Boss Slain credited to their career records.
- Anti-leech threshold requires a minimum damage contribution percentage to earn end-of-boss loot crates.

---

## 6. Persistent `/ma shop` Infrastructure

Players earn persistent currency across matches to spend in `/ma shop`:
- **Base Stats:** Permanent Max HP, Base Armor, and Attack Damage bonuses.
- **Advanced Stats:** Dodge chance, Cooldown Reduction (CDR), Critical Chance, and Flat True Damage.
- **Casino & Utilities:** Extra upgrade slots, extra rerolls, and Pin Permits.

---

## 7. Interactive GUI Suite

- **Match Record GUI (`/ma stats` / `MatchDetailGUI`):** Inspect combat metrics (Kills, Bosses Slain, Damage Dealt, Damage Absorbed), survival duration (`HH:mm:ss`), and every upgrade card utilized.
- **Class Selection GUI (`/ma class`):** Interactive class kits with permission and limit validation.
- **Upgrade Almanac GUI (`/ma upgrades preview`):** Traversal tree displaying all normal, super, and fusion recipes.
- **Live Upgrades Inspector (`/ma myupgrades`):** In-match interactive GUI showing real-time stat values of active upgrades without placeholder leaks.

---

## 8. Configuration Files Guide

```
plugins/MobArenav2/
├── settings.yml                  # Global plugin settings, starter upgrade config, rewards
├── upgrades.yml                  # Complete upgrade balance formulas, milestone levels, and lore
├── classes.yml                   # Class kits, gear, attribute bonuses, and slot limits
├── bosses.yml                    # Boss definitions, HP, display names, equipment, dynamic AI delays
├── bossskill.yml                 # Boss skill parameters, cooldowns, damage ratios, radii
├── boss_rewards.yml              # Tiered boss reward loot tables, anti-leech thresholds
├── supply.yml                    # Supply wave loot crate definitions
├── messages.yml                  # Localized messages and prefix formatting
├── arenas/
│   └── default.yml               # Default arena template automatically cloned for new arenas
└── shops/
    ├── base_stats.yml            # Permanent HP, Armor, Damage shop upgrades
    ├── advanced_stats.yml        # Dodge, CDR, Crit, Lifesteal, True DMG upgrades
    ├── casino_utilities.yml      # Pin permits, extra slots, extra rerolls
    └── ultimates.yml             # Class ultimate ability upgrades
```

---

## 9. Role-Aware Commands & Permissions Matrix

### 9.1 Player Commands
Regular players without administrative permissions only see and tab-complete these commands:

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/ma help [player]` | *None* | View available player commands. |
| `/ma join <arena>` | `mobarenav2.player.join` | Join an arena lobby. |
| `/ma leave` | `mobarenav2.player.leave` | Safely leave the current arena and restore previous location. |
| `/ma ready` | `mobarenav2.player.join` | Toggle ready state in the arena lobby. |
| `/ma class [class]` | `mobarenav2.player.join` | Open the Class Selection GUI or choose a class directly. |
| `/ma spec <arena>` | `mobarenav2.player.spec` | Spectate an active arena match. |
| `/ma list` | `mobarenav2.player.list` | View a list of all configured arenas. |
| `/ma shop [category]` | `mobarenav2.player.shop` | Open the permanent attribute progression shop. |
| `/ma upgrades` | `mobarenav2.player.join` | Open the Upgrades Directory and preview GUI. |
| `/ma myupgrades [player]` | `mobarenav2.player.join` | View active match upgrades and live combat metrics. |
| `/ma fuse` | `mobarenav2.player.join` | Open the Ultimate Fusion draft GUI. |
| `/ma top <arena> [page]` | `mobarenav2.player.top` | View arena leaderboards and survival/clear times. |
| `/ma stats [player]` | `mobarenav2.player.stats` | View lifetime career statistics. |

### 9.2 Administrator Commands
All administrative commands are strictly permission-gated and hidden from non-admin players:

| Command | Permission | Category | Description |
| :--- | :--- | :--- | :--- |
| `/ma help admin` | `mobarenav2.admin` | Help | View complete categorized administrative commands. |
| `/ma forcestart <arena>` | `mobarenav2.admin` | Match | Bypass countdowns and immediately start the match. |
| `/ma forceend <arena>` | `mobarenav2.admin` | Match | Immediately terminate an arena match and clean the world. |
| `/ma skipwave <arena>` | `mobarenav2.admin` | Match | Skip the current wave in an active arena. |
| `/ma killmobs <arena>` | `mobarenav2.admin` | Match | Kill all living monsters inside an arena. |
| `/ma kick <player>` | `mobarenav2.admin` | Match | Kick a player from an arena match. |
| `/ma revive <player>` | `mobarenav2.admin` | Match | Revive a fallen player with 100% HP, class effects, and upgrades. |
| `/ma setup <arena>` | `mobarenav2.admin` | Setup | Open the interactive arena creation wizard. |
| `/ma setp1 <arena>` | `mobarenav2.admin` | Setup | Set arena boundary corner point 1. |
| `/ma setp2 <arena>` | `mobarenav2.admin` | Setup | Set arena boundary corner point 2. |
| `/ma setspectator <arena>` | `mobarenav2.admin` | Setup | Set spectator spawn location. |
| `/ma savearena <arena>` | `mobarenav2.admin` | Setup | Validate, save, and activate an arena. |
| `/ma arenaedit <arena>` | `mobarenav2.admin` | Setup | Open visual GUI arena editor. |
| `/ma tp <arena>` | `mobarenav2.admin` | Setup | Teleport to arena lobby location. |
| `/ma reload` | `mobarenav2.admin` | System | Reload all configuration files, classes, upgrades, and shops. |
| `/ma summonboss <arena> <boss>` | `mobarenav2.admin` | Testing | Summon a test boss into an arena. |
| `/ma killtestboss <arena>` | `mobarenav2.admin` | Testing | Instantly kill an active test boss. |
| `/ma upgraderoll <player>` | `mobarenav2.admin` | Testing | Open an upgrade draft GUI prompt for a player. |
| `/ma supply <arena>` | `mobarenav2.admin` | Testing | Spawn a supply drop chest in an arena. |
| `/ma inv <player>` | `mobarenav2.admin` | Testing | Inspect a player's saved backup inventory. |
| `/ma stats reset <player>` | `mobarenav2.admin` | System | Reset lifetime career statistics for a player. |

### 9.3 Permission Hierarchy
- **`mobarenav2.player.*` (default: `true`):** Inherits all player permissions (`join`, `leave`, `spec`, `list`, `shop`, `stats`, `top`).
- **`mobarenav2.admin` (default: `op`):** Grants full administrative access, including all player commands, setup tools, bypasses, and debugging commands.

---

## 10. PlaceholderAPI Integration

| Placeholder | Description |
| :--- | :--- |
| `%mobarena_arena_status_<arena>%` | Current arena state (`WAITING`, `STARTING`, `RUNNING`, `VOTING`, `DISABLED`). |
| `%mobarena_arena_players_<arena>%` | Active player count in the specified arena. |
| `%mobarena_arena_wave_<arena>%` | Current active wave of the arena. |
| `%mobarena_lifetime_kills%` | Player's total lifetime career mob kills. |
| `%mobarena_boss_kills%` | Player's total lifetime career boss kills. |
| `%mobarena_highest_wave%` | Player's highest wave achieved across all arenas. |
| `%mobarena_games_played%` | Total arena matches played by the player. |
| `%mobarena_games_won%` | Total arena victories achieved by the player. |
| `%mobarena_win_rate%` | Player's career victory percentage (`%`). |
| `%mobarena_favorite_class%` | Player's most played combat class. |

---

## 11. Migration & Troubleshooting (v8.4.4)

### Upgrading to v8.4.4
1. **Backup:** Create a backup of your `plugins/MobArenav2/` directory.
2. **Replace Jar:** Replace the existing jar with `MobArenav2-8.4.4.jar`.
3. **Config Refresh:**
   - Rename `upgrades.yml` to `upgrades_backup.yml` to allow the plugin to extract the complete dynamic data-driven upgrade configurations.
   - Rename `bosses.yml` to `bosses_backup.yml` to extract dynamic boss AI delays and Crushing Blow parameters.
   - Ensure `settings.yml` includes the `starter-upgrade:` configuration block.
4. **Permissions:** If using a permission manager like LuckPerms, players only need `mobarenav2.player.*` (which is already `default: true`).

---

## 12. License & Credits
- **Author & Maintainer:** Meowchan12
- **License:** [MIT License](LICENSE)
- **Engine Architecture:** Google Antigravity & DeepMind Agentic Engineering
