# SiteCam v0.4.0 双端前端审查总表

日期：2026-10-03。对应版本规划：v0.4.0（20）。本报告整合当前工作区审查、Android 本轮实际操作记录与已确认的设计约束；后续修复安排见 [REPAIR_PLAN.md](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/audit-2026-10-03/REPAIR_PLAN.md)。

## 当前完成状态

| 工作 | 当前状态 | 结果边界 |
| --- | --- | --- |
| Android 页面与交互审查 | 已完成本轮约定审查并归档 | 有手机/折叠模拟器的部分运行证据；不是全设备或全部异常路径通过 |
| HarmonyOS 源码审查 | 已完成并归档 | 鸿蒙设备尚未接回；本报告无新的鸿蒙实机通过结论 |
| 总问题表与修复方案 | 已形成 | 是待实施方案，未把下述代码修复标成完成 |
| 设计约束与适配补图 | 已归档当前 01–31 图位，其中 23–31 为 9 张新增补图 | 已更新 manifest、DESIGN_CONTRACT、ADAPTIVE_COVERAGE 和 PROMPTS；图不证明实现完成 |
| 图 12 A–D 真实桌面图标资源 | 资源更新及两端资源编译已完成 | 图 12 保持不改；未重打 APK/HAP、未重新安装，桌面实际显示待核验 |

基线文档：[Android 分报告](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/audit-2026-10-03/android/android-audit.md)、[HarmonyOS 分报告](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/audit-2026-10-03/harmony/harmony-static-audit.md)、[官方依据](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/audit-2026-10-03/OFFICIAL_REFERENCES.md)、[设计契约](/Users/ninja/Documents/工程水印相机/assets/design/frontend-v0.4.0/DESIGN_CONTRACT.md)、[设计清单](/Users/ninja/Documents/工程水印相机/assets/design/frontend-v0.4.0/manifest.md)、[适配覆盖](/Users/ninja/Documents/工程水印相机/assets/design/frontend-v0.4.0/ADAPTIVE_COVERAGE.md)。

图标更新的原始资源和提示词见 [launcher-icons/README.md](/Users/ninja/Documents/工程水印相机/assets/design/frontend-v0.4.0/launcher-icons/README.md)。Android foreground/adaptive/monochrome 和 HarmonyOS AppScope/entry 的 base/dark 资源已同步；Android `:app:processReleaseResources` 通过，资源引用/无重名及 D 单色图居中 80% 安全区已检查；鸿蒙在既有 SiteCamHarmonyBuild 缓存仅同步图标后 `default@CompileResource` 成功。这些结果支持“资源可编译”，不支持“新安装包或桌面切换已验收”。

设计仍保留明确采用边界：图 27 的内屏/悬停和图 29-v2 左上画框并非精确 4:3，图 25 样式页签有误高亮；按覆盖文档只取布局关系，实际取景比例和样式归属以文字契约为准。

## 证据口径与运行基线

- **运行确认**：本轮确实执行的动作与对应画面；只支持该运行包、设备、窗口和步骤。不能由一处通过推导全设备通过。
- **源码确认**：具体调用链、分支或状态机制可在源码中定位。缺少设备复现时，不使用“实机已出现”“卡死已复现”等表述。
- **源码风险**：存在具体可发生的异步、布局或事件条件，实际触发与影响尚需定向复现；不与运行缺陷混计为已复现。
- **设计差异**：与本轮最新明确产品/导航要求不一致。可直接安排实现，但不是所有差异都表示旧代码崩溃。

Android 本轮使用已有 `app/build/outputs/apk/release/app-release.apk`，安装后读取为 `com.sitecam.app` 0.4.0 / code 20；APK 时间为 2026-10-03 00:37:09，大小 18,090,646 字节。审查未证明该 APK 与当前全部源码逐字相同，因此源码根因与运行现象保持关联但不冒充同一次构建证明。本轮未计算文件哈希。

