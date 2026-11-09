# Testing

The fork is validated with determinism checks, stress runs, self-tests, and
memory audits. This document lists what was run and how to reproduce it.

---

## 1. Test matrix

| Test | Purpose | Result |
|---|---|---|
| `WorldStateHasher` single vs parallel | Determinism across threading modes | Identical |
| `WorldStateHasher` 8 vs 16 vs 32 region size | Region-size independence | Identical |
| Block determinism (`blockdet`) | Block tick order independence | Identical |
| Random determinism (`randdet`) | Per-object RNG independence | Identical |
| Topology checks | Chunk/entity topology stability | Identical |
| Region ownership audit | No cross-region ownership leaks | Clean |
| `ThreadingSelfTest` | Thread/pool sanity | Pass |
| `ExplosionSelfTest` | Explosion determinism | Pass (4 runs) |
| IC2 explosion sync | Mod explosion compatibility | Pass |
| 60-second stress | Sustained tick stability | 20 TPS |
| BuildCraft stress | Modded world stress | 20 TPS |
| 150-player load test | Player/chunk tracker load | Completed |
| Dedicated server | Full server boot with 6 mods | `Done (2.477s)` |
| Memory unload/reload | World leak guard | `leakedWorlds=0`, `warnings=0` |
| Packet serialization | 4000 packets on 8 threads | Client joined and ran |

---

## 2. Determinism

`WorldStateHasher` is enabled with:

```text
-Dforge.threading.hashTicks=20
-Dforge.threading.audit=true
```

Run the same world seed and command sequence in:

1. `singleThreaded=true`;
2. `parallelDimensions=4`;
3. `regions=4`, `regionSizeChunks=8`;
4. `regions=4`, `regionSizeChunks=16`;
5. `regions=4`, `regionSizeChunks=32`.

Compare the hash dumps. They must match. Tests must not use wall-clock tick
skipping that changes entity simulation.

---

## 3. Stress

Recommended commands:

```text
-Dforge.threading.metrics=true
-Dforge.threading.regions=4
-Dforge.threading.regionSizeChunks=16
-Dforge.threading.regionBlockTicks=true
-Dforge.threading.parallelBlockUpdates=true
-Dforge.threading.parallelLighting=true
-Dforge.threading.packetSerialization=true
```

Run for at least 60 seconds with:

- 100+ entities/tick;
- 50+ tile entities/tick;
- 100+ chunks/tick random ticks;
- players moving across region boundaries;
- explosions (IC2/TNT) near region edges;
- chunk load/unload cycles.

Watch:

- TPS/MSPT;
- `dispatchFallback`, `eventInline`;
- region/path/chunkio pool queues;
- `deferred.ordered.enqueued/applied`;
- GC pauses.

---

## 4. Self-tests

```text
-Dforge.threading.selftest=true
-Dforge.threading.explosionSelftest=true
```

Expected:

- `ThreadingSelfTest` prints pass lines and leaves TPS at 20.
- `ExplosionSelfTest` produces identical block/entity results across runs.
- `IC2ExplosionSync` keeps IC2 explosion state consistent.

---

## 5. Memory audit

```text
-Dforge.memory.watchdog=true
-Dforge.memory.capabilityAudit=true
-Dforge.memory.auditIntervalTicks=1200
```

Recommended test:

1. Start a server with a world.
2. `/forgemem status` — note heap and owners.
3. Unload/reload a dimension or stop/start the server.
4. `/forgemem purge`.
5. `/forgemem status`.

Expected:

```text
[mem] unloads=2 purged=2 audits=9 warnings=0
[mem] heap 183.7MiB / 1820.5MiB (10%) loadedWorlds=1 leakedWorlds=0
capability owners: live=1 created=3 cleared=2
```

If `leakedWorlds` grows, run `/forgemem dump` and check `ModReferencePurger`
output. Aggressive purge is opt-in:

```text
-Dforge.memory.aggressivePurge=true
```

or `/forgemem aggressive on`.

---

## 6. Packet pipeline

```text
-Dforge.threading.packetSerialization=true
```

Test:

- 4000 packets serialized on 8 threads;
- client connects and receives chunk data;
- no `serFail`;
- inbound/outbound queues drain;
- `/forgethreading handlers` lists offloaded handlers.

Expected handler audit:

```text
server PLAY: 29 handlers / 27 call PacketThreadUtil
client PLAY: 76 handlers / 72 call PacketThreadUtil
```

---

## 7. Rendering

```text
-Dforge.threading.overlay=true
-Dforge.threading.metrics=true
```

Check:

- F3 overlay shows TPS/MSPT/FPS and pool stats;
- `render.chunk.compiled`, `deferred`, `uploads`;
- `render.used`, `render.fallback` (fallback should stay near 0/1);
- no GL errors;
- `RenderChunk` lazy VBO path is used;
- `setupTerrain` does not allocate per frame (profiler/telemetry).

---

## 8. Logs to include in a bug report

- `logs/latest.log`;
- `logs/debug.log`;
- `logs/forgethreading-<timestamp>.txt`;
- `crash-reports/*`;
- JVM flags;
- mod list;
- whether the issue reproduces with `preset=safe`.

Sanitize paths/usernames before posting publicly.
