# FX File Explorer 硬编码体验限制审计报告

审计对象：`nextapp.fx` `9.0.1.2`（版本码 `9012`，`targetSdk 33`）与 `9.1.0.8`（版本码 `9108`，`targetSdk 36`）。
审计范围：覆盖影响浏览、预览、缓存、网络传输、缩略图生成与系统目录访问的硬编码体验限制及优化点，不包含任何授权或校验逻辑。

---

## 一、版本编译与结构差异总览

| 对比维度 | 9012 版本 | 9108 版本 |
| :--- | :--- | :--- |
| 应用包名与入口 | `nextapp.fx` / `nextapp.fx.FX` | `nextapp.fx` / `nextapp.fx.FX` |
| SDK 版本要求 | `minSdk 21` / `targetSdk 33` | `minSdk 21` / `targetSdk 36` |
| 代码混淆特征 | 未启用 R8 激进裁剪，包名可读 | 启用 R8 混淆压缩，枚举拆箱与部分 Runnable 合并 |
| 原生加壳检查 | 无原生加壳，仅含 `ffmpeg` 与 `libnative-file-access.so` | 无原生加壳，仅含 `ffmpeg` 与 `libnative-file-access.so` |
| 原生设置基类 | 仍继承 `android.preference.PreferenceActivity` | 仍继承 `android.preference.PreferenceActivity` |
| 目录类型枚举 | `DirectoryCatalog$b` 枚举对象，`b()` 判定远程 | 拆箱为 `int`，`a0.f.j(int)` 判定 `4` 为远程 |

---

## 二、图片查看、左右滑动与预加载管线限制

| 限制项目 | 9012 位置与符号 | 9108 位置与符号 | 硬编码阈值与机制 | 模块处理策略 |
| :--- | :--- | :--- | :--- | :--- |
| 远程同目录滑动列表阈值 | `nextapp.fx.ui.viewer.image.k0.b` | `nextapp.fx.ui.viewer.image.b0.run` | 远程目录非当前项体积超过 `4194304` 字节直接过滤剔除 | 由 `SiblingSwipeHook` 按 `maxSwipeImageSizeMb` 放行，禁止全局 Hook 远程判断函数 |
| 文件夹独立排序丢失 | `ImageViewerActivity.s1` / `k0.b` | `ImageViewerActivity.E` / `b0.run` | 启动查看器时未读取 `DirectoryOptionStore`，仅用全局排序配置 | 枚举同目录图片前读取 `DirectoryOptionStore.e` / `DirectoryOptionStore.c` 覆盖排序 |
| 远程图片多次重复下载 | `ee.a.T` + `a9.c.g` | `qg.b.u` + `ua.b.h` | 先整图写入临时文件 `q5`/`m2` 再忽略该文件，静态解码再读流两次 | 接入 `PrefetchCacheManager` 磁盘单飞缓存与 `V1`/`c.read` 本地流复用，目录离开即由 `FolderCacheLifecycleHook` 清理关联缓存并取消在途传输 |
| 远程 JPEG 旋转信息缺失 | `ImageViewerActivity.j1` / `b2` | `ImageViewerActivity.C` | 非 `file://` URI 跳过路径级 EXIF 解析，竖屏原图显示倒置 | 缓存命中后从本地缓存文件提取 `ExifInterface` 旋转角传入解码器 |
| 大图软件渲染与画布上限 | `TouchImageDisplay.g` | `TouchImageDisplay.setImageBitmap` | 宽或高超过 `2160` px 强制切至 `LAYER_TYPE_SOFTWARE`，退回小图时切至 `LAYER_TYPE_HARDWARE`；9108 仅补充 `SDK < 35` 保护，无超大图分块 | 由 `TouchImageHardwareHook` 屏蔽软件/离屏层切换，保持 `LAYER_TYPE_NONE` 与双线性滤波；单边超画布上限或字节超 `96 MiB` 时在 `onDraw` 按 `2048` px 块与 1 px 重叠边带惰性渲染，单视图 LRU 上限 `24` 块 |
| 动图 GIF 帧探测阈值 | `a9.e.a` | `ua.b.f` | 无缓冲逐字节扫描多帧标记，最多读取 `1048576` 字节 | 帧探测入口自动包裹 `64 KiB` `BufferedInputStream`，配合本地缓存复用免除逐字节 I/O |
| 动图 GIF 播放模糊与低帧率 | `ImageViewerActivity.j1`/`l1` + `ImageDisplay.V` | `ImageViewerActivity.C` + `h.run` | 先投递预览图写入高倍 `prescale`，随后 `Movie` 播放未重置 `prescale` 且使用 `50ms` 定时器与软件层 | 由 `TouchImageHardwareHook` 以 `ImageDecoder` 解码为硬件 `AnimatedImageDrawable`，重置 `prescale = 1.0f` 与 `LAYER_TYPE_NONE`，绑定 43 字节 `1x1` `Movie` 哨兵维持手势并经 `Drawable.Callback` 按 VSYNC 驱动重绘；单帧静态 GIF 回落高清位图 |

