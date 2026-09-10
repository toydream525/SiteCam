# 工程水印相机 (SiteCam / Engineering Watermark Camera)

一款面向工程施工、现场检查、工程留档场景的Android 原生水印相机，并提供 HarmonyOS 原生开发版。

核心目标：**拍摄 → 自动写入工程信息 → 问题标记 → 按项目归档 → 快速查找/分享**。

**永久免费 · 开源 · 无广告。** Android 当前版本为 v0.2.9，鸿蒙开发版为 v0.2.8。Android 提供 APK；HarmonyOS 提供 API 12 起的原生源码及未签名开发 HAP，已在 API 26 模拟器验证。iOS 仍在开发中。

本项目采用 [MIT License](LICENSE) 开源。

- [普通用户使用指南](docs/USER_GUIDE.md)
- [下载 Android v0.2.9](https://github.com/toydream525/SiteCam/releases/tag/v0.2.9)
- [作者主页](https://yuriaqua.com)

## 平台与安装

| 平台 | 下载与状态 |
| --- | --- |
| Android 8.0+ | [v0.2.9 APK](https://github.com/toydream525/SiteCam/releases/download/v0.2.9/SiteCam-0.2.9-Android.apk) |
| HarmonyOS 5 / API 12 起（最低配置） | [开发说明](harmonyos/README.md)：API 26 模拟器验证；Debug / Release HAP 均未签名，不是普通真机安装包 |
| iOS | 开发中，暂无安装包 |

鸿蒙版已实现手机、折叠展开和大屏布局。核心验证 36 项中 35 项通过；视频硬件编解码、HarmonyOS 5/6 实际运行、真实平板和悬停待验收。详见[验证记录](harmonyos/VERIFICATION.md)、[更新日志](CHANGELOG.md)与[官网项目页](https://yuriaqua.com/sitecam/)。

以下功能和技术栈以安卓版为基准；鸿蒙版系统相册采用**独立副本**，原件与系统相册副本互不联动删除。

## 作者

- 作者：ninjaaqua
- GitHub：[@toydream525](https://github.com/toydream525)

---

## 1. 产品特色与架构边界

- **纯本地离线优先**：核心功能（拍照、水印渲染、项目管理、相册归档、问题标记）100% 离线可用，无需注册登录。
- **统一水印渲染引擎 (Unified Watermark Layout Engine)**：实时预览层与最终高分辨率照片输出共享相同的几何度量、字号比例与卡片排版，实现 1:1 所见即所得。
- **快门时间戳强一致性**：快门触发瞬间生成唯一 `captureTimestamp`，文件名、水印文本、EXIF 元数据与 Room 数据库完全对齐。
- **应用内相册优先**：照片、视频和编辑成品默认保存在应用内工程相册；设置中可开启系统相册显示。两边共用同一份文件，切换不会搬动旧资料，删除会同步影响两处。
- **工程与线路管理**：一个工程可填写一条可选线路，例如“滨江路雨污分流改造 / 北段雨水管”；支持搜索工程和线路、归档恢复、按最近拍摄/创建/更新/工程名/线路首字母排序，以及批量分类、归档、锁定、解锁、删除和导出。锁定只禁止该工程继续拍摄录像，已有资料仍可编辑、移动、删除和导出。
- **四档画质与方向记忆**：提供省空间、标准（默认）、更清晰、原尺寸四档；拍摄和编辑保存分别遵循当前选择。竖屏、左横屏、右横屏和自动方向可选并记住上次选择。
- **按日期整理相册**：可按年月、单日或日期范围查找拍摄内容，也可按工程、类别、问题等级和状态筛选；多选后可移动、分享、导出或删除。
- **编辑与归档导出**：详情页支持缩放、旋转、翻转、裁剪、撤销重做，以及箭头、文字和马赛克标注；原图与独立成品可以切换查看。批量导出为 ZIP 或总文件夹，各工程使用独立目录并包含照片、视频、编辑成品、工程信息、清单和报告。
- **首次使用指引与离线教程**：首次启动展示四页大字指引，完成或跳过后不因正常升级再次自动出现；设置可重看。帮助页带目录和分章节正文，离线可读。
- **四款桌面图标**：设置中可在 A“蓝图镜头”、B“工程印记”、C“现场坐标”、D“工程现场”之间切换，新装默认使用 D；只更换桌面入口，不影响工程资料。
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
