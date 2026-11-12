# Builds a release APK signed with AndroidKeys\updropapp and copies it to ..\v<version>\.
# Asks for the keystore password; it is only kept in this PowerShell process, never written to disk.
# Usage:  .\release.ps1            (version taken from app\build.gradle.kts)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$version = (Select-String -Path 'app\build.gradle.kts' -Pattern 'versionName = "([^"]+)"').Matches[0].Groups[1].Value
$key = 'D:\RandD\Android Apps\AndroidKeys\updropapp'
if (-not (Test-Path $key)) { throw "Keystore not found: $key" }

$secure = Read-Host "Password for updropapp keystore" -AsSecureString
$plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))

$env:JAVA_HOME = "$env:USERPROFILE\.jdks\jbr-21.0.11"
$env:UPDROP_KEYSTORE_FILE = $key
$env:UPDROP_KEYSTORE_PASSWORD = $plain
$env:UPDROP_KEY_ALIAS = 'updropapp'
$env:UPDROP_KEY_PASSWORD = $plain   # PKCS12: key password = store password
try {
    & .\gradlew.bat assembleRelease --offline
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }
} finally {
    Remove-Item Env:UPDROP_KEYSTORE_PASSWORD, Env:UPDROP_KEY_PASSWORD -ErrorAction SilentlyContinue
}

$out = Join-Path (Split-Path $PSScriptRoot) "v$version"
New-Item -ItemType Directory -Force $out | Out-Null
Copy-Item 'app\build\outputs\apk\release\app-release.apk' (Join-Path $out "Updrop-$version.apk") -Force
$bat = "@echo off`r`nrem Installs Updrop on the phone connected by USB (USB debugging on).`r`nset ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`r`nif not exist `"%ADB%`" set ADB=adb`r`n`"%ADB%`" install -r `"%~dp0Updrop-$version.apk`"`r`npause`r`n"
Set-Content -Path (Join-Path $out 'install.bat') -Value $bat -Encoding ascii -NoNewline
$bt = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" -Directory | Sort-Object Name | Select-Object -Last 1
& (Join-Path $bt.FullName 'apksigner.bat') verify --print-certs (Join-Path $out "Updrop-$version.apk") | Select-String 'SHA-256'   # must start 400e7f2e
Write-Host "Done: $out\Updrop-$version.apk" -ForegroundColor Green
