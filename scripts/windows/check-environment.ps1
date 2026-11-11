# Caliper Environment Verification for Windows (PowerShell)
# Run: .\scripts\windows\check-environment.ps1
[CmdletBinding()]
param()

$ErrorActionPreference = "Continue"

Write-Host "`n=======================================================" -ForegroundColor Cyan
Write-Host "   Caliper Environment Diagnostics (Windows 10/11)     " -ForegroundColor Cyan
Write-Host "=======================================================`n" -ForegroundColor Cyan

$allOk = $true

# 1. OS & Architecture
$os = Get-CimInstance Win32_OperatingSystem
Write-Host "Operating System: " -NoNewline
Write-Host "$($os.Caption) ($($os.OSArchitecture), Build $($os.BuildNumber))" -ForegroundColor Green

# 2. PowerShell
Write-Host "PowerShell Version: " -NoNewline
Write-Host "$($PSVersionTable.PSVersion)" -ForegroundColor Green

# 3. Java 21
Write-Host "Checking Java 21 JDK... " -NoNewline
try {
    $javaVer = & java -version 2>&1 | Out-String
    if ($javaVer -match 'version "(21[0-9._]*|2[2-9][0-9._]*)') {
        Write-Host "OK ($($Matches[0]))" -ForegroundColor Green
    } else {
        Write-Host "WARNING: Found Java, but Java 21 LTS is strongly recommended." -ForegroundColor Yellow
        Write-Host $javaVer.Trim() -ForegroundColor Gray
    }
} catch {
    Write-Host "FAILED: Java not found on PATH." -ForegroundColor Red
    Write-Host "  Install OpenJDK 21 via: winget install Microsoft.OpenJDK.21" -ForegroundColor Yellow
    $allOk = $false
}

# 4. Maven
Write-Host "Checking Apache Maven... " -NoNewline
try {
    $mvnVer = & mvn -v 2>&1 | Select-Object -First 1
    Write-Host "OK ($mvnVer)" -ForegroundColor Green
} catch {
    Write-Host "FAILED: Maven not found on PATH." -ForegroundColor Red
    Write-Host "  Install Maven via: winget install Apache.Maven" -ForegroundColor Yellow
    $allOk = $false
}

# 5. Node.js & npm
Write-Host "Checking Node.js & npm... " -NoNewline
try {
    $nodeVer = & node -v 2>&1
    $npmVer = & npm -v 2>&1
    Write-Host "OK (Node $nodeVer, npm $npmVer)" -ForegroundColor Green
} catch {
    Write-Host "FAILED: Node.js or npm not found." -ForegroundColor Red
    Write-Host "  Install Node via: winget install OpenJS.NodeJS.LTS" -ForegroundColor Yellow
    $allOk = $false
}

# 6. Docker Desktop & Container Mode
Write-Host "Checking Docker Desktop... " -NoNewline
try {
    $dockerInfo = & docker info --format '{{.OSType}}' 2>&1
    if ($LASTEXITCODE -eq 0) {
        $osType = $dockerInfo.Trim()
        if ($osType -eq "linux") {
            Write-Host "OK (Docker running in Linux container mode via WSL2)" -ForegroundColor Green
        } else {
            Write-Host "WARNING: Docker is in '$osType' container mode. Caliper requires Linux containers!" -ForegroundColor Yellow
            Write-Host "  Right-click Docker Desktop icon in tray -> 'Switch to Linux containers...'" -ForegroundColor Yellow
            $allOk = $false
        }
    } else {
        Write-Host "FAILED: Docker daemon not responding." -ForegroundColor Red
        Write-Host "  Ensure Docker Desktop is started and WSL 2 backend is enabled." -ForegroundColor Yellow
        $allOk = $false
    }
} catch {
    Write-Host "FAILED: Docker CLI not found." -ForegroundColor Red
    Write-Host "  Install Docker Desktop via: winget install Docker.DockerDesktop" -ForegroundColor Yellow
    $allOk = $false
}

# Summary
Write-Host "`n-------------------------------------------------------" -ForegroundColor Cyan
if ($allOk) {
    Write-Host "All prerequisites verified! You are ready to run Caliper on Windows." -ForegroundColor Green
    Write-Host "Next step: .\scripts\windows\build-sandbox-images.ps1" -ForegroundColor Cyan
} else {
    Write-Host "Some prerequisites are missing or misconfigured." -ForegroundColor Yellow
    Write-Host "Run .\scripts\windows\install-prerequisites.ps1 for setup guidance." -ForegroundColor Yellow
}
Write-Host "-------------------------------------------------------`n" -ForegroundColor Cyan
