[CmdletBinding()]
param(
    [ValidateSet("Status", "Clean", "RunGradle", "Fix")]
    [string]$Action = "Status",

    [switch]$IncludeBuildOutputs,

    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$repoRootResolved = (Resolve-Path -LiteralPath $repoRoot).Path

$safeGradleHome = Join-Path $env:USERPROFILE ".gradle"
$safeAndroidUserHome = Join-Path $env:USERPROFILE ".android"

function Resolve-AndroidSdkRoot {
    $candidates = New-Object System.Collections.Generic.List[string]

    if ($env:ANDROID_SDK_ROOT) { $candidates.Add($env:ANDROID_SDK_ROOT) }
    if ($env:ANDROID_HOME) { $candidates.Add($env:ANDROID_HOME) }

    $localProperties = Join-Path $repoRootResolved "local.properties"
    if (Test-Path -LiteralPath $localProperties) {
        $sdkLine = Select-String -Path $localProperties -Pattern '^sdk\.dir=' -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($sdkLine) {
            $rawValue = $sdkLine.Line.Substring("sdk.dir=".Length)
            $decoded = $rawValue.Replace('\:', ':').Replace('\\', '\')
            if ($decoded) {
                $candidates.Add($decoded)
            }
        }
    }

    $candidates.Add((Join-Path $env:LOCALAPPDATA "Android\Sdk"))

    foreach ($candidate in $candidates) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path -LiteralPath $candidate)) {
            return [System.IO.Path]::GetFullPath($candidate)
        }
    }

    return [System.IO.Path]::GetFullPath((Join-Path $env:LOCALAPPDATA "Android\Sdk"))
}

$safeAndroidSdkRoot = Resolve-AndroidSdkRoot

$suspiciousDirs = @(
    ".android",
    ".android-home",
    ".android-sdk",
    ".android-user-home",
    ".gradle",
    ".gradle-home",
    ".gradle-user",
    ".gradle-user-home",
    ".kotlin-daemon",
    ".kotlin-home",
    ".userhome",
    "build",
    "app\build",
    "app\release"
)

$alwaysCleanDirs = @(
    ".android-home",
    ".android-sdk",
    ".android-user-home",
    ".gradle-home",
    ".gradle-user",
    ".gradle-user-home",
    ".kotlin-daemon",
    ".kotlin-home",
    ".userhome"
)

$gitignoreEntries = @(
    ".android/",
    ".android-home/",
    ".android-sdk/",
    ".android-user-home/",
    ".gradle/",
    ".gradle-home/",
    ".gradle-user/",
    ".gradle-user-home/",
    ".idea/",
    ".kotlin-daemon/",
    ".kotlin-home/",
    ".userhome/",
    "build/",
    "app/build/",
    "app/release/",
    "local.properties",
    "*.apk",
    "*.aab",
    "backup_current_version_*.zip"
)

if ($IncludeBuildOutputs) {
    $cleanTargets = $alwaysCleanDirs + @(".android", ".gradle", "build", "app\build", "app\release")
} else {
    $cleanTargets = $alwaysCleanDirs
}

function Test-IsUnderRepo {
    param([string]$PathValue)

    if ([string]::IsNullOrWhiteSpace($PathValue)) {
        return $false
    }

    try {
        $fullPath = [System.IO.Path]::GetFullPath($PathValue)
    } catch {
        return $false
    }

    return $fullPath.StartsWith($repoRootResolved, [System.StringComparison]::OrdinalIgnoreCase)
}

function Get-DirectorySizeMb {
    param([string]$LiteralPath)

    if (-not (Test-Path -LiteralPath $LiteralPath)) {
        return 0
    }

    $sum = (Get-ChildItem -LiteralPath $LiteralPath -Recurse -File -Force -ErrorAction SilentlyContinue |
        Measure-Object -Property Length -Sum).Sum

    if ($null -eq $sum) {
        return 0
    }

    return [math]::Round($sum / 1MB, 2)
}

function Get-SuspiciousDirectoryReport {
    foreach ($relativePath in $suspiciousDirs) {
        $fullPath = Join-Path $repoRootResolved $relativePath
        if (Test-Path -LiteralPath $fullPath) {
            [PSCustomObject]@{
                Path   = $relativePath
                SizeMB = Get-DirectorySizeMb -LiteralPath $fullPath
            }
        }
    }
}

function Get-EnvironmentReport {
    @(
        [PSCustomObject]@{ Name = "GRADLE_USER_HOME"; Value = $env:GRADLE_USER_HOME },
        [PSCustomObject]@{ Name = "ANDROID_USER_HOME"; Value = $env:ANDROID_USER_HOME },
        [PSCustomObject]@{ Name = "ANDROID_SDK_ROOT"; Value = $env:ANDROID_SDK_ROOT },
        [PSCustomObject]@{ Name = "ANDROID_HOME"; Value = $env:ANDROID_HOME },
        [PSCustomObject]@{ Name = "HOME"; Value = $env:HOME }
    ) | ForEach-Object {
        [PSCustomObject]@{
            Name         = $_.Name
            Value        = if ([string]::IsNullOrWhiteSpace($_.Value)) { "<not set>" } else { $_.Value }
            PointsToRepo = if (Test-IsUnderRepo -PathValue $_.Value) { "YES" } else { "no" }
        }
    }
}