---

## 三、远程与本地缩略图限制

| 限制项目 | 9012 位置与符号 | 9108 位置与符号 | 硬编码阈值与机制 | 模块处理策略 |
| :--- | :--- | :--- | :--- | :--- |
| 远程文件缩略图大小拦截 | `dd.h.b` | `wg.e.j` | 远程项体积超过 `4194304` 字节跳过缩略图任务 | 由 `RemoteThumbnailHook` 按 `enableRemoteThumbnails` 与 `maxRemoteThumbnailSizeMb` 控制 |
| 缩略图最大像素与分辨率 | `ya.f.k` / `ya.f.c` | `pc.c.a` | 输出上限 `524288` 像素，目标边长 `512` px，质量 `70` | 开启 `highResThumbnails` 时安全放宽至目标 `1024` px 与 `2097152` 像素上限并保留采样降维 |
| 远程缩略图流缓冲区 | `dd.d$a.j` / `dd.j$a.j` | `be.k0.f` | 固定 `16384` 字节缓冲区整图转存临时文件并调用 `Thread.join()` 等待 | 若磁盘已有同文件预加载缓存则直接复用，免去二次拉取 |
| 缩略图内存 LRU 缓存 | `dd.c.<init>` | `kf.d.<init>` | 内存缓存容量上限固定为 `25` 项 | 保持原生轻量内存槽位，依赖磁盘缓存减轻滚动重载 |
| Dropbox 缩略图接口上限 | `nextapp.fx.plus.dirimpl.dropbox.c.w` | `ad.c.q` | 文件超过 `20000000` 字节返回不支持接口缩略图 | 受限于云端接口规格，在允许范围内可回落至流式缩略逻辑 |
| `ThumbnailDrawable` 内存溢出捕获 | `oe.a.a` / `oe.a.draw` | `ch.a.<init>` / `ch.a.draw` | 捕获低内存异常后置位标记并跳过位图绘制，仅输出日志提示 | 保留原生内存异常保护以免滚动密集视图引发 OOM |

---

## 四、本地存储与 Android/data、Android/obb 目录限制

| 限制项目 | 9012 位置与符号 | 9108 位置与符号 | 硬编码阈值与机制 | 模块处理策略 |
| :--- | :--- | :--- | :--- | :--- |
| 普通目录枚举未做提权回退 | `nextapp.fx.dirimpl.file.a.s1` | `ec.a.F0` | 仅调用标准 `File.listFiles()`，未在 `Android/data` 与 `Android/obb` 接入 Root 或 Shizuku | 由 `AndroidDataAccessHook` 仅针对精确作用域路径注入列表、读写流与增删改操作 |
| Android 10+ 显式拦截弹窗 | `nextapp.fx.dirimpl.file.a.s1` | `ec.a.F0` | `SDK >= 29` 且父级为 `Android` 时列表为空即抛出访问受限异常 | 提权开关开启时先于异常抛出点接管列表构造；9108 经由 `FileCatalog.g0` 构造子目录节点 |
| 子项可用性反义布尔判定 | `nextapp.fx.dirimpl.file.a.H0` | `ec.a.j0` | 通过 `!File.exists()` 返回 `true` 表示目标名称尚不存在 | 在 `Android/data` 与 `Android/obb` 域内保持语义一致，存在返回 `false`、不存在返回 `true` |
| 非特权 File 读写删重命名失败 | `nextapp.fx.dirimpl.file.b` / `c` | `ec.d` / `ec.e` | 直接调用普通进程文件流、`delete()`、`renameTo()` 与 `canWrite()` | 优先通过 Root 在 `/storage/emulated/<uId>` FUSE 挂载点直接访问以保持 `MediaProvider` FUSE 原生归属权限，无 Root 时回退 Shizuku AIDL 桥接 |

---

## 五、文本、脚本、编辑器与二进制查看限制

