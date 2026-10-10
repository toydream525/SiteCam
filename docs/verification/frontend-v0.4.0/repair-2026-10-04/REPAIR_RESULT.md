# SiteCam v0.4.0 修复结果

2026-10-04。全面检查中的 44 条问题已完成对应源码修复；Android 161 项自动测试通过，两端主包及鸿蒙测试包构建通过。设备运行结果与源码覆盖分别记录如下。

## 修复覆盖

| 问题编号 | 修复内容 | 实施记录 |
| --- | --- | --- |
| AND-CM01、AND-CM02 | 中断录像保留有效原片，先接管文件与索引，再处理成品；保存失败可恢复到既有重试入口 | [Android 核心](android-core.md) |
| AND-CM03、AND-CM04 | 脱敏块完全不透明；编辑变换在预览成功后一起提交，失败保留已有配方与绘画 | [Android 核心](android-core.md)、[页面](android-ui.md) |
| AND-CM05、AND-CM06、AND-CM07 | 标注保留原海拔；预览使用同一位置有效性规则；新快照冻结拍摄时区与保存目标 | [Android 核心](android-core.md) |
| AND-DATA-01、AND-DATA-02、AND-DATA-03 | 删除取消后的临界收尾、不可用原件的标注清理、工程删除与媒体移动一致互斥 | [Android 数据](android-data.md) |
| AND-001、AND-003 | 当前局部窗口、字号、全部折痕与安全区布局；横屏/外屏模式入口和权限提示重排 | [Android 布局](android-layout.md)、[页面](android-ui.md) |
| AND-002 | 日期长内容滚动，重开保留已有范围 | [Android 页面](android-ui.md) |
| AND-004、AND-006 | 当前预览坐标与实际测光工厂，焦圈跟随点击并观察 AF 结果 | [Android 核心](android-core.md)、[页面](android-ui.md) |
| AND-005、AND-007、AND-008 | 设置直达和独立方向入口；管理新建不自动切拍摄工程；水印字段按列顺序保存 | [Android 页面](android-ui.md)、[数据](android-data.md) |
| HCM-01、HCM-02、HCM-03、HCM-04、HCM-05 | 照片回调归属、视频跨阶段取消、视频快照字段、真实帧活动超时、颜色矩阵和范围 | [鸿蒙核心](harmony-core.md) |
| HAR-DATA-01、HAR-DATA-02、HAR-DATA-03 | 控件外观重开恢复、设置统一写入顺序、文件删除失败及中断恢复 | [鸿蒙核心](harmony-core.md) |
| HAR-S01、HAR-S02、HAR-S03、HAR-S04、HAR-S05、HAR-S06 | 设置分组刷新、局部坐标与二维折痕、窗口容量、三个导航入口、触摸与对焦反馈 | [鸿蒙页面](harmony-ui.md) |
| HAR-S07、HAR-S08、HAR-S09、HAR-S10、HAR-S11、HAR-S12 | 导出进度/取消、失败保稿、裁剪基准、字段状态通知、日期恢复、引导返回 | [鸿蒙页面](harmony-ui.md) |
| HAR-S13、HAR-S14、HAR-S15、HAR-S16、HAR-S17、HAR-S18 | 工程详情返回、相机会话、设置保存、主题配色、标注取消、触区与直接裁剪 | [鸿蒙页面](harmony-ui.md)、[核心](harmony-core.md) |

另完成图标测试 QA-01：当前 A–D PNG 解码及单色路径解析通过。旧的“读取已删除 C 前景 XML”断言已更新。

## 本轮验证

| 验证 | 结果与证据 |
| --- | --- |
| Android 自动测试 | **161/161 通过，0 跳过，45 个测试套件**。包括删除取消/移动互斥、管理创建选择、水印交错保存、原片恢复、编辑失败保稿、遮盖成品、EXIF/时区、窗口/折痕。见 [结构化结果](evidence/android-unit-test-results.json) |
| Android 主包 | debug APK 构建通过，版本 0.4.0（20）；保留数据更新既有 debug 应用。见 [构建日志](evidence/android-verification-4.log) |
| 鸿蒙主包 | ArkTS、原生代码、资源及未签名 debug HAP 构建通过。见 [最终日志](evidence/harmony-debug-build-final.log) |
| 鸿蒙测试包 | 包含新增设置顺序、视频元数据、删除恢复三个夹具，测试包编译通过；本轮没有执行设备测试。见 [测试包日志](evidence/harmony-test-build-1.log) |
| 鸿蒙颜色数值 | 使用生产转换参数的 BT.601/709、全/限幅颜色夹具通过。见 [结果](evidence/harmony-video-color.log) |
| Android Home 中断录像 | Pixel 8 / API 37、当前 debug 包、独立工程 FullAudit-20261003：录制后 Home，实际 Finalize 为 ERROR_SOURCE_INACTIVE；返回后新增媒体 ID 5，工程 ID 2，READY，24.120 秒，H.264 与 AAC。整段解码完成，577 个视频包 DTS 严格递增，抽帧包含冻结水印。见 [记录](evidence/runtime-after-home.json)、[视频信息](evidence/home-retained-probe.json)、[抽帧](evidence/home-retained-frame.png)、[运行日志](evidence/android-runtime.log) |
| Android 导航/对焦 | 设置直接进入完整页面；竖转横后点 (900,400)，焦圈中心与该触点一致。见 [设置](evidence/03-settings-direct.png)、[焦圈](evidence/05-focus-after-rotation.png) |
| Android 横屏/大字 | 横屏拍照、录像及主要动作可见；200% 字号时相册按容量收为单栏，相机主要控件仍可见。见 [横屏](evidence/04-landscape-camera.png)、[相册大字](evidence/07-landscape-font200.png)、[相机大字](evidence/08-camera-font200.png) |
| Android 日期 | 横屏月份列表可滚至 10–12 月，应用/取消保持可达。见 [年末月份](evidence/09-landscape-date-december.png) |

初次回归暴露的两个 JVM 环境边界已修正：FileProvider 元数据和 Robolectric 的文件描述符 EXIF 支持；真实 Room、磁盘清单、JPEG 结果及失败断言仍保留，生产逻辑未为测试环境增加分支。原失败日志保留，不覆盖为成功记录。

## 设备验收剩余项

- 本轮无鸿蒙设备连接，新增三个存储夹具、全部设置分组、连续裁剪/手势取消、导出与相机会话仍待鸿蒙实际运行。
- 实际折叠姿态与多折痕、真机近远 AF、锁屏和硬件编码错误、音频听验、鸿蒙视频颜色与长视频尾段，尚未取得本轮设备结果。
- A–D 图标资源解析通过；四个桌面图标逐项切换、重启恢复以及全部八种水印的完整产物矩阵未在本轮重跑。

本轮只运行一台 1536 MB、2 核安卓模拟器，已关闭；构建和设备测试串行，结束时内存压力正常。既有工程与媒体保留，未发布版本、上传商店或更换签名配置。鸿蒙主包留在既有构建缓存，未安装到设备。
