$ErrorActionPreference = 'Stop'

$Root = $PSScriptRoot
$Tools = Join-Path $Root '.tools'
$Sdk = Join-Path $Tools 'android-sdk'
$CmdZip = Join-Path $Tools 'commandlinetools-win.zip'
$CmdSrc = Join-Path $Tools 'cmdline-src'
$CmdLatest = Join-Path $Sdk 'cmdline-tools\latest'
$CmdToolsUrl = 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip'

function Resolve-JavaHome {
    $Candidates = @()
    if ($env:JAVA_HOME) {
        $Candidates += $env:JAVA_HOME
    }

    $JavaCommand = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($JavaCommand) {
        $Candidates += Split-Path (Split-Path $JavaCommand.Source -Parent) -Parent
    }

    $Candidates += Join-Path $Tools 'jdk-17'

    $Candidates += @(
        'C:\Program Files\Android\Android Studio\jbr',
        'C:\Program Files\Android\Android Studio\jre'
    )

    foreach ($Candidate in $Candidates | Select-Object -Unique) {
        if ($Candidate -and (Test-Path (Join-Path $Candidate 'bin\java.exe'))) {
            return $Candidate
        }
    }

    throw 'Missing Java. Install Android Studio/JDK 17 or set JAVA_HOME.'
}

$JavaHome = Resolve-JavaHome
$env:JAVA_HOME = $JavaHome
$env:PATH = "$JavaHome\bin;$env:PATH"

New-Item -ItemType Directory -Force -Path $Tools, $Sdk | Out-Null

if (!(Test-Path (Join-Path $CmdLatest 'bin\sdkmanager.bat'))) {
    if (!(Test-Path $CmdZip)) {
        Write-Host "Downloading Android command line tools..."
        & curl.exe -L --retry 5 --retry-delay 3 --connect-timeout 20 -o $CmdZip $CmdToolsUrl
        if ($LASTEXITCODE -ne 0) {
            throw "curl failed with exit code $LASTEXITCODE"
        }
    }

    if (Test-Path $CmdSrc) {
        Remove-Item -LiteralPath $CmdSrc -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $CmdSrc | Out-Null
    Expand-Archive -Path $CmdZip -DestinationPath $CmdSrc -Force
    New-Item -ItemType Directory -Force -Path (Split-Path $CmdLatest) | Out-Null
    if (Test-Path $CmdLatest) {
        Remove-Item -LiteralPath $CmdLatest -Recurse -Force
    }
    Move-Item -LiteralPath (Join-Path $CmdSrc 'cmdline-tools') -Destination $CmdLatest
}

$SdkManager = Join-Path $CmdLatest 'bin\sdkmanager.bat'
Write-Host "Accepting SDK licenses..."
1..200 | ForEach-Object { 'y' } | & $SdkManager --sdk_root=$Sdk --licenses | Out-Host

Write-Host "Installing SDK packages..."
& $SdkManager --sdk_root=$Sdk 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
if ($LASTEXITCODE -ne 0) {
    throw "sdkmanager failed with exit code $LASTEXITCODE"
}

Write-Host "Android SDK ready: $Sdk"
