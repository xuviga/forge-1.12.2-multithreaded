# NOTICE

This repository is an **unofficial community fork** of Minecraft Forge 1.12.2
(Forge 14.23.5.2860, MCP `stable_39`). It is not affiliated with, endorsed by,
or supported by Mojang Studios, Microsoft, or the MinecraftForge project.

## What this repository contains

- The fork-only Java sources under `src/main/java/net/minecraftforge/common/threading/`
  and `src/main/java/net/minecraftforge/common/memory/`.
- A unified patch (`patches/forge-1.12.2-multithreaded.patch`) that transforms a
  clean Forge 14.23.5.2860 MCP `stable_39` source tree into this fork.
- Build, patch, run, and test helper scripts.
- Documentation and benchmark summaries.

## What this repository does **not** contain

- Minecraft vanilla source code.
- A compiled Minecraft/Forge jar.
- Mojang assets, textures, sounds, or other proprietary game data.
- The complete Forge source distribution.

Minecraft is a trademark of Mojang Studios. Minecraft source code and assets are
copyright Mojang Studios / Microsoft. You must obtain the base Forge/Minecraft
source tree through legal channels and comply with the Minecraft EULA and the
MinecraftForge license.

## Licenses

- The fork code in this repository is released under the **GNU Lesser General
  Public License 2.1** (see `LICENSE`), matching the license used by Minecraft
  Forge.
- MinecraftForge is licensed under LGPL 2.1. See the upstream project:
  <https://github.com/MinecraftForge/MinecraftForge>.
- IndustrialCraft 2 and Just Enough Items are referenced only as compatibility
  targets. Their code is not distributed here.

## Attribution

- MinecraftForge team — the base Forge implementation and patch model.
- Mojang Studios / Microsoft — Minecraft.
- IC2 and JEI authors — compatibility testing targets.
- Everyone who reported telemetry, stress-test results, and crash logs.
