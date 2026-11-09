# v0.1.0 — initial open-source release

**Deterministic regionized multithreading for Minecraft Forge 1.12.2 (14.23.5.2860).**

This is the first public snapshot of the fork. It is experimental but playable;
test on a staging server and back up worlds.

## Highlights

- Regionized entity/tile-entity/block ticking with deterministic commits.
- Dimension-parallel world ticking.
- Async pathfinding, terrain staging, sharded lighting.
- Client simulation thread, render pipeline, LOD renderer.
- Parallel model baking, texture decoding, and deterministic loading.
- Per-player ordered packet serialization and inbound pipeline.
- Autopilot region-size tuning.
- F3 overlay, `/forgethreading`, telemetry dumps.
- Memory leak guard, capability audit, opt-in static purge (`/forgemem`).
- MSPT world-join peak reduced from **806 ms to 147 ms** (reference setup).

## Reference results

| Metric | Before | After |
|---|---:|---:|
| World-join MSPT peak | 806 ms | 147 ms |
| World-join frame peak | 1045 ms | 236 ms |
| Steady MSPT | — | 11–12.5 ms avg |
| Steady FPS | — | 160–223 |
| TPS | — | 20.00 |
| Leaked worlds | — | 0 |

See `benchmarks/RESULTS.md` for details.

## What is in this release

- `patches/forge-1.12.2-multithreaded.patch` — unified diff against a clean
  Forge 14.23.5.2860 MCP `stable_39` source tree (72 modified files, 35 new
  files).
- `src/main/java/` — fork-only classes (`threading`, `memory`, `RenderPipeline`,
  `LodRenderer`).
- `scripts/` — baseline assembly, patch apply, compile, jar build, run templates.
- `docs/` — architecture, configuration, performance, testing, compatibility,
  roadmap, FAQ.
- `benchmarks/` — final threading dump and result summaries.

## What is NOT included

- Minecraft/Forge source or binaries.
- A compiled jar.
- Mojang assets.

You must obtain the base source legally and build it yourself.

## Build

```powershell
.\scripts\apply-patches.ps1 -Target C:\path\to\forge-baseline
.\scripts\compile.ps1 -SourceRoot C:\path\to\forge-baseline -Libs C:\path\to\libs -Out C:\path\to\compile_out -ExtraClasspath C:\path\to\mods\industrialcraft-2-2.8.222-ex112-rebuilt-client.jar
.\scripts\build-jar.ps1 -SourceRoot C:\path\to\forge-baseline -Classes C:\path\to\compile_out -OutJar .\forge-1.12.2-multithreaded.jar
```

## Known issues

- First client frame after join: ~236 ms.
- Autopilot region-size change under load: ~117 ms tick.
- Some environments crash in `OpenAL64.dll` during sound init (native audio,
  not threading).
- Mods that assume all TE/entity ticks run on the main thread may need
  `regions=0` or `regionBlockTicks=false`.

## License

LGPL-2.1, matching MinecraftForge. See `LICENSE` and `NOTICE.md`.
