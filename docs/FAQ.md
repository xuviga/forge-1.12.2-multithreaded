# FAQ

## Is this an official Minecraft Forge fork?

No. It is an unofficial community fork of Forge 14.23.5.2860. It is not
affiliated with or supported by Mojang, Microsoft, or the MinecraftForge
project.

## Why is there no compiled jar in the repository?

A compiled jar would contain Minecraft/Forge classes. Redistributing those is
not allowed. This repository ships:

- fork-only source files;
- a patch against a clean Forge source tree;
- build/run scripts;
- documentation and benchmarks.

You must obtain the base source legally and build it yourself.

## Will it work with my modpack?

Start with `-Dforge.threading.preset=balanced` and test on a staging server.
If a mod misbehaves, try:

1. `-Dforge.threading.regionBlockTicks=false`
2. `-Dforge.threading.regions=0`
3. `-Dforge.threading.preset=safe`
4. `-Dforge.threading.singleThreaded=true`

Report the mod name/version and `/forgethreading dump` if it still fails.

## Is world simulation really deterministic?

Yes, for the tested paths. `ThreadAwareRandom` uses per-object keys instead of
region IDs, `DeferredMutationQueue` commits in `(regionId, seq)` order, and
`WorldStateHasher` compares single-threaded, parallel, and regionized runs.
Tests must not use wall-clock tick skipping that changes entity simulation.

## Why is my first join still ~236 ms?

The residual spike is `SPacketJoinGame` + first render. The current roadmap
includes warming `ViewFrustum`/shaders before showing `GuiDownloadTerrain` and
deferring the first world render. See `docs/ROADMAP.md`.

## Why does autopilot sometimes cause a ~117 ms tick?

Changing `regionSizeChunks` under load causes a one-time rebalance. Pin
`-Dforge.threading.regionSizeChunks=16` to disable autopilot, or wait for the
roadmap fix.

## Can vanilla clients connect?

The fork does not add required client packets; it changes server-side
scheduling. However, client-side simulation/render flags are optional. Test with
your exact client pack before production.

## Does it work on Linux?

The Java code is platform-neutral. The helper scripts are PowerShell/Windows.
You can adapt them to bash. The reference tests were performed on Windows.

## Can I use it commercially?

The fork code is LGPL-2.1. You must comply with the LGPL and with the Minecraft
EULA and MinecraftForge license. This is not legal advice.

## Where are the benchmarks?

See `benchmarks/RESULTS.md` and `docs/PERFORMANCE.md`. Always benchmark your own
hardware and modpack.

## How do I report a bug?

Use the issue template and include logs, mod list, preset, and whether
`preset=safe` fixes the problem. Sanitize personal data.
