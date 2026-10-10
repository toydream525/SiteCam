# v0.4.0 前端逐图覆盖计划

状态：计划稿，尚未安装、运行或实测。设备清单与代码功能路径是当前只读检查结果；本表描述后续对照设计图的检查点，不代表页面已实现或验收通过。

设计图来源和规范以 [`assets/design/frontend-v0.4.0/manifest.md`](../../../assets/design/frontend-v0.4.0/manifest.md) 及 [`DESIGN_CONTRACT.md`](../../../assets/design/frontend-v0.4.0/DESIGN_CONTRACT.md) 为准。逐图检查只观察已有能力的布局、入口和状态；随机示例名称、日期、数字不作为产品数据规范。

## 当前可用设备与签名入口

| 项目 | 只读结果 |
|---|---|
| Android（`adb devices -l`） | 当前无已连接设备；没有可报告的设备型号/编号。 |
| HarmonyOS（`hdc list targets`） | 当前无已连接设备；没有可报告的设备型号/编号。 |
| HarmonyOS Debug 签名脚本 | 未找到现有的专用 Debug 签名脚本。`harmonyos/scripts/build.sh` 是 Debug/Release 构建入口，当前复制产物名为 `entry-<target>-unsigned.hap`；`harmonyos/build-profile.json5` 中 `signingConfigs` 为空。故现有入口产出未签名 Debug HAP，不能称作 Debug 签名入口。此检查未读取任何签名秘密，也未构建或安装。 |

## 逐图检查点