| 限制项目 | 9012 位置与符号 | 9108 位置与符号 | 硬编码阈值与机制 | 模块处理策略 |
| :--- | :--- | :--- | :--- | :--- |
| 文本查看器行数与字符上限 | `TextViewerActivity.w0` | `pg.f0.run` | 超出 `51200` 行或 `5242880` 字符强制截断并插入提示行 | 由 `BonusLimitsHook` 按 `unlockTextPreviewLimits` 解除上限并保留 OOM 异常保护 |
| 文本编辑器文件大小拦截 | `nextapp.fx.ui.textedit.k.c` | `mg.n.l` | 超出 `51200` 行或 `1048576` 字符直接抛异常拒绝打开 | 开启 `unlockTextPreviewLimits` 后解除读取截断限制 |
| 脚本与执行视图预览截断 | `ExecActivity.d2` | `nextapp.fx.plus.ui.audio.w.run` | 超出 `10240` 行或 `1048576` 字符终止预览 | 开启 `unlockTextPreviewLimits` 后同步放开预览上限并保留 `OutOfMemoryError` 拦截 |
| 字符编码探测预读大小 | `pc.a.<init>` | `ue.a.<init>` | 固定缓存文件头前 `65536` 字节判断 BOM 与编码 | 保持 `64 KiB` 探测窗口足够识别 UTF 文本与二进制 XML 头 |
| 二进制查看器加载上限 | `BinaryViewerActivity.x5` | `BinaryViewerActivity.t2` | 静态默认上限 `5242880` 字节 | 审计记录项，大文件整体读入字节数组有内存风险，默认不强制修改 |

---

## 六、网络、云盘、索引与系统缓冲区限制

| 限制项目 | 9012 位置与符号 | 9108 位置与符号 | 硬编码阈值与机制 | 备注说明 |
| :--- | :--- | :--- | :--- | :--- |
| HTTP 客户端超时与连接池 | `ic.b.Z` / `ic.b.a0` | `oe.b.<init>` | 连接超时 `15000` ms，Socket 超时 `15000` ms，单路由 `10` 连接，总池 `20` 连接 | 并发预加载线程数设为 `1..4` 时可避免打满单主机连接配额 |
| SMB 客户端方言与 SMB3 状态 | `com.hierynomus.smbj.SmbConfig.u` | `com.hierynomus.smbj.SmbConfig.a` | 两者均原生配置 `SMB_3_1_1`、`SMB_3_0_2`、`SMB_3_0`、`SMB_2_1`、`SMB_2_0_2`；9012 默认未启用强制加密，9108 仅置 `SMB2_GLOBAL_CAP_LARGE_MTU` | 原生 `smbj` 已支持 SMB2/SMB3，SMBv1 由 `SmbLegacyCatalog` 单独承载，模块无需额外 Hook |
| SMB 客户端超时与列表计时器 | `smb.c.k` / `smb.a.h0` | `ae.c.run` / `fd.e.X` | SMB2 客户端超时 `15` 秒，库默认读写缓冲 `1048576` 字节，目录列举中断计时器 `30000` ms | 超大目录或高延迟 NAS 列目录超过 30 秒可能触发中断 |
| Legacy SMBv1 超时配置 | `SmbLegacyCatalog.<clinit>` | `SmbLegacyCatalog.<clinit>` | `jcifs.smb.client.soTimeout` 固定为 `30000` ms | 仅作用于旧版 SMBv1 兼容实现 |
| FTP 客户端超时配置 | `nextapp.fx.plus.dirimpl.ftp.c.p` | 同类网络配置路径 | 默认超时常量 `30000` ms | 受限于弱网环境或大文件暂停恢复场景 |
| 连接会话池空闲清理周期 | `SessionManager.n` / `SessionManager.w` | `SessionManager.e` / `SessionManager.k` | 清理调度均固定 `300000` ms；空闲超时 9012 为 `300000` ms，9108 缩短为 `90000`/`30000`/`10000` ms 分档 | 9108 会更快断开空闲远程会话，预加载复用原生 `FileItem` 流时可自动按需重连 |
| Dropbox 初始化超时 | 对应云盘连接模块 | `ad.b.connect` | 超时常量含 `20000` ms 与 `120000` ms | 遵循官方客户端初始化配置，不建议改动 |
| Google Drive / OneDrive 上传分块 | `googledrive.e.C` / `onedrive.d.C` / `onedrive.c.m` | `cd.g.n` / `dd.d.i` | 读写流块 `32768` 字节，分片缓冲区 `1048576` 字节，OneDrive 提交步长 `10485760` 字节 | 审计记录项，保持与官方服务端对齐接口一致 |
| 重复文件扫描 MD5 阈值 | `nextapp.fx.db.file.d.g` | `hg.i0.b` | 单文件 MD5 计算存在 `10485760` 字节分段逻辑，流缓冲区 `4096` 字节 | 审计记录项 |
| TAR 归档读取缓冲区 | `fa.p.<init>` | `wb.n.<init>` | 固定使用 `16384` 字节缓冲区 | 审计记录项 |
| 搜索大小预设分档 | `nextapp.fx.ui.search.x1$c.<clinit>` | `hg.k0.<clinit>` | 固定 `1024` 至 `1073741824` 七档文件大小条件常量 | 审计记录项 |
