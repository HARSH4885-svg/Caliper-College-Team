# Installs dependencies (if needed) and starts the Caliper Vite frontend on Windows.
# Run: .\scripts\windows\start-frontend.ps1
[CmdletBinding()]
param(
    [switch]$Install = $false
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path "$scriptDir\..\..").Path
$frontendDir = "$repoRoot\frontend"

Set-Location $frontendDir

if ($Install -or -not (Test-Path "$frontendDir\node_modules")) {
    Write-Host "Installing frontend npm dependencies..." -ForegroundColor Cyan
    & npm install
    if ($LASTEXITCODE -ne 0) {
        Write-Error "npm install failed."
        exit $LASTEXITCODE
    }
}

Write-Host "`nStarting Vite Frontend Dev Server at http://127.0.0.1:5173..." -ForegroundColor Green
Write-Host "Press Ctrl+C to terminate.`n" -ForegroundColor Gray

& npm run dev
