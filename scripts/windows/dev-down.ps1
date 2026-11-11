# Stops Caliper local infrastructure (PostgreSQL container) on Windows.
# Run: .\scripts\windows\dev-down.ps1
[CmdletBinding()]
param(
    [switch]$Volumes = $false
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path "$scriptDir\..\..").Path
Set-Location $repoRoot

if ($Volumes) {
    Write-Host "Stopping containers and removing database volumes..." -ForegroundColor Yellow
    & docker compose down -v
} else {
    Write-Host "Stopping PostgreSQL container..." -ForegroundColor Cyan
    & docker compose down
}

Write-Host "Caliper local infrastructure stopped." -ForegroundColor Green
