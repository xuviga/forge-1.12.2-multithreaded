# Forge 1.12.2 Multithreaded

**Детерминированный форк Minecraft Forge 14.23.5.2860 с регионной многопоточностью.**

[English README](README.md)

> **Статус:** экспериментальный, но играбельный. Проверен на клиенте и
> dedicated-сервере с IC2 2.8.222 и JEI. Перед тестами делайте бэкап миров.
> Это неофициальный форк, не связанный с Mojang или MinecraftForge.

---

## Что это

Minecraft 1.12.2 и Forge рассчитаны на один серверный поток. На современном
железе главный поток становится бутылочным горлышком задолго до насыщения CPU:
ИИ сущностей, tile entities, тики блоков, освещение, chunk I/O, pathfinding,
запекание моделей и сериализация пакетов конкурируют за одно ядро.

Этот форк добавляет **детерминированный слой регионной многопоточности**:

- **Region-потоки** параллельно тикают сущности, TE, блоки и снаряды в
  непересекающихся регионах мира.
- **Main thread** владеет игроками, chunk map, сетью, world border, спавном и
  реестрами.
- **Cross-region мутации** идут через `DeferredMutationQueue` и коммитятся
  детерминированно в порядке `(regionId, seq)`.
- **Per-object RNG** делает эволюцию мира независимой от размера региона и
  числа worker'ов.
- **`WorldStateHasher`** доказывает, что single-thread, parallel и regionized
  прогоны дают одинаковое состояние мира.

Это не «накидать thread pool на Minecraft», а поэтапная модель с явным
владением, детерминированными коммитами и большим набором тестов:
hash-детерминизм, аудит владения регионами, block/random determinism,
explosion self-tests, 60-секундные stress-прогоны, BuildCraft stress,
150-player load test и dedicated-server прогоны.

---

## Возможности

| Область | Что добавлено |
|---|---|
| Регионный тикинг | `RegionManager`, `Region`, `TickContext`, `TickPhase`, `ITickThread`, `ThreadType` |
| Детерминизм | `DeferredMutationQueue`, порядок `(regionId, seq)`, per-object `ThreadAwareRandom` |
| Параллельные измерения | `MinecraftServer.tickWorldsParallel`, `parallelDimensions` |
| Async compute | `PathfindingExecutor`, `AsyncTerrainGenerator`, `LightingExecutor` (16 shards) |
| Клиент | `ClientSimulationThread`, `ClientRenderSnapshot`, `MainThreadDispatcher`, `RenderPipeline`, LOD renderer |
| Параллельная загрузка | `LoadingExecutor`, fan-out/fan-in для mod discovery, recipes, models |
| Модели/текстуры | параллельное запекание моделей, параллельный decode текстур, ленивые буферы |
| Сеть | per-player ordered PLAY serialization, `InboundPacketPipeline`, affinity/seq |
| Автотюнинг | `ThreadingAutopilot`: каждые 200 тиков пробует `regionSizeChunks = 8/16/32`, принимает улучшение > 3% |
| Телеметрия | `/forgethreading`, F3 overlay, phase timings, pool stats, `logs/forgethreading-*.txt` |
| Память | `MemoryLeakGuard`, `CapabilityLeakTracker`, opt-in `ModReferencePurger`, `/forgemem` |
| Перф-фиксы | убран STW `System.gc()`, ленивые VBO, allocation-free terrain setup, бюджеты chunk I/O, `BackgroundPacer` |

---

## Производительность

Замеры на референсной машине (Windows, 6 ГБ heap, render distance 12,
TerrainWorld, `preset=auto`, 6 модов включая IC2 2.8.222 и JEI):

| Метрика | До | После |
|---|---:|---:|
| MSPT peak при входе в мир | **806 мс** | **147 мс** |
| Frame peak при входе | **1045 мс** | **236 мс** |
| Steady-state MSPT | — | **11–12.5 мс** avg, **17–48 мс** max |
| Steady-state frame | — | **~5 мс** avg, **9–24 мс** max |
| FPS | — | **160–223** |
| TPS | — | **20.00** |
| Entities / tick | — | **484–485** |
| Tile entities / tick | — | **85** |
| Chunks / tick (block ticks) | — | **220** |
| Утечки миров | — | **0** |
| Предупреждения memory audit | — | **0** |

