# Architecture

This document describes the concurrency model, ownership rules, and
determinism guarantees of the fork.

---

## 1. Goals

1. Use more than one core for world simulation without breaking MinecraftForge
   mods.
2. Preserve deterministic world evolution.
3. Keep the main thread authoritative for players, networking, chunk ownership,
   and registries.
4. Make every parallel subsystem observable and tunable at runtime.
5. Fail safe: if a feature is disabled, the server behaves like stock Forge.

Non-goals:

- Parallel OpenGL rendering (the GL context remains on the main thread).
- Parallel mod lifecycle events (mod loading/init remains serial).
- Rewriting Minecraft's data structures into a lock-free engine.

---

## 2. Thread model

```text
Main thread
  ├─ players, PlayerChunkMap, trackers
  ├─ network accept/read/write, packet dispatch
  ├─ world border, spawning, registries, commands
  ├─ world load/unload
  └─ client GL context and frame presentation

Region threads (pool)
  ├─ entity ticking for owned regions
  ├─ tile entity ticking for owned regions
  ├─ scheduled block/fluid ticks
  ├─ projectile ticking
  └─ whitelisted mod hooks

Dimension workers (pool)
  └─ one world tick per dimension, joined by the main thread

Async pools
  ├─ pathfinding
  ├─ lighting shards
  ├─ terrain generation
  ├─ chunk I/O
  ├─ model baking
  ├─ texture decoding
  └─ packet serialization

Client threads
  ├─ client simulation thread (entity ticks / snapshots)
  ├─ render prep thread (candidate collection)
  └─ main thread (GL upload + draw)
```

`ThreadType` and `ThreadAudit` classify threads and log violations. The default
event phase is `MAIN`; region-aware events must opt in explicitly.

---

## 3. Regionized ticking

`RegionManager` partitions the world into regions of `regionSizeChunks` chunks.
For each tick:

1. The main thread snapshots the list of loaded regions.
2. Region workers claim disjoint regions.
3. Each worker ticks the region's entities, tile entities, block ticks, and
   projectiles according to the active whitelist.
4. Mutations that cross a region boundary are enqueued in
   `DeferredMutationQueue`.
5. The main thread drains the queue and commits mutations in
   `(regionId, seq)` order.

`Region` tracks ownership and provides `clearEntityOwners()` on unload.
`RegionManager` sorts chunks by `(x, z)` before dispatch so the work order is
stable.

### Whitelists

- Entity classes: mobs, items, projectiles, and other classes that do not
  require main-thread-only state.
- Tile entity classes: opt-in or classloader-filtered; mod TEs can be excluded.
- Block ticks: enabled by `regionBlockTicks`.
- Scheduled updates: enabled by `parallelBlockUpdates`.

If a mod's class is not safe for region ticking, disable the relevant feature
or add a compatibility exclusion.

---

## 4. Determinism

Determinism is a hard requirement. Three mechanisms enforce it:

### 4.1 Per-object RNG keys

`ThreadAwareRandom` derives its seed from a stable per-object key:

- entity UUID;
- tile entity position;
- `ChunkPos`;
- explicit `TickContext.rngKey`.

It does **not** use `regionId`, so changing region size or worker count does not
change world evolution.

### 4.2 Ordered commits

`DeferredMutationQueue` assigns a monotonic `seq` to each mutation. The main
thread applies mutations grouped by `regionId` and ordered by `seq`. This makes
cross-region effects independent of worker scheduling.

### 4.3 WorldStateHasher

`WorldStateHasher` produces a stable hash of world state (blocks, entities, TE
data, random state relevant to simulation). It is used by tests to compare:

- single-threaded run;
- parallel dimension run;
- regionized run;
- different `regionSizeChunks` values.

The hash must be identical. Tests must not use wall-clock tick skipping that
changes entity simulation.

---

## 5. Main-thread ownership

The following are main-thread-only:

- `MinecraftServer` lifecycle and world load/unload;
- `PlayerChunkMap`, `PlayerList`, `EntityTracker`;
- network channels and `NetworkManager` writes;
- `WorldBorder`, spawn logic, weather;
- registries (`GameData`, `ForgeRegistries`);
- commands and permissions;
- mod lifecycle events (`preInit`, `init`, `postInit`, load/complete).

Region workers may read most world data but must not mutate main-owned
structures directly. Use `MainThreadDispatcher` or `DeferredMutationQueue`.

---

## 6. Async pathfinding

`PathfindingExecutor` runs path searches on a dedicated pool.

- `PathNavigate`, `PathNavigateGround`, `PathNavigateFlying`,
  `PathNavigateSwimmer` submit searches instead of blocking the tick.
- `ChunkCache` reads are confined to the main thread or wrapped by the path
  executor's snapshot rules.
- Completed paths are reaped on the main thread (`reapCompletedClient`) or
  during the server tick.
