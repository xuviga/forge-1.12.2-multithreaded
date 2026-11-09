# Configuration

All options are JVM system properties (`-D...`). They are read once at startup
unless noted otherwise.

---

## 1. Presets

```text
-Dforge.threading.preset=safe|balanced|aggressive
```

| Preset | `parallelDimensions` | `regions` | `regionSizeChunks` | `regionBlockTicks` | `asyncPathfinding` |
|---|---:|---:|---:|---|---|
| `safe` | 1 | 0 | 16 | false | false |
| `balanced` | 2 | 2 | 16 | false | true |
| `aggressive` | 2 | 2 | 8 | true | true |
| `auto` (default) | 4 | 4 | 8 + autopilot | true | true |

Explicit `-Dforge.threading.*` values always override the preset.

---

## 2. Core threading

| Property | Default | Description |
|---|---:|---|
| `forge.threading.singleThreaded` | `false` | Force stock single-thread behavior |
| `forge.threading.parallelDimensions` | `1` (preset-dependent) | Number of dimensions ticked in parallel (max 16) |
| `forge.threading.regions` | `0` | Number of region workers; > 0 enables regionized ticking |
| `forge.threading.regionSizeChunks` | `16` | Region side length in chunks |
| `forge.threading.regionBlockTicks` | `false` | Tick blocks/fluids inside regions |
| `forge.threading.regionAffinity` | `false` | Prefer stable region-to-worker affinity |
| `forge.threading.parallelBlockUpdates` | preset-dependent | Parallel scheduled block/fluid ticks |
| `forge.threading.parallelLighting` | preset-dependent | Sharded lighting (16 shards, margin 36) |
| `forge.threading.simulationDistance` | `12` | Server simulation distance |
| `forge.threading.playerTrackerRange` | `128` | Player tracker range |
| `forge.threading.playerTrackerInterval` | `4` | Player tracker update interval |

---

## 3. Async pathfinding

| Property | Default | Description |
|---|---:|---|
| `forge.threading.asyncPathfinding` | preset-dependent | Enable async path searches |
| `forge.threading.pathfindingThreads` | `cores / 2` | Pathfinding pool size |

---

## 4. Model and texture pipeline

| Property | Default | Description |
|---|---:|---|
| `forge.threading.parallelModelBaking` | `true` | Bake models on multiple threads |
| `forge.threading.modelBakingThreads` | `cores` | Model baking pool size |
| `forge.threading.parallelTextureDecoding` | `true` | Decode textures on multiple threads |
| `forge.threading.textureDecodingThreads` | `cores` | Texture decoding pool size |
| `forge.threading.parallelModelLoading` | `true` | Resolve models on multiple threads |

---

## 5. Parallel loading

| Property | Default | Description |
|---|---:|---|
| `forge.threading.parallelLoading` | `true` | Enable fan-out/fan-in loading |
| `forge.threading.loadThreads` | `8` | Loading pool size |
| `forge.threading.parallelModDiscovery` | `true` | Parallel mod candidate scanning |
| `forge.threading.parallelRecipeLoading` | `true` | Parallel recipe JSON construction |

Mod lifecycle events remain serial regardless of these flags.

---

## 6. Networking

| Property | Default | Description |
|---|---:|---|
| `forge.threading.packetSerialization` | preset-dependent | Per-player ordered packet serialization |
| `forge.threading.inboundPipeline` | preset-dependent | Inbound PLAY packet pipeline |

---

## 7. Client simulation and rendering

| Property | Default | Description |
|---|---:|---|
| `forge.threading.clientSimThread` | `false` | Client simulation thread |
| `forge.threading.clientSimEntities` | `false` | Tick client entities off-thread |
| `forge.threading.overlay` | `true` | F3 overlay |
| `forge.threading.metrics` | `false` | Periodic `[thread-metrics]` / `[client-metrics]` logs |
| `forge.threading.audit` | `false` | Thread audit assertions |
| `forge.threading.forwardMainEvents` | `false` | Forward events to main thread |
| `forge.threading.hashTicks` | `0` | Enable `WorldStateHasher` every N ticks |
| `forge.threading.hashTicksMax` | — | Max hash samples |
| `forge.threading.selftest` | `false` | Run `ThreadingSelfTest` |
| `forge.threading.explosionSelftest` | `false` | Run `ExplosionSelfTest` |

---

## 8. Autopilot

| Property | Default | Description |
|---|---:|---|
| `forge.threading.autopilot` | `true` in `auto` | Enable region-size autotuning |
| `forge.threading.autopilotWindow` | `200` | Window in ticks |
| `forge.threading.autopilotThreshold` | `0.03` | Minimum improvement (3%) to accept a candidate |
| `forge.threading.regionSizeCandidates` | `8,16,32` | Candidate region sizes |

An explicit `forge.threading.regionSizeChunks` disables autopilot.

---

## 9. Memory guard

| Property | Default | Description |
|---|---:|---|
| `forge.memory.watchdog` | `true` | Enable heap/leak watchdog |
| `forge.memory.auditIntervalTicks` | `1200` | Audit interval (server tick START only) |
| `forge.memory.heapWarnPercent` | `88` | Heap warning threshold, clamped 50..99 |
| `forge.memory.verboseAudit` | `false` | Log full audit at INFO |
| `forge.memory.aggressivePurge` | `false` | Opt-in reflective static purge on unload |
| `forge.memory.capabilityAudit` | `true` | Track world-bound capability owners |
| `forge.memory.capabilityTrackLimit` | `200000` | Max tracked owners |
| `forge.memory.unloadGraceMs` | `30000` | Grace period before treating a world as leaked |
| `forge.memory.maxScannedClasses` | `200000` | Scan cap for the purger |
| `forge.memory.maxScannedEntries` | `5000000` | Entry cap for the purger |

---

## 10. Commands

| Command | Permission | Description |
|---|---:|---|
| `/forgethreading status` | 2 | TPS, MSPT, pools, counters, phases |
| `/forgethreading overlay on\|off` | 2 | F3 overlay |
| `/forgethreading handlers` | 2 | Packet-handler audit |
| `/forgethreading dump` | 2 | Write `logs/forgethreading-<timestamp>.txt` |
| `/forgethreading reset` | 2 | Reset counters |
| `/forgepregen` | 2 | Pre-generate chunks |
| `/forgemem status` | 2 | Heap, loaded/leaked worlds, capability owners |
| `/forgemem purge` | 2 | Run a leak audit now |
| `/forgemem gc` | 2 | Request GC (diagnostic only) |
| `/forgemem aggressive on\|off` | 2 | Toggle static mod-reference purge |
| `/forgemem capability on\|off` | 2 | Toggle capability tracking |
| `/forgemem dump` | 2 | Dump memory audit |

---

## 11. Example launch flags

### Balanced dedicated server

```text
-Xms4G -Xmx6G -XX:+UseG1GC -XX:MaxGCPauseMillis=50
-Dforge.threading.preset=balanced
-Dforge.threading.metrics=true
-Dforge.memory.watchdog=true
```

### Aggressive single-player / test client

```text
-Xms2G -Xmx6G -XX:+UseG1GC -XX:MaxGCPauseMillis=50
-Dforge.threading.preset=aggressive
-Dforge.threading.overlay=true
```

### Maximum compatibility

```text
-Dforge.threading.preset=safe
-Dforge.threading.singleThreaded=true
```

### Debug determinism

```text
-Dforge.threading.regions=4
-Dforge.threading.regionSizeChunks=16
-Dforge.threading.hashTicks=20
-Dforge.threading.audit=true
```