Параллельная загрузка:

- `Scanned 10 mod candidates in 582 ms using the load pool`
- `Loading 287 blockstate model groups on 8 threads` → **715 мс**
- `Loading item models for 588 items on 8 threads` → **292 мс**
- `Decoding 2231 sprites on 16 threads`
- `Baking 4132 models on 16 threads`
- Dedicated server `Done (2.477s)`

> Цифры зависят от железа, модпака, размера мира и render distance.
> Бенчмаркайте свой сервер.

---

## Быстрый старт для владельцев серверов

### 1. Собрать патченный jar

Репозиторий **не содержит** исходники Minecraft/Forge и готовый jar. Нужно
взять чистый Forge 14.23.5.2860 MCP `stable_39` source tree и JDK.

```powershell
.\scripts\apply-patches.ps1 -Target C:\path\to\forge\source
.\scripts\compile.ps1 -SourceRoot C:\path\to\forge\source -Libs C:\path\to\libs
.\scripts\build-jar.ps1 -SourceRoot C:\path\to\forge\source -OutJar .\forge-1.12.2-multithreaded.jar
```

Подробно: [docs/BUILDING.md](docs/BUILDING.md).

### 2. Начать с безопасного пресета

```text
-Dforge.threading.preset=balanced
-Xms4G -Xmx6G -XX:+UseG1GC -XX:MaxGCPauseMillis=50
```

| Preset | `parallelDimensions` | `regions` | `regionSizeChunks` | `regionBlockTicks` | `asyncPathfinding` |
|---|---:|---:|---:|---|---|
| `safe` | 1 | 0 | 16 | false | false |
| `balanced` | 2 | 2 | 16 | false | true |
| `aggressive` | 2 | 2 | 8 | true | true |
| `auto` (default) | 4 | 4 | 8 + autopilot | true | true |

Начните с `balanced`. Если модпак стабилен — пробуйте `auto` или `aggressive`.
При проблемах совместимости — `safe` или `regions=0` + `regionBlockTicks=false`.

### 3. Рекомендуемые JVM-флаги

```text
-Xms4G -Xmx6G
-XX:+UseG1GC
-XX:MaxGCPauseMillis=50
-Dforge.threading.preset=balanced
-Dforge.threading.metrics=true
-Dforge.threading.overlay=true
-Dforge.memory.watchdog=true
```

### 4. Команды

| Команда | Назначение |
|---|---|
| `/forgethreading status` | TPS, MSPT, пулы, счётчики, фазы |
| `/forgethreading overlay on\|off` | F3 overlay |
| `/forgethreading handlers` | аудит packet handlers |
| `/forgethreading dump` | `logs/forgethreading-<timestamp>.txt` |
| `/forgethreading reset` | сброс счётчиков |
| `/forgepregen` | прегенерация чанков |
| `/forgemem status` | heap, loaded/leaked worlds, capability owners |
| `/forgemem purge` | запустить leak audit |
| `/forgemem gc` | диагностический GC |
| `/forgemem aggressive on\|off` | opt-in чистка static mod references |
| `/forgemem capability on\|off` | capability tracking |
| `/forgemem dump` | dump memory audit |

Все команды требуют permission level 2.

---

## Конфигурация

Полный список: [docs/CONFIGURATION.md](docs/CONFIGURATION.md).

Ключевые свойства:

