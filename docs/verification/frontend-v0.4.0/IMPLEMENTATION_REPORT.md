# SiteCam v0.4.0 实施与验收索引

记录日期：2026-10-03。Android、HarmonyOS 均为 0.4.0 / code 20。原21张功能图及新增22号紧凑大屏参考已归档，最终规范见 `assets/design/frontend-v0.4.0/DESIGN_CONTRACT.md`。本表区分实际运行、自动测试和源码确认；`coverage-plan.md` 是准备阶段计划，其中“无设备/无Debug脚本”并非最终状态。

## 最终安装包与构建

| 用途 | 本地文件 | 最终构建证据 |
|---|---|---|
| Android 正式签名 APK | `app/build/outputs/apk/release/app-release.apk` | `build/android-final-debug-release.log` |
| Android 模拟器 Debug APK | `app/build/outputs/apk/debug/app-debug.apk` | 同上 |
| Harmony 原身份 Debug 签名 HAP | `harmonyos/dist/debug/SiteCam-0.4.0-HarmonyOS-Debug-Signed.hap` | `build/harmony-final-debug-signed.log` |
| Harmony 原身份 AppGallery 签名 HAP / APP | `harmonyos/dist/appgallery/SiteCam-0.4.0-HarmonyOS-AppGallery-Signed.hap` / `SiteCam-0.4.0-HarmonyOS-AppGallery-Signed.app` | `build/harmony-final-store-signed.log` |
| Harmony 模拟器运行 HAP | `harmonyos/dist/entry-default-unsigned.hap` | `build/harmony-final-unsigned-simulator.log` |

上述最终构建均成功。Android 沿用仓库现有 `sitecam-release.jks` / `keystore.properties`。Harmony 脚本分别复用 `~/.ohos/sitecam-debug/signing-config.json`、`~/.ohos/sitecam-release/signing-config.json`，未新建或替换身份，未将凭据写入仓库。模拟器已有 `com.sitecam.app_` 身份，原签名 Debug HAP 对其覆盖安装被系统以签名不匹配拒绝；没有卸载或清除数据，实际运行使用与其原身份一致的 unsigned 包。签名交付包与模拟器运行包明确分列。

未修改数据库/模型、相机与定位服务、媒体处理与导出服务、SDK、原签名文件。授权范围外的未跟踪 ZIP、备案文档及历史 Huawei 验证目录未操作。水印公共排版器的内容宽度修复，是用户追加明确授权的唯一输出布局改变。

## 实际环境与有效截图

- Android：现有 SiteCam_API37_Pixel8 / API 37 ARM64，`emulator-5554`，运行 `com.sitecam.app.debug`，系统确认 0.4.0 / 20。横屏对照窗口 1280×800px、density 160（1px/dp）；4:3取景1066.7×800，右栏约213.3dp。快门100dp、缩略64dp、翻转48dp、倍率48dp高且宽度不超过220dp。手机截图1080×2400px、density420（约411×914dp）。尺寸与密度已恢复，字体1×，系统恢复浅色。
- Harmony：现有 SiteCamStorePhone / HarmonyOS 7 / API 26，`127.0.0.1:5557`。横屏1920×1080px、density2.625（约731×411vp），4:3取景1440×1080，右栏约183vp。短高快门210px=80vp、缩略168px=64vp、翻转126px=48vp。相机真实只公开1×，倍率容器按单档收紧。竖屏1080×1920px。
- 无物理设备。未把模拟器当真机、折叠铰链、外屏授权闭环或设备光学能力证据。
- 最终横屏：`android/03-camera-landscape-compact.png`、`harmony/03-camera-landscape-compact.jpeg`。画面中的彩块/白色及暗场是模拟器摄像头内容，不是应用白色容器。预览与拍照均保留原4:3画幅。
- 最终主题：`android/11-settings-phone-light.png`、`android/11-settings-phone-dark.png`。先前误标成深色的浅色截图已经重取覆盖。
- 最终水印编辑：`android/10-acceptance-runtime-tablet.png`、`android/10-watermark-appearance-phone.png`、`harmony/10-watermark-appearance-final.jpeg`。实际验收记录标签完整。两端 `10-watermark-font-small-*` / `10-watermark-font-large-*` 是滑块0.6× / 2.0×运行对比，完整可读且字号明显变化，操作后恢复1.0×。
- 当前目录保留阶段截图便于追溯。无 `compact` / `final` 后缀的旧横屏及部分旧水印缩略图不作为最终尺寸、自然底板或编辑预览结论。`android/baseline-old-release-permissions.png` 是原正式0.3.4基线，排除于本次验收。

## 水印测量与回归

公共布局按实际画笔测量最长显式换行段落，保留原标题/高亮字号与表格列比例，增加原内边距及1px测量余量，以上一版最大宽度为上限。不同样式继续使用各自既有字段呈现语义。短内容收紧，长工程/地址按原规则换行，不删字段、不改字号或设置。Android 预览、照片与视频继续共用布局/画笔；Harmony `ImageProcessor → paintWatermark` 与 `VideoService → watermarkPixelMap` 共用排版器。Android 小视窗预览按1080基准归一后缩放，消除原字号下限在外屏上造成的比例放大。编辑/样式比较预览单独拟合可用空间，不用于成片。

