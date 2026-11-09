[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$VanillaSource,

    [Parameter(Mandatory = $true)]
    [string]$ForgeOverlay,

    [Parameter(Mandatory = $true)]
    [string]$Out,

    [string]$ForgePatches,

    [switch]$KeepExisting
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $VanillaSource)) {
    throw "Vanilla source not found: $VanillaSource"
}
if (-not (Test-Path -LiteralPath $ForgeOverlay)) {
    throw "Forge overlay not found: $ForgeOverlay"
}

if ((Test-Path -LiteralPath $Out) -and -not $KeepExisting) {
    Remove-Item -LiteralPath $Out -Recurse -Force
}
New-Item -ItemType Directory -Path $Out -Force | Out-Null

Write-Host "Copying base source..."
Copy-Item -Path (Join-Path $VanillaSource "*") -Destination $Out -Recurse -Force

Write-Host "Applying Forge Java overlay..."
Copy-Item -Path (Join-Path $ForgeOverlay "*") -Destination $Out -Recurse -Force

if ($ForgePatches -and (Test-Path -LiteralPath $ForgePatches)) {
    $patchOut = Join-Path $Out "_forge_patches"
    New-Item -ItemType Directory -Path $patchOut -Force | Out-Null
    Write-Host "Copying Forge patch reference..."
    Copy-Item -Path (Join-Path $ForgePatches "*") -Destination $patchOut -Recurse -Force
}

$javaCount = (Get-ChildItem -LiteralPath $Out -Recurse -Filter "*.java" | Measure-Object).Count
Write-Host "Baseline ready: $Out"
Write-Host "Java files: $javaCount"
