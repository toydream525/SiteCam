# SiteCam v0.4.0 双端前端修复方案

日期：2026-10-03。输入为 [AUDIT_REPORT.md](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/audit-2026-10-03/AUDIT_REPORT.md) 中 8 项 Android 和 18 项 HarmonyOS 记录。本文件给出可执行的后续改动与验收关卡；B1–B6 尚未实施或验收完成。图 12 A–D 资源已独立更新并通过两端资源编译，实际打包安装后的桌面核验待完成，状态见末节。

## 本轮固定边界

- 保留包名/应用标识、当前版本规划、现有签名身份、工程/媒体/模板/字段 ID、既有数据库结构及用户资料。不得为界面改造重新生成签名、清空用户数据或重新定义媒体归属。
- 前端为主：页面、布局、导航、触摸、反馈、草稿和既有异步调用连接。必要的 CameraX 测光坐标、相机生命周期归属、水印字段列级提交列为独立跨层小改动，不扩展相机/存储/导出业务。
- 保留拍照/录像、媒体处理、定位与手动地址、权限、系统相册、导出、问题记录、编辑和水印能力。八种水印身份、字段和顺序、字号/透明度/位置与现有输出规则保持；底板仅按已授权的内容宽度规则修正。
- Android“同时在系统相册显示”与 HarmonyOS“另存到系统相册”保持各自原语义。Android 控件外观保留普通；鸿蒙保留普通/已有原生毛玻璃，不加模拟模糊或液态玻璃。
- 不新增分类增删、批量问题业务、账号/云/AI、专业相机能力、裁剪比例预设、导出格式、复制开关或视频质量选项。
- 遵循 [DESIGN_CONTRACT.md](/Users/ninja/Documents/工程水印相机/assets/design/frontend-v0.4.0/DESIGN_CONTRACT.md)、[manifest.md](/Users/ninja/Documents/工程水印相机/assets/design/frontend-v0.4.0/manifest.md)、[ADAPTIVE_COVERAGE.md](/Users/ninja/Documents/工程水印相机/assets/design/frontend-v0.4.0/ADAPTIVE_COVERAGE.md)。只采用清单当前版本：01–31 图位，23–31 为新增补图；04 为 v3、27 为 v2、29 为 v3。
- 图 27-v2 的内屏/悬停已收紧画框并留黑边；图 29-v3 左上取景框仍未精确达到 3:4，图 25 样式页签有误高亮；只采用其布局关系，画幅、样式归属与能力以文字契约为准。
- 产品界面不加入实现说明、交付状态、风险提示或解释小字；保留必要业务错误、权限信息和现有帮助内容。公开更新文案只写用户能感知的变化。

## 依赖与执行顺序

| 批次 | 主目标 | 对应问题 | 前置 | 状态 |
| --- | --- | --- | --- | --- |
| B1 | 统一窗口/安全区/二维折叠几何，修正窄拍摄栏与提示 | AND-001、AND-003；HAR-S02、S03、S04、S18 触区部分 | 已有契约与当前源码 | 待实施 |
| B2 | 相机直达导航、设置刷新、工程语义与返回 | AND-005、AND-007；HAR-S01、S05、S12、S13 | B1 的可用区域接口明确 | 待实施 |
| B3 | 长表单/日期/裁剪/标注、失败保稿与主题 | AND-002；HAR-S08、S09、S11、S16、S17、S18 裁剪部分 | B1；B2 返回来源约定 | 待实施 |
| B4 | 水印字段状态和保存、设置持久化反馈 | AND-008；HAR-S10、S15 | B2/B3 的状态与提交模式 | 待实施 |
| B5 | 对焦坐标/手势/结果与相机生命周期 | AND-004、AND-006；HAR-S06、S14 | B1 坐标基准，B2 相机导航 | 待实施 |
| B6 | 导出真实进度与取消，收束影响路径验收 | HAR-S07；双端受影响导出返回 | B2 来源返回，B3 提交反馈模式 | 待实施 |
| I | 图 12 A–D 实际资源同步 | 独立用户要求 | 图 12 原稿 | 资源更新/资源编译完成，打包安装核验待做 |

B3、B4、B5 的局部实现可在接口约定后分别推进，但共享的 Index.ets、CameraScreen 和窗口模型必须由明确的文件负责人依次整合，不能同时改写同一状态源。每批先完成对应关卡，再推进依赖该关卡的部分。

## B1：统一可用区域与响应式布局

