# Builds the four isolated Linux runner images used by Caliper on Windows via Docker Desktop.
# Run: .\scripts\windows\build-sandbox-images.ps1
[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path "$scriptDir\..\..").Path
Set-Location $repoRoot

Write-Host "Verifying Docker daemon connectivity..." -ForegroundColor Cyan
& docker info --format '{{.OSType}}' | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker is not running. Please start Docker Desktop and ensure it is in Linux container mode."
    exit 1
}

function Build-Image {
    param([string]$Tag, [string]$DockerfileName)
    Write-Host "`n===> Building $Tag from sandbox/Dockerfiles/$DockerfileName..." -ForegroundColor Cyan
    $dockerfile = "$repoRoot\sandbox\Dockerfiles\$DockerfileName"
    $context = "$repoRoot\sandbox\Dockerfiles"
    & docker build -q -t $Tag -f $dockerfile $context
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed building Docker image: $Tag"
        exit $LASTEXITCODE
    }
}

Build-Image "codesentinel/runner-native:1" "native.Dockerfile"
Build-Image "codesentinel/runner-java:1"   "java.Dockerfile"
Build-Image "codesentinel/runner-python:1" "python.Dockerfile"
Build-Image "codesentinel/runner-node:1"   "node.Dockerfile"

Write-Host "`nAll Caliper runner images built successfully:" -ForegroundColor Green
& docker images --format '  {{.Repository}}:{{.Tag}}  {{.Size}}' | Select-String "codesentinel/runner"
