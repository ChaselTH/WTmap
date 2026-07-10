# Thor WT Map

这是给 AYN Thor 做的 War Thunder 8111 辅助地图 App。

## 当前功能

- 顶部输入 PC IP，默认连接 `192.168.0.224:8111`。
- 地图直接使用 War Thunder 8111：
  - `/map.img` 地图底图
  - `/map_info.json` 地图网格/版本
  - `/map_obj.json` 单位/战区/机场等对象
  - `/icons.ttf` 官方地图图标字体
- 地图支持双指缩放、拖动，双击放大/复位。
- 地面单位图标做了压扁缩短处理，避免坦克图标像竖直长条。
- 自动识别载具类型：
  - `indicators.army == "tank"`：隐藏飞机仪表，只显示地图。
  - `indicators.army == "air"`：显示姿态仪和飞行数据。
- 飞机模式显示：
  - 姿态仪
  - 迎角、侧滑、过载
  - 俯仰、横滚、垂速
  - 推力、发动机 RPM/温度
  - 燃油、油耗、喷口角、襟翼/起落架/减速板

## 用户偏好

- 不要显示高度、表速、真空速、马赫数、油门。
- 地图图标尽量和 8111 官方网页一致。
- 坦克模式只需要地图，不要飞机 UI。
- Thor 项目的脚本不要混进隔壁 Android/小米项目。

## 构建方式

### 方式 1：Android Studio

直接用 Android Studio 打开这个文件夹。工程是普通 Android app：

- package: `com.codex.thorwarthunder`
- minSdk: 23
- targetSdk: 35
- compileSdk: 35

### 方式 2：本地脚本

脚本文件：

- `Build_Install_Thor_WT_Map.bat`
- `build_thor_wt_map.ps1`
- `setup_android_sdk.ps1`

需要另一台电脑有：

- JDK 17，或设置 `JAVA_HOME`
- Android SDK，或设置 `ANDROID_HOME` / `ANDROID_SDK_ROOT`
- SDK 里至少有 `platforms;android-35` 和 `build-tools;35.0.0` 或更新版本

如果没有 Android SDK，可以先运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\setup_android_sdk.ps1
```

它会把 SDK 装到本项目的 `.tools\android-sdk`。

只构建不安装：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build_thor_wt_map.ps1 -NoInstall
```

构建并安装到已连接的 Android 设备：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build_thor_wt_map.ps1
```

输出 APK：

```text
build\outputs\apk\debug\thor-wt-map-debug.apk
```

## 重要文件

- `app/src/main/java/com/codex/thorwarthunder/MainActivity.java`：主界面、IP 输入、轮询 8111、飞机/坦克 UI 切换。
- `app/src/main/java/com/codex/thorwarthunder/ThorMapView.java`：地图绘制、缩放拖动、官方图标字体、玩家箭头、机场线。
- `app/src/main/java/com/codex/thorwarthunder/FlightStatus.java`：解析 `/state` 和 `/indicators`。
- `app/src/main/java/com/codex/thorwarthunder/AttitudeView.java`：简易姿态仪。
- `app/src/main/java/com/codex/thorwarthunder/MapObject.java`：解析 `/map_obj.json`。

## 已知限制

- 8111 没有直接暴露发动机寿命/加力剩余时间字段，目前燃油时间只能按 `fuel_consume` 做近似。
- 如果 Thor 被投屏脚本息屏，`adb screencap` 可能抓到黑屏；这不一定代表 App 崩溃。