- Android 完整原生 graphics 渲染类：10-02 23:42，6项通过；布局单测：23:45，9项通过。见 `build/TEST-com.sitecam.app.core.watermark.UnifiedWatermarkRenderingTest.xml`、`build/TEST-com.sitecam.app.core.watermark.WatermarkLayoutEngineTest.xml`。包含8样式完整字段、真实字体边界、照片/视频同像素和视频90°/270°旋转。
- Android 最终定向回归：10-03 00:06，水印4项 + 相机几何3项，0失败/0错误。见 `build/android-final-targeted-tests.log`、`build/android-watermark-final-targeted.xml`、`build/android-geometry-final-targeted.xml`。覆盖短内容、长工程/地址、字段关闭、显式换行、.6/2字号、横竖位置与8样式边界，并验证120/240/360px小视窗的预览比例。最终随后的改动只有UI预览、控件宽度及标签绑定，最终APK已重编译。
- 8样式的实际原生画笔照片/视频透明叠层 PNG 在 `watermark-output/`，共16张。它们是渲染测试产物，不是摄像头实拍。
- Harmony：10-03 00:11–00:12，8款真 OffscreenCanvas 测量全部通过，见 `build/harmony-watermark-width-results.json`，构建见 `build/harmony-watermark-test-build.log`。覆盖4种画幅含240×180、.6/1/2字号、上下角位置、短长字段、开关及显式换行。只报告这8项定向检查；未声称旧综合 testability suite 全部执行通过。测量测试最初因主线程连续工作触发模拟器6秒冻结，改为复用测量Canvas并逐布局让出事件循环后完成；蓝白工程记录测试按其既有“标签:值”同一行修正断言，没有改样式内容。

## 逐图完成口径

源码简称 A = `app/src/main/java/com/sitecam/app/`，H = `harmonyos/entry/src/main/ets/`。每行均说明实现或合理沿用，未取得运行图的项目仅确认源码。