| 运行环境 | 已覆盖 | 证据位置 |
| --- | --- | --- |
| SiteCam_API37_Pixel8 / emulator-5554 / Android 17 API 37 / 1080×2400 / density 420 | 保留旧数据升级；独立审查工程；拍照、无声视频、视频问题、自由裁剪/箭头编辑、ZIP 到系统分享选择页后取消；竖屏/左横屏、字体 1.0/2.0 | [Android evidence 目录](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/audit-2026-10-03/android/evidence) |
| SiteCam_030_Fold / emulator-5556 / Android 17 API 37 / 展开 2208×1840 / density 420 | 独立 release 首启；OPENED/HALF_OPENED/CLOSED；外屏 1080×2092；工程与相机布局 | 同上；半折所得垂直分区不等于横向悬停验收 |
| HarmonyOS | 源码及当前 SDK 声明检查 | 无本轮设备运行；历史窗口/截图不纳入通过证明 |

Android 收尾已恢复两台字体 1.0、手机系统自动旋转与 user_rotation、折叠 state override；手机应用内拍摄方向仍为左横屏，当前工程仍是独立样本。未删除原有工程或其资料。样本及更完整操作说明保留在 Android 分报告。

## 总问题表

保留原始 8 个 AND 编号与 18 个 HAR 编号，共 26 条记录：9 条 P1、17 条 P2。只有 AND-001～AND-005 共 5 条包含本轮运行现象；其余条目为源码确认或风险，并分别写明确定性。优先级表示影响和修复顺序，不表示证据已升级为实机确认。

### Android

| 编号 / 优先级 | 结论与确定性 | 关键证据及定位 | 修复批次 |
| --- | --- | --- | --- |
| AND-001 / P1 | 展开折叠右栏录像入口缺失、半折只剩极窄文本；运行与源码均支持 | [展开画面](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/audit-2026-10-03/android/evidence/13-fold-open-camera.png)、[半折画面](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/audit-2026-10-03/android/evidence/14-fold-half-camera.png)；CameraBottomBar.kt:336–396 固定内边距/间距压入窄栏，CameraGeometry.kt:23–45 | B1 |
| AND-002 / P1 | 横屏日期月份 7–12 不可达；运行确认 | [日期画面](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/audit-2026-10-03/android/evidence/06-date-landscape.png)；GalleryScreen.kt:175–204 固定/不可滚动内容 | B3 |
| AND-003 / P2 | 无录音/无定位提示覆盖工程入口；运行确认 | [权限提示](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/audit-2026-10-03/android/evidence/08-video-permission-overlap.png)、[外屏](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/audit-2026-10-03/android/evidence/15-fold-closed-camera.png)；CameraScreen.kt:874–895 顶栏与提示原点不同 | B1 |
| AND-004 / P2 | 竖转横后焦圈偏离触点；运行确认，不能写成 Android 完全无对焦响应 | [横屏焦圈](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/audit-2026-10-03/android/evidence/11-focus-after-rotation.png)；操作注入点 (900,400)，圈心约 (756,547)；CameraScreen.kt:586–599 手势闭包保留旧 geometry | B5 |
| AND-005 / P2 | 仍为三点→中间拍摄设置；运行确认且不符合最新导航决策 | [首页控件](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/audit-2026-10-03/android/evidence/02-camera-controls.png)；CameraTopBar.kt:103–163。Android 工程箭头已是同排矢量，不重复套用鸿蒙问题 | B2 |
| AND-006 / P2 | 焦点工厂绕过 PreviewView 显示变换，未观察 AF 异步结果；源码确认，真实光学影响未测 | CameraManager.kt:568–577；FocusRing.kt:23–49；[CameraX 官方依据](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/audit-2026-10-03/OFFICIAL_REFERENCES.md) | B5 / X1 |
| AND-007 / P2 | 管理页新建保存会切换当前拍摄工程，不符合独立“去拍摄”语义；源码确认，运行尝试被系统覆盖层打断 | ProjectViewModel.kt:277–320，尤其 311；ProjectListScreen.kt:612–625。不得把被取消的 AuditB 输入算作复现成功 | B2 |
| AND-008 / P2 | 水印字段内容/开关以捕获的完整实体异步写回，有覆盖较新字段的风险；未做定向竞争或故障复现 | WatermarkEditorViewModel.kt:116–120、142–143；CameraViewModel.kt:706–723；样式/字号/透明度已有 FIFO，不能把它们混为同一缺陷 | B4 / X2 |

