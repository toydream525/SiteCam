# 开发说明

[返回项目首页](../README.md) · [使用指南](USER_GUIDE.md)

以下为开发与构建资料，普通用户直接下载安装包即可。

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
    ├── settings/         // 水印样式、质量、硬件诊断与桌面图标
    ├── onboarding/       // 首次使用功能指引
    ├── help/             // 离线教程目录与章节
    └── icon/             // A/B/C/D 桌面图标切换
```

---

## 4. 权限与存储策略

1. **权限集中处理、按需补开**：
   - 完成功能指引或暂时跳过后，进入权限指引，点“开启所需权限”后按相机、定位、麦克风顺序显式申请当前缺少的权限；已全部授予或已经处理过的权限不会重复自动弹出。
   - 相机是拍摄必需权限；定位和录音可选。精确定位或大致定位任一授权即可提供定位水印；拒绝麦克风后仍可录像，但视频没有声音。
   - 后续若缺少权限，从拍摄页的提示按需要补开；定位未授权时仍可拍摄和导出，水印会标注定位不可用。
2. **资料保存策略**：
   - 默认从应用内工程相册查看和管理新拍资料；设置中打开“同时在系统相册显示”后，之后拍摄和编辑保存的内容才会出现在系统相册。
   - 应用相册与系统相册共用同一份文件，删除会同时影响两处；切换开关不会搬动旧照片和视频。
   - Android 8/9 在未授予旧版存储权限时安全降级到应用专属目录，不阻断拍摄和导出；Android 10+ 使用系统的分区存储规则。
   - 工程文件夹导出：通过系统文件夹选择器获取用户目录，写入工程信息、照片/视频清单、编辑成品和报告；重名自动加序号，缺失媒体写入报告。

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

- **v0.2.8（本版源码，尚未发布）**：拍照/录像、动态变焦、统一工程水印、工程与可选线路管理、工程相册日期筛选、问题记录与状态、标注成品、四档画质、应用内/系统相册选择，以及 ZIP/总文件夹归档导出；新增首次使用指引、离线教程和 A/B/C/D 桌面图标切换（新装默认 D）。
- **视频边界**：90/270 度编码旋转按显示坐标布局 overlay；不同厂商硬件编码器、音频和旋转元数据仍需在目标真机复核。
- **v0.3.0**：现场安全合规 AI 图片分析接口 (On-device / Cloud 抽象)。
- **v0.4.0**：PDF 工程照片记录表与批量打印。
- **iOS**：正在开发中。
- **HarmonyOS 原生版**：正在开发中。