### 具体改动

1. **窗口输入改为当前窗口。** Android 使用现有 WindowManager/Compose 当前窗口及组件实际约束，不以 maximumWindowMetrics 或机型标签决定栏数；鸿蒙以实际 windowRect 和变化事件为准，显示对象/密度与该窗口关联。保留型号信息仅用于已知平台能力兼容，不作为正常布局的主判断。
2. **建立一种明确的坐标流。** 在环境层保留当前窗口矩形、密度、字号、按类型区分的四边避让、cutout/遮挡矩形、二维折叠特征、IME 区域；转换后所有相机和浮层布局使用同一窗口本地坐标。每个 pane 接收自己的矩形与可用宽高，触摸和内容在 pane 内再局部化。
3. **按接口坐标空间转换。** 来自显示坐标的折痕/切口矩形先减 `windowRect.left/top`，再按密度换算与窗口求交；进入 pane 再减 pane 的 x/y。Android 已是窗口本地的 FoldingFeature bounds 不重复减窗口偏移。保留全部官方返回矩形与 displayId，不只取第一条、水平条或 Y 值；对平台声明为分隔/遮挡的区域避让，姿态信息与实际遮挡分别表达。
4. **区分背景、交互安全区和 IME。** 相机黑色背景可铺满；工程、三枚导航、模式、变焦、快门、水印正文与浮层位于安全区。顶部/底部/左右系统栏、cutout、导航/手势边和自由窗口标题栏随方向更新。IME 独立保存，输入页面选择合适的滚动/收缩；若平台 safeDrawing 已消费 IME 或祖先已应用 padding，不再重复扣减。
5. **布局按内容是否放得下选择。** 约 600/840 只作起始参考；按实际 pane 宽度、系统字体、栏间距和最小内容宽度判定网格/双栏。拍摄模式横排放不下时换纵排或等价紧凑排布，拍照/录像保持可见可点；权限状态提示有独立位置，不叠在工程入口上。编辑器不得仅因 width>height 就扣 320 固定栏宽。
6. **折叠与尺寸约束。** 近平方内屏保留可用的右侧拍摄栏；竖向分隔在两侧布局；横向悬停使用上取景、下方全宽操作区。取景精确 4:3/3:4，空间不足留黑边与重排。快门常规≤100dp、短高≤80dp、缩略64dp、辅助触区≥48dp、图标24–28dp、常规文字14–16sp并随系统缩放、变焦高44–48dp/宽180–220dp。
7. **窗口变化只重排。** 当前拍摄工程、查看媒体、选中 ID 集合、筛选、滚动锚点、编辑 recipe、草稿与提交状态保持在原状态所有者；不要为宽窄布局分别建两套副本，也不要在 onAreaChange/布局回调中重新保存或切工程。

### 影响文件

| Android | HarmonyOS |
| --- | --- |
| [ScreenEnvironment.kt](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/layout/ScreenEnvironment.kt)、[ScreenProfile.kt](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/layout/ScreenProfile.kt)、[CameraGeometry.kt](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/layout/CameraGeometry.kt) | [WindowEnvironment.ets](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/WindowEnvironment.ets)、[DeviceLayoutProfile.ets](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/DeviceLayoutProfile.ets)、[CameraLayout.ets](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/CameraLayout.ets)、[ProjectPanelGeometry.ets](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/ProjectPanelGeometry.ets) |
| [CameraScreen.kt](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/camera/CameraScreen.kt)、[CameraBottomBar.kt](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/camera/components/CameraBottomBar.kt)，工程/相册/设置/标注现有布局消费处 | [Index.ets](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets)、ProjectsPageView、GalleryPageView、SettingsPageView、WatermarkPanels、FoldCoverControls、ProjectSwitchPanel |

### API 与关卡

Android 保持 minSdk 26 / compile、target 36，复用现有 `androidx.window:window:1.5.1` 与 Compose insets/约束，不为常规适配增加新依赖。鸿蒙保持 API 12 基线：`getWindowAvoidArea` 起于 9、`getCurrentFoldCreaseRegion` 及矩形数组起于 10、`windowRectChange` 起于 12；不无门控调用 API 22 的 `getWindowAvoidAreaIgnoringVisibility`。依据见 [官方参考](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/audit-2026-10-03/OFFICIAL_REFERENCES.md)。

