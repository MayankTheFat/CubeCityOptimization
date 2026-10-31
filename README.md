# CubeCityOptimization

A Paper **1.21+** server plugin that keeps big worlds and busy farms from melting the tick loop.
It combines five independent optimizers — chunks, hoppers, dispensers, TNT and mobs — behind one
config, one GUI, and one `/cci` command, with TPS-aware load shedding, SQLite/MySQL history,
PlaceholderAPI placeholders and optional Vault charging.

```
                 ┌──────────────────────────── CubeCityOptimization ────────────────────────────┐
                 │                                                                              │
  players ──────▶│  ChunkOptimizer   view/simulation distance, chunk budget, idle unloading     │
                 │  HopperOptimizer  transfer gate, density limit, chain prevention             │
                 │  DispenserOptimizer  per-block cooldown, per-chunk burst limit               │
                 │  TntOptimizer     per-chunk cap, merge, power cap, block-list trim           │
                 │  MobOptimizer     spawn gate + periodic reconcile + exemptions               │
                 │                                                                              │
                 │  TpsMonitor ──── pressure mode ──▶ shorter view distance, slower machines    │
                 │  GuiManager   /cci gui — edits config.yml live                               │
                 │  SqlStatsStorage  snapshots + action log (SQLite / MySQL)                    │
                 │  Hooks        PlaceholderAPI %cci_*%  ·  Vault cleanup fees                  │
                 └──────────────────────────────────────────────────────────────────────────────┘
```

## Requirements

