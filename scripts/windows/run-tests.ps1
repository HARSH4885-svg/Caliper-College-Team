# Executes all automated tests (backend, engine, judge, and frontend) on Windows.
# Run: .\scripts\windows\run-tests.ps1
[CmdletBinding()]
param(
    [switch]$BackendOnly = $false,
    [switch]$FrontendOnly = $false
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path "$scriptDir\..\..").Path
Set-Location $repoRoot

$envFile = "$repoRoot\.env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $idx = $line.IndexOf("=")
            $key = $line.Substring(0, $idx).Trim()
            $val = $line.Substring($idx + 1).Trim()
            if ($val.StartsWith('"') -and $val.EndsWith('"')) { $val = $val.Substring(1, $val.Length - 2) }
            elseif ($val.StartsWith("'") -and $val.EndsWith("'")) { $val = $val.Substring(1, $val.Length - 2) }
            [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
        }
    }
}

if (-not $FrontendOnly) {
    Write-Host "`n=======================================================" -ForegroundColor Cyan
    Write-Host "   Running Java & Docker Tests (Engine, Judge, Backend)" -ForegroundColor Cyan
    Write-Host "=======================================================`n" -ForegroundColor Cyan
    & mvn test
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Maven tests failed."
        exit $LASTEXITCODE
    }
    Write-Host "`nAll Java module tests passed!" -ForegroundColor Green
}

if (-not $BackendOnly) {
    Write-Host "`n=======================================================" -ForegroundColor Cyan
    Write-Host "   Running Frontend Vitest Suite                       " -ForegroundColor Cyan
    Write-Host "=======================================================`n" -ForegroundColor Cyan
    Set-Location "$repoRoot\frontend"
    & npm test
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Frontend tests failed."
        exit $LASTEXITCODE
    }
    Write-Host "`nAll frontend Vitest tests passed!" -ForegroundColor Green
}

Write-Host "`nAll test suites completed successfully!" -ForegroundColor Green
