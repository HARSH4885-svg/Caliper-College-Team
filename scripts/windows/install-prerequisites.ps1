# Caliper Prerequisites Installer Helper for Windows (PowerShell)
# Run in an elevated PowerShell terminal (Run as Administrator) if installing packages
[CmdletBinding()]
param(
    [switch]$AutoInstall = $false
)

Write-Host "`n=======================================================" -ForegroundColor Cyan
Write-Host "   Caliper Prerequisites Setup Guide (Windows)         " -ForegroundColor Cyan
Write-Host "=======================================================`n" -ForegroundColor Cyan

$commands = @(
    @{ Name = "WSL 2 Backend"; Command = "wsl --install --no-launch"; Note = "Enables Linux container virtualization" },
    @{ Name = "Docker Desktop"; Command = "winget install -e --id Docker.DockerDesktop"; Note = "Container runtime for code evaluation" },
    @{ Name = "Java 21 JDK"; Command = "winget install -e --id Microsoft.OpenJDK.21"; Note = "Backend runtime" },
    @{ Name = "Apache Maven"; Command = "winget install -e --id Apache.Maven"; Note = "Build tool for backend & engine modules" },
    @{ Name = "Node.js 20+ LTS"; Command = "winget install -e --id OpenJS.NodeJS.LTS"; Note = "Frontend build tool and compiler" },
    @{ Name = "Git for Windows"; Command = "winget install -e --id Git.Git"; Note = "Source control" }
)

if (-not $AutoInstall) {
    Write-Host "You can install all prerequisites using Windows Package Manager (winget):`n" -ForegroundColor White
    foreach ($item in $commands) {
        Write-Host "[$($item.Name)]" -ForegroundColor Green -NoNewline
        Write-Host " - $($item.Note)" -ForegroundColor Gray
        Write-Host "  > $($item.Command)`n" -ForegroundColor Yellow
    }
    Write-Host "To install automatically, re-run this script with -AutoInstall:" -ForegroundColor White
    Write-Host "  .\scripts\windows\install-prerequisites.ps1 -AutoInstall`n" -ForegroundColor Cyan
} else {
    Write-Host "Beginning automated installation via winget...`n" -ForegroundColor Cyan
    foreach ($item in $commands) {
        Write-Host "Installing $($item.Name)..." -ForegroundColor Yellow
        try {
            Invoke-Expression $item.Command
        } catch {
            Write-Host "Failed to install $($item.Name): $_" -ForegroundColor Red
        }
    }
    Write-Host "`nInstallation pass complete. Please restart your terminal/machine to refresh environment PATH." -ForegroundColor Green
}