- The pool size defaults to `cores / 2` (8 in the reference test).

If a mod mutates the world during pathfinding, the result may be stale; the
executor validates the destination and falls back to a synchronous search when
needed.

---

## 7. Lighting

`LightingExecutor` shards lighting updates into 16 shards with a margin of 36
blocks. A global read/write lock protects chunk sections:

- reads may run concurrently;
- writes are serialized per shard;
- the main thread joins shards at the end of the tick.

`parallelLighting=false` restores the stock path.

---

## 8. Client simulation and rendering

`ClientSimulationThread` can tick client-side entities on a separate thread and
publish snapshots through `ClientRenderSnapshot`.

`RenderPipeline` separates candidate collection/preparation from GL upload:

- the render prep thread collects visible chunks and builds draw candidates;
- the main thread performs GL uploads and draws;
- `RegionRenderCacheBuilder` uses lazy buffers (1 MB SOLID start, ≤ 32
  builders);
- `RenderChunk` is GL-free in its constructor and `setPosition`; VBOs are created
  lazily in `getVertexBufferByLayer`;
- `RenderGlobal.setupTerrain` is allocation-free and uses pools/double buffers.

`LodRenderer` provides a level-of-detail path for distant chunks. It is optional
and can be disabled if a mod conflicts with it.

---

## 9. Parallel loading

`LoadingExecutor` implements deterministic fan-out/fan-in:

- workers scan mod candidates, build recipe JSON, and resolve models;
- each worker writes to its own pre-sized slot;
- the loader thread joins and merges results in the original order;
- shared caches are concurrent;
- mod lifecycle events remain serial.

This keeps registry IDs, duplicate detection, and model resolution identical to
the sequential path.

---

## 10. Networking

- `PacketThreadUtil` tracks packet affinity and sequence.
- `PacketSerialization` uses a per-player ordered executor (pool `cores/2`,
  max 8). Local channels bypass the pool.
- `InboundPacketPipeline` processes inbound PLAY packets with main-thread
  handoff for handlers that require it.
- Handler audit (`/forgethreading handlers`) lists handlers that call
  `PacketThreadUtil` and can be offloaded.

Server PLAY: 29 handlers, 27 call `PacketThreadUtil`.
Client PLAY: 76 handlers, 72 call `PacketThreadUtil`.

---

## 11. Autopilot

`ThreadingAutopilot` runs every `autopilotWindow` (default 200) ticks:

1. Measures average MSPT for the current `regionSizeChunks`.
2. Tries a candidate from `8/16/32`.
3. Accepts the candidate only if it improves MSPT by more than
   `autopilotThreshold` (default 3%).
4. Settles on the best value.

An explicit `-Dforge.threading.regionSizeChunks=N` disables autopilot.

---

## 12. Memory safety

`MemoryLeakGuard` listens for `WorldEvent.Unload` and severs known strong
references:

- `RegionManager.clearEntityOwners`;
- `ChunkIOExecutor.purgeWorld`;
- `FarmlandWaterManager.purgeDimension`;
- `ClientSimulationThread.clearWorld`;
- `PregenManager.clearWorld`;
- `IC2ExplosionSync.clearWorld`;
- `CapabilityLeakTracker.onWorldUnload`;
- optional `ModReferencePurger.purge`.

`CapabilityLeakTracker` uses a `ReferenceQueue` and weak references. It always
tracks `World` objects and tracks `TileEntity`/`Entity`/`Chunk` only when their
classloader is the mod classloader.

`ModReferencePurger` is opt-in. It reflectively scans static `Map`, `Collection`,
and array entries in active mod jars and removes entries that reference an
unloaded world directly or through a one-level field named
`world`, `worldObj`, `level`, `dimension`, `worldServer`, or `targetWorld`. Every
removal is logged.

`ForgeChunkManager.unloadWorld` also drops player/pending tickets whose
`Ticket.world` is the unloaded world.

---

## 13. Telemetry

`ThreadingTelemetry` records:

- TPS/MSPT, frame time, FPS;
- per-phase timings (`world`, `entities`, `te`, `network`, `render`, ...);
- pool sizes, active/queued/completed counts;
- counters (`dispatchFallback`, `eventInline`, `render.uploaded`, ...);
- autopilot trials.

`/forgethreading dump` writes `logs/forgethreading-<timestamp>.txt`.
The F3 overlay prints a compact subset.

---

## 14. Failure modes

- If a region worker throws, the region is quarantined for the tick and the
  exception is reported; the main thread continues.
- If a deferred mutation is invalid at commit time, it is dropped and counted.
- If autopilot destabilizes the tick, set `autopilot=false` or pin
  `regionSizeChunks`.
- If a mod is incompatible with region ticking, disable `regions` or
  `regionBlockTicks` for that pack.
