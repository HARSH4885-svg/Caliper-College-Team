# Builds (if needed) and starts the Caliper Spring Boot backend on Windows.
# Run: .\scripts\windows\start-backend.ps1
[CmdletBinding()]
param(
    [switch]$Build = $false,
    [switch]$NoSeed = $false
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path "$scriptDir\..\..").Path
Set-Location $repoRoot

$envFile = "$repoRoot\.env"
if (-not (Test-Path $envFile)) {
    Write-Host "No .env found. Running dev-up.ps1 first..." -ForegroundColor Yellow
    & "$scriptDir\dev-up.ps1"
}

# Load .env into process environment
Write-Host "Loading environment variables from .env..." -ForegroundColor Cyan
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
        $idx = $line.IndexOf("=")
        $key = $line.Substring(0, $idx).Trim()
        $val = $line.Substring($idx + 1).Trim()
        # Remove surrounding quotes if present
        if ($val.StartsWith('"') -and $val.EndsWith('"')) { $val = $val.Substring(1, $val.Length - 2) }
        elseif ($val.StartsWith("'") -and $val.EndsWith("'")) { $val = $val.Substring(1, $val.Length - 2) }
        [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
    }
}

if (-not $NoSeed) {
    [System.Environment]::SetEnvironmentVariable("SEED_DEMO", "true", "Process")
}

$jarPath = "$repoRoot\backend\target\codesentinel-backend-0.1.0.jar"
if ($Build -or -not (Test-Path $jarPath)) {
    Write-Host "Building Caliper backend with Maven (-DskipTests)..." -ForegroundColor Cyan
    & mvn -B -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Maven package failed."
        exit $LASTEXITCODE
    }
}

Write-Host "`nStarting Caliper Backend on http://127.0.0.1:8080..." -ForegroundColor Green
Write-Host "Demo Instructor: demo-instructor@demo.invalid / demo-password-1" -ForegroundColor Yellow
Write-Host "Press Ctrl+C to terminate.`n" -ForegroundColor Gray

& java -jar $jarPath
