# Shared toolchain resolution for build.ps1 and install.ps1.
#
# BroTimer does not vendor its own JDK / Android SDK / Gradle (~873 MB). It borrows the one that
# already exists in the BroMic project on this machine and is proven to build a Kotlin + Compose
# APK here. Override with $env:BROTIMER_TOOLS if that folder ever moves.

function Resolve-BroTimerTools {
    param([string]$Root)

    $candidates = @()
    if ($env:BROTIMER_TOOLS) { $candidates += $env:BROTIMER_TOOLS }
    $candidates += (Join-Path $Root '.tools')
    $candidates += (Join-Path $Root '..\..\bro mic\.tools')
    $candidates += (Join-Path $HOME 'Desktop\bro mic\.tools')

    foreach ($c in $candidates) {
        if (-not $c) { continue }
        $full = [System.IO.Path]::GetFullPath($c)
        if ((Test-Path (Join-Path $full 'jdk\bin\java.exe')) -and
            (Test-Path (Join-Path $full 'android_sdk')) -and
            (Test-Path (Join-Path $full 'gradle\bin\gradle.bat'))) {
            return $full
        }
    }

    Write-Host ""
    Write-Host "Could not find the Android toolchain (JDK + Android SDK + Gradle)." -ForegroundColor Red
    Write-Host "Looked in:" -ForegroundColor Red
    foreach ($c in $candidates) {
        if ($c) { Write-Host ("  " + [System.IO.Path]::GetFullPath($c)) -ForegroundColor DarkGray }
    }
    Write-Host ""
    Write-Host 'Fix: point BROTIMER_TOOLS at the folder that contains jdk\, android_sdk\ and gradle\:' -ForegroundColor Yellow
    Write-Host '  $env:BROTIMER_TOOLS = "C:\path\to\.tools"' -ForegroundColor Yellow
    Write-Host ""
    exit 1
}

function Initialize-BuildEnvironment {
    param([string]$Root)

    $tools = Resolve-BroTimerTools -Root $Root
    Write-Host "Toolchain: $tools" -ForegroundColor DarkGray

    $env:JAVA_HOME = Join-Path $tools 'jdk'
    $env:ANDROID_HOME = Join-Path $tools 'android_sdk'
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
    $env:PATH = (Join-Path $tools 'gradle\bin') + ';' + $env:PATH

    # Gradle's daemon builds a NIO Selector from an AF_UNIX socket pair placed in %TMP%. On this
    # machine AF_UNIX connect() fails with EINVAL for any path under
    # C:\Users\<user>\AppData\Local, so the daemon dies with "Unable to establish loopback
    # connection" before the build even starts. Pointing TMP inside the repo avoids it.
    # (Same workaround as bro mic\build_android.ps1 - do not remove.)
    $env:TMP = Join-Path $Root '.gradle-tmp'
    $env:TEMP = $env:TMP
    if (-not (Test-Path $env:TMP)) { New-Item -ItemType Directory -Path $env:TMP -Force | Out-Null }

    return $tools
}

function Get-Adb {
    param([string]$Tools)

    $bundled = Join-Path $Tools 'android_sdk\platform-tools\adb.exe'
    if (Test-Path $bundled) { return $bundled }

    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    Write-Host "adb not found (looked in the SDK's platform-tools and on PATH)." -ForegroundColor Red
    exit 1
}