Android 精确文件路径、复现和最小修法见 [Android 分报告的问题表](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/audit-2026-10-03/android/android-audit.md:15)；修复文件全路径见修复方案。

### HarmonyOS

| 编号 / 优先级 | 结论与确定性 | 源码位置 | 修复批次 |
| --- | --- | --- | --- |
| HAR-S01 / P1 | 宽屏设置分组用按值 Builder 参数；状态依赖缺口可确认，当前设备右栏不刷新/假卡顿尚未复现 | SettingsPageView.ets:19、30、37；官方状态依据见下文 | B2 |
| HAR-S02 / P1 | 相机全窗口坐标与浮层可用窗口坐标混用；静态确认，遮挡程度待测 | WindowEnvironment.ets:67、127–134；Index.ets:243–248、340–351、734、809–822、859、906–910、999–1001 | B1 |
| HAR-S03 / P2 | 仅保留首个水平折痕、窗口只记录 Y 偏移；能力缺口确认 | WindowEnvironment.ets:36、45、64、90、110–134；CameraLayout.ets:29 | B1 |
| HAR-S04 / P2 | 机型/默认屏幕驱动大屏分类，网格/侧栏仍有固定空间假设；规则确认，全部设备影响未测 | DeviceLayoutProfile.ets:5–22；Index.ets:916、936；GalleryPageView.ets:64、73–74；ProjectsPageView.ets:20 | B1 |
| HAR-S05 / P1 | 工程箭头为文本；缺独立方向与直达设置，仍保留三点中间页/重复问题开关；设计差异确认 | Index.ets:727–745、345、347、857 | B2 |
| HAR-S06 / P2 | Down 即对焦与双指缩放竞争，无 AF 状态反馈；源码缺口确认，不能断言镜头完全不工作 | Index.ets:872–881；CameraService.ets:439 | B5 |
| HAR-S07 / P1 | 实际导出不进入唯一带进度/取消的页面；调用链确认 | Index.ets:683–684、290、950–951、980–994；ExportService.ets:21、36、49 | B6 |
| HAR-S08 / P1 | 工程/问题弹窗在异步保存前关闭；busy 丢弃或保存失败时不能保留输入；错误路径确认 | ProjectDialogs.ets:31；QuickIssueDialog.ets:29；Index.ets:301、620、675–676 | B3 |
| HAR-S09 / P1 | 裁剪边界跨目标或连续裁剪复用；状态链确认 | Index.ets:182–185、694–695；CropDialog.ets:26–28、71 | B3 |
| HAR-S10 / P1 | 深层字段原地写入不产生父 @State 所需通知；机制与代码确认，具体画面待测 | Index.ets:170、940；Models.ets:58、78；WatermarkPanels.ets:44 | B4 |
| HAR-S11 / P2 | 已有日期范围重开默认 MONTH/当前月，直接应用会替换范围；静态确认 | DateRangeDialog.ets:78–103 | B3 |
| HAR-S12 / P2 | 首启引导→权限完成后返回 guide；导航链确认 | Index.ets:377、942、948；PermissionGuidePageView.ets:13–18 | B2 |
| HAR-S13 / P2 | 工程详情内栈与根返回分离；显式路径差异确认，框架实际返回分发次序未测 | ProjectsPageView.ets:4–14；Index.ets:294 | B2 |
| HAR-S14 / P2 | 离开相机等待释放后才换页；打开多个 await 后才捕获 token；等待/失效窗口确认，设备卡顿与竞态结果未测 | Index.ets:411–423、527–570；CameraService.ets:538–548 | B5 / X3 |
| HAR-S15 / P2 | 多个设置保存 Promise 未等待/处理失败；错误路径确认 | Index.ets:944；Store.ets:39 | B4 |
| HAR-S16 / P2 | 浅色弹窗未选按钮使用深色字配固定深色底；颜色组合确认 | ProjectDialogs.ets:28；QuickIssueDialog.ets:25、27；DateRangeDialog.ets:123、136；base/element/color.json | B3 |
| HAR-S17 / P2 | 标注触摸没有取消/活动指针归属，空文字仍更新历史；分支缺口确认 | Index.ets:700–704 | B3 |
| HAR-S18 / P2 | 若干辅助触区不足、裁剪小预览/滑条呈现及解释小字未符合契约；设计差异确认 | Index.ets:731–733、936；ProjectsPageView.ets:17；GalleryPageView.ets:60；CropDialog.ets:49–73 | B1、B3 |

