# WTmap 安装与连接说明

## 1. 下载 APK

打开项目的 [GitHub Releases](https://github.com/ChaselTH/WTmap/releases)，进入最新版本，在 `Assets` 中下载：

```text
WTmap-v0.1.0.apk
```

GitHub 同时会自动提供 `Source code (zip)` 和 `Source code (tar.gz)` 源码压缩包。

## 2. 在 AYN Thor 上安装

1. 把 APK 下载或复制到 Thor。
2. 用文件管理器打开 APK。
3. 如果系统提示不允许安装未知应用，请给当前浏览器或文件管理器开启“允许安装未知应用”。
4. 完成安装后启动 `WTmap`。

如果曾安装过由不同证书签名的旧测试版，Android 可能提示“应用未安装”或签名不一致。先备份需要的设置，再卸载旧版并重新安装 Release APK。

使用 ADB 安装时，可以执行：

```powershell
adb install -r WTmap-v0.1.0.apk
```

需要明确启动到 Thor 下屏时，可以执行：

```powershell
adb shell am start --display 4 -n com.codex.thorwarthunder/.MainActivity
```

## 3. 准备电脑上的《战争雷霆》

1. 确保电脑和 Thor 连接到同一个局域网。
2. 启动《战争雷霆》并进入可使用地图数据的游戏场景。
3. 在电脑浏览器打开 `http://127.0.0.1:8111`，确认能看到游戏本地网页。
4. 在 Windows 命令提示符运行 `ipconfig`，找到当前网络适配器的 IPv4 地址，例如 `192.168.0.224`。
5. 如果 Thor 无法连接，请检查 Windows 防火墙是否允许《战争雷霆》在当前专用网络通信，并确认没有启用会隔离局域网设备的访客 Wi-Fi。

`8111` 是《战争雷霆》提供的本地接口，不需要在 WTmap 中安装额外的电脑服务。

## 4. 连接 WTmap

1. 在 WTmap 顶部输入电脑的 IPv4 地址，只填 IP 即可，例如 `192.168.0.224`。
2. 点击“连接”。WTmap 会自动使用 `8111` 端口。
3. 显示“已连接”后，IP 输入框和连接按钮会自动折叠。
4. 需要重新输入时，点击左上角的小 `>`。

如果一直显示连接失败，请依次确认：

- 电脑浏览器能打开 `http://127.0.0.1:8111`。
- Thor 浏览器能打开 `http://电脑IP:8111`。
- 输入的是电脑局域网 IPv4，而不是公网 IP、虚拟网卡地址或 `127.0.0.1`。
- 两台设备处于同一网络，电脑防火墙没有拦截。

## 5. Root、按键映射与瞄准镜像

普通 8111 地图显示不依赖 Root；以下功能需要 Magisk/Root：

- 向游戏发送硬件键盘按键。
- “瞄准”模式镜像上屏。
- 在下屏镜像区域拖动并同步控制上屏。

首次点击相关按钮时，Magisk 会请求超级用户权限，请选择允许。建议在 Magisk 中把 WTmap 设为长期允许。若不希望每次看到授权通知，可在 Magisk 的超级用户通知设置中关闭 WTmap 的授权提示；不要拒绝 WTmap 权限，否则镜像和按键功能无法工作。

默认操作：

- `瞄准`：默认映射 `Z`，点击后开启或关闭上屏镜像。
- `起落架`：初始映射可以在设置中填写，例如 `G`。
- 右上角设置按钮：编辑按钮名称和映射按键，也可以新增或删除自定义按钮。
- 左下角小 `>`：只负责展开或折叠操作按钮栏；点击功能按钮不会自动折叠。

## 6. 注意事项

- “瞄准”镜像是针对 AYN Thor 双屏系统和当前上屏方向适配的设备专用功能。
- 不要使用会直接替换副屏物理 `layerStack` 的旧镜像脚本，否则可能导致副屏旋转、Home 失效或 AYN 操作面板失效。
- 系统升级、Magisk 配置变化或不同 Thor 固件可能影响 Root 输入功能。
- Release APK 使用固定签名。以后升级时应直接覆盖安装同一发布渠道的 APK，不要混用其他人重新签名的构建。

## 7. 从源码构建并安装

Windows 环境需要 JDK 17 和 Android SDK。首次准备环境：

```powershell
git clone https://github.com/ChaselTH/WTmap.git
cd WTmap
powershell -NoProfile -ExecutionPolicy Bypass -File .\setup_android_sdk.ps1
```

只构建 APK：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build_thor_wt_map.ps1 -NoInstall
```

构建并安装到已通过 ADB 连接的 Thor：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build_thor_wt_map.ps1
```

如果自行构建时生成了新的签名证书，该 APK 不能直接覆盖 GitHub Release 中的正式 APK。