| 图 | Android 实现 / 证据 | Harmony 实现 / 证据 | 本轮运行边界 |
|---|---|---|---|
| 01 手机主页面 | 相机主页无主导航、常显水印/记问题；`android/01-camera-phone-final.png` | 同结构原生控件，管理随系统主题；`harmony/01-camera-phone.jpeg`、最终03 | 主入口模拟器；无真机 |
| 02 平板工程 | 紧凑列表＋真实媒体右栏；卡片浏览不切当前，独立去拍摄；`android/02-projects-tablet-light.png` / dark | `H/pages/components/ProjectsPageView.ets`双栏真实媒体、媒体先进入相册再开详情 | Android双栏运行；Harmony双栏源码 |
| 03 横屏 | 4:3贴边、左上紧凑工程、右侧全高分布、无白手势条；最终compact图 | 同上、刷新并入右上工具，单档倍率适宽；最终compact图 | 两端真实模拟器窗口，按密度确认 |
| 04 录像/工程/更多 | 原录像回调、真实镜头过滤、更多与顶部工程切换；A/feature/camera | 更多限高滚动、遮罩与返回先关；`harmony/04-camera-more-compact.jpeg` | 更多实际运行；录制见下方媒体记录 |
| 05 定位/锁定/归档 | 沿用已有状态与服务，保留刷新/授权入口；A/feature/camera/CameraScreen.kt | 按项权限判定、定位永久拒绝入口可去设置；H/pages/Index.ets | 无真实GPS/撤权完整实测 |
| 06 外屏 | 移除展开覆盖、始终更多通水印/设置/真实镜头，避免水印遮镜头按钮 | 移除管理覆盖，紧凑更多保留现有操作 | 几何/源码；无真实外屏 |
| 07 排序/批量/表单 | 既有排序及批量语义沿用；卡片编辑/导出常显；A/feature/projects/ProjectListScreen.kt | 既有排序/批量沿用，工程表单最大600vp；ProjectsPageView/ProjectBatchActionDialog | 源码确认，未逐项执行批量数据操作 |
| 08 分类/更多/空态 | 既有分类选择/删除确认，不新增CRUD，去附加小字 | 原生分类选择及删除确认沿用，更多不再旁路切当前工程 | 源码确认 |
| 09 内容/字段 | 三tab、宽屏预览并排、编辑/排序/开关；最终phone与tablets图 | WatermarkPanels三tab、真实现有字段绑定、弹层返回先关；最终appearance图 | 两端编辑实际运行，未逐项改写全部字段 |
| 10 八样式/大屏编辑 | 大预览＋2列选择，整体最大1080dp；候选确认语义；验收大预览及.6/2实图 | 同8样式原生选择、UI拟合预览、字号/透明度直接响应绑定；最终10图 | 两端预览运行；8款输出自动测试 |
| 11 明暗设置 | 5组及大屏目录、内容上限720；真light/dark图 | 同5组原生页面，原生系统主题资源；浅色运行图 | Android明暗实际；Harmony系统深色仅源码 |
| 12 画质/图标/关于 | 沿用真实配置与关于，管理主题及布局 | 原生画质/图标/关于沿用，系统返回先退设置子页，版本更新 | 源码确认；未替换用户图标 |
| 13 权限 | 首次引导/管理区分，每项先系统申请，内容上限720；13管理实图 | SDK按项授权/未弹窗永久拒绝判定，允许大致位置即满足位置组 | 管理页运行/源码闭环；首次从未请求→拒绝→设置完整链未重置实测 |
| 14 帮助 | 大屏目录/正文并排、正文上限720，实际USER_GUIDE入口/版本同步 | 原有≥840双栏合理沿用，正文/重播有上限、帮助入口同步 | 源码确认，未逐章运行 |
| 15 四步引导 | 既有4步/滚动合理沿用，内容560上限及主题 | 既有4步原生滚动与560上限合理沿用 | 源码确认；未清引导完成状态 |
| 16 悬停/大字/深色相册 | 折痕按完整窗口坐标、控件独立安全区，相册自适应网格/主题 | cameraLayout折痕分区、管理不阻断外屏 | 几何自动测试及源码；无真实悬停/铰链/分屏/全页面大字运行 |
| 17 筛选/多选 | 紧凑网格、筛选弹层、多选时操作；17 filter/selection图 | 同结构、返回先关筛选/退出多选；空批量问题回调不显示操作 | Android筛选/多选实际，Harmony源码 |
| 18 详情/标注/裁切 | 详情暗色、宽/横屏预览工具并排、转换FlowRow/滚动、自由裁切；18新照片图 | 原大屏并排沿用，短横工具滚动，自由裁切原处理 | 两端新保存照片详情运行；标注/裁切保存本轮仅源码 |
| 19 视频/问题 | 原视频详情/问题服务，问题表单IME滚动、等级状态换行 | 同原生问题表单滚动，继续真实回调 | Android问题表单/录像保存与播放实际；Harmony视频仅源码 |
| 20 导出/移动/日期 | 原选择/导出处理，UI滚动分组，去内部数字/小字 | ExportChoice/Export/Move/DateRange原业务沿用，弹层限宽可滚动 | UI/源码；本轮未再次执行完整导出文件选择交付 |
| 21 关键反馈 | 沿用真实空/锁/定位/处理中反馈、返回来源修复 | 同，弹层遮罩及更多/设置/相册/水印分层返回消费 | 更多遮罩实际；其它状态源码，未模拟所有失败 |
| 22 紧凑大屏补充 | 100/64/48dp与短高80上限，多列/双栏及表单宽上限；compact实图 | 80/64/48vp短高及单档倍率/4:3；compact实图 | 两端代表窗口通过，非任意设备全覆盖 |

## 媒体保存与未测项

Android 最新源码包实际完成一次拍照保存，生成 `示例工程项目_20261003_001909_686.jpg`，相册数量从1变2，打开详情看到了按内容收紧的完整5字段底板。证据 `android/18-saved-photo-new-watermark.png`。这条是摄像头保存路径，与 `watermark-output/` 的渲染测试分开记录。

Harmony 最新同源包实际于00:27:16完成拍照保存，显示“照片已保存到工程相册”，相册从1张变2张，打开新媒体 gallery-media-2 详情确认五字段自然底板。证据 `harmony/18-saved-photo-new-watermark.jpeg` / 对应JSON。Android/Harmony均使用模拟器摄像头，未称真机拍摄。

Android 最新包另实际开始并停止一次录像，保存 `示例工程项目_20261003_002741_993.mp4`，相册显示“3 项 · 照片 2 · 视频 1”，见 `android/19-gallery-video-saved.png`。在已有系统播放器实际播放至0:28 / 0:57，完整五字段自然底板可见，见 `android/19-recorded-video-playback.png`。此为实际编码/保存/播放路径，与原生叠层测试区分；未对音轨或逐帧时序作额外结论。详情缩略图采用Fit修复了原Crop裁掉字段的问题，网格仍保留Crop。暗色详情同时使用浅色系统栏图标，返回管理页恢复系统主题。最终APK已包含这两项展示修复。00:41之后同源截图 `android/19-video-new-watermark.png` / `18-saved-photo-new-watermark.png` 显示浅色系统栏图标且字段完整；`android/17-gallery-final-return-light.png` 确认离开详情后恢复浅色管理页和深色图标。

Harmony视频编码保存、完整工程导出交付、首次权限拒绝与系统设置往返、真实折叠/外屏/铰链/分屏及跨全部页面大字体，目前均未列为实测通过。已有8样式渲染与边界测试不能替代这些设备/业务回归。
