# Scripts

All scripts are PowerShell 5.1+ and assume Windows paths.

| Script | Purpose |
|---|---|
| `assemble-baseline.ps1` | Recreate the `vanilla + Forge overlay` baseline |
| `apply-patches.ps1` | Check and apply the fork patch |
| `compile.ps1` | Compile the patched source tree |
| `build-jar.ps1` | Build the runtime jar |
| `run-client.ps1` | Client launch template |
| `run-server.ps1` | Dedicated server launch template |
| `smoke-test.ps1` | Verify the repository/patch structure |

---

## Typical flow

```powershell
# 1. Recreate the baseline (optional if you already have one)
.\scripts\assemble-baseline.ps1 `
  -VanillaSource C:\path\to\vanilla_2847 `
  -ForgeOverlay C:\path\to\forge_official_mcp `
  -Out C:\path\to\forge-baseline

# 2. Apply the fork patch
.\scripts\apply-patches.ps1 -Target C:\path\to\forge-baseline

# 3. Compile
.\scripts\compile.ps1 `
  -SourceRoot C:\path\to\forge-baseline `
  -Libs C:\path\to\libs `
  -Out C:\path\to\compile_out `
  -ExtraClasspath C:\path\to\mods\industrialcraft-2-2.8.222-ex112-rebuilt-client.jar

# 4. Build the jar
.\scripts\build-jar.ps1 `
  -SourceRoot C:\path\to\forge-baseline `
  -Classes C:\path\to\compile_out `
  -OutJar C:\path\to\forge-1.12.2-multithreaded.jar
```

---

## Notes

- `compile.ps1` uses `--release 8` by default. If you build with JDK 8 itself,
  pass `-NoRelease`.
- `compile.ps1` excludes the Forge/Minecraft jars that are replaced by the
  source tree. Adjust the `$exclude` list if your `libs` layout differs.
- `compile.ps1` needs `-ExtraClasspath` for mod APIs imported by the fork. In
  particular, `IC2ExplosionSync` imports `ic2.api.event.ExplosionEvent`, so pass
  the IC2 client jar when compiling.
- `run-client.ps1` and `run-server.ps1` are templates. Edit the default paths
  at the top of each file.
- The patch is line-ending sensitive. Apply it with Git and keep
  `core.autocrlf=false`.
- `smoke-test.ps1` is used by CI to verify that the patch contains the expected
  fork classes.
