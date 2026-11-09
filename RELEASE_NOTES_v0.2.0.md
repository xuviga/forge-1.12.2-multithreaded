# v0.2.0 — warm boot, adaptive governor, low-memory mode

**Second snapshot of the deterministic regionized multithreading fork of Minecraft Forge 1.12.2 (14.23.5.2860).**

This release folds the post-v0.1.0 development tree into the public patch set.
It is still experimental; test on a staging server and back up worlds.

## Highlights

- **Warm boot** (`WarmBoot`): after the player closes the window, the fully
  initialized client is hidden instead of destroyed. A local IPC endpoint
  (`SHOW`/`QUIT`, `127.0.0.1:25566`) shows or quits it, so the next launch skips
  mod `preInit`/`init` and resource loading.
- **Adaptive governor** (`PerformanceGovernor`): self-tuning MSPT governor that
  shrinks AI-LOD radii and simulation distance when the rolling MSPT window is
  over budget and expands them back when comfortably under budget, with
  hysteresis and a warm-up window.
- **Low-memory mode** (`MemoryTrim`): for heaps <= 1280 MB, drops
  `LaunchClassLoader.resourceCache` bytecode after startup and caps simulation
  distance.
- **Class-scan cache** (`ClassCache`): persistent cache used by the parallel
  load pool.
- **Stats overlay** (`StatsOverlay`): in-game FPS / frame time / TPS / MSPT /
  heap / RAM / per-core CPU overlay on F8.
- **Preset tuning**: `balanced`, `aggressive`, and `maximum` now leave the
  region worker count on `auto` and use `regionSizeChunks=4`.

## Repository delta vs v0.1.0

| Metric | v0.1.0 | v0.2.0 |
|---|---:|---:|
| Full patch paths | 107 | 114 |
| Modified files | 72 | 74 |
| New files | 35 | 40 |
| Forge-layer Java files | 728 | 733 |
| `common.threading` classes | 29 | 34 |
| `vanilla-only.patch` paths | 46 | 46 |

The 7 additional patch paths are the five new classes (`ClassCache`,
`WarmBoot`, `StatsOverlay`, `PerformanceGovernor`, `MemoryTrim`) plus
`FMLModContainer` and `FMLDeobfTweaker`, which are modified for the first time.

## Verification

- New full patch applies cleanly to a clean Forge 14.23.5.2860 MCP `stable_39`
  baseline (`git apply --check` exit code 0).
- `vanilla-only.patch` is byte-identical to v0.1.0; all changes are in
  `net/minecraftforge/**`.
- Client run: TPS 20.00, MSPT 11.11 ms (max 14.64), FPS 192, `dimensions=4`,
  `regions=4`, 2390 path jobs, 119k region block units.

## Build

Same workflow as v0.1.0. See `RELEASE_NOTES_v0.1.0.md` and `docs/BUILDING.md`.

## Known issues

Same as v0.1.0. See the previous release notes.

## License

LGPL-2.1, matching MinecraftForge. See `LICENSE` and `NOTICE.md`.