| | |
|---|---|
| Server | Paper 1.21+ (uses Paper's per-player view distance and Adventure) |
| Java | 21 |
| Optional | PlaceholderAPI 2.11+, Vault 1.7+ with an economy provider |
| Tested against | Paper 1.21.4 build 232, Java 21.0.13 |

## Install

1. Build (or drop a release jar in `plugins/`):

   ```bash
   mvn -B package
   # target/CubeCityOptimization-1.0.0.jar
   ```

2. Copy the jar to `plugins/`, start the server once, then tune `plugins/CubeCityOptimization/config.yml`.
3. `/cci reload` after edits, or use `/cci gui` and click.

With no edits the plugin starts with conservative defaults and **SQLite** storage.

## Commands

| Command | What it does |
|---|---|
| `/cci status` | TPS/MSPT, pressure mode, world/entity/hopper/TNT counts, storage |
| `/cci gui` | 45-slot control panel; config clicks write `config.yml` |
| `/cci cleanup [world]` | Removes old drops/orbs, trims dense chunks, unloads idle chunks |
| `/cci chunks` | Per-world chunk counts and your view distance |
| `/cci hoppers` | Cooldown, density, throttled/blocked counters |
| `/cci tnt` | Merged TNT, blocked TNT, saved blocks |
| `/cci mobs` | Caps, removed mobs, denied spawns |
| `/cci stats` | Storage backend + last 10 recorded actions |
| `/cci reload` | Re-reads config and restarts all tasks (changing `storage.*` needs a restart) |
| `/cci help` | Command list |

Aliases: `/cubecityoptimization`, `/cco`.

## Permissions

| Permission | Default | Grants |
|---|---|---|
| `cco.use` | everyone | `/cci` + `/cci help`, plus child `cco.status` |
| `cco.status` | everyone | `/cci status`, `/cci chunks`, `/cci hoppers`, `/cci tnt`, `/cci mobs` |
| `cco.gui` | op | `/cci gui` |
| `cco.cleanup` | op | `/cci cleanup` |
| `cco.stats` | op | `/cci stats` |
| `cco.reload` | op | `/cci reload` |
| `cco.admin` | op | children: all of the above |
| `cco.bypass.viewdistance` | false | your own view distance is never changed |

## What each module does (and does not do)

### Chunks
* Applies Paper's **per-player** `view-distance` and `simulation-distance`.
* Converts `max-chunks-per-player` into a radius: `r = floor((sqrt(chunks) - 1) / 2)`, then takes
  the smaller of that and `view-distance`. `289` is exactly a 17×17 chunk area, so `r = 8`.
* Unloads loaded chunks that are **not in use**, have **no tile entities**, **no entities**, are
  outside a 5x5 spawn area, and have no player within 3 chunks (at most 800 chunks per world per
  pass). Chunks with hoppers, furnaces or dropped items are never unloaded, so farms keep working.
* Under pressure mode (with `chunks.adaptive: true`), view distance is additionally clamped to
  `tick.pressure-view-distance`.
* It does **not** force-load or pre-generate chunks; the server still decides what to load.

### Hoppers
* **Transfer cooldown** is enforced by cancelling `InventoryMoveItemEvent` when a hopper transfers
  again before `transfer-cooldown-ticks × 50 ms` (× pressure multiplier). Cancelling the transfer
  is the observable equivalent of a slower hopper and costs almost nothing on the tick loop.
* **Density**: placing a hopper above `max-per-chunk` is cancelled.
* **Chains**: placing a hopper directly against another hopper is cancelled (`disable-chains`).
* Optional `only-near-players` skips the gate for hoppers outside `player-range` blocks.
* Bukkit exposes no hook for the internal "check for items" cooldown, so the plugin throttles the
  transfers themselves rather than pretending to patch the tile-entity timer.

### Dispensers / droppers
* Per-block cooldown via `BlockDispenseEvent` cancellation; the block is not modified.
* Per-chunk burst limit (`max-per-second-per-chunk`) catches redstone clocks that bypass the
  per-block cooldown by alternating dispensers.
* Density limit on placement.

### TNT
* `max-per-chunk` caps primed TNT; excess primed TNT is cancelled and removed.
* Nearby primed TNT inside `merge-radius` is merged into one entity: the new entity is removed and
  its power (4.0) is added to the survivor's explosion radius, capped by `max-explosion-power`.
* `max-blocks-per-explosion` trims the `EntityExplodeEvent` block list.
* The entity maps are concurrent, and all storage writes happen on the async storage thread.
  World access always stays on the server thread — Bukkit/Paper does not permit otherwise.

### Mobs
* Natural spawns are gated by `global-cap` (per world) and `per-chunk-cap` before the entity is
  created.
* A periodic reconcile pass recounts every world and removes excess mobs, farthest-from-players
  first.
* Exemptions: named mobs, tamed mobs, persistent mobs, villagers. `hostile-only` restricts the
  whole module to `Monster`.
* Pressure mode can deny natural spawns entirely (`tick.pressure-spawn-throttle`).

### Tick / TPS
* Samples `Server#getTPS()` and `Server#getAverageTickTime()` and enters **pressure mode** after 3
  low samples, leaving after 5 healthy ones (hysteresis avoids flapping).
* Pressure mode shortens view distance, multiplies hopper/dispenser cooldowns, and can stop natural
  spawns.
* The plugin cannot change the server's 20 TPS tick rate — that is a Paper/vanilla constant. Pressure
  mode is load shedding, not a tick-rate rewrite, and it is honest about that.

### Requested features, honestly mapped

| Requested | Status |
|---|---|
| Chunk limits, view/simulation distance, unload delay, cleanup command | Implemented |
| Entity activation ranges | **Not implemented** — Paper (1.21.4) exposes no plugin API for this. Tune `entity-activation-range` in `paper-world-defaults.yml`; this plugin instead distance-gates its own hopper/dispenser work |
| Hopper transfer cooldown, density, chain blocking | Implemented as a transfer gate (Bukkit has no hook for the internal check timer) |
| Dispenser cooldown, density, redstone spam protection | Implemented |
| Tick-rate optimization | Implemented as TPS-aware load shedding — the 20 TPS tick rate itself is not changed |
| TNT merge, per-chunk cap, explosion trimming, async tracking | Implemented; entity work stays on the main thread, only telemetry/SQL is async |
| Mob caps, auto-remove, named/tamed exemptions | Implemented |
| GUI, SQLite/MySQL, PlaceholderAPI, Vault | Implemented |

## Configuration

<details>
<summary>Full reference (defaults)</summary>

| Key | Default | Meaning |
|---|---|---|
| `general.debug` | `false` | Log every throttle/merge |
| `general.sample-interval-ticks` | `40` | TPS sampling period |
| `chunks.enabled` | `true` | Master switch for the chunk module |
| `chunks.view-distance` | `6` | Per-player view distance |
| `chunks.simulation-distance` | `4` | Per-player simulation distance |
| `chunks.max-chunks-per-player` | `289` | Chunk budget, converted to a radius |
| `chunks.unload-delay-seconds` | `30` | How often idle chunks are swept |
| `chunks.adaptive` | `true` | Apply pressure-mode view distance |
| `cleanup.enabled` | `true` | Enable scheduled cleanup |
| `cleanup.interval-seconds` | `300` | `0` = manual only |
| `cleanup.remove-dropped-items` | `true` | Remove old item entities |
| `cleanup.item-min-age-seconds` | `120` | Age threshold for items |
| `cleanup.remove-xp-orbs` | `true` | Remove old XP orbs |
| `cleanup.xp-min-age-seconds` | `120` | Age threshold for orbs |
| `cleanup.max-entities-per-chunk` | `64` | Excess entities are removed |
| `cleanup.keep-entities` | `ITEM_FRAME, ARMOR_STAND, VILLAGER, ITEM_DISPLAY, BLOCK_DISPLAY` | Never removed by cleanup |
| `hoppers.enabled` | `true` | Master switch |
| `hoppers.transfer-cooldown-ticks` | `8` | Minimum ticks between transfers |
| `hoppers.max-per-chunk` | `24` | Placement density limit (`0` = off) |
| `hoppers.disable-chains` | `true` | Refuse hopper-on-hopper placement |
| `hoppers.only-near-players` | `true` | Skip far hoppers |
| `hoppers.player-range` | `96` | Blocks |
| `dispensers.enabled` | `true` | Master switch |
| `dispensers.cooldown-ticks` | `10` | Per-block cooldown |
| `dispensers.max-per-chunk` | `16` | Placement density limit |
| `dispensers.max-per-second-per-chunk` | `8` | Burst limit |
| `tick.enabled` | `true` | Pressure mode |
| `tick.tps-threshold` | `17.0` | Enter pressure below this |
| `tick.pressure-view-distance` | `4` | View distance while under pressure |
| `tick.pressure-cooldown-multiplier` | `2.0` | Machine slowdown factor |
| `tick.pressure-spawn-throttle` | `true` | Deny natural spawns under pressure |
| `tnt.enabled` | `true` | Master switch |
| `tnt.max-per-chunk` | `16` | Primed TNT cap |
| `tnt.merge-radius` | `4.0` | Merge distance |
| `tnt.max-explosion-power` | `6.0` | Radius cap after merging |
| `tnt.max-blocks-per-explosion` | `120` | Block-list trim (`0` = off) |
| `mobs.enabled` | `true` | Master switch |
| `mobs.global-cap` | `400` | Mobs per world (`0` = off) |
| `mobs.per-chunk-cap` | `12` | Mobs per chunk (`0` = off) |
| `mobs.auto-remove-excess` | `true` | Reconcile pass removes overflow |
| `mobs.remove-interval-seconds` | `60` | Reconcile interval |
| `mobs.hostile-only` | `false` | Only count monsters |
| `mobs.exempt-named` | `true` | Keep name-tagged mobs |
| `mobs.exempt-tamed` | `true` | Keep tamed animals |
| `mobs.exempt-persistence` | `false` | Keep persistent mobs (`/summon` marks mobs persistent) |
| `storage.type` | `sqlite` | `sqlite` or `mysql` |
| `storage.sqlite.file` | `cubecity.db` | Relative to the plugin folder |
| `storage.mysql.*` | host `localhost`, port `3306`, db `cubecity`, user `root`, empty password, `use-ssl: false` | MySQL connection |
| `storage.save-interval-seconds` | `60` | Snapshot cadence (async) |
| `storage.retention-days` | `30` | Action rows older than this are pruned |
| `economy.enabled` | `false` | Charge for `/cci cleanup` |
| `economy.cleanup-cost` | `0.0` | Vault amount |
| `gui.enabled` | `true` | Allow `/cci gui` |
| `gui.title` | `CubeCity Optimization` | Inventory title |
| `messages.prefix` | `<dark_aqua><bold>CubeCity</bold></dark_aqua> <gray>»</gray> ` | Chat prefix (MiniMessage) |

</details>

## Placeholders (PlaceholderAPI)

`%cci_status%`, `%cci_tps%`, `%cci_tps_avg%`, `%cci_tps_colored%`, `%cci_mspt%`, `%cci_pressure%`,
`%cci_players%`, `%cci_worlds%`, `%cci_chunks%`, `%cci_entities%`, `%cci_mobs%`, `%cci_hoppers%`,
`%cci_dispensers%`, `%cci_tnt%`, `%cci_mobs_removed%`, `%cci_hoppers_throttled%`,
`%cci_dispensers_throttled%`, `%cci_tnt_merged%`, `%cci_blocks_saved%`, `%cci_chunks_unloaded%`,
`%cci_cleanups%`, `%cci_storage%`.

Counts are cached and refreshed every 10 seconds; placeholders never scan the world themselves.

## Storage

SQLite and MySQL share one schema:

```sql
cco_snapshots(id, created_at, tps, mspt, worlds, chunks, entities,
              hoppers, dispensers, tnt, mobs, counters)
cco_actions  (id, created_at, action, detail)
```

* `record`/`action` writes are queued to a single daemon thread (`CubeCity-Storage`). Schema
  creation and pruning run on a Bukkit async task at startup, and `/cci stats` runs its one SELECT
  on an async task, replying on the main thread — the tick loop never waits on JDBC.
* `counters` is a compact JSON object of cumulative plugin counters, useful for Grafana-style
  polling of the SQL table.
* If the database is unreachable the plugin logs one warning at startup (later failures are logged
  at FINE) and keeps optimizing — storage is never on the critical path.
* The SQLite JDBC and MySQL Connector/J drivers are shaded into the jar, so no library manager is
  needed.

## Build, test, CI

```bash
mvn -B verify          # compile + 19 unit tests + shaded jar
mvn -B test            # unit tests only
```

Unit tests cover the pure logic that does not need a running server: the cooldown gate used by
hoppers/dispensers, chunk key packing, counter JSON encoding, number formatting, and a parse test
that pins the shipped `config.yml` to the documented defaults. The GitHub
Actions workflow in `.github/workflows/ci.yml` runs `mvn -B verify` on JDK 21 and uploads the jar.

## Project layout

```
src/main/java/city/cube/optimization/
├── CubeCityOptimization.java     plugin entry point, task lifecycle
├── Stats.java                    thread-safe counters
├── chunk/ChunkOptimizer.java     view distance, chunk budget, idle unload
├── hopper/HopperOptimizer.java   transfer gate, density, chains
├── dispenser/DispenserOptimizer.java
├── tnt/TntOptimizer.java
├── mob/MobOptimizer.java
├── tick/TpsMonitor.java          pressure mode with hysteresis
├── gui/GuiManager.java           45-slot live config editor
├── command/CcoCommand.java       /cci + tab completion
├── storage/                      SQLite/MySQL snapshots + action log
├── hooks/                        PlaceholderAPI expansion, Vault economy
├── config/PluginConfig.java      immutable config snapshot
└── util/                         cleanup, keys, messages, totals
```

## Safety notes

* Everything that touches the world runs on the server thread; only counters and SQL run async.
* Chunk unloading skips chunks in use, chunks with tile entities, and the spawn area.
* Mob reconcile always skips villagers; named and tamed mobs are skipped by default, persistent
  mobs only when `mobs.exempt-persistence: true`.
* This plugin is not a substitute for profiling. Use `spark`/`timings` to find the real hotspot;
  the plugins' counters (visible in `/cci status` and the GUI) tell you what it actually changed.

## License

MIT — see [LICENSE](LICENSE).
