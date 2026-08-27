# 工程水印相机 (SiteCam / Engineering Watermark Camera)

一款面向工程施工、现场检查、工程留档场景的高稳定性 Android 原生水印相机。

核心目标：**拍摄 → 自动写入工程信息 → 问题标记 → 按项目归档 → 快速查找/分享**。

**永久免费 · 开源 · 无广告。** Android 版本当前可用，iOS 与 HarmonyOS 原生版正在开发中。

本项目采用 [MIT License](LICENSE) 开源。

- [普通用户使用指南](docs/USER_GUIDE.md)
- [下载 Android 正式版](https://github.com/toydream525/SiteCam/releases/latest)
- [作者主页](https://yuriaqua.com)

## 作者

- 作者：ninjaaqua
- GitHub：[@toydream525](https://github.com/toydream525)

---

## 1. 产品特色与架构边界

- **纯本地离线优先**：核心功能（拍照、水印渲染、项目管理、相册归档、问题标记）100% 离线可用，无需注册登录。
- **统一水印渲染引擎 (Unified Watermark Layout Engine)**：实时预览层与最终高分辨率照片输出共享相同的几何度量、字号比例与卡片排版，实现 1:1 所见即所得。
- **快门时间戳强一致性**：快门触发瞬间生成唯一 `captureTimestamp`，文件名、水印文本、EXIF 元数据与 Room 数据库完全对齐。
- **兼容存储归档**：Android 10+ 使用共享存储 `Pictures/SiteCam/<工程名称>/`；Android 8/9 在未授予旧版存储权限时安全降级到应用专属目录，并通过 FileProvider 访问。
- **跨厂商工程导出**：工程支持“压缩包”和 SAF “不压缩文件夹”两种互斥导出方式；目录选择结果持久化，不依赖华为/鸿蒙文件管理器的特定包名。
- **清晰架构边界**：Camera、Watermark、Database、Location、Media 各层高内聚低耦合。

---

## 2. 技术栈

| 模块 | 选型 | 说明 |
| :--- | :--- | :--- |
| 语言 | Kotlin 2.1.x | 协程 Flow + StateFlow |
| UI 框架 | Jetpack Compose + Material 3 | 现场高对比度工程配色 |
| 相机框架 | CameraX 1.4.x Stable | Preview + ImageCapture (MAX_QUALITY) + ZoomRatio |
| 数据库 | Room 2.6.x + SQLite | Project, Category, Media, Issue, WatermarkTemplate |
| 键值存储 | DataStore Preferences | 闪光灯记忆、默认模板、照片质量 |
| 媒体持久化 | MediaStore API + ExifInterface | Scoped Storage 兼容 + EXIF GPS 写入 |
| 图片加载 | Coil 2.7.x | 异步相册缩略图加载 |
| 最低版本 | minSdk = 26 (Android 8.0) | targetSdk = 36 (Android 16) |

---

## 3. 工程架构目录

```text
com.sitecam.app/
├── SiteCamApplication.kt
├── MainActivity.kt
├── core/
│   ├── camera/           // CameraManager, CameraCapability, OrientationManager, Diagnostics
│   ├── database/         // Room AppDatabase, Entity, DAO
│   ├── location/         // LocationTracker (前台 GPS + 纯原生 Fallback), ReverseGeocoder
│   ├── media/            // MediaStoreManager (Pictures/SiteCam/), NamingEngine, ExifPreserver
│   ├── watermark/        // WatermarkData, WatermarkLayoutEngine, Bitmap & Canvas Renderers
│   ├── preferences/      // AppSettingsDataStore
│   └── di/               // AppContainer 服务定位器
└── feature/
    ├── navigation/       // Screen 路由与 AppNavHost
    ├── camera/           // 拍摄主页面、TopBar、BottomBar、ZoomPills、FocusRing
    ├── projects/         // 工程项目列表、快捷创建对话框
    ├── gallery/          // 工程相册、日期过滤、照片大图查看与分享
    ├── issue/            // 现场问题快速标记
    └── settings/         // 水印样式、质量、硬件诊断复制
```

---

## 4. 权限与存储策略

1. **权限按需申请**：
   - `android.permission.CAMERA`：首次启动仅申请核心拍摄权限。
   - `android.permission.RECORD_AUDIO`：切换录像并开始录制时按需申请；拒绝后仍可录制无声视频。
   - `android.permission.ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`：仅前台拍摄时读取经纬度。若未授权，相机正常工作并显示“定位不可用”。
2. **存储策略**：
   - Android 10+ (API 29+)：使用标准 MediaStore Scoped Storage，无需全盘文件权限。
   - Android 8/9：声明兼容 `WRITE_EXTERNAL_STORAGE` (maxSdkVersion=28)；拒绝后照片、录像和 ZIP 导出使用应用专属目录，不阻断核心功能。
   - 工程文件夹导出：通过 `ACTION_OPEN_DOCUMENT_TREE` 获取用户目录，写入 `project_info.json`、`photo_index.csv`、`export_report.json` 及 `Photos/`、`Videos/`、`Annotations/`；重名自动加序号，缺失媒体写入报告。

---

## 5. 编译与测试

```bash
# 运行单元测试（Debug/Release）
./gradlew testDebugUnitTest testReleaseUnitTest

# 编译 Debug APK
./gradlew assembleDebug

# 执行 Lint 代码检查
./gradlew lintDebug lintRelease

# 构建正式签名 APK（缺少签名配置时会明确失败）
./gradlew assembleRelease
```

---

## 6. 版本规划 (Roadmap)

- **v0.2.7 (当前版)**：拍照/录像、动态变焦、统一工程水印、工程管理与相册、现场问题记录、标注成品，以及 ZIP/SAF 文件夹归档导出；录像采用串行状态机和拍摄时水印快照，转码失败保留原片并标记可重试状态。
- **视频边界**：90/270 度编码旋转按显示坐标布局 overlay；不同厂商硬件编码器、音频和旋转元数据仍需在目标真机复核。
- **v0.3.0**：现场安全合规 AI 图片分析接口 (On-device / Cloud 抽象)。
- **v0.4.0**：PDF 工程照片记录表与批量打印。
- **iOS**：正在开发中。
- **HarmonyOS 原生版**：正在开发中。
