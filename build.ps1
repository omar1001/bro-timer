# Builds the debug APK.
#
#   .\build.ps1            normal build
#   .\build.ps1 -Clean     wipe app\build first

param([switch]$Clean)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'tools.ps1')

Initialize-BuildEnvironment -Root $PSScriptRoot | Out-Null

Set-Location $PSScriptRoot

if ($Clean) {
    Write-Host "Cleaning..." -ForegroundColor Cyan
    & gradle clean
}

Write-Host "Building debug APK..." -ForegroundColor Cyan
& gradle assembleDebug

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build FAILED (gradle exit $LASTEXITCODE)." -ForegroundColor Red
    exit $LASTEXITCODE
}

$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) {
    Write-Host "Gradle reported success but the APK is missing: $apk" -ForegroundColor Red
    exit 1
}

$info = Get-Item $apk
Write-Host ""
Write-Host "APK: $apk" -ForegroundColor Green
Write-Host ("Size: {0:N2} MB   Built: {1}" -f ($info.Length / 1MB), $info.LastWriteTime) -ForegroundColor Green
