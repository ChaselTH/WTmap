param(
    [switch]$NoInstall
)

$ErrorActionPreference = 'Stop'

$Root = $PSScriptRoot
$Tools = Join-Path $Root '.tools'

function Resolve-JavaHome {
    $Candidates = @()
    if ($env:JAVA_HOME) {
        $Candidates += $env:JAVA_HOME
    }

    $JavacCommand = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($JavacCommand) {
        $Candidates += Split-Path (Split-Path $JavacCommand.Source -Parent) -Parent
    }

    $Candidates += Join-Path $Tools 'jdk-17'

    $Candidates += @(
        'C:\Program Files\Android\Android Studio\jbr',
        'C:\Program Files\Android\Android Studio\jre',
        'C:\Users\51511\AppData\Local\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime\java-runtime-gamma\windows-x64\java-runtime-gamma'
    )

    foreach ($Candidate in $Candidates | Select-Object -Unique) {
        if ($Candidate -and (Test-Path (Join-Path $Candidate 'bin\javac.exe')) -and (Test-Path (Join-Path $Candidate 'bin\jar.exe'))) {
            return $Candidate
        }
    }

    throw 'Missing JDK. Install Android Studio/JDK 17 or set JAVA_HOME.'
}

function Resolve-AndroidSdk {
    $Candidates = @()
    if ($env:ANDROID_HOME) {
        $Candidates += $env:ANDROID_HOME
    }
    if ($env:ANDROID_SDK_ROOT) {
        $Candidates += $env:ANDROID_SDK_ROOT
    }
    $Candidates += @(
        (Join-Path $Tools 'android-sdk'),
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
    )

    foreach ($Candidate in $Candidates | Select-Object -Unique) {
        if ($Candidate -and (Test-Path (Join-Path $Candidate 'platforms')) -and (Test-Path (Join-Path $Candidate 'build-tools'))) {
            return $Candidate
        }
    }

    throw 'Missing Android SDK. Install Android Studio SDK or set ANDROID_HOME.'
}

function Resolve-BuildTools($SdkRoot) {
    $Preferred = Join-Path $SdkRoot 'build-tools\35.0.0'
    if (Test-Path (Join-Path $Preferred 'aapt2.exe')) {
        return $Preferred
    }

    $Found = Get-ChildItem -Path (Join-Path $SdkRoot 'build-tools') -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName 'aapt2.exe') } |
        Sort-Object Name -Descending |
        Select-Object -First 1
    if ($Found) {
        return $Found.FullName
    }

    throw 'Missing Android build-tools. Install build-tools;35.0.0 or newer.'
}

function Resolve-AndroidJar($SdkRoot) {
    $Preferred = Join-Path $SdkRoot 'platforms\android-35\android.jar'
    if (Test-Path $Preferred) {
        return $Preferred
    }

    $Found = Get-ChildItem -Path (Join-Path $SdkRoot 'platforms') -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName 'android.jar') } |
        Sort-Object Name -Descending |
        Select-Object -First 1
    if ($Found) {
        return Join-Path $Found.FullName 'android.jar'
    }

    throw 'Missing Android platform. Install platforms;android-35 or newer.'
}

$JavaHome = Resolve-JavaHome
$Sdk = Resolve-AndroidSdk
$BuildTools = Resolve-BuildTools $Sdk
$AndroidJar = Resolve-AndroidJar $Sdk

$Aapt2 = Join-Path $BuildTools 'aapt2.exe'
$D8 = Join-Path $BuildTools 'd8.bat'
$ZipAlign = Join-Path $BuildTools 'zipalign.exe'
$ApkSigner = Join-Path $BuildTools 'apksigner.bat'
$Javac = Join-Path $JavaHome 'bin\javac.exe'
$Jar = Join-Path $JavaHome 'bin\jar.exe'
$KeyTool = Join-Path $JavaHome 'bin\keytool.exe'

foreach ($Required in @($Aapt2, $D8, $ZipAlign, $ApkSigner, $Javac, $Jar, $KeyTool, $AndroidJar)) {
    if (!(Test-Path $Required)) {
        throw "Missing build tool: $Required"
    }
}

