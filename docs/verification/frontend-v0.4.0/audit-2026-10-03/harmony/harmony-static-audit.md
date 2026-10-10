# HarmonyOS v0.4.0 前端静态审计

审计日期：2026-10-03。范围：当前工作区的 ArkUI 页面、交互连接、窗口环境、相机界面生命周期，以及保存、编辑、导出所需的直接调用链。本文为源码审计结果；本轮未修改应用源码、未安装包、未操作设备，也未用历史截图代替当前运行验收。

依据：当前 `AGENTS.md`、`assets/design/frontend-v0.4.0/DESIGN_CONTRACT.md`、`manifest.md`，以及本轮最新明确要求：闪光灯、拍摄方向、设置齿轮独立；齿轮直达完整设置；移除相机三点菜单及中间“拍摄设置”；保留相机“记问题”；工程下拉使用同排真实图标；按当前可用窗口与系统避让信息适配。

项目最低兼容版本为 API 12，编译与目标 SDK 为 26.0.0，见 [build-profile.json5:7](/Users/ninja/Documents/工程水印相机/harmonyos/build-profile.json5:7)。下述最小修复建议以 API 12 可用方案为基础。

## 结论与处理顺序

发现 18 项需要处理或定向验证的前端问题。优先处理设置分组与水印状态刷新、保存弹窗过早关闭、不可达的导出进度/取消，再统一窗口坐标与导航。相机“无法对焦”“进入设置卡死”的实际设备表现仍需复现；源码可以证明相应缺口，但不能据此声称已重现设备故障或测得卡顿时长。

| 编号 | 优先级 | 问题 | 证据性质 |
| --- | --- | --- | --- |
| HAR-S01 | P1 | 宽屏设置分组通过按值 Builder 参数传递，缺少刷新依赖 | 静态根因已确认，设备表现待复现 |
| HAR-S02 | P1 | 相机使用全窗口坐标，浮层使用可用窗口尺寸，避让原点不统一 | 静态坐标不一致已确认，遮挡程度待设备验证 |
| HAR-S03 | P2 | 折痕只保留首个水平矩形，窗口只记录 Y 偏移 | 静态能力缺口已确认 |
| HAR-S04 | P2 | 大屏策略依赖机型/默认屏幕，部分页面固定栏宽或网格 | 静态规则不符合当前窗口适配要求 |
| HAR-S05 | P1 | 相机入口仍为三点中间页，缺独立方向和直达齿轮 | 静态设计偏差已确认 |
| HAR-S06 | P2 | 按下即对焦与双指缩放竞争，无对焦状态反馈 | 静态交互缺口已确认，对焦效果待设备验证 |
| HAR-S07 | P1 | 实际导出路径不进入唯一带进度和取消的页面 | 静态调用链已确认 |
| HAR-S08 | P1 | 工程和问题弹窗先关闭再保存，失败会丢失输入界面 | 静态错误路径已确认 |
| HAR-S09 | P1 | 裁剪边界跨照片、跨裁剪操作复用 | 静态状态链已确认 |
| HAR-S10 | P1 | 水印字段深层原地修改不触发预览所需状态刷新 | 静态根因已确认，设备表现待复现 |
| HAR-S11 | P2 | 重开日期筛选默认回到本月，直接应用会改写原范围 | 静态状态链已确认 |
| HAR-S12 | P2 | 首启引导完成后权限页继续会返回引导 | 静态导航链已确认 |
| HAR-S13 | P2 | 工程内层详情栈与根系统返回处理分离 | 静态路径差异已确认，事件分发次序待设备验证 |
| HAR-S14 | P2 | 相机导航等待硬件释放；打开流程缺少中途失效检查 | 静态等待/竞态窗口已确认，时延与错误待设备验证 |
| HAR-S15 | P2 | 设置页多个保存回调未等待或处理失败 | 静态错误路径已确认 |
| HAR-S16 | P2 | 浅色弹窗存在深色文字配固定深色底 | 静态颜色组合已确认 |
| HAR-S17 | P2 | 标注触摸未处理取消与活动指针，空文字仍写撤销历史 | 静态分支缺口已确认 |
| HAR-S18 | P2 | 辅助触区、裁剪呈现与成果纯净要求仍有偏差 | 静态设计偏差已确认 |

