# Builds and installs BroTimer on the phone connected over USB.
#
#   .\install.ps1              build, then install over the existing app
#   .\install.ps1 -Fresh       uninstall first (wipes saved alarms), then install
#   .\install.ps1 -SkipBuild   install whatever APK is already built
#   .\install.ps1 -Launch      also open the app on the phone afterwards

param(
    [switch]$Fresh,
    [switch]$SkipBuild,
    [switch]$Launch
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'tools.ps1')

$tools = Initialize-BuildEnvironment -Root $PSScriptRoot
$adb = Get-Adb -Tools $tools

$devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' }
if (-not $devices) {
    Write-Host "No phone connected." -ForegroundColor Red
    Write-Host "Plug it in over USB, unlock it, and make sure USB debugging is on." -ForegroundColor Yellow
    Write-Host "Then check with: adb devices" -ForegroundColor Yellow
    exit 1
}
Write-Host ("Device: " + ($devices -join ', ')) -ForegroundColor DarkGray

if (-not $SkipBuild) {
    & (Join-Path $PSScriptRoot 'build.ps1')
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) {
    Write-Host "No APK at $apk - run .\build.ps1 first." -ForegroundColor Red
    exit 1
}

if ($Fresh) {
    Write-Host "Uninstalling the old copy (this deletes saved alarms)..." -ForegroundColor Yellow
    & $adb uninstall com.brotimer | Out-Null
}

Write-Host "Installing..." -ForegroundColor Cyan
$log = & $adb install -r $apk 2>&1
$log | ForEach-Object { Write-Host $_ }

if ($LASTEXITCODE -ne 0) {
    # HyperOS blocks the normal streamed install with INSTALL_FAILED_USER_RESTRICTED unless
    # "Install via USB" is on in Developer options - and it turns itself off again. Pushing the
    # APK and installing it from the device's own shell is not subject to that check, so try it
    # before giving up. (Measured on this phone 2026-09-04: streamed install refused, this worked.)
    if ($log -match 'USER_RESTRICTED') {
        Write-Host ""
        Write-Host "HyperOS refused the direct install. Retrying via the device shell..." -ForegroundColor Yellow
        & $adb push $apk /data/local/tmp/brotimer.apk | Out-Null
        & $adb shell pm install -r -t /data/local/tmp/brotimer.apk
        $ok = ($LASTEXITCODE -eq 0)
        & $adb shell rm -f /data/local/tmp/brotimer.apk | Out-Null
        if (-not $ok) {
            Write-Host ""
            Write-Host "Still refused. Turn ON Developer options -> Install via USB, then retry." -ForegroundColor Yellow
            exit 1
        }
    }
    elseif ($log -match 'UPDATE_INCOMPATIBLE') {
        Write-Host ""
        Write-Host "Signature mismatch with the installed copy. Run:" -ForegroundColor Yellow
        Write-Host "  .\install.ps1 -Fresh" -ForegroundColor Yellow
        exit 1
    }
    else {
        exit $LASTEXITCODE
    }
}

if ($Launch) {
    & $adb shell monkey -p com.brotimer -c android.intent.category.LAUNCHER 1 | Out-Null
}

Write-Host ""
Write-Host "Installed. Open BroTimer and work through the Setup tab (the gear icon)." -ForegroundColor Green
Write-Host "On this Xiaomi phone, Autostart and 'Display pop-up windows while running in" -ForegroundColor Yellow
Write-Host "background' must be switched on by hand or alarms will not appear." -ForegroundColor Yellow
