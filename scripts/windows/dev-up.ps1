# Starts Caliper local infrastructure (PostgreSQL container) on Windows.
# Run: .\scripts\windows\dev-up.ps1
[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path "$scriptDir\..\..").Path
Set-Location $repoRoot

$envFile = "$repoRoot\.env"
$envExample = "$repoRoot\.env.example"

if (-not (Test-Path $envFile)) {
    Write-Host "No .env found; copying from .env.example..." -ForegroundColor Yellow
    Copy-Item $envExample $envFile

    # Generate random passwords using .NET cryptographic RNG
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $pwdBytes = New-Object byte[] 16
    $rng.GetBytes($pwdBytes)
    $pgPass = -join ($pwdBytes | ForEach-Object { "{0:x2}" -f $_ })

    $jwtBytes = New-Object byte[] 36
    $rng.GetBytes($jwtBytes)
    $jwtSecret = [System.Convert]::ToBase64String($jwtBytes)

    (Get-Content $envFile) `
        -replace '^POSTGRES_PASSWORD=.*', "POSTGRES_PASSWORD=$pgPass" `
        -replace '^JWT_SECRET=.*', "JWT_SECRET=$jwtSecret" |
        Set-Content $envFile
    Write-Host "Generated random POSTGRES_PASSWORD and JWT_SECRET in .env." -ForegroundColor Green
}

# Start PostgreSQL container
Write-Host "Starting PostgreSQL container via Docker Compose..." -ForegroundColor Cyan
& docker compose up -d
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to start PostgreSQL container."
    exit $LASTEXITCODE
}

Write-Host "Waiting for database to report healthy..." -ForegroundColor Cyan
$retries = 15
while ($retries -gt 0) {
    $health = (& docker inspect --format '{{.State.Health.Status}}' codesentinel-postgres 2>$null)
    if ($health -eq "healthy") {
        Write-Host "PostgreSQL is healthy on 127.0.0.1:5432!" -ForegroundColor Green
        break
    }
    Start-Sleep -Seconds 2
    $retries--
}

Write-Host "`nLocal environment is UP. Next steps:" -ForegroundColor Green
Write-Host "  To build & start backend:   .\scripts\windows\start-backend.ps1" -ForegroundColor Cyan
Write-Host "  To start frontend:          .\scripts\windows\start-frontend.ps1" -ForegroundColor Cyan
