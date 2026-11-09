# Performance

All numbers below are from the reference test machine and are provided as
evidence, not as a guarantee. Your modpack, world, hardware, and render
distance will change the results.

---

## 1. Test setup

- Minecraft 1.12.2, Forge 14.23.5.2860, MCP `stable_39`.
- Windows, JDK 8 runtime, JDK 21 build.
- `-Xms2G -Xmx6G -XX:+UseG1GC -XX:MaxGCPauseMillis=50` (client).
- Render distance 12.
- TerrainWorld test map.
- 6 mods, including IndustrialCraft 2 2.8.222-ex112 and JEI.
- `preset=auto`, `regionSizeChunks` autotuned.

---

## 2. World-join spike

### Diagnosis

The original 806 ms MSPT spike was a burst at world start:

| Phase | Cost |
|---|---:|
| `SPacketJoinGame` | **~940 ms** (`50k glGenBuffers` + `ViewFrustum`) |
| explicit `System.gc()` | **~473 ms** |
| `ChunkIOExecutor` | **~184 ms** |
| 160 render builders | **~10 MB direct each** |

### Fixes

- Removed `System.gc()` from `loadWorld`, `launchIntegratedServer`, and
  `unloadWorld`.
- `RenderChunk` is GL-free in constructor/`setPosition`; VBOs are lazy in
  `getVertexBufferByLayer`.
- Shared CPU-constant modelview matrix.
- `RegionRenderCacheBuilder`: lazy buffers, 1 MB SOLID start, ≤ 32 builders.
- `RenderGlobal.setupTerrain`: allocation-free, pools/double buffers.
- No synchronous `updateChunkNow` on the game thread.
- `updateChunks`: 4 chunks/frame warm-up, budget 12, immediate ≤ 1/frame by EMA,
  uploads ≤ 4/frame.
- `ChunkIOExecutor.tick` budget: 8 ms.
- `BackgroundPacer` yields background pools when the game tick/frame overruns.

### Result

| Metric | Before | After |
|---|---:|---:|
| MSPT peak | **806 ms** | **147 ms** |
| Frame peak | **1045 ms** | **236 ms** |
| Steady MSPT | — | **11–49 ms** |
| Steady frame | — | **< 25 ms** |
| Camera rotation | spikes | **23°/tick without spikes** |

---

## 3. Steady-state results

From `msp560_final.log` (2026-11-09):

| Metric | Value |
|---|---:|
| TPS | **20.00** |
| MSPT avg | **11.2–12.5 ms** |
| MSPT max | **17–48 ms** |
| Frame avg | **~5 ms** |
| Frame max | **9–24 ms** |
| FPS | **160–223** |
| Entities / tick | **484–485** |
| Tile entities / tick | **85** |
| Chunks / tick (block ticks) | **220** |
| Path submitted/completed | ~300 / ~300 per 100 ticks |

From the final threading dump:

```text
ForgeThreading: TPS=20.00 MSPT=22.07 max=91.50 FPS=113
preset=auto singleThreaded=false dimensions=4 regions=4 regionSize=16 autopilot=true
pools: world=3 region=4 path=8 chunkio=1 packetOut=0 packetIn=0
render.used=2462 render.fallback=1
```

> The `MSPT=22.07 max=91.50` snapshot is from a different moment than the
> `msp560_final.log` steady-state window; both are included for transparency.

---

## 4. Autopilot results

### Client run

```text
baseline regionSize=8  avgMs=27.857
trial   regionSize=16 avgMs=16.436  accepted
trial   regionSize=32 avgMs=30.571  rejected
```

### Server stress run

```text
baseline regionSize=8  avgMs=10.821
trial   regionSize=16 avgMs=9.030   accepted
trial   regionSize=32 avgMs=8.627   accepted
```

### Separate model/loading run

```text
baseline regionSize=8  avgMs=32.061
trial   regionSize=16 avgMs=24.259  accepted
trial   regionSize=32 avgMs=11.060  accepted
settled regionSize=32
```

Autopilot only accepts a candidate when the improvement is greater than 3%.
An explicit `-Dforge.threading.regionSizeChunks=N` disables autopilot.

---

## 5. Parallel loading

From the parallel loading/model run:

```text
Scanned 10 mod candidates in 582 ms using the load pool
Loading 287 blockstate model groups on 8 threads -> 715 ms
Loading item models for 588 items on 8 threads -> 292 ms
Decoding 2231 sprites on 16 threads
Baking 4132 models on 16 threads
Forge Mod Loader has successfully loaded 6 mods
Dedicated server: Done (2.477s)
```

Parallel loading uses deterministic fan-out/fan-in: workers write to private
slots, the loader thread merges in original order.

---

## 6. Memory

From `mem_guard_test3.log`:

```text
[mem] unloads=2 purged=2 audits=9 warnings=0
[mem] heap 183.7MiB / 1820.5MiB (10%) loadedWorlds=1 leakedWorlds=0
capability owners: live=1 created=3 cleared=2
dim 0: 1 live owners
net.minecraft.world.WorldServer = 1
world unload dim=0 purged 1 static reference(s)
```

Result: no leaked worlds, no audit warnings, stable heap after unload/reload.

---

## 7. Residual spikes

Two residual spikes remain:

1. **First client frame after join: ~236 ms**
   - `SPacketJoinGame` ~63–101 ms + first render.
2. **One tick ~117 ms when autopilot changes `regionSizeChunks` under load.**

Planned fixes:

- Warm `ViewFrustum`/shaders before `GuiDownloadTerrain`, or defer the first
  world render.
- Do not change `regionSizeChunks` on the first window or under load.

Verify with:

- `logs/forgethreading-*.txt` (`wmax` phases);
- `msp560_*.log`;
- `gc_msp560.log`, `gc_spin.log`;
- `phase_*.log`.

---

## 8. How to benchmark your server

1. Start the server with `-Dforge.threading.metrics=true`.
2. Join, wait for the world to settle.
3. Run `/forgethreading status` and `/forgethreading dump`.
4. Capture 5–10 minutes under your normal player load.
5. Compare:
   - TPS/MSPT avg/max;
   - `wmax` per phase;
   - pool queue/active counts;
   - `dispatchFallback`, `eventInline`, `render.fallback`;
   - GC logs (`-Xlog:gc*` on modern JDK, or `-XX:+PrintGCDetails` on JDK 8).
6. Test the same scenario with `preset=safe` and `preset=balanced` to isolate
   threading effects.

Please include the dump and mod list when reporting performance issues.
