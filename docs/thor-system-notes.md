# AYN Thor dual-screen notes for future Codex work

This file records the device-specific findings from the Thor / War Thunder map work so future development can continue from git instead of rediscovering the same system behavior.

## Device and display facts

- Primary / upper physical display id: `4630946441858561667`
- Secondary / lower physical display id: `4630946482288158084`
- Primary logical display: `displayId=0`, normal `layerStack=0`
- Secondary logical display: `displayId=4`, normal `layerStack=4`
- Current landscape override:
  - primary: `1920x1080`, `rotation=1`
  - secondary: `1240x1080`, `rotation=1`
- WTmap should normally be launched on the lower display:

```powershell
adb shell am start --display 4 -n com.codex.thorwarthunder/.MainActivity
```

## Important warning: do not mirror by stealing the lower display layer stack

An experimental low-latency mirror changed the secondary physical display to show the primary display stack directly:

- enable experiment: secondary physical display -> `layerStack=0`
- restore normal lower screen: secondary physical display -> `layerStack=4`

This produced a fast mirror, but it is not safe for WTmap:

- the lower screen can rotate 90 degrees because the projection bypasses normal DisplayManager orientation handling;
- the lower Home navigation stops working;
- the AYN lower-screen button / operation panel stops working;
- WTmap's own button can stop receiving touches because the whole lower physical display is effectively showing the upper display stack.

Root cause: this is a physical-display SurfaceFlinger layerStack takeover, not a mirror surface inside WTmap's own window. It bypasses Thor's DualScreenAssistant and SystemUI layers.

If a test leaves the lower screen stuck in the wrong stack/projection, restore it with the diagnostic helper in `tools/diagnostics/SurfaceDisplayRestore.java`.

Build and push the helper from the repo root:

```powershell
$jdk = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-17*' | Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
$env:JAVA_HOME=$jdk
$env:Path=(Join-Path $jdk 'bin') + ';' + $env:Path
$env:ANDROID_HOME='D:\Codex_project\ayn_thor\.tools\android-sdk'
$androidJar = Join-Path $env:ANDROID_HOME 'platforms\android-35\android.jar'
$javac = Join-Path $jdk 'bin\javac.exe'
$d8 = Join-Path $env:ANDROID_HOME 'build-tools\35.0.0\d8.bat'
$out = 'D:\Codex_project\ayn_thor\WTmap\build\diagnostics\restore_classes'
Remove-Item -LiteralPath $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $out | Out-Null
& $javac -cp $androidJar -d $out 'D:\Codex_project\ayn_thor\WTmap\tools\diagnostics\SurfaceDisplayRestore.java'
& $d8 --classpath $androidJar --output 'D:\Codex_project\ayn_thor\WTmap\build\diagnostics' (Join-Path $out 'SurfaceDisplayRestore.class')
adb push 'D:\Codex_project\ayn_thor\WTmap\build\diagnostics\classes.dex' /data/local/tmp/surface-display-restore.dex
```

Restore the lower screen to Thor's normal landscape secondary display projection:

```shell
adb shell su -c 'CLASSPATH=/data/local/tmp/surface-display-restore.dex app_process / SurfaceDisplayRestore 4630946482288158084 4 1 0 0 1240 1080 0 0 1240 1080'
```

After restoring, force-stop any old WTmap build that still contains mirror code before relaunching.

Better future directions:

1. Find and call a safe AYN DualScreenAssistant interface, if one exists.
2. If system privileges are acceptable, build a proper in-window mirror layer instead of switching the whole physical display stack.
3. Use screenshot/screencap only as a fallback; it works inside the app window but is too expensive for smooth aiming.

## Thor system packages involved

- `com.odin.dualscreen.assistant`
  - system UID package from `/system/app/DualScreenAssistant/DualScreenAssistant.apk`
  - exposes only launcher `MainActivity` and boot `TccServices` publicly
  - contains internal classes/strings such as:
    - `TccServices`
    - `ScreenModeControlPanel`
    - `DualScreenControlView`
    - `OnlyDisplayScreenAbove`
    - `OnlyDisplayScreenBelow`
    - `openSecondaryScreen`
    - `closeSecondaryScreen`
    - `openMainScreen`
    - `closeMainScreen`
    - `dual_screen_display_mode`
    - `service call SurfaceFlinger 1013`
- `com.odin.gameassistant`
  - system UID package from `/system/app/GameAssistant/GameAssistant.apk`
  - owns the floating/game assistant UI and accessibility foreground monitor
- `com.odin.mapping`
  - touch mapping service package from `/system/app/TouchMapping/TouchMapping.apk`

Useful setting:

```shell
adb shell settings get system dual_screen_display_mode
```

Observed value during normal dual-screen use: `0`.

## Build / install notes

Build from the repository root used by this workspace:

```powershell
$jdk = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-17*' | Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
$env:JAVA_HOME=$jdk
$env:ANDROID_HOME='D:\Codex_project\ayn_thor\.tools\android-sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
powershell -NoProfile -ExecutionPolicy Bypass -File .\build_thor_wt_map.ps1 -NoInstall
adb install -r 'D:\Codex_project\ayn_thor\WTmap\build\outputs\apk\debug\thor-wt-map-debug.apk'
adb shell am start --display 4 -n com.codex.thorwarthunder/.MainActivity
```

Git may not be on PATH in PowerShell; this path worked on the development PC:

```powershell
& 'C:\Program Files\Git\cmd\git.exe' -C D:\Codex_project\ayn_thor\WTmap status
```

## WTmap behavior notes

- War Thunder local API normally runs on port `8111`.
- Map should follow the `8111` map image/state rather than trying to manually implement a full-map/local-map toggle.
- The user's gameplay setup:
  - upper screen: War Thunder
  - lower screen: WTmap reference map
- Current safe `瞄准` behavior:
  - sends keyboard `Z` to upper display via root `input`;
  - does not take over or mirror the lower physical display.
- Root input can trigger Magisk grant toasts depending on Magisk settings. Avoid adding more root calls on every frame/touch.

## Icon / map rendering decisions already made

- War Thunder font glyph fallback can show raw letters such as `8`, `F`, `R`, or `P`; WTmap should map known problematic symbols to drawn icons.
- `P` from `8111` is treated as a fire-control marker:
  - red square corner-frame style;
  - gaps at the midpoint of each side;
  - blinking.
- Nearby respawn markers should collapse to one marker per area, regardless of enemy/friendly ownership. Separate clusters far apart should remain separate.
- Ground unit icons should avoid black outlines and remain small enough not to clutter the map.
