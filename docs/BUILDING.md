# Building from source

This fork is distributed as **source patches + fork-only classes**, not as a
compiled Minecraft jar. You must obtain a clean Forge 14.23.5.2860 MCP
`stable_39` source tree and build it yourself.

---

## 1. Baseline

The patch was generated against this baseline:

- Minecraft 1.12.2
- Forge **14.23.5.2860**
- MCP mappings **`stable_39`**
- Base assembly: `vanilla_2847` source tree with the `forge_official_mcp` overlay
  (the same layout produced by the original `assemble_v2.bat`).

The patch is a unified diff of:

- **74 modified files**
- **39 new files** (the `threading`, `memory`, `RenderPipeline`, and `LodRenderer`
  classes, plus the v0.2 `ClassCache`, `StatsOverlay`, `PerformanceGovernor`,
  and `MemoryTrim` additions)

If your source tree is assembled differently, the patch may not apply cleanly.
In that case, use the fork-only classes from `src/main/java/` and port the
modified hunks manually with `git apply --reject` or `patch -p1`.

On Windows, a baseline assembled from `vanilla_2847` may contain CRLF line
endings while the repository stores the patch with LF (see `.gitattributes`).
`scripts/apply-patches.ps1` automatically retries with `--ignore-whitespace` for
such trees; pass `-IgnoreWhitespace` to force it.

### Reproduce the baseline

If you have the vanilla source and the Forge MCP overlay:

```powershell
.\scripts\assemble-baseline.ps1 `
  -VanillaSource C:\path\to\vanilla_2847 `
  -ForgeOverlay C:\path\to\forge_official_mcp `
  -Out C:\path\to\forge-baseline
```

Then apply the patch:

```powershell
.\scripts\apply-patches.ps1 -Target C:\path\to\forge-baseline
```

---

## 2. Prerequisites

- **JDK 21** (the original build used Eclipse Adoptium 21.0.11).
- **JDK 8** for running the game (the original launch scripts used JRE 1.8.0_301).
- A `libs` directory containing the Forge/Minecraft dependency jars used by your
  Gradle workspace.
- Minecraft assets and natives for running the client.
- Git (recommended) or a `patch` binary.

The source is compiled with:

```text
javac --release 8 -encoding UTF-8 -proc:none -nowarn
```

This keeps the produced classes compatible with Java 8 while using a modern JDK.

---

## 3. Apply the patch

```powershell
.\scripts\apply-patches.ps1 -Target C:\path\to\forge-baseline
```

The script runs `git apply --check` first and refuses to apply a patch that does
not match the baseline.

Manual alternative:

```bash
cd /path/to/forge-baseline
git apply --check /path/to/patches/forge-1.12.2-multithreaded.patch
git apply /path/to/patches/forge-1.12.2-multithreaded.patch
```

If you use `patch` instead of Git:

```bash
patch -p1 < patches/forge-1.12.2-multithreaded.patch
```

---

## 4. Compile

```powershell
.\scripts\compile.ps1 `
  -SourceRoot C:\path\to\forge-baseline `
  -Libs C:\path\to\libs `
  -Out C:\path\to\compile_out `
  -ExtraClasspath C:\path\to\mods\industrialcraft-2-2.8.222-ex112-rebuilt-client.jar
```

`-ExtraClasspath` is required when compiling with IC2 support because
`IC2ExplosionSync` imports `ic2.api.event.ExplosionEvent`. You can list several
mod jars separated by commas.

The script:

1. Collects every `*.java` under `-SourceRoot`.
2. Builds a classpath from every `*.jar` in `-Libs`, excluding the Forge/Minecraft
   jars that are replaced by the source tree:
   - `forge-1.12.2-14.23.5.2860-mci.jar`
   - `forge-1.12.2-14.23.5.2860-userdev3.jar`
   - `minecraft_merged-1.12.2.jar`
3. Runs `javac` into `-Out`.
4. Prints `JAVAC_EXIT=0` on success.

The original command was equivalent to:

```bat
javac -d compile_out -cp "%CP%" --release 8 -encoding UTF-8 -proc:none -nowarn -Xmaxerrs 2000 @final_sources.txt
```

---

## 5. Build the runtime jar

```powershell
.\scripts\build-jar.ps1 `
  -SourceRoot C:\path\to\forge-baseline `
  -Classes C:\path\to\compile_out `
  -OutJar C:\path\to\forge-1.12.2-14.23.5.2860-multithreaded.jar
```

The jar must contain:

- compiled classes from `-Classes`;
- `assets/`, `ibxm/`;
- `log4j2.xml`, `log4j2_server.xml`;
- `forge_at.cfg`, `forge_logo.png`, `mcplogo.png`, `pack.png`, `url.png`,
  `mcpmod.info`, `yggdrasil_session_pubkey.der`.

The original jar was ~12.95 MB with ~4,955 classes.

---

## 6. Run the client

Use `scripts/run-client.ps1` as a template. It launches `GradleStart` with:

```text
-Xms2G -Xmx6G -XX:+UseG1GC -XX:MaxGCPauseMillis=50
-Djava.library.path=<natives>
-Dfml.ignoreInvalidMinecraftCertificates=true
-cp <start>;<forge-jar>;<libs>
GradleStart --username Dev --gameDir <run-dir> --assetsDir <assets> --assetIndex 1.12 --version 1.12.2
```

Edit the variables at the top of the script for your machine.

---

## 7. Run a dedicated server

Use `scripts/run-server.ps1` as a template. It launches `GradleStartServer` with:

```text
-Xms1G -Xmx3G
-Dfml.ignoreInvalidMinecraftCertificates=true
-cp <start>;<forge-jar>;<libs>
GradleStartServer --gameDir <run-dir> --nogui
```

Recommended production flags:

```text
-Xms4G -Xmx6G -XX:+UseG1GC -XX:MaxGCPauseMillis=50
-Dforge.threading.preset=balanced
-Dforge.threading.metrics=true
-Dforge.memory.watchdog=true
```

---

## 8. Reproducibility notes

- Use `git config core.autocrlf false` before applying the patch to avoid
  CRLF/LF churn.
- The repository has `.gitattributes` that forces LF for Java/patch files and
  CRLF for Windows scripts.
- The patch is line-ending sensitive. Apply it with Git for best results.
- Keep the original `final_sources.txt` ordering stable if you script the build;
  deterministic class output is not required, but reproducible input order makes
  diffing easier.

---

## 9. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `git apply` says "No valid patches" | Patch file was saved as UTF-16 | Use the repository file as-is, or re-save as UTF-8 |
| `patch does not apply` | Wrong baseline / different Forge snapshot | Use Forge 14.23.5.2860 MCP `stable_39`; try `git apply --reject` |
| `javac` cannot find `net.minecraft.*` | Classpath missing `libs` jars | Check `-Libs` and exclude list |
| `javac` fails on generics | Source tree not fully MCP-remapped | Re-run the baseline assembly |
| Client crashes in `OpenAL64.dll` | Native audio issue | Retry, change sound device, or validate on dedicated server |
| Server OOM during load | Heap too small for the modpack | Increase `-Xmx`; reduce `parallelLoading` if needed |
