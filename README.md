# WTmap for AYN Thor

WTmap 是为 AYN Thor 双屏掌机制作的《战争雷霆》8111 辅助地图。上屏运行游戏，下屏显示实时地图、载具标记和飞行参数；“瞄准”模式还可以在下屏镜像并触控上屏游戏。

## 主要功能

- 读取《战争雷霆》本地 `8111` 接口，显示实时地图和单位标记。
- 自动区分陆战与空战界面。
- 地图拖动、缩放和双击复位。
- 重生点按区域合并，减少标记重叠。
- 修正部分官方字体中显示成 `8`、`F`、`R`、`P` 的图标。
- 红色闪烁火控框。
- 空战姿态仪和精简飞行参数。
- 可编辑的操作按钮与键盘映射。
- “瞄准”模式在下屏镜像上屏，并将下屏拖动同步为上屏实体触摸。

## 下载与安装

普通用户请从 [Releases](https://github.com/ChaselTH/WTmap/releases) 下载最新 APK。完整安装、连接和首次使用说明见 [INSTALL.md](INSTALL.md)。

## 运行要求

- AYN Thor 双屏 Android 掌机。
- 运行《战争雷霆》的电脑与 Thor 处于同一局域网。
- 地图功能使用游戏的 `8111` 本地接口。
- “瞄准”镜像和硬件按键映射需要 Root/Magisk 授权。

## 从源码构建

需要 JDK 17，以及 Android SDK Platform 35 和 Build Tools 35.0.0。Windows 下可运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\setup_android_sdk.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\build_thor_wt_map.ps1 -NoInstall
```

生成的 APK 位于：

```text
build\outputs\apk\debug\thor-wt-map-debug.apk
```

Android Studio 也可以直接打开仓库根目录。当前应用信息：

- 包名：`com.codex.thorwarthunder`
- `minSdk`：23
- `compileSdk`：35
- `targetSdk`：26（Thor 镜像功能依赖系统隐藏接口）

## 开发资料

- [CODEX_HANDOFF.md](CODEX_HANDOFF.md)：后续 Codex 开发交接。
- [docs/thor-system-notes.md](docs/thor-system-notes.md)：Thor 双屏、镜像、输入设备和恢复方案记录。
- [README_THOR_WT_MAP.md](README_THOR_WT_MAP.md)：早期项目说明。

## 免责声明

这是非官方个人工具，与 Gaijin Entertainment、AYN 或 Magisk 无隶属关系。Root 和系统输入功能具有设备风险，请确认自己理解相关操作后使用。