每条的完整证据、官方接口、最小修复与回归见 [HarmonyOS 分报告](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/audit-2026-10-03/harmony/harmony-static-audit.md:36)。

## 双端共同根因与交叉核对

这些是对上述条目的归并，不另增加缺陷数量。

| 共同根因 | 当前证据 | 修复所需统一规则 |
| --- | --- | --- |
| 当前窗口和实际子区域没有作为唯一布局输入 | Android ScreenEnvironment.kt:32–44 使用 maximum metrics，ScreenProfile.kt:8–17 依型号；鸿蒙 DeviceLayoutProfile 与默认显示分类；两端混用 600/840 阈值 | window 当前尺寸只决定外层；每个 pane 按自己的约束、字号和最小可用内容宽高决定横排/双栏/网格。不能用型号、平板标签或最大屏幕替代 |
| 四边避让和局部原点分散 | AND-003、HAR-S02；相机背景、控件、提示、浮层分别计算偏移 | 统一当前方向四边 insets 与 cutout；绘制背景和交互安全区分开；明确哪些层已经消费避让，避免重复扣除或漏加原点 |
| 二维折叠信息未贯穿组件 | Android CameraGeometry 分区能力与测试覆盖有限；HAR-S03 只水平单矩形/单轴偏移 | 使用官方当前折叠特征/全部矩形；确认接口坐标空间，显示坐标先减窗口 X/Y，再转入 pane 本地。窗口本地数据不能再减一次；逐矩形求交后布局 |
| IME 与长内容未作为独立约束 | AND-002、HAR-S02/S04/S18；部分输入流程被系统手写教学覆盖，未获运行结论 | 保留独立 IME 状态/区域；内容区可滚动，主要提交/取消区可达；不要通过缩小系统字体或增加说明小字解决 |
| 页面、局部 UI、提交状态分散 | HAR-S01/S08/S10/S12/S13/S15、AND-007/008 | 页面状态、所选对象/集合、筛选、草稿与异步提交归属明确；窗口变化只重排，不重建业务状态、不隐式切工程、不重放保存 |
| 触摸/反馈/硬件坐标不是同一变换链 | AND-004/006、HAR-S06 | 点击和焦圈使用预览本地坐标；硬件映射由预览实际变换产生；只对完成识别的点击发起对焦，观察结果 |

管理页新建语义需要双端一致修复：Android 已记为 AND-007；鸿蒙管理页 [Index.ets:718](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:718) 与相机工程切换面板 :351 都调用 `editProject(new Project())`，:676 的 creating 分支无条件 `selectProject`。这是同一问题的交叉调用链补充，未增加独立编号、未做鸿蒙运行确认。修复应明确区分“管理保存”和“相机创建并使用”。

### Builder 与水印状态结论的确定性