```text
-Dforge.threading.preset=safe|balanced|aggressive
-Dforge.threading.singleThreaded=true|false
-Dforge.threading.parallelDimensions=N
-Dforge.threading.regions=N
-Dforge.threading.regionSizeChunks=N
-Dforge.threading.regionBlockTicks=true|false
-Dforge.threading.regionAffinity=true|false
-Dforge.threading.asyncPathfinding=true|false
-Dforge.threading.pathfindingThreads=N
-Dforge.threading.simulationDistance=N
-Dforge.threading.parallelModelBaking=true|false
-Dforge.threading.modelBakingThreads=N
-Dforge.threading.parallelTextureDecoding=true|false
-Dforge.threading.textureDecodingThreads=N
-Dforge.threading.parallelBlockUpdates=true|false
-Dforge.threading.parallelLighting=true|false
-Dforge.threading.packetSerialization=true|false
-Dforge.threading.parallelLoading=true|false
-Dforge.threading.loadThreads=N
-Dforge.threading.parallelModDiscovery=true|false
-Dforge.threading.parallelRecipeLoading=true|false
-Dforge.threading.parallelModelLoading=true|false
-Dforge.threading.autopilot=true|false
-Dforge.threading.autopilotWindow=200
-Dforge.threading.autopilotThreshold=0.03
-Dforge.threading.metrics=true|false
-Dforge.threading.audit=true|false
-Dforge.threading.overlay=true|false
-Dforge.threading.clientSimThread=true|false
-Dforge.threading.clientSimEntities=true|false
-Dforge.threading.hashTicks=N
-Dforge.threading.selftest=true|false
-Dforge.threading.explosionSelftest=true|false
```

Memory guard:

```text
-Dforge.memory.watchdog=true
-Dforge.memory.auditIntervalTicks=1200
-Dforge.memory.heapWarnPercent=88
-Dforge.memory.verboseAudit=false
-Dforge.memory.aggressivePurge=false
-Dforge.memory.capabilityAudit=true
-Dforge.memory.capabilityTrackLimit=200000
-Dforge.memory.unloadGraceMs=30000
-Dforge.memory.maxScannedClasses=200000
-Dforge.memory.maxScannedEntries=5000000
```

---

## Архитектура

```text
                         ┌──────────────────────────────┐
                         │          Main Thread          │
                         │ players / network / chunk map │
                         │ world border / spawning /     │
                         │ registries / commands         │
                         └───────────────┬──────────────┘
                                         │
                         DeferredMutationQueue (regionId, seq)
                                         │
        ┌────────────────────────────────┼────────────────────────────────┐
        ▼                                ▼                                ▼
  Region 0..N                     Dimension 0..M                  Async pools
  entities / tile entities        world tick                       pathfinding
  blocks / projectiles            (parallelDimensions)             lighting
  whitelisted work                                                  terrain
                                                                    loading
                                                                    model/texture
```

- **Main thread:** игроки, `PlayerChunkMap`, трекеры, сеть, `WorldBorder`,
  спавн, реестры, команды, загрузка/выгрузка миров.
- **Region threads:** whitelisted entity/TE/block/projectile ticking для
  непересекающихся регионов. Cross-region записи ставятся в очередь.
- **Детерминизм:** `ThreadAwareRandom` использует per-object ключ (UUID
  сущности, позиция TE, `ChunkPos`), а не `regionId`. `RegionManager` сортирует
  чанки по `(x, z)`. `WorldStateHasher` сравнивает single/parallel/regions.
- **Клиент:** GL-контекст остаётся на main thread; подготовка рендера,
  симуляция сущностей, decode текстур и запекание моделей — offload с
  ограниченными очередями и handoff на main thread.
- **Память:** на `WorldEvent.Unload` `MemoryLeakGuard` рвёт известные сильные
  ссылки (region ownership, chunk-I/O queue, forced-chunk tickets, farmland,
  client simulation, pregen, IC2 explosion sync, capability owners). Opt-in
  reflective purger удаляет static-ссылки модов на выгруженный мир.

Подробно: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Совместимость

Проверено:

- **Forge 14.23.5.2860**, MCP `stable_39`, Minecraft 1.12.2.
- **IC2 2.8.222-ex112** (включая explosion sync hooks).
- **JEI** (recipe sync).
- **BuildCraft** stress-прогоны.
- Dedicated и integrated server.
- 6-модовый пак, 150-player load test.

Ограничения:

- Моды, которые ожидают, что все TE/сущности тикаются на main thread, могут
  потребовать `-Dforge.threading.regions=0` или
  `-Dforge.threading.regionBlockTicks=false`.
- Моды с глобальным mutable world state могут потребовать `preset=safe` или
  compatibility patch.
- Клиентский рендер всё ещё single-GL-thread; offload только подготовка.
- Форк основан на конкретном срезе Forge. Патч на другую версию/сильно
  патченное дерево не наложится без rebase.
- В некоторых окружениях возможен краш `OpenAL64.dll` при инициализации звука
  с `preset=balanced`. Это нативный аудио-баг, не threading; используйте
  dedicated server для проверки.