function Show-Status {
    Write-Host ""
    Write-Host "Repo: $repoRootResolved"
    Write-Host ""
    Write-Host "Environment"
    Get-EnvironmentReport | Format-Table -AutoSize

    Write-Host ""
    Write-Host "Recommended defaults"
    [PSCustomObject]@{
        GRADLE_USER_HOME  = $safeGradleHome
        ANDROID_USER_HOME = $safeAndroidUserHome
        ANDROID_SDK_ROOT  = $safeAndroidSdkRoot
    } | Format-List

    $report = @(Get-SuspiciousDirectoryReport | Sort-Object SizeMB -Descending)
    Write-Host ""
    Write-Host "Directories in repo that usually should not grow unchecked"

    if ($report.Count -eq 0) {
        Write-Host "  none"
    } else {
        $report | Format-Table -AutoSize
    }
}

function Remove-TrackedPaths {
    param([string[]]$RelativePaths)

    foreach ($relativePath in $RelativePaths) {
        $fullPath = Join-Path $repoRootResolved $relativePath
        if (Test-Path -LiteralPath $fullPath) {
            $sizeMb = Get-DirectorySizeMb -LiteralPath $fullPath
            Remove-Item -LiteralPath $fullPath -Recurse -Force
            Write-Host ("removed {0} ({1} MB)" -f $relativePath, $sizeMb)
        } else {
            Write-Host ("missing {0}" -f $relativePath)
        }
    }
}

function Ensure-GitignoreEntries {
    $gitignorePath = Join-Path $repoRootResolved ".gitignore"
    $existing = @()

    if (Test-Path -LiteralPath $gitignorePath) {
        $existing = Get-Content -LiteralPath $gitignorePath
    }

    $missing = @($gitignoreEntries | Where-Object { $_ -notin $existing })
    if ($missing.Count -eq 0) {
        Write-Host ".gitignore already contains baseline entries"
        return
    }

    $lines = New-Object System.Collections.Generic.List[string]
    foreach ($line in $existing) {
        $null = $lines.Add($line)
    }

    if ($lines.Count -gt 0 -and $lines[$lines.Count - 1] -ne "") {
        $null = $lines.Add("")
    }

    foreach ($entry in $missing) {
        $null = $lines.Add($entry)
    }

    Set-Content -LiteralPath $gitignorePath -Value $lines -Encoding UTF8
    Write-Host ("added {0} entries to .gitignore" -f $missing.Count)
}

function Invoke-GradleSafely {
    param([string[]]$Arguments)

    if (-not $Arguments -or $Arguments.Count -eq 0) {
        throw "Provide Gradle tasks after -Action RunGradle, for example: -Action RunGradle assembleDebug"
    }

    New-Item -ItemType Directory -Force -Path $safeGradleHome | Out-Null
    New-Item -ItemType Directory -Force -Path $safeAndroidUserHome | Out-Null

    if (-not (Test-Path -LiteralPath $safeAndroidSdkRoot)) {
        Write-Warning "Android SDK not found at $safeAndroidSdkRoot"
    }

    $gradleCmd = Join-Path $repoRootResolved "gradlew.bat"
    if (-not (Test-Path -LiteralPath $gradleCmd)) {
        throw "gradlew.bat not found in repo root"
    }

    Write-Host "Running Gradle with safe environment"
    Write-Host "  GRADLE_USER_HOME=$safeGradleHome"
    Write-Host "  ANDROID_USER_HOME=$safeAndroidUserHome"
    Write-Host "  ANDROID_SDK_ROOT=$safeAndroidSdkRoot"
    Write-Host ""

    $previousGradleHome = $env:GRADLE_USER_HOME
    $previousAndroidUserHome = $env:ANDROID_USER_HOME
    $previousAndroidSdkRoot = $env:ANDROID_SDK_ROOT
    $previousAndroidHome = $env:ANDROID_HOME

    try {
        $env:GRADLE_USER_HOME = $safeGradleHome
        $env:ANDROID_USER_HOME = $safeAndroidUserHome
        $env:ANDROID_SDK_ROOT = $safeAndroidSdkRoot
        $env:ANDROID_HOME = $safeAndroidSdkRoot

        Push-Location $repoRootResolved
        & $gradleCmd @Arguments
        $exitCode = $LASTEXITCODE
        if ($exitCode -ne 0) {
            throw "Gradle exited with code $exitCode"
        }
    } finally {
        Pop-Location
        $env:GRADLE_USER_HOME = $previousGradleHome
        $env:ANDROID_USER_HOME = $previousAndroidUserHome
        $env:ANDROID_SDK_ROOT = $previousAndroidSdkRoot
        $env:ANDROID_HOME = $previousAndroidHome
    }
}

function Invoke-Fix {
    Write-Host "Applying repository safety baseline"
    Write-Host ""

    Ensure-GitignoreEntries

    Write-Host ""
    Write-Host "Cleaning duplicate local caches from repository"
    Remove-TrackedPaths -RelativePaths $alwaysCleanDirs

    Write-Host ""
    Write-Host "Current status"
    Show-Status

    Write-Host ""
    Write-Host "Recommended next step"
    Write-Host "  Run Gradle through this script or keep Android Studio and terminal sessions away from repo-local HOME and GRADLE_USER_HOME."
}

switch ($Action) {
    "Status" {
        Show-Status
    }
    "Clean" {
        Write-Host "Cleaning repository-local caches"
        if ($IncludeBuildOutputs) {
            Write-Host "Build outputs are included"
        } else {
            Write-Host "Build outputs are excluded"
        }
        Write-Host ""
        Remove-TrackedPaths -RelativePaths $cleanTargets
    }
    "RunGradle" {
        Invoke-GradleSafely -Arguments $GradleArgs
    }
    "Fix" {
        Invoke-Fix
    }
}
