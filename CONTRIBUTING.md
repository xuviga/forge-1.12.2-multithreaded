# Contributing

Thanks for improving the fork. Please keep changes focused, deterministic, and
testable.

---

## 1. Ground rules

1. **Java 8 source compatibility.** The build uses JDK 21 with `--release 8`.
2. **No new hard dependencies.** Do not require client-only or mod-specific
   classes from the core threading layer.
3. **Preserve public APIs** in `net.minecraftforge.common.threading` and
   `net.minecraftforge.common.memory`.
4. **Determinism first.** Any change that affects world simulation must preserve
   `WorldStateHasher` equality across single/parallel/regions.
5. **Main-thread ownership.** Do not mutate players, chunk maps, network state,
   registries, or world border from region workers. Use
   `DeferredMutationQueue` or `MainThreadDispatcher`.
6. **Opt-in risk.** New aggressive behavior must be behind a system property and
   default to safe.

---

## 2. Development workflow

1. Fork the repository.
2. Create a branch:
   ```bash
   git checkout -b feature/my-change
   ```
3. Apply the patch to a clean Forge 14.23.5.2860 MCP `stable_39` source tree.
4. Make your changes.
5. Run the determinism and stress tests from `docs/TESTING.md`.
6. Regenerate the patch if you changed files outside `src/main/java/`:
   ```bash
   git diff --binary > patches/forge-1.12.2-multithreaded.patch
   ```
   (Use the same baseline as described in `docs/BUILDING.md`.)
7. Open a pull request with:
   - what changed and why;
   - before/after telemetry (`/forgethreading dump`);
   - test commands and results;
   - compatibility notes.

---

## 3. Coding style

- Follow the existing Forge/Minecraft style.
- Keep comments concise and technical.
- Avoid `System.gc()` in load/unload/render paths.
- Do not allocate in per-frame render paths if a pool/double-buffer is available.
- Use `ThreadLocal` or explicit context objects for per-thread state.
- Prefer `Atomic*`, `volatile`, and concurrent collections over broad locks.
- Document any new system property in `docs/CONFIGURATION.md`.

---

## 4. Testing requirements

Before a PR is ready:

- [ ] `javac --release 8` passes.
- [ ] `WorldStateHasher` matches across single/parallel/regions.
- [ ] `ThreadingSelfTest` passes.
- [ ] `ExplosionSelfTest` passes.
- [ ] 60-second stress run stays at 20 TPS.
- [ ] `/forgethreading status` shows no unexpected `dispatchFallback` growth.
- [ ] `/forgemem status` shows `leakedWorlds=0` after unload/reload.
- [ ] New properties are documented.
- [ ] New code has no hard client-only references in the server path.

---

## 5. Reporting bugs

Use the issue template. Include:

- Forge version, preset, overrides;
- mod list;
- `logs/latest.log`, `logs/debug.log`;
- `/forgethreading dump`;
- crash report;
- whether `preset=safe` fixes it;
- minimal reproduction steps.

Sanitize paths, usernames, IPs, and tokens.

---

## 6. Security

Do not open public issues for exploit details. Contact the maintainers privately
and allow time for a fix.

---

## 7. License

By contributing, you agree that your contribution is licensed under
**LGPL-2.1**, matching the rest of the fork.