P1 表示会阻断关键交互、造成操作结果不明确或违反本轮核心入口要求，应先修；P2 表示局部路径、特定窗口或异常条件下的问题。

## 问题明细

### HAR-S01：宽屏设置分组缺少有效刷新依赖

- 位置：[SettingsPageView.ets:19](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/SettingsPageView.ets:19)，同文件 30、37 行。
- 触发：可用宽度达到 840 后，点击左侧“水印”“权限与隐私”等分组。
- 证据：左栏只更新 `selectedGroup`；右栏调用 `this.group(this.selectedGroup)`，`@Builder group(group:string)` 内通过普通按值参数分支。官方 Builder 规则说明按值参数不会维持父状态到内部 UI 的刷新关系；当前结构可能只改变左侧选中态，右栏仍显示初次分组。这与“设置看起来无反应”有直接关联，但尚未在当前包复现。
- 最小修复：右侧活跃分组 Builder 直接读取 `this.selectedGroup`，或提取带 `@Prop` 的分组组件；静态窄屏分组可保留复用。不要通过强制重建整个设置页解决。
- 依据：[ArkUI @Builder 参数传递与刷新](https://github.com/openharmony/docs/blob/master/zh-cn/application-dev/ui/state-management/arkts-builder.md)。不采用仅 API 20 起支持的绑定辅助方法作为 API 12 基线修复。
- 回归：连续切换全部五组；进入画质/外观子页并返回；840 两侧缩放窗口；大字号下仍能访问每组与返回。动态行值也应确认刷新，但未将返回后重建能解决的行值单独定为缺陷。

### HAR-S02：全窗口、可用窗口和浮层坐标混用

- 位置：[WindowEnvironment.ets:67](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/WindowEnvironment.ets:67)、127–134；[Index.ets:243](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:243)、248、340–351、734、809–822、859、906–910、999–1001。
- 触发：横屏刘海/挖孔、非零手势边、分屏、浮窗或输入法弹出时打开工程面板、水印面板及底部操作。
- 证据：`windowWidth/windowHeight` 扣除四边避让，但相机几何在相机页重新使用 `environment.width/height` 全窗口尺寸；相机根节点明确不加避让 padding。工程面板以可用尺寸求位置，放回全窗口时没有统一补上安全原点。工具栏只局部使用四边数据，竖屏仍从 y=0 开始；控制栏按全窗口坐标定位。所有避让类型又在环境层被合并为四个最大值，键盘与永久系统边界失去区别。撤销条另以 `max(280,windowWidth-24)` 强制最小宽度，窄于 304 的窗口仍可能溢出。
- 影响：布局尺寸、触区与浮层锚点可能不在同一坐标系；主操作、面板或撤销条可能贴边或被遮挡。不能只给某一个按钮补固定边距。
- 最小修复：保留全黑背景；为交互内容定义一个窗口本地的安全矩形与明确原点。取景器、工具栏、拍摄栏和浮层由同一矩形计算；区分系统/切口/导航避让与 IME，再按页面是否输入确定滚动/压缩策略。保留原始避让矩形或必要类型信息，窗口变化后统一重算。
- 官方接口：`getWindowProperties().windowRect`、`getWindowAvoidArea`、`windowSizeChange`、`windowRectChange`、`avoidAreaChange`；当前已经部分订阅，主要问题是信息丢失与坐标使用。API 12 可用；`getWindowAvoidAreaIgnoringVisibility` 起于 API 22，不能无门控替换。
- 回归：普通竖屏、左右横屏、短高分屏、可移动浮窗、显示/隐藏系统栏、打开/收起键盘；逐项确认闪光/方向/设置、快门、工程、水印、取消和撤销可见可点，4:3 取景比例不变。

### HAR-S03：折痕信息被压成单一水平位置

- 位置：[WindowEnvironment.ets:36](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/WindowEnvironment.ets:36)、45、64、90、101、110–134；[CameraLayout.ets:29](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/CameraLayout.ets:29)。
- 触发：纵向铰链、多折痕、折叠后窗口平移、旋转或跨显示区域。
- 证据：只有半折叠时读取折痕；只取 `width>height` 的首个矩形；环境只输出 `creaseTop/creaseHeight`。窗口偏移仅保留 Y，不记录 X；默认屏幕被当作当前显示来源，也未关联折痕 `displayId`。后续几何只能分上下区域。
- 影响：已有横向悬停分区不代表完成纵向、多折与浮窗铰链适配。
- 最小修复：保留 SDK 提供的全部二维折痕矩形和显示标识，转换到当前窗口本地坐标后与可用内容矩形求交；依据实际被分隔区域决定分区。不新增机型名称判断，也不把某一姿态的固定值推广到所有折叠设备。
- 官方接口：`display.getCurrentFoldCreaseRegion()`、`FoldCreaseRegion.displayId/creaseRects` 均 API 10 起可用；配合 API 12 的窗口矩形变化。API 12 已能表达二维、多矩形数据。
- 回归：水平/纵向折痕、内外屏切换、旋转、窗口在折痕两侧移动；确认没有控件横跨不可用区域，并记录设备实际返回的矩形。

### HAR-S04：布局仍依赖设备分类和固定空间假设

- 位置：[DeviceLayoutProfile.ets:5](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/DeviceLayoutProfile.ets:5)–22、33–35；[Index.ets:916](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:916)、936；[GalleryPageView.ets:64](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/GalleryPageView.ets:64)、73–74；[ProjectsPageView.ets:20](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/ProjectsPageView.ets:20)、36–37。
- 触发：同一平板/折叠设备缩成窄窗；普通设备获得宽窗；编辑器短高但很窄；系统大字号。
- 证据：`screenFamily` 含 Pocket/novaFlip/Pura X 字符串和上次分类粘滞规则，并读取默认显示尺寸；相册双栏还要求 `largeScreenScheme&&width>=600`，工程/设置主要用 840。相册/工程详情网格固定三列。编辑器只要 `width>height` 就扣除固定 320 工具栏，因此 350×250 这类窗口留给图像的宽度只有 30。
- 最小修复：设备能力用于决定“是否存在某能力”，当前组件可用宽高、字体和实际避让用于决定布局。双栏采用内容最小宽度之和与间距判断，统一过渡策略；编辑器必须同时满足画布和工具栏容量，否则单栏/底部工具；网格按可用宽度选择列数。不能把 840 变成另一个忽略字体与避让的绝对设备判断。
- 回归：对同一设备连续缩放窗口；检查 600、840 两侧及短高窗口，选中项、详情、编辑草稿不因布局切换丢失；大字号不迫使操作按钮出屏。

### HAR-S05：首页入口尚未落实最新决策

- 位置：[Index.ets:727](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:727)–745，345、347、857；[OrientationDialog.ets:1](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/OrientationDialog.ets:1)。
- 触发：打开相机，点击右上角设置。
- 证据：工程箭头仍为 `Text('⌄')`；右上为 `Button('•••')`；点击打开 `cameraMoreOpen`，中间页放“拍摄方向”“拍摄后记录问题”“全部设置”。相机已有独立“记问题”入口，因此中间页再次暴露相同开关。
- 最小修复：以真实矢量下拉图标替换文本箭头；闪光、方向、设置三个独立触区；方向直接打开现有四选项对话框；齿轮调用完整设置路由；移除三点、中间页和重复问题开关。保留现有 AUTO/PORTRAIT/LEFT/RIGHT 与录像期间禁用规则，不新增方向类型。
- 回归：手机竖屏、左右横屏、宽屏、外屏各入口可达；齿轮一次点击进入完整设置，返回回到相机；“记问题”状态与拍后行为一致。

### HAR-S06：对焦触发时机与缩放手势未分离

- 位置：[Index.ets:872](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:872)–881；[CameraService.ets:439](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/CameraService.ets:439)，356。
- 触发：双指缩放、滑动起手、触碰水印附近，或点击对焦后移动场景。
- 证据：`TouchType.Down` 只要求有触点就调用对焦，第一根手指按下时双指缩放尚未识别，已修改对焦模式；同一个 XComponent 随后注册 PinchGesture。相机服务设为单次 AUTO 后，未见对焦状态监听或恢复连续对焦的逻辑，也没有可见对焦指示。当前水印画布和快门白闪层已设 `HitTestMode.None`；只有水印内容区域有编辑按钮，不能把整块预览误判为被透明层全部遮挡。
- 最小修复：仅在满足单指点击条件且未进入缩放、拖动/取消时发起对焦；建立一个坐标转换函数并复用预览旋转、裁剪、镜像结果；显示原生风格对焦反馈并按实际模式策略收尾。相机不支持时保持可拍，错误应被接住。
- 官方接口：`isFocusModeSupported`、`setFocusMode`、`setFocusPoint`、`focusStateChange`。服务已有旋转映射，不能仅凭 `{x,y}` 公式就宣判镜头坐标错误；应以当前实际 surface 大小、裁剪和旋转联合验证。
- 回归：前后镜头、竖屏/左右横屏、窗口缩放；点击画面九宫格位置观察实际焦点，双指缩放不触发误对焦；水印点击仍能编辑。该条不能写为“当前设备对焦已修复/已证实完全失效”。

### HAR-S07：导出进度与取消控件不在实际流程上

- 位置：[Index.ets:683](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:683)–684、290、950–951、980–994；[ExportChoiceDialog.ets:13](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/ExportChoiceDialog.ets:13)；[ExportService.ets:21](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/ExportService.ets:21)、36、49。
- 触发：工程或相册选择资料后导出，尤其是大量文件。
- 证据：所有现用入口 `exportStart` 打开格式选择弹窗，选择后直接 `task(exportNow)`；不存在进入 `page='export'` 的调用。唯一消费 `message` 显示导出进度、设置 `exporter.cancelled` 的控件在不可达的 `exportPage`。根浮层只处理 `video.running`；系统返回在 `busy` 时被拦截。导出服务有进度与取消检查，但实际页面未接入。
- 影响：整理阶段留在原页面、没有导出进度且无法通过应用取消，用户可能以为卡死；不能把“源码里有取消按钮”算作交付。
- 最小修复：把已有服务的进度与取消连接到实际导出流程中的原生进度对话框/已批准页面，给出明确忙碌状态，保存当前选择与返回来源；保留现有文件选择器、ZIP/文件夹与质量语义。
- 回归：两种格式、空选择拦截、大批资料、选择器取消、整理阶段取消、写入失败与完成返回。无需改导出数据结构或引入新导出格式。

### HAR-S08：保存弹窗先关闭，错误时不能保留草稿

- 位置：[ProjectDialogs.ets:31](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/ProjectDialogs.ets:31)；[QuickIssueDialog.ets:29](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/QuickIssueDialog.ets:29)；[Index.ets:301](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:301)、620、675–676。
- 触发：保存新建/编辑工程或问题记录时数据库返回失败，或当前另一个任务尚处 `busy`。
- 证据：两个弹窗都 `controller.close()` 后才调用返回 `void` 的保存回调。父层异步保存失败仅 toast；忙碌时 `task` 直接返回，动作可被无提示丢弃。问题保存和取消问题还先原地修改捕获的 `item` 再访问数据库，失败时界面对象可能与持久化结果不同。
- 最小修复：保存回调返回 Promise，弹窗进入局部 pending；只在提交成功后关闭，失败保留输入、显示必要的操作错误；提交前冻结目标 ID 和草稿，成功后刷新/替换对象。禁用重复保存，避免用全局 busy 静默丢弃用户确认。
- 回归：正常保存、重复点击、保存失败再重试、编辑已有问题与取消问题失败；验证输入仍在、同一目标不串写、列表和详情一致。保留现有数据事务，不扩大成存储重构。

### HAR-S09：裁剪边界沿用上一次操作

- 位置：[Index.ets:182](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:182)–185、694–695；[CropDialog.ets:26](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/CropDialog.ets:26)–28、71。
- 触发：裁剪照片 A 后编辑照片 B；或同一照片应用裁剪后再次打开裁剪并直接应用。
- 证据：`beginEdit` 重置编辑 recipe、历史和预览，但未重置 `cropLeft/Top/Right/Bottom`；每次裁剪弹窗沿用这些值。应用后追加新的 crop step，而预览已是执行原 recipe 的结果；旧边界又作用于新的已裁图，可能再次缩小。
- 最小修复：若语义是对当前预览新增裁剪，每次新裁剪从完整 `[0,0,1,1]` 初始化；若是调整已有步骤，则明确编辑同一坐标基准下的步骤。采用与既有自由裁切一致的最小方案，并与撤销/重做同步。
- 回归：A→B、新裁剪→再打开直接取消/应用、撤销→重做→裁剪；预览与最终文件裁切范围一致。不新增比例预设。

### HAR-S10：全页水印字段改动不产生深层状态通知

- 位置：[Index.ets:170](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:170)、940；[Models.ets:58](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/Models.ets:58)、78；[WatermarkPanels.ets:44](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/WatermarkPanels.ets:44)。
- 触发：完整水印页编辑字段内容、字段标签或开启/关闭某字段。
- 证据：`styleDraft` 为 `@State Watermark`，字段回调直接写 `styleDraft.fields[i].value/label/enabled`；`Field`、`Watermark` 为普通类，未提供嵌套观察关系。官方规则明确 `@State` 不能观察嵌套对象属性原地改动。字段输入控件可能显示自身文本，而依赖父层 props 的预览/列表未同步；相机定时刷新已在离开相机时停止，不能依赖时钟偶然重绘。
- 最小修复：对修改的字段、fields 数组及 draft 做明确的新值赋回，确保父层可观察变更；或局部使用合适的可观察组件模型。无需为此全项目迁移状态框架。
- 依据：[ArkUI @State 观察范围](https://github.com/openharmony/docs/blob/master/zh-cn/application-dev/ui/state-management/arkts-state.md)。
- 回归：标签、内容、开关、增删、排序分别操作后立刻核对预览；返回相机、重进水印页、拍照保存后内容一致，全部八种现有样式保留。

### HAR-S11：日期范围重开后默认变成本月

- 位置：[DateRangeDialog.ets:78](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/DateRangeDialog.ets:78)–103。
- 触发：设置历史月份、指定日或自定义区间，关闭后再打开日期弹窗，未修改直接点应用。
- 证据：`mode` 始终初始为 MONTH；`aboutToAppear` 将 year/month 设为当前年月，只把 initialStart/End 放入 start/end；MONTH 分支应用时忽略这些已有范围。
- 最小修复：从现有范围恢复对应模式与年月，或以可完整表达已有范围的模式初始化；未修改时保持原范围。
- 回归：年、月、日、区间各自重开与应用；跨年、闰日、反向日期仍使用已有校验规则。

### HAR-S12：首启权限完成后的返回目标错误

- 位置：[Index.ets:377](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:377)、942、948；[PermissionGuidePageView.ets:13](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/PermissionGuidePageView.ets:13)–18。
- 触发：首次使用完成或跳过引导，进入权限页，允许相机后继续。
- 证据：引导进入 permissions 时，`navigate` 将 `permissionsReturn` 设为当前 `guide`；权限完成只把 `permissionHandled=true`，然后导航到 `permissionsReturn`。因此会返回引导页，而非按钮语义所示的下一步。再次点击引导完成才进入相机/工程。
- 最小修复：区分首次开通与设置内权限管理的来源/完成目的地，首启完成沿既有业务决策进入工程选择或相机，设置管理则返回来源。重播引导的完成/取消目标也需显式定义，避免复用一个可变 `pageOrigin` 推断所有路径。
- 回归：新装首启、跳过引导、仅相机授权、可选权限拒绝、设置进入权限、帮助重播引导、系统返回。可选麦克风/位置拒绝不能阻止已授权的照片拍摄。

### HAR-S13：工程内层栈没有接入根系统返回

- 位置：[ProjectsPageView.ets:4](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/ProjectsPageView.ets:4)–14；[Index.ets:294](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:294)。
- 触发：宽屏工程页选中详情后缩窄窗口，再使用系统返回。
- 证据：工程页持有私有 `NavPathStack`；缩窄时仅清除 placeholder，不清除实际 project 路由，因此能保留详情。详情上的“返回工程列表”显式 `paths.pop()`，但根 `onBackPress` 在 page=projects 时直接回 camera，未查询子栈。相册则在根部显式处理了 galleryStack。
- 最小修复：统一返回分发，让当前最内层有效路由先处理；根持有工程栈或暴露返回处理，并让手势返回与按钮返回保持一致。保留窗口变化时的选中工程。
- 回归：宽→窄、窄→宽、详情按钮返回、系统手势/按键返回；确认实际 ArkUI Navigation 与根 `onBackPress` 的分发次序后定案。源码证实两条显式路径不同，尚未声称所有设备必然直接退出。

### HAR-S14：相机离开与重开存在阻塞路径和失效窗口

- 位置：[Index.ets:411](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:411)–423、527–570、258–260；[CameraService.ets:538](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/CameraService.ets:538)–548。
- 触发：从相机进入设置、频繁返回相机、打开过程中切后台/销毁 surface。
- 证据：离开相机时先 await 方向恢复与相机会话/输出/输入逐项释放，之后才更新 page；若设备释放慢，整个页面切换要等待，且 cameraReady 已被置 false。打开时最初检查 foreground/page/surface，但在权限、方向和关闭的多个 await 后才捕获生命周期 token，中间未重查；可能在界面已失效后仍尝试 open，直到末尾检查才关闭。
- 最小修复：统一相机打开/关闭的串行状态与调用归属；开头捕获 token/surface，并在每个关键 await 后检查；失效时不要启动新会话。导航可先进入明确的新页面，由生命周期层安全释放，但必须避免新旧会话交叉释放。先记录真实耗时再决定 UI 转场与关闭次序，不用固定延时掩盖。
- 回归：相机→设置→返回、快速连点、后台/前台、旋转/折叠触发 surface 重建；检查只存在有效会话、无旧回调回写，并测量点击到页面呈现的耗时。

### HAR-S15：设置变更的保存失败未处理

- 位置：[Index.ets:944](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:944)；[Store.ets:39](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/Store.ets:39)。
- 触发：画质、另存相册、快门声或控件外观的持久化失败。
- 证据：回调先修改设置，再调用异步 `persist()`，未 await、catch 或恢复已确认值；与桌面图标等走 `task` 的设置不同。页面会呈现新值，但无法证明已保存，重启可能回退。
- 最小修复：对这些设置使用统一、可等待的局部保存入口，明确乐观更新失败后的回退或重试；只提交当前已确认值并处理错误。连续变化可以串行/合并，但不新增设置项。
- 回归：成功修改后退出重进；连续切换；受控保存失败后当前值、提示、重进值一致。
- 已排除：`Store.saveCurrentWatermark` 31–37 行已有 Promise 串行队列并捕获每次配置快照，未将快速水印保存误报成无串行保护。

### HAR-S16：浅色弹窗的未选按钮颜色不可辨

- 位置：[ProjectDialogs.ets:28](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/ProjectDialogs.ets:28)；[QuickIssueDialog.ets:25](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/QuickIssueDialog.ets:25)、27；[DateRangeDialog.ets:123](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/DateRangeDialog.ets:123)、136；[base/color.json:16](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/resources/base/element/color.json:16)。
- 触发：系统浅色模式打开工程类别、问题等级/状态、日期模式选择。
- 证据：未选中背景固定 `#303030`，文字使用 `PAGE_TEXT/text_primary`；浅色资源为 `#191B20`，形成深色文字配深色底。相机专用深色面板不能作为管理弹窗的配色依据。
- 最小修复：管理弹窗采用成对的语义文字/表面资源；选中与未选中、禁用状态一起核对。相机保留自己的深色语义。
- 回归：浅色、深色、运行中切换主题；上述按钮的未选/选中/禁用状态均能辨认。

### HAR-S17：标注手势未处理取消和单指归属

- 位置：[Index.ets:700](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:700)–704。
- 触发：标注时系统打断、第二根手指加入、触点移出后结束，或文字内容为空时点击画布。
- 证据：只有 Down/Move/Up，没有 Cancel 清理；不保存活动 pointer ID，Up 使用第一个 changedTouches 与之前起点组合；`pushEdit()` 发生在空文字校验前，失败操作仍清掉 redo 并添加历史。
- 最小修复：只追踪一次有效单指标注手势；多指或 Cancel 取消当前草稿并清理起点/轨迹；确认操作有效后才更新历史。保持当前工具集合与编辑文件流程。
- 回归：拖出边界、系统中断、双指、空文字、撤销/重做；未完成手势不得生成额外标注。

### HAR-S18：触区与裁剪呈现仍有设计差距

- 位置：[Index.ets:731](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:731)–733、936；[ProjectsPageView.ets:17](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/ProjectsPageView.ets:17)；[GalleryPageView.ets:60](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/GalleryPageView.ets:60)；[CropDialog.ets:49](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/CropDialog.ets:49)–73。
- 证据：若干辅助 Button 明确宽高 44，编辑返回仅 24×24 Image 直接 onClick，未提供契约要求的至少 48 触区。裁剪仍是固定 170 高小预览加四个滑条，并带“拖动范围由四个边界控制…”说明文字和百分比层，尚未落实设计中的直接操作裁剪区域，也不符合用户要求的纯净页面。
- 最小修复：图标保留紧凑尺寸，外层触区达到约定大小；短高窗口通过布局重排保障可达，不放大大屏所有控件。裁剪保留自由裁切能力，按批准设计提供可操作边界，移除未经要求的解释小字；不从图中擅自增加比例预设。
- 回归：点按图标边缘、大字号、短高窗口；裁剪拖动与应用/取消可达。正式提示、表单错误、必要权限说明及业务数据不应因“纯净”要求被无差别删除。

## 页面与状态覆盖记录

| 页面/模块 | 已检查的源码内容 | 仍需运行证据 |
| --- | --- | --- |
| 相机首页、横屏右栏、外屏、悬停 | 工程/地址/闪光/方向/设置、水印入口、模式、快门、相册、镜头、记问题、缩放、surface、录制禁用、系统栏和窗口几何 | 当前包各窗口截图；点击坐标；真实对焦/旋转/录制状态 |
| 工程切换、工程管理、详情、批量 | 选择工程、搜索、归档筛选、排序、锁定/归档、撤销、编辑、导出、删除、批量菜单、内层导航 | 键盘与长标题布局；宽窄切换；连续操作与保存失败 |
| 相册、筛选、日期、选择 | 分组网格、详情栈、来源返回、类别/状态/程度筛选、日期模式、选择/全选/清除、分享/移动/删除/导出 | 复选框与父点击是否重复触发；筛选面板与 IME；原生分享/选择器返回 |
| 详情、问题、照片标注、裁剪 | 图片/视频分支、问题保存/移除、图像编辑目标、预览修订号、撤销/重做、保存、放弃、裁剪 | 图像缩放/拖动；手势取消；目标切换；保存结果与预览一致 |
| 水印快捷页、完整样式与字段 | 现有八种样式、字号/透明度/位置、字段标签与内容/开关/排序/增删、即时保存、预览渲染入口 | 深层字段刷新；多行/长字段；照片/视频和预览所见所得 |
| 设置与子页 | 窄屏分组、宽屏主从、画质、声音、系统相册副本、控件外观、图标、权限、隐私、帮助、关于、诊断与彩蛋入口 | 分组刷新；各子页返回；资源主题切换；支持/不支持替换图标的系统 |
| 权限、引导与帮助 | 首启/管理区分、实际 grant 查询、重复请求、系统设置回跳、可选权限、四步引导、重播与帮助分类 | 系统拒绝/永久拒绝/回收权限；首启流程；后台回前台后权限变化 |
| 导出 | 现用格式对话框、参数与所选资料快照、服务进度/取消、完成/失败回调、文件选择器调用 | 真正可达的进度/取消；两种格式成功/失败/取消与返回 |
| 主题、字体、窗口 | 明暗资源、局部硬编码色、字号/触区、主从阈值、系统/切口/导航/键盘避让、折痕 | 系统大字号；浮窗/分屏；显示区域与折痕的实际返回值 |

覆盖含现用组件和这些入口直接调用的服务；未把未被调用的旧 Builder 当作已交付页面。`exportPage` 虽在 build 分支中存在但无入口，因此只用于说明可达性缺口；`coverCompanionPage` 等旧路径不作为当前 UI 验收依据。

## 已存在且应保留的保护

- 录制期间导航与切镜头有拦截；闪光能力逐模式检查并读回后更新 UI，未把“不支持闪光”当成需要强行启用的缺陷。
- 相机水印画布和快门白闪层已禁用命中；不能在未测坐标前归因于整屏透明层拦截。
- 编辑保存捕获目标 ID，预览使用 revision 丢弃过期结果；这些机制应在布局修复时继续保留。
- 导出前重新读取所选资料和工程并检查资料移动情况；修复进度界面不应改变这些保护或导出内容。
- 快捷水印保存有串行队列；水印渲染已按实际字段计算自然宽度并设宽高边界，不能把旧固定底板问题直接套到当前源码。当前输出宽度仍需要实际长短内容与八样式截图验收。
- 日期解析已有无效日期、闰日及起止关系校验；当前问题是重开初始化，不是建议替换整套日期逻辑。

## 官方依据与最低版本

| 用途 | 采用依据 | API 12 适用性 |
| --- | --- | --- |
| Builder、状态刷新 | [@Builder](https://github.com/openharmony/docs/blob/master/zh-cn/application-dev/ui/state-management/arkts-builder.md)、[@State](https://github.com/openharmony/docs/blob/master/zh-cn/application-dev/ui/state-management/arkts-state.md) | 直接读取状态、首层重新赋值和基础组件状态方案可用；不依赖新绑定 API |
| 窗口矩形与避让 | 本机 SDK [@ohos.window.d.ts:4323](/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/ets/api/@ohos.window.d.ts:4323)、同文件 `windowRectChange` | `getWindowAvoidArea` 起于 API 9，`windowRectChange` 起于 API 12；忽略可见性的新增查询起于 API 22 |
| 折痕、切口 | 本机 SDK [@ohos.display.d.ts:425](/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/ets/api/@ohos.display.d.ts:425)、974、1128 | 折痕区域/显示 ID/矩形数组起于 API 10，CutoutInfo 起于 API 9 |
| 对焦 | 本机 SDK [@ohos.multimedia.camera.d.ts:2290](/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/ets/api/@ohos.multimedia.camera.d.ts:2290)，`setFocusPoint` 与 `focusStateChange` | 对应会话对焦接口起于 API 11；先检查支持能力，再处理状态回调 |
| 命中策略 | [ArkUI hitTestBehavior](https://github.com/openharmony/docs/blob/master/zh-cn/application-dev/reference/apis-arkui/arkui-ts/ts-universal-attributes-hit-test-behavior.md) | 当前已有 `HitTestMode.None` 保护；修复按实际控件区域验证 |

## 设备接入后的最小回归顺序

1. 先核实运行包版本与当前源码对应关系，记录设备 API、实际窗口尺寸、方向和系统字体；打开相机→齿轮设置→全部分组→返回，定位用户感知的无响应发生在哪一步。
2. 复现单指对焦与双指缩放，分别在竖屏和左右横屏记录触点、可见反馈与成像反应；不以相机已打开代替对焦验收。
3. 运行首启权限链、工程新建/编辑/问题保存、日期重开、A/B 照片连续裁剪、水印字段即时预览、导出进度/取消。
4. 在同一份数据上切换窄屏/宽屏/短高窗口与大字号，检查已选工程、资料、筛选和草稿保留；再按实际硬件覆盖内外屏与折痕。
5. 每项保存问题编号、触发步骤、实际结果和截图/日志位置；只有实际完成的步骤标为通过。构建成功、旧截图或源码存在控件均不算运行通过。