$env:JAVA_HOME = $JavaHome
$env:PATH = "$JavaHome\bin;$env:PATH"

$Build = Join-Path $Root 'build\manual'
$Res = Join-Path $Build 'res'
$Gen = Join-Path $Build 'generated'
$Classes = Join-Path $Build 'classes'
$Dex = Join-Path $Build 'dex'
$Out = Join-Path $Root 'build\outputs\apk\debug'

function Reset-Directory($Path) {
    if (Test-Path $Path) {
        Remove-Item -LiteralPath $Path -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $Path | Out-Null
}

function Invoke-Native($Description, [scriptblock]$Command) {
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed with exit code $LASTEXITCODE"
    }
}

Reset-Directory $Res
Reset-Directory $Gen
Reset-Directory $Classes
Reset-Directory $Dex
New-Item -ItemType Directory -Force -Path $Out | Out-Null

Invoke-Native 'aapt2 compile' { & $Aapt2 compile --dir (Join-Path $Root 'app\src\main\res') -o $Res }
$Flats = Get-ChildItem -Path $Res -Filter *.flat -Recurse | ForEach-Object { $_.FullName }

$Unsigned = Join-Path $Build 'app-debug-unsigned.apk'
Invoke-Native 'aapt2 link' { & $Aapt2 link `
    -o $Unsigned `
    -I $AndroidJar `
    --manifest (Join-Path $Root 'app\src\main\AndroidManifest.xml') `
    --java $Gen `
    --min-sdk-version 23 `
    --target-sdk-version 26 `
    --version-code 1 `
    --version-name 0.1 `
    --debug-mode `
    $Flats }

$Sources = @()
$Sources += Get-ChildItem -Path (Join-Path $Root 'app\src\main\java') -Filter *.java -Recurse | ForEach-Object { $_.FullName }
$Sources += Get-ChildItem -Path $Gen -Filter *.java -Recurse | ForEach-Object { $_.FullName }

Invoke-Native 'javac' { & $Javac -encoding UTF-8 -source 8 -target 8 -classpath $AndroidJar -d $Classes $Sources }

$ClassFiles = Get-ChildItem -Path $Classes -Filter *.class -Recurse | ForEach-Object { $_.FullName }
Invoke-Native 'd8' { & $D8 --lib $AndroidJar --output $Dex $ClassFiles }
Invoke-Native 'jar' { & $Jar uf $Unsigned -C $Dex classes.dex }

$Aligned = Join-Path $Build 'app-debug-aligned.apk'
Invoke-Native 'zipalign' { & $ZipAlign -p -f 4 $Unsigned $Aligned }

$Keystore = Join-Path $Tools 'debug.keystore'
if (!(Test-Path $Keystore)) {
    Invoke-Native 'keytool' { & $KeyTool -genkeypair `
        -v `
        -keystore $Keystore `
        -storepass android `
        -alias androiddebugkey `
        -keypass android `
        -keyalg RSA `
        -keysize 2048 `
        -validity 10000 `
        -dname 'CN=Android Debug,O=Android,C=US' }
}

$Signed = Join-Path $Out 'thor-wt-map-debug.apk'
Invoke-Native 'apksigner sign' { & $ApkSigner sign --ks $Keystore --ks-pass pass:android --key-pass pass:android --out $Signed $Aligned }
Invoke-Native 'apksigner verify' { & $ApkSigner verify --verbose $Signed }

Write-Host "Built APK: $Signed"

if (!$NoInstall) {
    $Adb = Join-Path (Split-Path $Root -Parent) '.tools\platform-tools\adb.exe'
    if (!(Test-Path $Adb)) {
        $Adb = 'D:\Codex_project\Android\.tools\platform-tools\adb.exe'
    }
    if (!(Test-Path $Adb)) {
        $Adb = Join-Path $Sdk 'platform-tools\adb.exe'
    }
    if (!(Test-Path $Adb)) {
        throw "Missing adb.exe"
    }

    Invoke-Native 'adb install' { & $Adb install -r $Signed }
    Invoke-Native 'adb start' { & $Adb shell am start -n com.codex.thorwarthunder/.MainActivity }
    Write-Host "Installed and launched Thor WT Map."
}
