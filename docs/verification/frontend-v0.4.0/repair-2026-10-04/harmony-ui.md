# HarmonyOS 前端修复记录

日期：2026-10-04。当前状态：下表项目已修改源码；本代理未构建、未安装、未启动设备。静态差异格式检查通过；编译与运行验证交由主任务串行执行。原版本、应用标识、签名、八种水印样式与拍摄/导出格式保持原约定。

| 原问题 | 本轮修改 | 主要位置 |
| --- | --- | --- |
| HAR-S01 | 宽屏设置由直接读取 selectedGroup 的分支选择静态分组，去掉活跃分组按值参数依赖 | SettingsPageView |
| HAR-S02 | 内容、取景、工具栏与浮层使用同一安全原点；四边永久避让与键盘分别存储；输入页面按键盘压缩；撤销栏不强制 280 最小宽 | WindowEnvironment、Index |
| HAR-S03 | 读取当前窗口所属 displayId，保存 X/Y 偏移、全部二维折痕，转换一次后裁剪；多矩形分隔可用区域 | WindowEnvironment、WindowGeometry、CameraLayout |
| HAR-S04 | 布局决策改用当前可用宽高、字号和栏内容容量；去机型名称、默认物理屏幕与 tablet 标签；相册/详情网格随栏宽变化，编辑器先检查画布容量 | DeviceLayoutProfile、WindowGeometry、Index、Projects/Gallery/Settings |
| HAR-S05 | 真实矢量工程 chevron；闪光、方向、齿轮独立；齿轮直达完整设置；移除相机三点中间页；较窄工具栏上下排列并支持横向滚动操作区 | Index、ic_chevron_down.svg |
| HAR-S06 | 完成单指点击且没有拖动/多指/取消后才调用 focus；圈位置按实际 surface 居中裁剪换算；接入核心 focusStateChange 结果，不把定时消失作成功 | Index、核心代理 CameraService |
| HAR-S07 | 所有真实导出入口接可见进度浮层与取消；系统返回也请求取消；整理、压缩、选择位置、交付等阶段补取消检查 | Index、ExportService |
| HAR-S08 | 工程/问题保存 Promise、局部 pending、失败保稿和错误；成功后关闭；冻结问题 ID，不先改列表对象；忙碌会返回错误而非吞确认 | ProjectDialogs、QuickIssueDialog、Index |
| HAR-S09 | 新照片编辑和每次新裁剪均重置完整范围；应用仍向当前预览追加既有自由裁剪步骤 | Index |
| HAR-S10 | 字段内容、标签、开关、增删与顺序均复制 draft 后重新赋回；关闭快捷页等待持久化，失败留在当前页 | Index |
| HAR-S11 | 日期从既有范围推导年/月/日，其他范围原样进入区间模式 | DateRangeDialog |
| HAR-S12 | 首启权限目的地明确为当前工程相机或工程列表；设置权限返回原页；帮助重播明确返回帮助 | Index |
| HAR-S13 | 工程详情导航栈和选中 ID 移交根状态；系统返回先弹内层详情，宽窄切换保持状态 | Index、ProjectsPageView |
| HAR-S14 | 打开前捕获 lifecycle/surface，在权限、方向、关闭后重新检查；导航不等待硬件释放；失效打开收尾后重试当前有效入口 | Index、核心代理 CameraService |
| HAR-S15 | 画质/声音/相册副本/控件外观通过可等待顺序队列，成功后更新已确认值，失败恢复并提示 | Index |
| HAR-S16 | 工程类别、问题等级/状态、日期未选按钮使用主题表面资源与主题文字 | ProjectDialogs、QuickIssueDialog、DateRangeDialog |
| HAR-S17 | 标注跟踪活动 pointer ID；Cancel/多指/忙碌清理轨迹；空文字和过小裁剪先校验再压历史 | Index |
| HAR-S18 | 相机、工程和相册辅助按钮与编辑返回触区 48；裁剪使用图像上的八处直接拖动边界，去四滑条、比例数字与解释小字 | Index、ProjectsPageView、GalleryPageView、CropDialog |
| 管理新建工程 | 管理页新建保存后定位新工程，不切当前拍摄工程；相机工程面板新建继续创建并使用 | Index |
| 核心接线 | 初始化在首次刷新前 recoverDeletions；单条/批量资料和工程删除调用新的 files.deleteMedia/deleteProjects | Index、核心代理 MediaService |

既有测试页中“机型分类隔离”“普通直屏倍率”断言同步为当前窗口容量口径，横屏工具栏上限同步两排高度；未运行这些断言。主任务同时新增的 CoreRepairChecks 导入/三条入口保留。

运行仍需核验：当前包全部五设置分组；普通、横屏、短高、大字、IME、实际多折痕的触区/取景与弹层；真实对焦及会话切换；保存错误保稿与重试；日期重开；连续裁剪与标注取消；两种导出的取消、文件选择器取消、失败和正常完成。压缩/系统文件拷贝期间取消需等待当前系统异步调用返回；若已经写入目标，错误明确说明可能保留部分资料。
