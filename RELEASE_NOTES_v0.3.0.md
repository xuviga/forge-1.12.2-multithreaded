# v0.3.0 - client render governor, dedicated chunk-gen pool

**Third snapshot of the deterministic regionized multithreading fork of Minecraft Forge 1.12.2 (14.23.5.2860).**

This release focuses on client frame pacing and on isolating chunk generation
from region ticking. It is still experimental; test on a staging server and back
up worlds.

## Highlights

- **Client render governor** (`ClientRenderGovernor`): adaptive frame pacing for
  the client. When frame time overruns budget it trims render distance and
  GUI/world work, then restores them after recovery (8 ms / 0.75 s low
  watermark, 5 ms / 15 s recovery, 30 s penalty window, minimum render
  distance 6).
- **Dedicated chunk-generation pool** (`ChunkGenExecutor`): terrain generation
  runs on its own workers with tuned queue caps (1024 scheduled / 512 pending)
  instead of competing with region threads.
- **Scheduled block-tick crash fix**: the v0.2 crash
  `Timed out waiting for region scheduled block tick` is gone; modded scheduled
  block ticks now fall back to the owning region queue.

## Repository delta vs v0.2.0

| Metric | v0.2.0 | v0.3.0 |
|---|---:|---:|
| Full patch paths | 113 | 125 |
| Modified files | 74 | 83 |
| New files | 39 | 42 |
| `common.threading` classes | 33 | 35 |
| `common.memory` classes | 4 | 4 |
| `vanilla-only.patch` paths | 46 | 56 |

The additional patch paths are `ClientRenderGovernor`, `ChunkGenExecutor`, and
`block/model/ModelCache`, plus the vanilla client/render updates needed by the
governor.

## Verification

- Regenerated full patch applies to a clean Forge 14.23.5.2860 MCP `stable_39`
  baseline with `git apply --check` (strict and `--ignore-whitespace`).
- Applying the full patch to the clean baseline reproduces the fork tree with
  **0/2922 normalized file mismatches**.
- The layer repository (`forge-layer` + `vanilla-only.patch`) reproduces the same
  tree with **0/2922 normalized file mismatches**.
- `scripts/smoke-test.ps1`: OK, 41 fork Java sources.
- Rebuilt runtime jar: 13,013,234 bytes,
  SHA-256 `B79676152F06E88368BA505D655D546AD6262A091A76BD011F512C675C9D22BB`.

## Client defaults in this snapshot

These are the tuned values used on the reference machine; they are not enforced
by the patch, only recommended:

- `guiWorldRenderInterval=1` (world renders every frame behind the GUI; `-1`
  caused a black background and flicker).
- `fancyGraphics=false`, render distance 32, LOD off.
- JEI `maxColumns=20`.
- Flight pre-generation on, chunk I/O threads 4, scheduled caps 1024/512.

## Build

Same workflow as v0.2.0. See `RELEASE_NOTES_v0.1.0.md` and `docs/BUILDING.md`.

## Known issues

Same as v0.1.0/v0.2.0: the world render behind the GUI remains the largest
GUI-frame cost, and the JEI panel is second. Per-section and merged LOD must not
be re-enabled on Intel Iris at render distance 32.

## License

LGPL-2.1, matching MinecraftForge. See `LICENSE` and `NOTICE.md`.