关卡 G1：在同一数据状态下完成窄→宽→窄、竖→左右横屏、短高+字号2.0、输入法显示/收起；确认模式入口、工程、快门与关闭/提交按钮可操作、取景比例准确、原状态保留。对 2D 几何转换增加必要的纯函数回归：非零窗口 X/Y、双侧 insets、多个矩形、窗口已本地坐标不重复偏移。折痕或 cutout 实机不可用时保留未测，不能用计算测试代签设备通过。

## B2：明确导航与状态来源

### 具体改动

1. 两端工程名与真实 chevron 同行；独立闪光、方向、设置图标。设置一次点击直达完整设置；移除相机三点/拍摄设置中间层与重复记问题开关。方向仅自动、竖屏、左横屏、右横屏；窄窗/大字采用 2×2。方向与切镜头使用不同语义图标，既有录像禁用规则保持。
2. 完整设置保留五组及所有真实项目。鸿蒙活跃分组 Builder 直接读取 `selectedGroup`，或用带 @Prop 的局部组件维持依赖；窄屏静态分组复用可保留。不能靠重建整个页面来掩盖状态传递错误。
3. 管理页点击卡片只查看资料，独立“去拍摄”才改变当前拍摄工程。管理新建只保存并定位新卡片；相机“创建并使用”保留立即使用。Android 分离 create 的来源/结果；鸿蒙 `editProject/saveProject` 明确区分来源，避免 creating 无条件 selectProject。
4. 首启、权限管理、帮助重播记录明确完成目的地。首启权限完成进入既有工程/相机下一步；管理权限返回设置；可选权限拒绝不挡已授权拍照。保留首次申请系统弹窗与必要时系统设置的原生规则。
5. 工程和相册同一内层导航状态跨宽窄布局复用；系统返回先处理最内层详情/弹层，再返回来源页。明确设置子页、编辑未保存、权限、帮助的返回目标，避免只靠一个可变 pageOrigin 推断。

### 影响文件、API 与关卡

