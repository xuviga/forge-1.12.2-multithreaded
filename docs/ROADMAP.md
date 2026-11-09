# Roadmap

---

## Done

- [x] Deobfuscated Forge 1.12.2 source tree (MCP `stable_39`).
- [x] Concurrency hardening (volatile/atomic flags, ThreadLocal contexts,
      atomic event bus, thread assertions).
- [x] Regionized entity/TE/block ticking.
- [x] Deterministic `DeferredMutationQueue` with `(regionId, seq)` commits.
- [x] Per-object `ThreadAwareRandom`.
- [x] `WorldStateHasher` determinism tests.
- [x] Dimension-parallel ticking.
- [x] Async pathfinding, terrain staging, sharded lighting.
- [x] Client simulation thread and render snapshot.
- [x] RenderPipeline, lazy VBOs, lazy render buffers, LOD renderer.
- [x] Parallel model baking, texture decoding, and deterministic loading.
- [x] Per-player ordered packet serialization and inbound pipeline.
- [x] Autopilot region-size tuning.
- [x] F3 overlay, `/forgethreading`, telemetry dumps.
- [x] Memory leak guard, capability audit, opt-in static purger.
- [x] MSPT 806 ms spike fix.
- [x] Dedicated server boot with 6 mods.
- [x] 150-player load test.

---

## Next

### Sub-100 ms world join

Residual spikes:

- first client frame after join: ~236 ms;
- autopilot region-size change under load: ~117 ms.

Planned:

- [ ] Warm `ViewFrustum` and shaders before showing `GuiDownloadTerrain`.
- [ ] Optionally defer the first world render by one frame.
- [ ] Do not change `regionSizeChunks` on the first window or under load.
- [ ] Add a `forge.threading.joinWarmup=true` flag.

### Regionized mutation coverage

- [ ] Extend `DeferredMutationQueue` to more mod APIs.
- [ ] Add explicit cross-region mutation helpers for common mod patterns.
- [ ] Add per-mod compatibility exclusions.

### Client rendering

- [ ] Further separate render preparation from GL upload.
- [ ] Add a bounded upload queue with backpressure.
- [ ] Improve LOD transition quality.

### Testing and tooling

- [ ] CI job that verifies the patch structure.
- [ ] Reproducible benchmark script.
- [ ] Automated determinism matrix (single/parallel/regions × sizes).
- [ ] Memory leak regression suite.

### Documentation

- [ ] Video walkthrough for server owners.
- [ ] Per-mod compatibility matrix.
- [ ] Migration guide from stock Forge.

---

## Ideas / research

- [ ] Region-aware entity tracker.
- [ ] Async chunk generation pipeline with deterministic commit.
- [ ] Better integration with modern GCs (G1/ZGC).
- [ ] Optional Netty pipeline pinning.
- [ ] Server-side only build without client assets.

---

## Non-goals

- Parallel OpenGL rendering.
- Parallel mod lifecycle events.
- Breaking Forge API compatibility.
- Shipping Minecraft source or binaries.
