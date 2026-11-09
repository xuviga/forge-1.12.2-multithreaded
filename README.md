# Forge 1.12.2 Multithreaded

**A deterministic, regionized multithreading fork of Minecraft Forge 14.23.5.2860.**

[![License: LGPL-2.1](https://img.shields.io/badge/License-LGPL--2.1-blue.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.12.2-green.svg)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-14.23.5.2860-orange.svg)](https://github.com/MinecraftForge/MinecraftForge)
[![Java](https://img.shields.io/badge/Java-8%20target%20%7C%2021%20build-red.svg)](docs/BUILDING.md)

> **Status:** experimental but playable. Tested on integrated and dedicated
> servers with IC2 2.8.222 and JEI. Back up your worlds before testing.
> This is an unofficial fork and is not affiliated with Mojang or MinecraftForge.

---

## What is this?

Minecraft 1.12.2 and Forge were designed around a single server thread. On modern
hardware the main thread becomes the bottleneck long before the CPU is saturated:
entity AI, tile entities, block ticks, lighting, chunk I/O, pathfinding, model
baking, and packet serialization all compete for one core.

This fork adds a **deterministic regionized multithreading layer** on top of
Forge 1.12.2:

- **Region threads** tick entities, tile entities, blocks, and projectiles for
  disjoint world regions in parallel.
- **Main thread** keeps ownership of players, the chunk map, network, world
  border, spawning, and registries.
- **Cross-region mutations** are queued in a `DeferredMutationQueue` and committed
  in a deterministic order `(regionId, seq)`.
- **Per-object RNG keys** make world evolution independent of region size and
  worker count.
- **`WorldStateHasher`** proves that single-threaded, parallel, and regionized
  runs produce the same world state.

The result is not "throw a thread pool at Minecraft". It is a staged, auditable
concurrency model with explicit ownership, deterministic commits, and a large
test suite (hash determinism, region ownership audits, block/random determinism,
explosion self-tests, 60-second stress runs, BuildCraft stress, 150-player load
tests, and dedicated-server runs).

---

## Features

| Area | What was added |
|---|---|
| Regionized ticking | `RegionManager`, `Region`, `TickContext`, `TickPhase`, `ITickThread`, `ThreadType` |
| Deterministic commits | `DeferredMutationQueue`, `(regionId, seq)` ordering, per-object `ThreadAwareRandom` keys |
| Dimension parallelism | `MinecraftServer.tickWorldsParallel`, `parallelDimensions` |
| Async compute | `PathfindingExecutor`, `AsyncTerrainGenerator`, `LightingExecutor` (16 shards) |
| Client concurrency | `ClientSimulationThread`, `ClientRenderSnapshot`, `MainThreadDispatcher`, `RenderPipeline`, LOD renderer |
| Parallel loading | `LoadingExecutor`, fan-out/fan-in mod discovery, recipe loading, model resolution |
| Model/texture pipeline | parallel model baking, parallel texture decoding, lazy render buffers |
| Networking | per-player ordered PLAY packet serialization, `InboundPacketPipeline`, packet affinity/seq |
| Autotuning | `ThreadingAutopilot` probes `regionSizeChunks = 8/16/32` every 200 ticks and accepts improvements > 3% |
| Telemetry | `/forgethreading`, F3 overlay, phase timings, pool stats, `logs/forgethreading-*.txt` |
| Memory safety | `MemoryLeakGuard`, `CapabilityLeakTracker`, opt-in `ModReferencePurger`, `/forgemem` |
| Performance fixes | removed STW `System.gc()`, lazy VBOs, allocation-free terrain setup, chunk-I/O budgets, `BackgroundPacer` |

---

## Performance snapshot

Measured on the reference test machine (Windows, 6 GB heap, render distance 12,
TerrainWorld, `preset=auto`, 6 mods including IC2 2.8.222 and JEI):

| Metric | Before | After |
|---|---:|---:|
| World-join MSPT peak | **806 ms** | **147 ms** |
| World-join frame peak | **1045 ms** | **236 ms** |
| Steady-state MSPT | — | **11–12.5 ms** avg, **17–48 ms** max |
| Steady-state frame | — | **~5 ms** avg, **9–24 ms** max |
| FPS | — | **160–223** |
| TPS | — | **20.00** |
| Entities / tick | — | **484–485** |
| Tile entities / tick | — | **85** |
| Chunks / tick (block ticks) | — | **220** |
| Leaked worlds | — | **0** |
| Memory audit warnings | — | **0** |

Parallel loading results:

- `Scanned 10 mod candidates in 582 ms using the load pool`
- `Loading 287 blockstate model groups on 8 threads` → **715 ms**
- `Loading item models for 588 items on 8 threads` → **292 ms**
- `Decoding 2231 sprites on 16 threads`
- `Baking 4132 models on 16 threads`
- Dedicated server `Done (2.477s)`

> These numbers are from a specific test setup. Your hardware, modpack, world
> size, and render distance will change the results. Always benchmark your own
> server.

---

## Quick start (server owners)

### 1. Build or obtain a patched jar

This repository does **not** ship Minecraft/Forge source or a compiled jar.
You need a clean Forge 14.23.5.2860 MCP `stable_39` source tree and a JDK.

```powershell
# 1. Apply the fork patch to a clean Forge source tree
.\scripts\apply-patches.ps1 -Target C:\path\to\forge\source

# 2. Compile against your Forge/Minecraft libraries
.\scripts\compile.ps1 -SourceRoot C:\path\to\forge\source -Libs C:\path\to\libs

# 3. Build the runtime jar
.\scripts\build-jar.ps1 -SourceRoot C:\path\to\forge\source -OutJar .\forge-1.12.2-multithreaded.jar
```

See [docs/BUILDING.md](docs/BUILDING.md) for the exact baseline and classpath
details.

### 2. Start with a safe preset

Add JVM flags to your server launch script:

```text
-Dforge.threading.preset=balanced
-Xms4G -Xmx6G -XX:+UseG1GC -XX:MaxGCPauseMillis=50
```

Presets:

| Preset | `parallelDimensions` | `regions` | `regionSizeChunks` | `regionBlockTicks` | `asyncPathfinding` |
|---|---:|---:|---:|---|---|
| `safe` | 1 | 0 | 16 | false | false |
| `balanced` | 2 | 2 | 16 | false | true |
| `aggressive` | 2 | 2 | 8 | true | true |
| `auto` (default) | 4 | 4 | 8 + autopilot | true | true |

Start with `balanced`. If your modpack is stable, try `auto` or `aggressive`.
If you see mod incompatibilities, drop to `safe` or set `regions=0` and
`regionBlockTicks=false`.

### 3. Recommended JVM flags

```text
-Xms4G -Xmx6G
-XX:+UseG1GC
-XX:MaxGCPauseMillis=50
-Dforge.threading.preset=balanced
-Dforge.threading.metrics=true
-Dforge.threading.overlay=true
-Dforge.memory.watchdog=true
```

Do not add `-XX:+UseParallelGC` or a tiny `-Xmx`; the fork allocates more
transient objects during parallel loading and rendering.

### 4. In-game commands

| Command | Purpose |
|---|---|
| `/forgethreading status` | TPS, MSPT, pools, counters, phases |
| `/forgethreading overlay on\|off` | F3 overlay |
| `/forgethreading handlers` | packet-handler audit |
| `/forgethreading dump` | write `logs/forgethreading-<timestamp>.txt` |
| `/forgethreading reset` | reset counters |
| `/forgepregen` | pre-generate chunks |
| `/forgemem status` | heap, loaded/leaked worlds, capability owners |
| `/forgemem purge` | run a leak audit now |
| `/forgemem gc` | request GC (diagnostic only) |
| `/forgemem aggressive on\|off` | opt-in static mod-reference purge |
| `/forgemem capability on\|off` | capability tracking toggle |
| `/forgemem dump` | dump memory audit |

All commands require permission level 2.

---

## Configuration

The full list is in [docs/CONFIGURATION.md](docs/CONFIGURATION.md). The most
important properties:

```text
-Dforge.threading.preset=safe|balanced|aggressive
-Dforge.threading.singleThreaded=true|false
-Dforge.threading.parallelDimensions=N
-Dforge.threading.regions=N
-Dforge.threading.regionSizeChunks=N
-Dforge.threading.regionBlockTicks=true|false
-Dforge.threading.regionAffinity=true|false
-Dforge.threading.asyncPathfinding=true|false
-Dforge.threading.pathfindingThreads=N
-Dforge.threading.simulationDistance=N
-Dforge.threading.parallelModelBaking=true|false
-Dforge.threading.modelBakingThreads=N
-Dforge.threading.parallelTextureDecoding=true|false
-Dforge.threading.textureDecodingThreads=N
-Dforge.threading.parallelBlockUpdates=true|false
-Dforge.threading.parallelLighting=true|false
-Dforge.threading.packetSerialization=true|false
-Dforge.threading.parallelLoading=true|false
-Dforge.threading.loadThreads=N
-Dforge.threading.parallelModDiscovery=true|false
-Dforge.threading.parallelRecipeLoading=true|false
-Dforge.threading.parallelModelLoading=true|false
-Dforge.threading.autopilot=true|false
-Dforge.threading.autopilotWindow=200
-Dforge.threading.autopilotThreshold=0.03
-Dforge.threading.metrics=true|false
-Dforge.threading.audit=true|false
-Dforge.threading.overlay=true|false
-Dforge.threading.clientSimThread=true|false
-Dforge.threading.clientSimEntities=true|false
-Dforge.threading.hashTicks=N
-Dforge.threading.selftest=true|false
-Dforge.threading.explosionSelftest=true|false
```

Memory guard:

```text
-Dforge.memory.watchdog=true
-Dforge.memory.auditIntervalTicks=1200
-Dforge.memory.heapWarnPercent=88
-Dforge.memory.verboseAudit=false
-Dforge.memory.aggressivePurge=false
-Dforge.memory.capabilityAudit=true
-Dforge.memory.capabilityTrackLimit=200000
-Dforge.memory.unloadGraceMs=30000
-Dforge.memory.maxScannedClasses=200000
-Dforge.memory.maxScannedEntries=5000000
```

---

## Architecture

```text
                         ┌──────────────────────────────┐
                         │          Main Thread          │
                         │ players / network / chunk map │
                         │ world border / spawning /     │
                         │ registries / commands         │
                         └───────────────┬──────────────┘
                                         │
                         DeferredMutationQueue (regionId, seq)
                                         │
        ┌────────────────────────────────┼────────────────────────────────┐
        ▼                                ▼                                ▼
  Region 0..N                     Dimension 0..M                  Async pools
  entities / tile entities        world tick                       pathfinding
  blocks / projectiles            (parallelDimensions)             lighting
  whitelisted work                                                  terrain
                                                                    loading
                                                                    model/texture
```

**Main thread owns:** players, `PlayerChunkMap`, trackers, network, `WorldBorder`,
spawning, registries, commands, world load/unload.

**Region threads own:** whitelisted entity/TE/block/projectile ticking for
disjoint regions. Cross-region writes are queued, not applied immediately.

**Determinism:** `ThreadAwareRandom` uses a per-object key (entity UUID, TE
position, `ChunkPos`) instead of `regionId`. `RegionManager` sorts chunks by
`(x, z)`. `WorldStateHasher` compares single-threaded, parallel, and regionized
runs.

**Client:** the GL context remains on the main thread. Render preparation,
entity simulation snapshots, texture decoding, and model baking are offloaded
with bounded queues and main-thread handoff.

**Memory:** on `WorldEvent.Unload`, `MemoryLeakGuard` severs known strong
references (region ownership, chunk-I/O queue, forced-chunk tickets, farmland,
client simulation, pregen, IC2 explosion sync, capability owners). An opt-in
reflective purger can remove static mod references to the unloaded world.

More detail: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Compatibility

Tested targets:

- **Forge 14.23.5.2860**, MCP `stable_39`, Minecraft 1.12.2.
- **IndustrialCraft 2 2.8.222-ex112** (including explosion sync hooks).
- **Just Enough Items** (recipe sync).
- **BuildCraft** stress runs.
- Dedicated server and integrated server.
- 6-mod pack, 150-player load test.

Caveats:

- Mods that assume all tile entities/entities tick on the main thread may need
  `-Dforge.threading.regions=0` or `-Dforge.threading.regionBlockTicks=false`.
- Mods with global mutable world state may need `preset=safe` or a compatibility
  patch.
- Client rendering is still single-GL-thread; only preparation is offloaded.
- This fork is based on a specific Forge source snapshot. Applying the patch to
  a different Forge version or a heavily patched tree will fail or need rebasing.
- Some environments crash in `OpenAL64.dll` during sound initialization with
  `preset=balanced`. That is a native audio issue, not a threading issue; retry
  or use a dedicated server for validation.

See [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

---

## Testing and benchmarks

The fork was validated with:

- **Determinism:** `WorldStateHasher` single vs parallel vs regions; block/random
  determinism runs; topology checks; region ownership audits.
- **Stress:** 60-second runs, BuildCraft stress, 150-player load test, dedicated
  server runs.
- **Self-tests:** `ThreadingSelfTest`, `ExplosionSelfTest`, IC2 explosion sync.
- **Memory:** world unload/reload cycles with `leakedWorlds=0`, `warnings=0`.
- **Rendering:** F3 overlay, phase breakdown screenshots, chunk compile/upload
  counters.

Reproduction steps and raw results: [docs/TESTING.md](docs/TESTING.md) and
[benchmarks/RESULTS.md](benchmarks/RESULTS.md).

---

## Known issues and roadmap

Known residual spikes:

- First client frame after joining a world: **~236 ms** (`SPacketJoinGame`
  ~63–101 ms + first render).
- One tick of **~117 ms** when autopilot changes `regionSizeChunks` under load.

Roadmap:

- Warm `ViewFrustum`/shaders before showing `GuiDownloadTerrain`, or defer the
  first world render.
- Do not let autopilot change `regionSizeChunks` on the first window or under
  load.
- Extend regionized mutation coverage to more mod APIs.
- Add more per-mod compatibility shims.
- Improve client render-thread separation without touching the GL context.
- Add automated patch-application CI and reproducible benchmark scripts.

See [docs/ROADMAP.md](docs/ROADMAP.md).

---

## Repository layout

```text
src/main/java/                         Fork-only Java sources
  net/minecraftforge/common/threading/ Regionized ticking, async pools, telemetry
  net/minecraftforge/common/memory/    Leak guard, capability audit, purger
  net/minecraft/client/renderer/       RenderPipeline, LodRenderer
patches/                               Unified patch against Forge 14.23.5.2860
scripts/                               Apply, assemble, compile, build, run
docs/                                  Architecture, config, performance, testing
benchmarks/                            Benchmark summaries
```

---

## Building from source

Full instructions: [docs/BUILDING.md](docs/BUILDING.md).

Short version:

1. Obtain a clean Forge 14.23.5.2860 MCP `stable_39` source tree.
2. `git apply patches/forge-1.12.2-multithreaded.patch`.
3. Compile with JDK 21 using `--release 8` and the Forge/Minecraft libraries.
4. Stage compiled classes + assets and build the jar.
5. Run with the provided client/server launch templates.

The original build used:

- JDK 21.0.11 (`javac --release 8 -proc:none -encoding UTF-8`).
- A `libs` directory containing the Forge/Minecraft dependency jars.
- `GradleStart` / `GradleStartServer` for launch.
- `-Xms2G -Xmx6G -XX:+UseG1GC -XX:MaxGCPauseMillis=50`.

---

## Contributing

Contributions are welcome. Please:

1. Keep Java 8 source compatibility.
2. Do not add hard dependencies on client-only or mod-specific classes.
3. Preserve the `net.minecraftforge.common.threading` and `memory` public APIs.
4. Run the determinism and stress tests before opening a PR.
5. Include before/after telemetry (`/forgethreading dump`) for performance PRs.

See [CONTRIBUTING.md](CONTRIBUTING.md).

---

## License and credits

- Fork code: **LGPL-2.1** (see [LICENSE](LICENSE)), matching MinecraftForge.
  The original Forge license notice and authorship notes are preserved in
  [LICENSE-FORGE.txt](LICENSE-FORGE.txt).
- Minecraft/Forge/Mojang/Microsoft trademarks and copyrights belong to their
  respective owners.
- This repository does not distribute Minecraft source code, assets, or binaries.
- The patch contains only modifications; you must obtain the base source legally.

Credits: MinecraftForge team, Mojang Studios/Microsoft, IC2 and JEI authors, and
everyone who contributed logs, benchmarks, and compatibility reports.

---

## Disclaimer

This is an unofficial experimental fork. Use at your own risk. Back up worlds
and test on a staging server before production. The authors are not responsible
for data loss, crashes, bans, or server instability caused by this software.