- Android：[CameraTopBar.kt](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/camera/components/CameraTopBar.kt)、CameraScreen、[AppNavHost.kt](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/navigation/AppNavHost.kt)、[ProjectViewModel.kt](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/projects/ProjectViewModel.kt)、[ProjectListScreen.kt](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/projects/ProjectListScreen.kt)。保留既有 Navigation/权限 API，不新增 SDK 门槛。
- HarmonyOS：Index、[SettingsPageView.ets](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/SettingsPageView.ets)、ProjectsPageView、OrientationDialog、PermissionGuidePageView、OnboardingPageView、HelpPageView。使用现有 NavPathStack/状态能力，基础 @Builder/@Prop 方案适用 API 12，不采用 API 20 新绑定辅助作为基线。
- 关卡 G2：相机三图标逐个点击→动作→取消/返回；设置五组与全部子页；A 当前→管理新建 B 保存→返回仍 A→浏览 B 不切换→点 B 去拍摄才切换；首启/设置权限/帮助重播三条路径；工程详情宽→窄后按钮返回与系统返回一致。
- HAR-S01 先以官方 [Builder 规则](https://github.com/openharmony/docs/blob/master/zh-cn/application-dev/ui/state-management/arkts-builder.md) 修正依赖，再在当前鸿蒙包验证右栏刷新；未取得设备时只标“源码修复/构建通过”，不标“卡死已消失”。

## B3：输入、日期、裁剪与标注

### 具体改动

1. Android 日期对话框内容按当前可用高度滚动，确认/取消可达，替换固定 500dp 或不可滚动 Column 的空间假设；全部月份及手动输入模式保持可用。鸿蒙从已有范围恢复模式/年月，未修改直接应用不改变原范围；保留已有非法日期、闰日与起止关系校验。
2. 工程和问题弹窗维护局部草稿、目标 ID 与 pending；保存回调可等待，成功后关闭、失败保稿。先提交草稿，成功后再刷新/替换列表和详情；取消问题失败也保持原界面状态。重复点击被明确禁用，不由全局 busy 静默丢弃确认。
3. 每次新裁剪明确以当前预览为坐标基准并从完整范围初始化；若调整已有步骤则编辑原步骤而非重复追加。A/B 照片、撤销/重做与裁剪 draft 分离。按已批准设计呈现可拖动自由边界，仍是已有自由裁切，不加比例预设。
4. 标注只追踪一个有效活动指针；Cancel、多指接管或无效手势清理未提交轨迹；空文字等输入通过校验后才压入撤销历史。保留 editTarget/revision 防旧结果覆盖与已有文件提交顺序。
5. 主题颜色成对使用页面文字/表面资源，修复浅色深字配固定深底；图标紧凑但点击区符合48dp。移除裁剪解释小字，保留必要错误与正式业务字段。

### 影响文件、API 与关卡

- Android：[GalleryScreen.kt](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/gallery/GalleryScreen.kt) 日期布局；工程/问题/标注仅在 B1 约束传播影响到对应内容区时调整，不把鸿蒙特有状态问题机械复制为 Android 缺陷。
- HarmonyOS：Index、[ProjectDialogs.ets](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/ProjectDialogs.ets)、[QuickIssueDialog.ets](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/QuickIssueDialog.ets)、[DateRangeDialog.ets](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/DateRangeDialog.ets)、[CropDialog.ets](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/CropDialog.ets)、UiPrimitives 与现有明暗资源。
- API：使用既有滚动、触摸、对话框、Promise/组件状态能力；无需提高 Android minSdk 或鸿蒙 API 12 基线。保持原业务服务调用，不新增数据库迁移。
- 关卡 G3：横屏/短高/大字/IME 下 1–12 月、年/日/范围及手动输入均可达；范围重开不变；工程/问题保存失败输入不丢、重试不串目标；A 裁剪后 B 初始化全图，同图再次裁剪/撤销/重做无意外累裁；手势取消不落标注；浅/深主题均可辨。
- 只针对易回归的状态与异步分支补必要测试：日期初始化保留、裁剪基准、保存失败草稿、无效文字不改历史。纯配色/触区尺寸不写镜像实现的单元测试。

## B4：水印即时状态与持久化

### 具体改动

1. 鸿蒙字段内容/标签/开关采用可观察的新值赋回，使完整编辑页的列表、预览与返回相机状态同步。优先局部不可变更新，不全项目迁移观察框架；依据 [@State 观察范围](https://github.com/openharmony/docs/blob/master/zh-cn/application-dev/ui/state-management/arkts-state.md)。
2. Android 内容/开关更新改为按字段 ID 的列级变更，并在同一字段的提交顺序上使用明确通道；同一列的旧提交不能晚到覆盖新值，不同列不能互相用旧完整实体覆盖。复用现有 label/order 列级写法；不改 ID、表结构与模板归属。完整页和相机快捷页使用同一规则。
3. 输入保留本地 draft，结束/返回前提交最后值；错误保留可编辑内容与重试路径。不要每个字符写入时重建整页，也不要用固定延时假装最后一次保存成功。
4. 鸿蒙画质/相册副本/声音/控件外观保存可等待、能处理失败；依据确认策略回退或重试。保留 Store.saveCurrentWatermark 已有串行快照，不替换成更弱的写法；Android 已有外观 FIFO 继续保留。
5. 同一次字段配置用于预览、照片、视频已有布局入口；不为解决刷新删字段、缩小字号或重做八样式。现有按内容宽度测量作为基础，只在确有不一致时做已授权范围内修复。

### 影响文件、API 与关卡

- Android：[WatermarkEditorViewModel.kt](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/watermark/WatermarkEditorViewModel.kt)、[CameraViewModel.kt](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/camera/CameraViewModel.kt)、[WatermarkDao.kt](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/database/dao/WatermarkDao.kt)，完整/快捷编辑 UI 的草稿与提交连接。
- HarmonyOS：Index、[WatermarkPanels.ets](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/WatermarkPanels.ets)、WatermarkEditingPreview、SettingsPageView；只有提交接口确有必要时触及 Store 的现有保存入口，不改媒体存储协议。
- API：Android 使用已依赖 Room/协程能力；列级 UPDATE 不引入 schema migration。鸿蒙采用 API 12 现有状态/Promise 能力。不得依赖提高系统最低版本解决刷新。
- 关卡 G4：连续输入→立刻开关→排序→返回→重进，最后内容、标签、开关与顺序均保留；两个并发动作不会覆盖彼此列；受控保存失败 draft 可恢复；八样式预览即时响应。照片/视频所见所得验证只在取得实际输出后标通过。
- AND-008 当前是源码风险；先用可控调度/延迟提交验证旧实体覆盖路径，再以修复后的同一路径验证，不声称本轮已丢失真实用户字段。

## B5：对焦、反馈与相机生命周期

### 具体改动

1. Android 手势与焦圈置于同一个 PreviewView/预览局部坐标基准；手势回调读最新几何，避免 pointerInput 长期捕获旧原点。当前 PreviewView 进入可用预览状态后，以其 `meteringPointFactory` 创建点，让实际裁剪、方向和镜像参与映射。
2. 观察 FocusMeteringResult/失败，反馈“请求/结果”与真实能力一致；计时消失不能冒充 AF 成功。不支持 AF 的设备保持拍照，勿用模拟器彩条证明光学焦点。
3. 鸿蒙用完成识别的单指点击触发对焦，双指缩放、拖动/取消不触发误对焦。将 surface 尺寸、预览旋转、裁剪、镜像一起形成一次变换；已有 CameraService 旋转公式不得脱离实际预览设置单独替换。
4. 鸿蒙监听支持的 focusStateChange，处理请求错误与模式收尾；按现有自动对焦策略恢复/保持模式，不凭设计图引入专业手动对焦。
5. 鸿蒙相机打开/关闭序列明确唯一归属：开头捕获生命周期 token 与 surface，每个关键 await 后验证；已失效不再 open。导航离开可先呈现目标页并由生命周期安全释放，但需确保旧 close 不关闭新会话。先记录关键阶段实际耗时，再确定是否有硬件释放拖慢导航；不加入固定等待掩盖竞态。

### 影响文件、API 与关卡

- Android：CameraScreen、[FocusRing.kt](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/camera/components/FocusRing.kt)、[CameraManager.kt](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/camera/CameraManager.kt)。以现有 CameraX 版本可用的 PreviewView 工厂和 Future 结果 API 实现，不新增拍摄流程或镜头能力。
- HarmonyOS：Index、[CameraService.ets](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/CameraService.ets)、EntryAbility 的现有前后台通知连接（仅必要时）。相关会话对焦接口在本机 SDK 标注 API 11 起可用，基线12可覆盖；调用前仍检查焦点模式支持。
- 关卡 G5a：竖→左右横→竖、窗口变化、折叠变化后中心与四角点击，圈紧随触点；双指缩放不误发单点对焦；水印区域编辑入口保持。
- 关卡 G5b：实际 AF 镜头近/远物、前后镜头、裁剪/变焦后测焦与反馈；没有对应硬件就保留未测。模拟器只可验坐标和交互。
- 关卡 G5c：相机→设置→返回、连续切换、后台/前台、surface 销毁重建；无旧回调回写和交叉释放，记录点击到目标页呈现的实际耗时。只在取得此证据后判断“进入设置卡顿”是否消除。

## B6：连接真实导出进度与取消

### 具体改动

1. HarmonyOS 现用 ExportChoiceDialog 确认后进入明确的导出进行状态，在用户实际所在流程展示已有服务进度与取消；不要只修改无入口的旧 exportPage。
2. 保留导出来源、所选工程/媒体 ID 快照与已有提交前复查；原生文件目标选择器、ZIP/文件夹、原文件与四档照片质量不变。
3. busy 不再使用户在无反馈页面失去返回/取消出口；取消请求连接 `exporter.cancelled` 并按服务真实阶段给出终态。系统选择器取消与导出错误均返回原选择，不虚报导出成功；现有部分失败报告语义保留。
4. Android 仅做受 B1/B2 影响的布局/返回回归；本轮其 ZIP 已到系统分享选择页，不据此重构导出引擎或声称内容验证已完成。

### 影响文件、API 与关卡

- HarmonyOS：Index、[ExportChoiceDialog.ets](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/ExportChoiceDialog.ets)、[ExportDialog.ets](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/ExportDialog.ets) 或当前批准的原生进度承载组件；ExportService 的 existing progress/cancel 作为接入点。若有不可取消的系统阶段，按实际能力禁用/等待，不伪造已经停止。
- API：沿用现有 DocumentViewPicker 与导出服务；本批不增加 API 版本要求，不新增外部权限、导出格式或文件树。
- 关卡 G6：单工程/多资料两种格式；进行中可见进度与可用取消；整理取消、系统选择取消、写入失败、正常完成；完成/取消后选择与返回来源合理。产物内容只在实际生成并核对后标通过。

## 必要跨层改动单列

| 编号 | 为什么不能只改外观 | 最小范围 | 保持不变 |
| --- | --- | --- | --- |
| X1 / B5 Android 焦点工厂 | UI 圈正确不代表硬件测光点正确，当前工厂忽略 PreviewView 变换 | CameraManager 接受/使用当前 PreviewView 工厂或已经由该工厂构造的点，观察现有对焦 Future | CameraX 会话/拍摄/录像业务、镜头选择与图片管线 |
| X2 / B4 Android 字段提交 | 仅让输入框不卡顿不能防整实体异步覆盖其他字段 | WatermarkDao 加入必要的按 ID 列更新，调用端使用同一字段的顺序通道；复用 label/order 已有方法 | schema、字段/模板 ID、八样式、顺序含义与数据迁移版本 |
| X3 / B5 Harmony 生命周期归属 | 页面立即切走而不处理旧 open/close 会产生交叉释放 | Index 与 CameraService 之间的 token/surface 检查及开关序列归属，必要的错误收尾 | 原拍摄、录像、照片旋转/编码、闪光能力、存储/定位接口语义 |

其他保存/草稿修复优先限制在现有 UI 回调和状态所有者；不得由“跨层”一词扩展为数据库、相机服务或媒体架构重写。

## I：真实桌面图标独立更新

用户明确以图 12 原稿为准，修改两端实际 A/B/C/D 资源，不反改设计图。A 蓝图镜头、B 工程印记、C 现场坐标、D 工程现场；名称、顺序、已有切换标识和平台支持条件保持。

| 项目 | 状态 |
| --- | --- |
| 原始视觉锚点 | [12-quality-icons-about.png](/Users/ninja/Documents/工程水印相机/assets/design/frontend-v0.4.0/12-quality-icons-about.png)，保持原图 |
| 资源制作与替换 | 已完成，源图与提示词见 [launcher-icons/README.md](/Users/ninja/Documents/工程水印相机/assets/design/frontend-v0.4.0/launcher-icons/README.md)；Android foreground/adaptive/monochrome，Harmony AppScope/entry base/dark 已同步 |
| 资源编译 | Android `:app:processReleaseResources` 通过，资源引用/无重名及 D 单色图居中 80% 安全区已检查；Harmony 既有 SiteCamHarmonyBuild 缓存仅同步图标后 `default@CompileResource` 成功 |
| 完整包与桌面切换验证 | 未重打 APK/HAP、未重新安装，实际桌面显示待下一次构建安装核验；Android 分报告使用旧运行包，不计新资源通过 |
| 鸿蒙 API 门控 | 保留现有替换图标可用版本判断；API12设备不因资源更新执行不支持的新API |

图标独立关卡 GI：设置页四个选项及桌面实际图标与图 12 同一视觉身份，A/B/C/D 切换、重启后状态与默认回退符合现有平台能力；名称/alias/资源绑定无串位。不得通过新签名或改应用标识获得安装。

## 每批完成的记录方式

每批只记录实际完成的级别：源码已修改 → 对应构建/必要检查通过 → 指定窗口/状态运行通过。记录所用包来源、设备/API、窗口/字体/姿态、步骤、实际结果与证据位置。历史构建、概念图和旧截图不能自动升级当前状态；不例行计算哈希。

测试范围随修改收敛：几何/状态/异步竞争使用能揭示回归的必要测试；触区、色彩与文字布局用实际画面和操作确认；每批通过后不无理由重复全量测试。真实用户数据不用于删除/错误注入，独立样本沿用本轮做法。当前任务不因编写本方案新增设备操作。

## 待设备与覆盖空白

- 鸿蒙所有当前运行回归待设备接回，特别是 Builder 右栏刷新、对焦实际映射、首启权限返回、保存失败保稿、裁剪残留和导出取消。
- Android 真机光学 AF、前后/多摄、闪光、有声录制、中断；真平板竖屏、真实分屏/浮窗、横向/多折痕和多方向挖孔仍缺证据。
- 系统手写笔教学覆盖导致部分输入和管理创建未完成；不以本次被取消操作证明 AND-007 已复现。
- 字段竞争与失败注入、全部日期/筛选组合、标注全工具、批量/移动/错误路径、八水印样式全部照片/视频一致性，以及导出内容逐项仍需按实际修复影响补齐。
- 新 A–D 图标资源编译已完成，但完整包构建和设备桌面状态尚未核验；旧 APK 运行记录不适用。
- 低性能帧率与全部页面动效未验收；本轮存在无有效切换段或并发控制疑点的录像不用于通过结论。

达到对应关卡后再更新问题状态；目前“审查、方案和设计补图完成”不等于“产品代码全部修复并验收”。
