# Compatibility

---

## 1. Tested environment

- Minecraft 1.12.2
- Forge **14.23.5.2860**
- MCP **`stable_39`**
- Java 8 runtime, Java 21 build
- Integrated server and dedicated server
- 6-mod test pack, including:
  - IndustrialCraft 2 **2.8.222-ex112**
  - Just Enough Items

---

## 2. Mod compatibility

### IndustrialCraft 2

- IC2 explosion handling is synchronized through `IC2ExplosionSync`.
- Region workers can tick IC2 entities/TEs only if their classes are safe for
  region ticking.
- If IC2 machines behave inconsistently, try:

```text
-Dforge.threading.regions=0
```

or:

```text
-Dforge.threading.regionBlockTicks=false
```

### Just Enough Items

- JEI recipe sync is unaffected by world ticking.
- Parallel recipe loading keeps registry IDs stable.
- If JEI shows missing recipes, disable `parallelRecipeLoading` to compare.

### BuildCraft

- BuildCraft stress runs passed with regionized ticking.
- Pipes/quarries that mutate distant chunks may rely on main-thread ordering;
  if issues appear, reduce `regions` or disable `regionBlockTicks`.

### General rule

Start with:

```text
-Dforge.threading.preset=balanced
```

If a mod is incompatible, try in this order:

1. `-Dforge.threading.regionBlockTicks=false`
2. `-Dforge.threading.regions=0`
3. `-Dforge.threading.preset=safe`
4. `-Dforge.threading.singleThreaded=true`

Then report the mod name, version, and `/forgethreading dump`.

---

## 3. Mods that assume main-thread ticking

Some mods store global mutable state or assume that all TE/entity ticks happen on
the server thread. Symptoms:

- duplicated or lost items;
- machines that update only sometimes;
- concurrent modification exceptions;
- inconsistent redstone/pipe behavior.

Mitigation:

- disable region ticking for that mod's classes (compatibility exclusion);
- run `regions=0` or `safe`;
- add a compatibility shim.

---

## 4. Client rendering

- The GL context remains on the main thread. The fork does not parallelize
  OpenGL calls.
- Render preparation, entity snapshots, texture decoding, and model baking can be
  offloaded.
- LOD rendering is optional. If a shader/mod conflicts, disable the LOD path.
- Some environments crash in `OpenAL64.dll` during sound initialization with
  `preset=balanced`. This is a native audio issue, not a threading issue.
  Retry, change the sound device, or validate on a dedicated server.

---

## 5. Forge version and patch baseline

The patch is generated against a specific source snapshot
(`vanilla_2847 + forge_official_mcp`, Forge 14.23.5.2860, MCP `stable_39`).

Applying it to:

- a different Forge version;
- a different MCP mapping;
- a heavily patched/custom source tree;
- a different mod loader fork;

will likely fail or require manual rebasing.

Use `git apply --check` first. If it fails, use `git apply --reject` and port the
hunks manually, or take the fork-only classes from `src/main/java/` and integrate
them.

---

## 6. Server-side vs client-side

- The fork is primarily a server/world-tick optimization.
- Client-side simulation and render changes are optional.
- A vanilla client can connect to a server running this fork **only if** the
  network protocol is unchanged. This fork does not add required client packets;
  it changes server-side scheduling.
- Test with your exact client modpack before production.

---

## 7. Known incompatible patterns

| Pattern | Why | Workaround |
|---|---|---|
| Mod assumes all TEs tick on server thread | Region threads break the assumption | `regions=0` or exclusion |
| Mod mutates remote chunks from a TE tick | Cross-region mutation ordering | `DeferredMutationQueue` or `regions=0` |
| Mod uses a global static world reference | Leak/ownership issues | `MemoryLeakGuard` + `/forgemem` audit |
| Mod uses `System.gc()` on world load | STW pause | remove or disable |
| Shader/mod assumes main-thread render prep | RenderPipeline ordering | disable client render offload |
| Native audio driver crashes | OpenAL bug | retry / change device / dedicated server |

---

## 8. Reporting compatibility issues

Include:

- mod name and version;
- Forge version;
- preset and overrides;
- `/forgethreading dump`;
- `logs/latest.log`, `logs/debug.log`;
- crash report;
- whether `preset=safe` fixes it.

Please do not post full worlds or personal data.
