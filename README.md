# FX Plus Module

面向 FX File Explorer 的 libxposed API 102 增强模块，包名为 `com.scluzlep.fxplusmodule`，作用域为 `nextapp.fx`。

## 支持版本

- FX File Explorer `9.0.1.2` (`9012`)
- FX File Explorer `9.1.0.8` (`9108`)
- 模块运行要求：Android 9+ (`minSdk 28`)，libxposed API 102 宿主机环境

## 核心功能

### 1. 远程大图切换解锁
- 解除看图器在构建同级滑动列表时的 4 MiB 硬编码限制。
- 支持无限制或自定义单图 MB 上限，完整保留 FX 每个目录独立的排序方式。
- 仅作用于看图器同级构建链路，不影响非看图目录的文件扫描。

### 2. 相邻原图预加载、磁盘 LRU 与目录离开清理
- 仅在磁盘缓存原始图片字节到 `cacheDir/fxplus_prefetch`，不进行后台 `Bitmap` 解码，防止大图占用看图器内存。
- 看图器按 100% 原图分辨率读取命中缓存的流数据，并恢复 JPEG 的 EXIF 方向信息。
- 支持三种限流模式：
  - `COUNT_ONLY`：仅按个数，围绕当前索引预加载 `N±k`，默认开启且默认 `k = 3`。
  - `CAPACITY_ONLY`：仅按容量，在磁盘缓存预算内向前后扩展预加载，默认容量上限 `256 MB`。
  - `BOTH`：同时受 `N±k` 与磁盘容量上限约束。
- 默认单文件预加载大小为无限制，支持自定义并发数 `1..4` 与仅 Wi-Fi 预加载。
- 采用 `.part` 写完再原子重命名为 `.bin` 的机制、同 key 单飞去重、快速滑过自动取消、容量下调时立即执行 LRU 淘汰。
- 磁盘缓存键绑定规范化目录标识；当所有 `ExplorerActivity` 窗口栈与看图会话均离开该目录后，自动回收对应目录的 `.bin`/`.part` 缓存与旧版无前缀遗留文件，并取消在途下载。

### 3. GPU 硬件大图渲染、硬件瓦片分块与 VSYNC 硬件 GIF 播放
- **禁用 `> 2160 px` 软件图层**：绕过 `TouchImageDisplay` 对超 `2160 px` 位图切换 `LAYER_TYPE_SOFTWARE` 及退回小图切换 `LAYER_TYPE_HARDWARE` 的限制，始终保持 `LAYER_TYPE_NONE` 与双线性过滤绘制。
- **超大图硬件瓦片分块**：当位图宽或高超出硬件画布上限或字节数超过 `96 MiB` 时，自动按 `2048 px` 逻辑块及 1 像素重叠边带惰性切块渲染，单视图保留上限 `24` 块的 LRU 缓存并安全回收淘汰瓦片，消除边界 1 px 接缝与超限崩溃。
- **VSYNC 硬件 GIF 播放**：为帧探测流补充 `64 KiB` 缓冲，使用 `ImageDecoder` 直接解码为硬件 `AnimatedImageDrawable`；显式重置预览残留 `prescale = 1.0f`，绑定 43 字节 `1x1` `Movie` 哨兵维持双指缩放/平移/翻页手势同时停用原生 `50 ms` 定时器，通过 `Drawable.Callback` 驱动 VSYNC 满帧动画，单帧静态 GIF 自动回落高清位图。

### 4. 远程缩略图解锁与高清保护
- 解锁远程目录缩略图的大小限制，支持自定义单文件上限，默认无限制。
- 若目标原图已存在于看图预加载缓存，优先复用本地缓存文件生成缩略图。
- 高清缩略图开关启用安全缩放边界，限制最大边长与像素总数以防内存溢出。

### 5. Android/data 与 Android/obb 目录访问
- 仅操作 `/storage/emulated/<userId>/Android/data` 与 `/storage/emulated/<userId>/Android/obb` 的 FUSE 挂载路径，自动将 `/sdcard` 前缀规范化为对应用户的模拟存储路径。
- 数据通路采用 Root FUSE 会话优先、Shizuku 存储桥接 `content://com.scluzlep.fxplusmodule.storagebridge` 回退，支持目录列表、读写打开、创建删除、重命名与跨受限边界复制移动。

### 6. 双端设置与原地热重载
- 注入 FX 原生设置页 `MainPrefActivity`，添加 `FX Plus 增强设置` 入口并弹出跟随主题的原生配置对话框。
- 独立管理端与 FX 原生设置弹窗通过 `RemotePreferences` 与 FX 本地偏好按 `updatedAtMs` 时间戳双向同步，修改后实时生效。
- 支持 libxposed API 102 的 `onHotReloading` 与 `onHotReloaded`，热重载时清理旧句柄并关闭遗留预加载线程池。

### 7. 附加文本限制解除
- 提供独立开关，用于解除文本查看器、文本编辑器与命令行执行输出的大小截断限制。

### 8. SMB3 协议支持说明
- FX `9012` 与 `9108` 内置的 `com.hierynomus.smbj` 客户端均已原生启用 `SMB_3_1_1`、`SMB_3_0_2`、`SMB_3_0`、`SMB_2_1`、`SMB_2_0_2` 方言，SMBv1 由独立兼容目录承载。本模块未改动 SMB 协议栈，默认以兼容方式运行；若服务端强制要求 SMB3 传输加密，受限于上游 `smbj` 默认配置限制。

## 本地构建与测试

设置环境变量后在 `codesource` 目录执行单元测试、构建与 Lint 检查：

```powershell
$env:GRADLE_USER_HOME = "E:\Gradle"
$env:ANDROID_HOME = "D:\Androidsdk"
$env:ANDROID_SDK_ROOT = "D:\Androidsdk"

& "E:\Gradle\gradle-9.7.0\bin\gradle.bat" -p "D:\antigproj\fxmoudles\codesource" `
  :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintVitalRelease --no-daemon
```

本地存在待验证的目标 APK 时，单元测试中的 `DualApkDexContractTest` 会直接解析真实 APK 的 `classes*.dex`，逐项校验 `9012` 与 `9108` 的类名、方法签名与字段类型。

构建产物路径：
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- 未签名 Release APK：`app/build/outputs/apk/release/app-release-unsigned.apk`

## SigHelper 签名与校验

GitHub Actions 工作流 `.github/workflows/build-release.yml` 使用 SigHelper 托管的 PKCS#12 密钥库秘密完成 `zipalign` 与 `apksigner` 签名，并输出 `SHA256SUMS.txt`。所需仓库秘密变量为 `SIGNING_KEYSTORE_BASE64`、`SIGNING_PASSWORD`、`SIGNING_KEY_PASSWORD` 与 `SIGNING_KEY_ALIAS`。

下载发布版 APK 后，可使用 `SigHelper` CLI 或 Android Build-Tools 校验签名与证书摘要：

```bash
android-sig veri <owner>/<repo> --apk fxplusmodule-v<version>.apk
```

使用 `apksigner` 进行本地证书校验：

```bash
"$ANDROID_HOME/build-tools/37.0.0/apksigner" verify --verbose --print-certs fxplusmodule-v<version>.apk
```

## 审计文档

更多硬编码限制、双版本符号对齐说明与合规边界见 [docs/HARDCODED_LIMITS_AUDIT.md](docs/HARDCODED_LIMITS_AUDIT.md)。
