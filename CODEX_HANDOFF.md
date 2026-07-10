# Codex Handoff

继续开发这个 App 时，请优先读：

1. `README_THOR_WT_MAP.md`
2. `app/src/main/java/com/codex/thorwarthunder/MainActivity.java`
3. `app/src/main/java/com/codex/thorwarthunder/ThorMapView.java`
4. `app/src/main/java/com/codex/thorwarthunder/FlightStatus.java`

## 当前上下文

用户在 AYN Thor 双屏安卓掌机上运行这个 App，用来显示 War Thunder PC 的 8111 端口数据。默认 PC IP 是 `192.168.0.224`，端口固定 `8111`。

8111 已确认存在这些资源：

- `/indicators`
- `/state`
- `/map_info.json`
- `/map_obj.json`
- `/map.img`
- `/icons.ttf`

官方网页地图图标不是 PNG/SVG，而是 `icons.ttf` 字体。`ThorMapView` 已按官方 JS 逻辑映射：

- `Airdefence -> 4`
- `Structure -> 5`
- `waypoint -> 6`
- `capture_zone -> 7`
- `bombing_point -> 8`
- `defending_point -> 9`
- `respawn_base_tank -> 0`
- `respawn_base_fighter -> .`
- `respawn_base_bomber -> :`
- 其他单位取 icon 字符串首字母

玩家箭头是手动画的三角形，机场是 `sx/sy/ex/ey` 画线。

## 最新改动状态

- 坦克模式：`FlightStatus.isAircraft == false` 时隐藏底部 `bottomPanel`，只保留地图。
- 飞机模式：显示底部 `AttitudeView` 和 12 个数据 tile。
- 姿态数据来自 `/indicators`：
  - `aviahorizon_pitch`
  - `bank`，fallback `aviahorizon_roll`
- 地面单位图标在 `ThorMapView.drawMapObject` 中用 `canvas.scale(1.15f, 0.58f, x, y)` 做压扁缩短。

## 用户明确说过的要求

- 地图支持双指缩放和拖动。
- 地图图标尽量和官方一致。
- 飞机 UI 不显示高度、表速、真空速、马赫数、油门。
- 坦克模式只显示地图。
- Thor 项目和隔壁 Android/小米项目不要互相混 bat。

## 构建提示

本机没有完整系统 Gradle，所以当前保留了手工构建脚本 `build_thor_wt_map.ps1`。它会尝试自动找：

- `JAVA_HOME`
- PATH 里的 `javac.exe`
- Android Studio 自带 JDK
- `ANDROID_HOME` / `ANDROID_SDK_ROOT`
- 本项目 `.tools/android-sdk`

如果另一台电脑用 Android Studio，直接打开项目也可以。

## 验证提示

- 用 `Invoke-RestMethod http://<PC-IP>:8111/indicators` 看 `army` 当前是 `air` 还是 `tank`。
- 坦克模式下底部飞机 UI 应隐藏。
- 飞机模式下底部姿态仪和数据应显示。
- 如果 `adb screencap` 是黑屏，先查 `adb shell dumpsys power`，Thor 可能处于 `Asleep`。
