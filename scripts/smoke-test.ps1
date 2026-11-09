[CmdletBinding()]
param(
    [string]$RepoRoot = ""
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $RepoRoot) {
    $RepoRoot = Join-Path $scriptDir ".."
}

$patch = [IO.Path]::Combine($RepoRoot, "patches", "forge-1.12.2-multithreaded.patch")
if (-not (Test-Path -LiteralPath $patch)) {
    throw "Patch missing: $patch"
}
if ((Get-Item -LiteralPath $patch).Length -lt 1000) {
    throw "Patch looks too small: $patch"
}

$required = @(
    "net/minecraftforge/common/threading/Threading.java",
    "net/minecraftforge/common/threading/RegionManager.java",
    "net/minecraftforge/common/threading/DeferredMutationQueue.java",
    "net/minecraftforge/common/threading/WorldStateHasher.java",
    "net/minecraftforge/common/threading/ClassCache.java",
    "net/minecraftforge/common/threading/WarmBoot.java",
    "net/minecraftforge/common/threading/StatsOverlay.java",
    "net/minecraftforge/common/threading/PerformanceGovernor.java",
    "net/minecraftforge/common/threading/MemoryTrim.java",
    "net/minecraftforge/common/memory/MemoryLeakGuard.java",
    "net/minecraftforge/common/memory/CapabilityLeakTracker.java",
    "net/minecraft/client/renderer/RenderPipeline.java",
    "net/minecraft/client/renderer/LodRenderer.java"
)

$text = Get-Content -LiteralPath $patch -Raw
foreach ($needle in $required) {
    if ($text.IndexOf($needle, [System.StringComparison]::Ordinal) -lt 0) {
        throw "Patch does not contain expected path: $needle"
    }
}

$javaDir = [IO.Path]::Combine($RepoRoot, "src", "main", "java")
$javaCount = (Get-ChildItem -LiteralPath $javaDir -Recurse -Filter "*.java" | Measure-Object).Count
if ($javaCount -lt 30) {
    throw "Expected at least 30 fork Java sources, found $javaCount"
}

Write-Host "Smoke test OK."
Write-Host "Patch: $patch"
Write-Host "Fork Java sources: $javaCount"
