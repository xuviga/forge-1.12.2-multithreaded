# Benchmark results

Reference environment:

- Minecraft 1.12.2, Forge 14.23.5.2860, MCP `stable_39`
- Windows, JDK 8 runtime, JDK 21 build
- Client: `-Xms2G -Xmx6G -XX:+UseG1GC -XX:MaxGCPauseMillis=50`
- Render distance 12
- TerrainWorld
- 6 mods, including IC2 2.8.222-ex112 and JEI
- `preset=auto`

---

## World join

| Metric | Before | After |
|---|---:|---:|
| MSPT peak | 806 ms | 147 ms |
| Frame peak | 1045 ms | 236 ms |

Diagnosis:

```text
SPacketJoinGame            ~940 ms  (50k glGenBuffers + ViewFrustum)
explicit System.gc()       ~473 ms
ChunkIOExecutor            ~184 ms
160 render builders        ~10 MB direct each
```

Fixes:

- removed `System.gc()` from load/unload;
- lazy VBOs in `RenderChunk`;
- CPU-constant modelview matrix;
- lazy `RegionRenderCacheBuilder` buffers (1 MB SOLID start, ≤ 32 builders);
- allocation-free `setupTerrain`;
- no sync `updateChunkNow` on the game thread;
- `updateChunks` budgets;
- `ChunkIOExecutor.tick` 8 ms budget;
- `BackgroundPacer`.

---

## Steady state

From `msp560_final.log`:

| Metric | Value |
|---|---:|
| TPS | 20.00 |
| MSPT avg | 11.2–12.5 ms |
| MSPT max | 17–48 ms |
| Frame avg | ~5 ms |
| Frame max | 9–24 ms |
| FPS | 160–223 |
| Entities/tick | 484–485 |
| TE/tick | 85 |
| Chunks/tick | 220 |

Final threading dump excerpt:

```text
ForgeThreading: TPS=20.00 MSPT=22.07 max=91.50 FPS=113
preset=auto singleThreaded=false dimensions=4 regions=4 regionSize=16 autopilot=true
blockTicks=true scheduled=true lighting=true packetOut=true packetIn=true
pools: world=3/0q0 region=4/0q0 path=8/0q0 chunkio=1/0q0 packetOut=0/0q0 packetIn=0/0q0
counters: dispatchFallback=0 dispatchQueue=0 eventInline=0 eventSkip=0
render.chunk.compiled=5403 render.chunk.deferred=4563 render.chunk.uploads=3393
render.used=2462 render.fallback=1
```

---

## Autopilot

Client:

```text
baseline regionSize=8  avgMs=27.857
trial   regionSize=16 avgMs=16.436  accepted
trial   regionSize=32 avgMs=30.571  rejected
```

Server stress:

```text
baseline regionSize=8  avgMs=10.821
trial   regionSize=16 avgMs=9.030   accepted
trial   regionSize=32 avgMs=8.627   accepted
```

Separate model/loading run:

```text
baseline regionSize=8  avgMs=32.061
trial   regionSize=16 avgMs=24.259  accepted
trial   regionSize=32 avgMs=11.060  accepted
settled regionSize=32
```

---

## Parallel loading

```text
Scanned 10 mod candidates in 582 ms using the load pool
Loading 287 blockstate model groups on 8 threads -> 715 ms
Loading item models for 588 items on 8 threads -> 292 ms
Decoding 2231 sprites on 16 threads
Baking 4132 models on 16 threads
Forge Mod Loader has successfully loaded 6 mods
Dedicated server: Done (2.477s)
```

---

## Memory

```text
[mem] unloads=2 purged=2 audits=9 warnings=0
[mem] heap 183.7MiB / 1820.5MiB (10%) loadedWorlds=1 leakedWorlds=0
capability owners: live=1 created=3 cleared=2
dim 0: 1 live owners
net.minecraft.world.WorldServer = 1
world unload dim=0 purged 1 static reference(s)
```

---

## Residual spikes

| Spike | Value | Cause |
|---|---:|---|
| First client frame after join | ~236 ms | `SPacketJoinGame` + first render |
| Autopilot region-size change | ~117 ms | region resize under load |

Follow-up: warm `ViewFrustum`/shaders before `GuiDownloadTerrain`, defer the
first world render, and avoid `regionSizeChunks` changes on the first window or
under load.