Подробно: [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

---

## Тесты и бенчмарки

- **Детерминизм:** `WorldStateHasher` single vs parallel vs regions; block/random
  determinism; topology checks; region ownership audits.
- **Stress:** 60-секундные прогоны, BuildCraft stress, 150-player load test,
  dedicated server.
- **Self-tests:** `ThreadingSelfTest`, `ExplosionSelfTest`, IC2 explosion sync.
- **Память:** циклы unload/reload с `leakedWorlds=0`, `warnings=0`.
- **Рендер:** F3 overlay, phase breakdown screenshots, chunk compile/upload
  counters.

Воспроизведение: [docs/TESTING.md](docs/TESTING.md) и
[benchmarks/RESULTS.md](benchmarks/RESULTS.md).

---

## Известные проблемы и roadmap

Остаточные пики:

- Первый клиентский кадр после входа в мир: **~236 мс**
  (`SPacketJoinGame` ~63–101 мс + первый render).
- Один тик **~117 мс** при смене `regionSizeChunks` автопилотом под нагрузкой.

Roadmap:

- Прогреть `ViewFrustum`/шейдеры до показа `GuiDownloadTerrain` или отложить
  первый рендер мира.
- Не менять `regionSizeChunks` на первом окне/под нагрузкой.
- Расширить regionized mutation coverage на больше mod API.
- Добавить compatibility shims для конкретных модов.
- Улучшить client render-thread separation без затрагивания GL-контекста.
- Добавить CI для patch-application и воспроизводимых бенчмарков.

Подробно: [docs/ROADMAP.md](docs/ROADMAP.md).

---

## Структура репозитория

```text
src/main/java/                         Fork-only Java sources
  net/minecraftforge/common/threading/ Regionized ticking, async pools, telemetry
  net/minecraftforge/common/memory/    Leak guard, capability audit, purger
  net/minecraft/client/renderer/       RenderPipeline, LodRenderer
patches/                               Unified patch against Forge 14.23.5.2860
scripts/                               Apply, assemble, compile, build, run
docs/                                  Architecture, config, performance, testing
benchmarks/                            Benchmark summaries
```

---

## Сборка из исходников

Полно: [docs/BUILDING.md](docs/BUILDING.md).

Кратко:

1. Взять чистый Forge 14.23.5.2860 MCP `stable_39` source tree.
2. `git apply patches/forge-1.12.2-multithreaded.patch`.
3. Скомпилировать JDK 21 с `--release 8` и Forge/Minecraft libraries.
4. Стейджить классы + assets и собрать jar.
5. Запустить через шаблоны client/server.

Оригинальная сборка использовала:

- JDK 21.0.11 (`javac --release 8 -proc:none -encoding UTF-8`).
- `libs` с Forge/Minecraft dependency jars.
- `GradleStart` / `GradleStartServer`.
- `-Xms2G -Xmx6G -XX:+UseG1GC -XX:MaxGCPauseMillis=50`.

---

## Контрибуция

1. Java 8 source compatibility.
2. Никаких hard dependencies на client-only или mod-specific классы.
3. Сохранять публичные API `net.minecraftforge.common.threading` и `memory`.
4. Перед PR прогонять determinism и stress tests.
5. Для perf-PR прикладывать before/after `/forgethreading dump`.

См. [CONTRIBUTING.md](CONTRIBUTING.md).

---

## Лицензия и кредиты

- Код форка: **LGPL-2.1** (см. [LICENSE](LICENSE)), как и MinecraftForge.
- Minecraft/Forge/Mojang/Microsoft trademarks и copyright принадлежат их
  владельцам.
- Репозиторий не распространяет исходники Minecraft, assets или binaries.
- Патч содержит только изменения; базовый source tree нужно получить легально.

Кредиты: MinecraftForge team, Mojang Studios/Microsoft, IC2 и JEI authors,
всем, кто присылал логи, бенчмарки и compatibility reports.

---

## Дисклеймер

Неофициальный экспериментальный форк. Используйте на свой риск. Делайте бэкап
миров и тестируйте на staging-сервере. Авторы не отвечают за потерю данных,
краши, баны или нестабильность сервера.
