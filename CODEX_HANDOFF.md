# Codex handoff

后续换电脑或新开 Codex 继续开发时，请优先阅读这些文件：

1. `docs/thor-system-notes.md` - AYN Thor 双屏、8111、镜像实验、恢复命令和踩坑记录。
2. `README_THOR_WT_MAP.md` - 项目基本说明。
3. `app/src/main/java/com/codex/thorwarthunder/MainActivity.java` - 主 UI、连接、顶部按钮和飞机参数面板。
4. `app/src/main/java/com/codex/thorwarthunder/ThorMapView.java` - 地图绘制、图标、火控标记和交互。
5. `app/src/main/java/com/codex/thorwarthunder/MapObject.java` - 8111 地图对象解析和重生点聚合。

## 当前目标和设备

这个项目是在 AYN Thor 双屏安卓掌机上给 War Thunder 做下屏地图参考工具。

- 上屏运行 War Thunder。
- 下屏运行 WTmap。
- War Thunder 本地数据来自 `http://<IP>:8111`。
- WTmap 包名：`com.codex.thorwarthunder`。
- WTmap 应启动到副屏：

```shell
adb shell am start --display 4 -n com.codex.thorwarthunder/.MainActivity
```

## 重要警告

不要再用“把副屏物理 display 的 layerStack 切到上屏 layerStack=0”的方式做镜像。

这个实验虽然低延迟，但会导致：

- 副屏肉眼旋转或缩放异常；
- Home 失效；
- AYN 副屏操作面板失效；
- WTmap 自己的按钮无法正常退出镜像。

如果副屏被实验镜像弄乱，先看 `docs/thor-system-notes.md` 里的恢复命令。

当前安全版本中，“瞄准”按钮只发送键盘 `Z`，不会再接管/镜像副屏。

## 已实现/近期变更

- 连接成功后，IP 输入框和连接按钮会折叠，只保留左侧小 `>` 按钮用于展开。
- 顶部按钮栏横向可滑动。
- 新增“瞄准”按钮，对应向上屏发送键盘 `Z`。
- 飞机模式地图靠左上，右侧显示更清晰的飞行参数面板。
- 去掉速度、高度、马赫等游戏里已经能直接看的参数，保留更适合下屏参考的数据。
- 图标 fallback 做了修正，避免部分 8111 字符显示成裸字母/数字。
- `P` 被绘制成红色闪烁火控框。
- 重生点按距离聚合，每个区域只保留一个标记。
- 地面单位图标去掉黑色边框并缩小。

## 构建/安装提醒

注意：仓库外层 `D:\Codex_project\ayn_thor\app` 曾经有旧版源码，容易误装旧 APK。

开发这个 GitHub 仓库时，请在 `D:\Codex_project\ayn_thor\WTmap` 目录下构建：

```powershell
cd D:\Codex_project\ayn_thor\WTmap
$jdk = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-17*' | Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
$env:JAVA_HOME=$jdk
$env:ANDROID_HOME='D:\Codex_project\ayn_thor\.tools\android-sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
powershell -NoProfile -ExecutionPolicy Bypass -File .\build_thor_wt_map.ps1 -NoInstall
adb install -r 'D:\Codex_project\ayn_thor\WTmap\build\outputs\apk\debug\thor-wt-map-debug.apk'
```

如果从另一个电脑 clone 项目，优先使用仓库内的构建脚本或 Android Studio 打开 `WTmap` 项目根目录。