[ArkUI 官方 @Builder 文档](https://github.com/openharmony/docs/blob/master/zh-cn/application-dev/ui/state-management/arkts-builder.md) 说明按值参数不会保持状态变化到内部构建 UI 的同步关系。HAR-S01 的实参是 `selectedGroup`，内部条件判断是普通 `group:string`，因此状态传递写法缺口可由机制和源码确认；其他父层依赖是否偶然导致重建、当前设备表现是否等同用户所述“卡死”，仍需实际测量。不要把该问题写成已证实线程死锁。

[ArkUI 官方 @State 文档](https://github.com/openharmony/docs/blob/master/zh-cn/application-dev/ui/state-management/arkts-state.md) 说明直接嵌套属性赋值不在基础 @State 的观察范围。HAR-S10 的普通 Field/Watermark 深层原地变更不保证父预览刷新；输入框自身局部更新不能作为父状态已同步的证明。最小修复是明确的新值赋回或局部可观察状态关系，不要求全项目升级状态框架。

## 应保留的能力与已经存在的保护

- Android 已有运行对焦圈反馈、宽屏设置分类切换；鸿蒙的问题不能不加区别地套到 Android。
- 鸿蒙水印画布/快门白闪层已设置 `HitTestMode.None`，不能把预览全区故障直接归因于透明遮挡。
- 鸿蒙编辑目标/revision 防过期回写、快速水印串行保存、导出前重查资料归属，以及闪光支持检查/实际读回应保留。Android 模板外观已有 FIFO，不与字段整实体更新风险混同。
- 两端既有水印内容宽度测量不代表八样式已完成照片/视频验收；继续使用统一布局，不重做样式身份、字段顺序或输出业务。
- 包名/应用标识、版本规划、数据库与媒体身份、原签名、导出格式、平台相册语义、位置和权限能力保持。允许的相机坐标与字段 DAO 最小跨层修复在修复方案单列。

## 完成边界与覆盖空白

本轮已交付审查、修复方案、设计补图与通过资源编译的图标更新；后续 B1–B6 产品代码修复尚未在此报告中验收。设计图和资源编译均不能代替设备运行验收。

| 未覆盖/未完成 | 已有证据的准确含义 | 后续只补相关检查 |
| --- | --- | --- |
| 鸿蒙所有当前包运行路径 | 只有源码与 API 检查，设备未接回 | 接回后先设置入口/五组刷新、对焦、首启返回，再按修复批次 |
| Android 当前源码与运行 APK 完整对应、新 A–D 图标 | 本轮读取到版本 0.4.0(20)，测试发生在图标改动前 | 修复/图标最终包按实际构建来源确认；不复用旧截图冒充 |
| 真实光学对焦、前后/多摄、闪光、真实声音与中断 | 模拟器已验证部分入口/反馈，不能证明硬件效果 | 有对应硬件时验证近远物/角点、声音与后台中断 |
| 真平板竖屏、真实分屏/浮窗、横向铰链、多折痕、全部 cutout 方向 | 已有手机横屏与部分折叠姿态画面 | 当前窗口变化、实际折痕/挖孔返回值和操作可达性 |
| 管理新建完整运行链、部分 IME、水印快速输入 | 系统手写笔教学遮住操作，AuditB 创建取消；未算通过 | 排除系统覆盖层后，按对应问题重放一次 |
| 保存失败/竞争、全日期/筛选组合、全标注工具、批量/移动/删除错误 | 源码路径已读，未完整注入故障或操作 | 仅对改动路径做定向错误/并发回归 |
| 八水印样式全部照片/视频所见所得、导出内容逐项 | 有短/长预览与样本、ZIP 到系统选择页；未完成全部内容验收 | 对受影响渲染/提交链验证，保持既有八样式与格式 |
| 全页动效、低性能帧率、折叠过渡 | 部分实际动作录像可用；14-fold-transition 的有效片段不覆盖切换，18-fold-motion 有并发控制疑点 | 这两段不用于通过结论；有改动时取相应有效过程证据 |

旧 `docs/verification/frontend-v0.4.0/coverage-plan.md` 属于此前计划快照，其“设备未连接/尚未运行”不能覆盖本轮 Android 已完成记录；新的适配覆盖文档仍是后续布局/验收要求，不能与本轮实测表互换。