| 图 | 页面/场景 | 需对照的已有功能和检查点 | 主要代码入口 |
|---|---|---|---|
| 01 | 手机相机、工程、设置主页面 | 手机窗口下相机、项目管理、设置入口能完整呈现并可达；项目管理与相机导航关系清楚。 | Android `CameraScreen.kt`, `ProjectListScreen.kt`, `SettingsScreen.kt`; Harmony `Index.ets`, `ProjectsPageView.ets`, `SettingsPageView.ets` |
| 02 | 平板工程与媒体 | 工程列表/资料及媒体在宽窗口内排布合理；点工程卡片只查看工程资料或媒体，不改变当前拍摄工程。 | Android `ProjectListScreen.kt`, `GalleryScreen.kt`; Harmony `ProjectsPageView.ets`, `GalleryPageView.ets` |
| 03 | 横屏相机最终图 | 横屏取景铺满可用区域；没有大型左侧工程/工具栏和底部白色横条；项目小入口、安全区及拍摄控件互不遮挡。 | Android `CameraScreen.kt`, `CameraGeometry.kt`; Harmony `Index.ets`, `CameraLayout.ets`, `WindowEnvironment.ets` |
| 04 | 录像、相机顶部工程切换、更多 | 拍照/录像待机和录制状态可辨；相机顶部入口明确选择工程后允许切换；更多操作按当前已有功能呈现。 | Android `CameraScreen.kt`, `CameraTopBar.kt`; Harmony `Index.ets`, `ProjectSwitchPanel.ets` |
| 05 | 定位刷新、锁定、归档状态 | 已有地址刷新与权限/定位状态反馈可辨；工程锁定、归档状态对应现有状态操作；授权后地址未显示时检查刷新入口可用。 | Android `AddressRefreshCoordinator.kt`, `ProjectViewModel.kt`; Harmony `Index.ets`, `LocationService.ets`, `ProjectsPageView.ets` |
| 06 | 折叠屏外屏相机 | 外屏窗口下相机与主要控件不被裁切；小型工程入口和可用安全区域正确；不出现白色底部横条。 | Android `CameraScreen.kt`, `ScreenEnvironment.kt`; Harmony `FoldCoverControls.ets`, `DeviceLayoutProfile.ets`, `Index.ets` |
| 07 | 工程排序、表单、批量 | 现有排序项、工程新建/编辑字段及已支持批量操作可读、可操作；编辑/导出入口常显。 | Android `ProjectBrowser.kt`, `ProjectListScreen.kt`, `ProjectViewModel.kt`; Harmony `ProjectsPageView.ets`, `ProjectDialogs.ets`, `ProjectBatchActionDialog.ets` |
| 08 | 工程类别、更多、删除、空态 | 只检查现有类别选择/筛选、更多、删除和空态；不把图示解释为新增类别增删能力。删除确认与空态适配当前窗口。 | Android `ProjectBrowser.kt`, `ProjectListScreen.kt`; Harmony `ProjectCategoryDialog.ets`, `ProjectsPageView.ets` |
| 09 | 水印内容字段 | 已有字段名称/内容编辑入口和保存反馈布局清楚；输入与键盘场景可完成；不扩展字段业务规则。 | Android `WatermarkCustomizationScreen.kt`, `WatermarkEditorViewModel.kt`; Harmony `WatermarkPanels.ets` |
| 10 | 大屏水印和八种样式 | 保留既有八种样式身份、字段及顺序、字号、不透明度与内容；验证底板随内容收紧且有最大宽度，保留换行和防越界，并确认预览、照片、视频布局一致。不新增样式、不删字段、不缩小预览，不改变底板宽度之外的既有渲染输出。 | Android `WatermarkStylePicker.kt`, `WatermarkBitmapRenderer.kt`; Harmony `WatermarkPanels.ets`, `WatermarkRenderer.ets` |
| 11 | 阔屏设置与明暗主题 | 宽屏/深色主题下设置项可见；相册设置按平台既有语义表达：Android 共享文件设置，HarmonyOS 另存副本。 | Android `SettingsScreen.kt`; Harmony `SettingsPageView.ets`, `MediaService.ets` |
| 12 | 画质、图标、关于 | 对照现有画质选项、应用图标选择和关于信息页面；核对控件在窄/宽窗口不溢出。 | Android `PhotoQualityProfile.kt`, `AppIconPicker.kt`, `SettingsScreen.kt`; Harmony `SettingsPageView.ets`, `FeaturePages.ets` |
| 13 | 权限状态与授权流程 | 未请求的权限首次由应用触发系统请求；拒绝后按系统实际状态再次请求或引导系统设置；已授权状态准确呈现。 | Android `PermissionGuideScreen.kt`, `PermissionAccess.kt`; Harmony `PermissionGuidePageView.ets`, `Index.ets` |
| 14 | 帮助首页、章节、窄屏权限说明 | 帮助目录与章节内容可滚动阅读；窄屏文字、返回入口和权限相关既有说明不遮挡操作。 | Android `HelpScreen.kt`, `UserGuideRepository.kt`; Harmony `HelpPageView.ets` |
| 15 | 首次启动四步引导 | 对照现有首次启动引导步骤和前进/返回/完成操作；各步在手机及较小窗口完整显示。 | Android `OnboardingScreen.kt`, `OnboardingPreferences.kt`; Harmony `OnboardingPageView.ets` |
| 16 | 折叠屏内屏、大字号、键盘、深色相册 | 内屏宽度、大字体与键盘弹出后，表单仍可滚动输入；手动输入入口可达；深色媒体页面内容与控件有足够辨识度。 | Android `ProjectListScreen.kt`, `GalleryScreen.kt`, `ScreenEnvironment.kt`; Harmony `ProjectsPageView.ets`, `GalleryPageView.ets`, `WindowEnvironment.ets` |
| 17 | 相册筛选和多选 | 对照已有媒体列表、日期筛选和选择/批量操作；筛选状态与选中状态清楚，空结果可读。 | Android `GalleryScreen.kt`, `GalleryViewModel.kt`; Harmony `GalleryPageView.ets`, `DateRangeDialog.ets` |
| 18 | 照片详情、标注、自由裁切 | 现有照片详情、标注/马赛克和自由裁切可用；验证窄屏、键盘与保存/取消操作；不新增图中比例预设。 | Android `PhotoDetailScreen.kt`, `PhotoAnnotationScreen.kt`; Harmony `DetailPageView.ets`, `CropDialog.ets` |
| 19 | 视频问题状态 | 对照现有视频详情和问题记录的新增/编辑状态；视频播放/预览区域与操作入口适配可用窗口。 | Android `PhotoDetailScreen.kt`, `QuickIssueDialog.kt`; Harmony `DetailPageView.ets`, `QuickIssueDialog.ets`, `VideoInspection.ets` |
| 20 | 导出、移动工程、日期筛选 | 对照已有导出选项/进度、工程移动和日期范围筛选；Android 文件共享配置与 HarmonyOS 另存副本语义各自保持。 | Android `ProjectViewModel.kt`, `ProjectExportEngine.kt`, `GalleryScreen.kt`; Harmony `ExportChoiceDialog.ets`, `ExportDialog.ets`, `ProjectMoveDialog.ets`, `DateRangeDialog.ets` |
| 21 | 空相册、未保存退出、视频详情、反馈、导出完成 | 对照已有空状态、编辑退出确认、视频详情/问题反馈和导出完成反馈；各状态不遮挡底层必要操作，不展示不支持的动作。 | Android `GalleryScreen.kt`, `PhotoDetailScreen.kt`, `PhotoAnnotationScreen.kt`; Harmony `GalleryPageView.ets`, `DetailPageView.ets`, `Index.ets`, `ExportDialog.ets` |

## 大屏补充参考图 22

图 22 是横屏相机大屏布局补充，不新增页面。检查第 03 项时，按契约核对双栏/多列与留白、控件尺寸上限、右侧拍摄控件栏 4:3 画幅关系与全高分布，以及水印底板按实际内容收紧。图像画布尺寸不作为设备尺寸或画幅规格；这是后续运行检查项，当前未实测。

## 建议运行矩阵

后续有设备可用时，按同一检查点分别覆盖 Android 手机与平板、HarmonyOS 手机/平板、折叠屏外屏/内屏；窗口维度覆盖竖屏、横屏、分屏/铰链与大字号，权限维度覆盖未请求、已拒绝、已授权。无法取得的硬件形态标记为未测，不以模拟布局或设计图代替设备结果。
