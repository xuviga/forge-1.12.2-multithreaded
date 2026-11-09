# Changelog

All dates are from the original development and test environment
(2026-11-08 / 2026-11-09).

---

## 2026-11-09 — v0.2: adaptive governor, low-memory mode

### Added

- `PerformanceGovernor` — self-tuning MSPT governor. It shrinks AI-LOD radii and
  simulation distance when the rolling MSPT window is over budget and expands
  them back when comfortably under budget (2 bad / 10 good windows, 10-window
  warm-up, hysteresis).
- `MemoryTrim` — low-memory mode for heaps <= 1280 MB: drops
  `LaunchClassLoader.resourceCache` bytecode after startup and caps simulation
  distance.
- `ClassCache` — persistent class-scan cache used by the load pool.
- `StatsOverlay` — client performance overlay (FPS, frame time, TPS/MSPT, heap,
  physical RAM, per-core CPU) on F8 / Options -> Stats Overlay.

### Changed

- `balanced`, `aggressive`, and `maximum` presets now leave the region worker
  count on `auto` and use `regionSizeChunks=4`.
- Low-memory systems disable dimension/region/model-baking parallelism and use a
  reduced simulation distance.
- `ThreadingMetrics`, `RegionManager`, `MainThreadDispatcher`, and
  `LightingExecutor` updated for the governor, overlay, and metrics.
- `FMLModContainer` and `FMLDeobfTweaker` now carry the fork hooks; these two
  Forge classes are modified for the first time in this release.

### Patch

- Full patch: 113 paths (74 modified + 39 new).
- `vanilla-only.patch`: 46 paths (Minecraft.java close handling updated).

### Verified

- Client run: TPS 20.00, MSPT 11.11 ms (max 14.64), FPS 192, `dimensions=4`,
  `regions=4`, 2390 path jobs, 119k region block units.

---

## 2026-11-09 — Memory guard, parallel loading, final build

### Added

- `net.minecraftforge.common.memory`:
  - `MemoryLeakGuard` — world unload reference firewall + heap/leak watchdog.
  - `CapabilityLeakTracker` — `ReferenceQueue`-based weak audit of world-bound
    capability owners.
  - `ModReferencePurger` — opt-in reflective purge of static mod references to
    unloaded worlds.
  - `CommandForgeMemory` — `/forgemem`.
- `LoadingExecutor` — deterministic fan-out/fan-in loading:
  - parallel mod candidate scanning;
  - parallel recipe JSON construction;
  - parallel model resolution.
- `BackgroundPacer` — background pools yield CPU when the game tick/frame
  overruns.
- `PathfindingExecutor` refinements.
- Final `threading`/`memory` integration into `ForgeInternalHandler`,
  `ForgeEventFactory`, `ForgeChunkManager`, `FarmlandWaterManager`,
  `DimensionManager`, `ChunkIOExecutor`, `ServerCommandManager`.

### Changed

- Parallel loading properties:
  `parallelLoading`, `loadThreads`, `parallelModDiscovery`,
  `parallelRecipeLoading`, `parallelModelLoading`.
- `ForgeChunkManager.unloadWorld` drops player/pending tickets for the unloaded
  world.

### Verified

- Dedicated server boot with 6 mods.
- `leakedWorlds=0`, `warnings=0`, heap stable after unload.
- `Scanned 10 mod candidates in 582 ms using the load pool`.
- `Baking 4132 models on 16 threads`.
- Final jar: ~12.95 MB, `JAVAC_EXIT=0`.

---

## 2026-11-09 — MSPT 806 ms fix and telemetry

### Added

- `ThreadingTelemetry` phase timings and `logs/forgethreading-*.txt` dumps.
- F3 overlay integration in `GuiOverlayDebug`.
- `/forgethreading [status|overlay on|off|handlers|dump|reset]`.
- `/forgepregen`.
- `ThreadingMetrics` `[thread-metrics]` / `[client-metrics]`.
- `ThreadAudit`.

### Fixed

