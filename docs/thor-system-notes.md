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

Current safe mirror direction used by WTmap:

- Do not modify the lower physical display's `layerStack`.
- Keep WTmap running on logical display `4`.
- Keep the top WTmap control bar as normal app UI on the lower screen.
- Only the content area below the top bar becomes the upper-screen mirror.
- The current implementation uses a root-side `MirrorRootService` and an in-app `TextureView`, so the mirror is inside WTmap's own window instead of replacing the whole lower display.
- Lower-screen touch events inside the mirrored content area are translated and injected into upper logical display `0`.

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
  - sends the configured keyboard key, default `Z`;
  - toggles WTmap's in-window upper-display mirror below the fixed top bar;
  - does not take over or mirror the whole lower physical display.
- Root input can trigger Magisk grant toasts depending on Magisk settings. Avoid adding more root calls on every frame/touch.
- Thor's physical input device `/dev/input/event6` declares only some keyboard keys. In one observed state it had keys such as `W/E/U/O/S/L/Z/C/V/M/arrow keys`, but not `G`.
- For mapped buttons:
  - Button mappings are generic, not hard-coded per action. Current key normalization supports A-Z, 0-9, `SPACE`, `ENTER`, `ESC`, `TAB`, `SHIFT`, `CTRL`, `ALT`, arrow keys, and `F1`-`F12`.
  - WTmap first searches `/dev/input/event6` and then `/dev/input/event*` for the real Linux key label.
  - If a device declares the key, WTmap sends EV_KEY with `sendevent`.
  - If the key is missing, WTmap falls back to Android's system `/system/bin/uinput` helper and keeps a WTmap virtual keyboard alive through a FIFO in the root shell. This is needed for mappings such as 起落架=`G`; creating a brand-new virtual keyboard on every tap was too unreliable for Wine/game input.
- `/system/bin/uinput` is a shell wrapper around `/system/framework/uinput.jar` and accepts newline-separated JSON commands: `register`, `delay`, and `inject`.
- Thor's `/system/bin/uinput` expects numeric strings in JSON, not symbolic names:
  - `bus`: `USB`
  - `configuration` type examples: `0x40045564` for `UI_SET_EVBIT`, `0x40045565` for `UI_SET_KEYBIT`
  - event triplets use numbers such as `EV_KEY=1`, `EV_SYN=0`, `KEY_G=34`

## UI / button mapping notes

- The top IP input and connect button collapse after connection. The small left `>` control expands them again.
- Action buttons live in a bottom operation strip, not the top connection row.
- The bottom-left menu is a small `>` control, matching the IP expand control. Keep it compact so the button list can expand into the full bottom strip without crowding the top row.
- Tapping action buttons while the bottom button strip is expanded should not collapse the strip; preserve the expanded state across button actions and mirror-mode UI rebuilds.
- Settings is a full page, not an `AlertDialog`.
- Settings page layout:
  - top-left back button saves and returns;
  - top-right `新增` and `删除`;
  - table with two editable columns: button name and mapped key.
- Button names are user-editable and must sync back to the top action buttons after returning from settings.
- `瞄准` is special because it toggles mirror mode; do not delete it unless a replacement mirror entry exists.
- Custom buttons are normal key-sending actions.
- Android may keep the WTmap process around after the Activity is destroyed. Static executors in input helpers must tolerate restart/reopen; otherwise a later button tap can crash with `RejectedExecutionException` from a terminated executor.

## Icon / map rendering decisions already made

- War Thunder font glyph fallback can show raw letters such as `8`, `F`, `R`, or `P`; WTmap should map known problematic symbols to drawn icons.
- `P` from `8111` is treated as a fire-control marker:
  - red square corner-frame style;
  - gaps at the midpoint of each side;
  - blinking.
- Nearby respawn markers should collapse to one marker per area, regardless of enemy/friendly ownership. Separate clusters far apart should remain separate.
- Ground unit icons should avoid black outlines and remain small enough not to clutter the map.
