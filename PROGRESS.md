# 工程水印相机 (SiteCam) - 开发进度总览

## 状态总览
- **当前状态**：v0.2.7 功能与兼容性整改完成，待本轮集中测试/Lint/Release 构建及目标厂商真机复核；相机底栏版式保持邻接任务最新版不变。
- **目标平台**：Android Native (Kotlin 2.1, AGP 8.12.0, Gradle 8.13, Jetpack Compose Material 3, Room, CameraX/Media3).

---

## 阶段里程碑进度

| 里程碑 | 内容说明 | 状态 | 交付文件 |
| :--- | :--- | :--- | :--- |
| **Milestone 0** | 架构骨架、Room 7 大实体与 DAO、DataStore 配置、高对比度主题 | ✅ 100% 完成 | `AppDatabase.kt`, `AppSettingsDataStore.kt`, `Theme.kt` |
| **Milestone 1** | CameraX 拍摄系统、统一水印排版引擎 (1:1 预览与照片)、快门时间戳、Scoped Storage 存储、EXIF 元数据 | ✅ 100% 完成 | `CameraManager.kt`, `WatermarkLayoutEngine.kt`, `MediaStoreManager.kt` |
| **Milestone 2** | 多工程项目管理、工程切换、单屏快速新建、相册大图查看与分享/删除 | ✅ 100% 完成 | `ProjectListScreen.kt`, `GalleryScreen.kt`, `PhotoDetailScreen.kt` |
| **Milestone 3** | 水印自定义编辑器：字段开关、新增自定义属性（标段/监理单位）、排序、字号与透明度调节 | ✅ 100% 完成 | `WatermarkCustomizationScreen.kt`, `WatermarkEditorViewModel.kt` |
| **Milestone 4** | 问题模式与快速隐患登记、分类筛选与台账整合 | ✅ 100% 完成 | `QuickIssueDialog.kt`, `GalleryViewModel.kt` |
| **Milestone 5** | 隐患照片涂鸦标注系统：箭头、矩形、圆圈、画笔、文字、脱敏遮盖，全分辨率 ContentScale.Fit 坐标映射，原图/标注双图保留 | ✅ 第三轮关键链路完成 | `PhotoAnnotationScreen.kt`, `PhotoAnnotationViewModel.kt` |
| **Milestone 6** | 视频录制：录后 Media3 转码烧录统一水印、保留音频/真实宽高时长、失败保留原片并标记重试 | ✅ 代码路径已接入，需真机编解码复核 | `VideoWatermarkTranscoder.kt`, `CameraViewModel.kt` |
| **Milestone 7** | 工程归档导出：ZIP 与 SAF 不压缩文件夹、可靠 JSON/CSV、标注成品、缺失报告、失败视频后处理 sidecar | ✅ 代码路径完成 | `ProjectExportEngine.kt`, `ProjectListScreen.kt` |

---

## 验证与测试结果

1. **单元测试**：本轮 Debug/Release 各 35 项通过，0 failure / 0 error（包含相机/视频方向、字段矩阵、文字几何、坐标映射、定位新鲜度、导出与命名、水印快照测试）。
   - `WatermarkLayoutEngineTest`：经典/极简/信息板排版计算、长文本自动折行、横竖屏自适应
   - `NamingEngineTest`：重名序号自增、非法特殊字符清洗
   - `CameraCapabilityTest`：动态多焦段与变焦档位解析
   - `ProjectExportTest`：Excel UTF-8 BOM CSV 编码、ZIP 归档头部签名
2. **APK 构建**：本轮 `assembleRelease` SUCCESS，输出 `app/build/outputs/apk/release/SiteCam-0.2.7-release.apk`；Release 签名缺失时构建会明确失败，Debug 不受影响。
3. **代码 Lint 检查**：本轮 `lintDebug`、`lintRelease` SUCCESS；仅有既有兼容性/依赖版本提示，无 Error。

## 视频水印边界

录像完成后进入 Media3 Transformer，使用与照片相同的 `WatermarkLayoutEngine` 生成逐帧叠加并保留音频；针对 90/270 度编码旋转先按显示尺寸布局再映射回编码像素，只有转码成功才登记 `READY`。转码失败仍保存原片，登记 `FAILED_VIDEO_WATERMARK_RETRY`，导出附带真实元数据 sidecar 供重试，不伪称已烧录。Media3 在不同厂商编解码器上的兼容性仍需 API 36/37 真机复核。

## 数据与权限边界

首次启动只申请相机；定位是可选能力，录像按需申请录音，拒绝时仍可录制无声视频。Room 数据库不参与云备份/设备迁移备份，避免 GPS 与失效 MediaStore URI 被恢复。

## Target SDK 与备份边界

已安装 Android 36 平台，并升级 `compileSdk/targetSdk=36`、AGP 8.12.0、Gradle 8.13；Activity 使用 edge-to-edge 与预测返回兼容配置，仍需在 API 36/37 真机或模拟器复核系统栏行为。应用关闭云备份与设备迁移备份中的 Room 数据库，避免 GPS/地址与跨设备失效 MediaStore URI 被恢复。

---

## 2026-08-26 接手验收与成品化

- 新增项目独立 Release 签名配置；签名密钥与 `keystore.properties` 仅保存在本机项目目录，并通过 `.gitignore` 排除，后续版本需沿用该密钥才能覆盖升级。
- 邻接任务此前的成品为 0.2.7；本轮修改后不沿用旧的“32 项通过”结论，必须以集中验证输出更新。
- 正式签名安装包应保留在 `app/build/outputs/apk/release/`，并按当前版本命名 `SiteCam-0.2.7-release.apk`。
- APK 使用 RSA 4096 位证书，APK Signature Scheme v2 验证通过。
- 已在 `SiteCam_API37_Pixel8` (API 37) 模拟器执行冷启动/权限/首屏 smoke test：相机权限授权后 `MainActivity` 正常前台运行，工程、重点问题、设置、拍照、录像、无定位降级等首屏控件正常渲染，Logcat 未出现 `FATAL EXCEPTION`。
- 检测到一台 USB Android 真机，但本次未主动安装，避免未经明确操作意图改动用户手机。