- Removed `System.gc()` from world load/unload paths.
- Lazy VBO creation in `RenderChunk`.
- CPU-constant modelview matrix.
- Lazy `RegionRenderCacheBuilder` buffers (1 MB SOLID start, ≤ 32 builders).
- Allocation-free `RenderGlobal.setupTerrain` with pools/double buffers.
- Removed synchronous `updateChunkNow` from the game thread.
- `updateChunks` budgets: warm-up 4/frame, budget 12, immediate ≤ 1/frame,
  uploads ≤ 4/frame.
- `ChunkIOExecutor.tick` 8 ms budget.
- `BackgroundPacer` yields background work.

### Results

- MSPT peak **806 → 147 ms**.
- Frame peak **1045 → 236 ms**.
- Steady MSPT **11–12.5 ms** avg, **17–48 ms** max.
- FPS **160–223**.
- 20 TPS, 484 entities/tick, 85 TE/tick, 220 chunks/tick.

---

## 2026-11-09 — Networking, client simulation, parallel lighting/block updates

### Added

- `PacketSerialization` — per-player ordered PLAY serialization.
- `InboundPacketPipeline` — inbound PLAY pipeline.
- `MainThreadDispatcher` — bounded main-thread handoff.
- `LightingExecutor` — 16 shards, 36-block margin, global read/write lock.
- `DeferredMutationQueue` — ordered cross-region commits.
- `ClientSimulationThread`, `ClientRenderSnapshot`.
- `ThreadingAutopilot` — region-size probing every 200 ticks.
- `ThreadAwareRandom` per-object keys.
- `ThreadingSelfTest`, `ExplosionSelfTest`, `IC2ExplosionSync`.

### Changed

- `MinecraftServer.tickWorldsParallel`.
- `World`, `WorldServer`, `ChunkProviderServer`, `Chunk`.
- `Entity`, `EntityLiving`, `EntityLivingBase`, `EntityTracker`.
- `PlayerChunkMap`, `PlayerList`.
- `PathNavigate*`.
- `NetworkManager`, `NetHandlerPlayClient`, `PacketThreadUtil`.

### Results

- 4000 packets serialized on 8 threads; client joined.
- Autopilot: client `8 @ 27.857` → `16 @ 16.436` accepted → `32 @ 30.571`
  rejected.
- Server stress: `8 @ 10.821` → `16 @ 9.030` → `32 @ 8.627` accepted.

---

## 2026-11-08 — Fork foundation

### Added

- Deobfuscation pipeline:
  - `convert_tsrg.py`, `deobfuscator.py`, `deobfuscator_mci.py`,
    `remap_sources.py`, `check_srg.py`;
  - `fix_bom.py`, `fix_generic.py`, `fix_at_inject.py`;
  - `compare_patches.py`, `patch_minecraft.py`.
- Assembled source tree:
  - vanilla 1.12.2 + Forge 14.23.5.2860;
  - MCP `stable_39`;
  - 525 Forge patches, 118 `// Forge:` markers, 62 residual `func_` in 23 files.
- Build pipeline:
  - `compile_check.bat` (JDK 21, `--release 8`);
  - `make_mapped_jar.bat`;
  - `assemble_v2.bat`, `assemble_final.bat`.

### Added (threading layer, first pass)

- `TickPhase`, `Region`, `ITickThread`, `ThreadType`.
- `Threading` config and presets.
- `RegionManager`.
- `DeferredMutationQueue`.
- `ThreadAwareRandom`.
- `PathfindingExecutor`.
- `AsyncTerrainGenerator`.
- `ClientSimulationThread`.
- `WorldStateHasher`.
- `ThreadingSelfTest`.
- `ThreadingMetrics`.

### Verified

- `2916 .java -> 4955 .class`, `JAVAC_EXIT=0`.
- Dedicated server boot with 6 mods, including IC2 2.8.222.
- First client launch and screenshots.

---

## 2026-11-08 — Render pipeline and LOD

- `RenderPipeline` separated render prep from GL upload.
- `LodRenderer` LOD path (26 iterations).
- Lazy VBOs and render buffers.
- `ChunkRenderDispatcher`, `ChunkRenderWorker`, `RenderChunk` changes.
- Client simulation thread/entities experiments.
- Async terrain handover and stability runs.
- BuildCraft stress and jstack diagnostics.
