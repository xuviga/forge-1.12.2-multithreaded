# v0.2.0 — adaptive governor, low-memory mode

**Second snapshot of the deterministic regionized multithreading fork of Minecraft Forge 1.12.2 (14.23.5.2860).**

This release folds the post-v0.1.0 development tree into the public patch set.
It is still experimental; test on a staging server and back up worlds.

Warm boot is not part of this release. Closing the window shuts the client down
normally; the previous hide/SHOW client behaviour was removed.

## Highlights

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
| Full patch paths | 107 | 113 |
| Modified files | 72 | 74 |
| New files | 35 | 39 |
| Forge-layer Java files | 728 | 732 |
| `common.threading` classes | 29 | 33 |
| `vanilla-only.patch` paths | 46 | 46 |

The 6 additional patch paths are the four new classes (`ClassCache`,
`StatsOverlay`, `PerformanceGovernor`, `MemoryTrim`) plus `FMLModContainer` and
`FMLDeobfTweaker`, which are modified for the first time.

## Verification

- New full patch applies cleanly to a clean Forge 14.23.5.2860 MCP `stable_39`
  baseline (`git apply --check` exit code 0).
- `vanilla-only.patch` contains the updated `Minecraft.java` close handling
  (46 paths).
- Client run: TPS 20.00, MSPT 11.11 ms (max 14.64), FPS 192, `dimensions=4`,
  `regions=4`, 2390 path jobs, 119k region block units.

## Build

Same workflow as v0.1.0. See `RELEASE_NOTES_v0.1.0.md` and `docs/BUILDING.md`.

## Known issues

Same as v0.1.0. See the previous release notes.

## License

LGPL-2.1, matching MinecraftForge. See `LICENSE` and `NOTICE.md`.
