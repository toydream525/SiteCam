# 数据、存储、导出、设置与定位审查

审查日期：2026-10-03。基线为当前工作区，Android / HarmonyOS v0.4.0（20）。本分报告只读产品源码，未修改应用、设计图、用户资料，未启动模拟器、构建或安装包，未计算文件校验和。

新增 6 项：2 项 P1、4 项 P2。均为静态调用链确认；竞争、取消和故障路径尚未做定向设备复现。优先级反映影响，不代表已经运行复现。此前前端报告的 26 条保留原编号，本报告不重复计数。相机、编码、渲染的独立分报告另行汇总。

## 新增问题

| 编号 | 优先级 | 问题 | 确定性 |
|---|---|---|---|
| AND-DATA-01 | P1 | 删除在移除数据库记录后可随页面退出被取消，文件删除或索引恢复无法保证完成 | 缺口和可达入口确认；取消时序未注入复现 |
| AND-DATA-02 | P2 | 删除工程漏处理不可用媒体，其标注成品文件随索引级联删除而失去归属 | 查询过滤及级联链确认；未做系统回收站场景 |
| AND-DATA-03 | P1 | 大屏工程删除与相册移动的锁范围不同，刚移往另一工程的照片仍可被删除 | 双栏入口及交错执行链确认；未做定向竞争复现 |
| HAR-DATA-01 | P2 | 相机控件“毛玻璃”样式保存后，重新打开应用恢复为“普通” | 保存、默认值、加载遗漏确认 |
| HAR-DATA-02 | P2 | 快捷水印保存队列写入整份旧设置，可能覆盖随后成功保存的工程选择或画质 | 可达交错执行链确认；未注入延迟复现 |
| HAR-DATA-03 | P2 | 删除先移除文件、后提交数据库；失败会留下指向已删除文件的媒体和标注记录 | 顺序及失败路径确认；未注入 I/O / 数据库故障 |

### AND-DATA-01：删除操作没有覆盖取消边界的收尾

证据：

- [MediaDeletionCoordinator.kt:45](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/media/MediaDeletionCoordinator.kt:45) 先删除标注记录，:51 再调用物理删除；:62 先删除媒体记录，:79 再调用物理删除。两个物理删除调用不在外层恢复保护中，恢复只在函数正常返回 `FAILED` 后执行（:54–56、:82–90）。
- [MediaStoreManager.kt:220](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/media/MediaStoreManager.kt:220) 的物理删除切到 `withContext(Dispatchers.IO)`，内部 `try` 无法接住进入/返回该上下文时在调用方抛出的取消。
- [GalleryViewModel.kt:91](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/gallery/GalleryViewModel.kt:91) 将操作放进 `viewModelScope`；[AppNavHost.kt:209](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/navigation/AppNavHost.kt:209) 的 ViewModel 归属相册导航项，:224 的返回弹出该项；[GalleryScreen.kt:55](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/gallery/GalleryScreen.kt:55) 删除期间仍可点击“退出相册”。

具体执行：媒体数据库删除已提交 → 删除协程正在等待 IO 执行/结果 → 用户退出相册使页面任务取消。若 IO 尚未开始，物理文件保留而数据库媒体、问题记录已丢失；若实际删除失败后返回时取消，原本应执行的恢复也会跳过。标注记录与标注文件存在相同窗口。私有媒体文件没有全目录重建索引入口，重新打开相册不能恢复该资料。

反证核对：全局媒体锁保证操作互斥，但不会阻止持锁任务取消；`CaptureOperationCoordinator.finish` 的不可取消收尾仅清理拍摄预约，不包住这里的删除；`runCatching` 恢复分支只在获得 `FAILED` 结果后运行。现有 [MediaDeletionCoordinatorTest.kt:7](/Users/ninja/Documents/工程水印相机/app/src/test/java/com/sitecam/app/core/media/MediaDeletionCoordinatorTest.kt:7) 仅验证能否恢复标注指针的布尔条件，未运行实际协调器，也未覆盖取消。

修复方向：以媒体 ID 和删除阶段记录一次完整操作；数据库记录开始移除后，确保必要的物理操作和补偿在页面取消后仍可完成。不能只在 UI 禁用返回，也不能仅捕获取消后继续在已经取消的上下文中调用挂起恢复。验收应在标注索引提交后、媒体索引提交后、物理删除返回前分别注入取消，确认结果是完整删除或可重试的完整记录。

机制依据：[Kotlin withContext 官方文档](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-context.html) 说明上下文在执行前取消可以不运行代码块，切回原调度器也可能丢弃结果并抛出取消；[Android ViewModel 作用域文档](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-apis) 说明 Compose 的默认 ViewModel 使用最近的 ViewModelStoreOwner。上述缺陷结论还结合了本应用实际导航及任务归属。

### AND-DATA-02：删除工程只枚举当前可见媒体

证据：[ProjectViewModel.kt:338](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/projects/ProjectViewModel.kt:338) 使用 `getMediaItemsByProject` 枚举待删资料；[MediaItemDao.kt:14](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/database/dao/MediaItemDao.kt:14) 明确筛选 `isUnavailable = 0`。成功处理这些行后，ProjectViewModel :346 直接删除工程。

系统相册移入回收站或删除原件后，[MediaAvailabilitySync.kt:46](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/media/MediaAvailabilitySync.kt:46) 会隐藏媒体但保留关联问题及标注，以便恢复。此时删除整个工程，不可用媒体不会进入删除协调器。工程外键级联会删除该媒体记录（[MediaItemEntity.kt:15](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/database/entity/MediaItemEntity.kt:15)），再级联删除它的标注记录（[AnnotationEntity.kt:15](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/database/entity/AnnotationEntity.kt:15)）；单独保存的标注成品没有经过物理删除，成为无索引文件。系统回收站后来还原原件也不会还原已被级联删除的工程索引。

反证核对：可见查询用于相册展示是合理的；问题是复用于“删除整个工程”。同步器能恢复的是仍存在的不可用记录，不能复活已经级联删除的行；原件不可读不代表标注成品也不存在。

修复方向：删除工程应获取该工程全部记录，包括不可用媒体，并逐一处理原件、标注、问题；保留已实现的“原件已经不存在可清索引”分支。验收用带标注照片进入系统回收站后删除工程，确认标注文件确实被处理，失败时工程与对应索引仍可重试。

### AND-DATA-03：工程删除与资料移动缺少共同的完整操作范围

证据：

- [ProjectViewModel.kt:336](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/projects/ProjectViewModel.kt:336) 只在外层持拍摄协调锁；:338 获取一次媒体列表，:342 每次调用删除协调器才取得媒体锁，:346 最终删工程时未持媒体锁。
- [GalleryViewModel.kt:96](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/gallery/GalleryViewModel.kt:96) 的移动只持 `MediaOperationCoordinator`；它不参与拍摄锁。数据库内的媒体与问题移动虽在同一事务，但与工程删除不是同一操作。
- [MediaDeletionCoordinator.kt:36](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/media/MediaDeletionCoordinator.kt:36) 只按原快照的 ID 重读最新记录，不检查该记录是否已离开准备删除的工程。
- [AppNavHost.kt:178](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/navigation/AppNavHost.kt:178) 同时提供工程 ViewModel 与 :190 的相册 ViewModel；[ProjectListScreen.kt:606](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/projects/ProjectListScreen.kt:606) 在大屏保持相册栏显示，没有把工程 `isExporting` 传给相册。相册按自己的 `isLoading` 控制移动（[GalleryScreen.kt:62](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/gallery/GalleryScreen.kt:62)）。

具体交错 A：工程 A 有多张照片 → 左栏开始删 A，记录一次列表 → 右栏把尚未处理的照片 X 移到 B，移动成功 → 删除循环按 X 的 ID 重读到 B 中的记录，仍删其文件与索引。用户刚移走的资料会消失。交错 B：删除 A 枚举完成后，相册把另一资料移入 A → 最终删 A 只通过外键移除新增资料的索引，留下未处理的文件。

反证核对：这不是单个 ViewModel 内的重复点击；两个栏使用独立的忙碌状态。逐媒体互斥锁能避免一次物理操作重叠，但不能固定工程删除的成员集合；拍摄锁只防拍摄/切工程相关操作，Gallery 移动不使用它。

修复方向：工程的成员枚举、相关媒体删除和最后工程删除必须与移动共享一致的操作边界，并在移动提交与删除执行时校验目标归属。实现时避免非重入锁嵌套。验收覆盖 A→B 移出、B→A 移入在枚举前后及逐项删除之间的交错，不允许成功移入 B 的资料被 A 的旧删除任务消除。

### HAR-DATA-01：相机控件外观字段没有从持久化设置恢复

证据：[Models.ets:125](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/Models.ets:125) 默认 `cameraControlStyle = 'NORMAL'`；[Index.ets:944](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:944) 选择毛玻璃写入 `BLUR` 并持久化；[Store.ets:39](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/Store.ets:39) 序列化完整设置。然而 :14 从保存内容逐字段恢复到新的 Settings 时未复制该字段。

结果：选择毛玻璃并等待保存完成，下一次 Store.open 后内存回到 NORMAL，设置页和取景器样式都回到普通。随后任何保存还会把默认值覆盖回持久化内容。

反证核对：不是设备毛玻璃能力降级或 HAR-S15 的写入失败；即便完整 JSON 成功保存，加载路径仍必然遗漏。Android 对应字段在 AppSettingsDataStore 中有成对读取/写入，不存在同一遗漏。

修复方向：恢复时读取并校验此枚举，缺少旧字段时保留 NORMAL。验收分别用 NORMAL、BLUR、旧版无字段内容执行保存→关闭→重开。

### HAR-DATA-02：快捷水印 FIFO 会覆写后来保存的非水印设置

证据：[Store.ets:33](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/Store.ets:33) 调用时立即保存整份 `settings` 的 JSON 快照，再排到 `watermarkSave` 队列；:35 等待模板数据库更新后，:36 将旧整份快照写入同一 preferences 键。独立的 :39 `saveSettings` 不加入该队列。

实际入口：[Index.ets:907](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:907) 输入、开关、滑条等每次触发 :692 的不等待保存。关闭面板并不等待队列完成。用户之后可通过 :357 切换工程，或 :944 修改画质；这两条调用独立 `saveSettings`。

具体交错：工程 A 状态下快捷水印产生待写队列 → 关闭快捷面板 → 切换 B 的 settings 已成功写入，界面提示切换完成 → 较早的水印队列恢复执行并写回含 `currentProject=A` 的整份 JSON。当前内存仍指向 B，但重启读到 A；画质、相册副本、快门声音等非水印字段也可被相同路径回退。

反证核对：队列能保证多个水印保存之间 FIFO，但没有覆盖与 `saveSettings` 的交错；即使每个 Promise 都成功，仍然可能回写旧字段。本项与 HAR-S15 未处理 Promise 失败、HAR-S10 深层 UI 状态通知以及 AND-008 字段实体覆盖分别独立，不重复计数。

修复方向：统一所有设置提交的顺序与字段归属；水印排队写入不能回写其不拥有的其他设置旧值。验收阻塞一次水印模板更新，在后续工程/画质保存成功后放行队列并重新打开，确认最新设置未回退。

### HAR-DATA-03：删除失败会留下已不存在的文件指针

证据：[Index.ets:674](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:674) 详情删除先 `files.remove`，后 `store.deleteMedia`；批量媒体/工程删除 :682 同样先删物理文件，尤其工程先遍历删除其全部文件，最后才调用数据库删除。[ImageProcessor.ets:45](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/ImageProcessor.ets:45)–:46 按 `path/source/edited` 顺序逐个删除，后续失败不恢复前面已经删除的文件。[Store.ets:56](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/Store.ets:56)、:59 的回滚只能恢复数据库行。

两条具体失败路径：原片已删、编辑成品删除失败，数据库删除尚未开始；全部文件已删、随后数据库 delete/commit 失败并回滚。两者都会保留仍标为正常的媒体/标注记录，但原片或编辑成品已经不存在。工程循环还可能先删掉多张图片，最后一次失败使该工程全部旧索引继续出现。

反证核对：`task` 捕获异常能提示失败并释放 busy，不能恢复已删文件；数据库事务不能回滚文件系统。重试删除可清理残余，但第一次失败后界面没有把失效记录协调为明确的可重试状态。系统相册副本不在此删除范围，本项不声称副本会被删。

修复方向：把删除阶段与失败补偿集中到协调层，使应用能在物理删除与数据库提交之间失败或重启后继续收尾；不能仅调整一行先后顺序就宣称跨存储原子性。验收包括第二个文件 unlink 失败、媒体事务失败、工程最后事务失败，确认展示和重试状态与实际文件一致。

## 覆盖范围

下列生产文件逐文件完整阅读，交叉调用入口按问题所需补读；未把“搜索到文件名”当成完整审查。

| 模块 | 已完整阅读 |
|---|---|
| Android 数据库与迁移 | AppDatabase；5 个 DAO：ProjectDao、MediaItemDao、IssueDao、WatermarkDao、ProjectCategoryDao；7 个 Entity：Project、MediaItem、Issue、Annotation、WatermarkTemplate、WatermarkField、ProjectCategory；WatermarkTemplateMutations。涵盖 1→2→3→4→5、唯一约束、外键、初始化、移动及问题/标注事务 |
| Android 文件与导出 | MediaStoreManager、MediaSaveTarget、MediaOperationCoordinator、MediaDeletionCoordinator、MediaAvailabilitySync、MediaFileReservation、NamingEngine；ProjectExportEngine、ExportOptions。涵盖私有/系统相册分流、待发布行、冲突命名、删除补偿、外部回收站、ZIP、目标目录、压缩质量、清单、缺失及目标写入报告 |
| Android 设置与定位 | AppSettingsDataStore、LocationTracker、ReverseGeocoder、LocationFreshness、AddressRefreshCoordinator。涵盖设置读取/写入、选择工程及最近历史、粗略/精确权限、系统定位开关、旧坐标、请求代次、地址归属、超时/取消 |
| Android 管理入口 | ProjectViewModel、GalleryViewModel、PhotoDetailViewModel、SettingsViewModel；AppIconPicker、AppIconChoice；MainActivity、AppContainer、CaptureOperationCoordinator、PermissionAccess、AndroidManifest、file_paths。补读 AppNavHost 及 ProjectListScreen / GalleryScreen 的双栏、返回和操作禁用分支 |
| HarmonyOS 存储与导出 | Store、Models、MediaService、ExportService、OfflinePrivacy、ExternalLinkService、BuildVariant；补读 ImageProcessor 的路径及 remove/removePath。涵盖表结构补列/回填、事务、模板队列、媒体/问题移动、删除、系统相册副本、分享、ZIP/目录、质量及导出清单 |
| HarmonyOS 定位与页面调用 | LocationService、Index 全文件、SettingsPageView、AppScope/app.json5、entry/src/main/module.json5。覆盖项目/相册/标注数据入口、settings、权限申请/回收、外部设置回返、生命周期、图标门控、导出入口及后台收尾 |

相机采集、编码器、图像变换、EXIF 写入、预览与水印渲染由相机媒体分报告负责；这里仅检查其数据提交、文件归属和页面调用交界。Android PermissionGuideScreen 等前端完整审查沿用此前前端报告，本分报告不重复宣称逐行覆盖。

## 已存在的保护与排除项

- Android 迁移没有破坏性重建兜底；已有重复问题/标注整理及唯一媒体约束。移动与问题 projectId 同事务，问题保存重读当前媒体归属，不能将旧详情里的工程 ID 直接写回。此保护不能消除 AND-DATA-03 的跨操作成员竞争。
- Android 系统相册同步保留不可用记录，权限异常/未知可用性不等同外部删除；删除后恢复标注指针已有防止指向已删文件的判断。取消窗口与工程遗漏是另外两条路径。
- Android 导出先暂存，再交付；选中媒体会重新核对数量与工程归属，缺源文件和目标写入失败分开记载，输出尺寸来自实际文件。目录名/文件名含 ID 并清理路径分隔符，未发现由当前工程名称直接穿越导出目录的路径。未新增与任务无关的通用安全扫描。
- HarmonyOS 导出可记录缺源/编辑成品，并为目标文件交付失败生成附加报告；未把“已有报告”误写成所有文件系统故障均能完整报告。取消/进度入口不可达已是 HAR-S07，不重复计数。目录 mkdir/stat 失败、部分目标输出和 ZIP 写入失败后的残留需定向 I/O 场景验证，本轮没有足够运行证据另计一条。
- Android 地址刷新前后重查权限，检查坐标新鲜度及地址归属；自动地址与手动刷新有代次隔离。HarmonyOS 反查返回前后重查授权，以生命周期、权限、刷新令牌和坐标距离确认能否提交；失权/停止后清除坐标与地址锚。未发现可据当前代码独立确认的“撤权后旧地址必然回来”问题。
- Android 预览使用过期坐标与最终保存快照不一致由 AND-CM06 记录；鸿蒙新视频没有即时填入结构化坐标/地址、导致导出清单缺字段由 HCM-03 记录。本报告核对了 Store/Export 端证据，仅交叉引用，不再新增编号。
- Android 图标以 PackageManager 的别名状态为真源，变更后读回并尝试恢复；manifest 有 A/B/C/D，默认 D。HarmonyOS app 配置有 A/B/C alternateIcons、默认 D，SettingsPageView :32 按 apiVersion≥26 门控调用。平台支持、系统桌面刷新及升级保留仍须在对应系统实测；没有仅凭配置宣称这些设备验收已通过。

## 验证边界及后续验收

本分任务未执行测试或设备操作，构建/测试由主任务统一安排，以避免额外模拟器和内存占用。既有 Android 用例可提供部分回归依据：ProjectDataTransactionsTest、MediaAvailabilitySyncTest、MediaDeletionCoordinatorTest、ProjectExportEngineIntegrationTest、ProjectExportTest、MediaSaveTargetTest、MediaFileReservationTest、AddressRefreshCoordinatorTest、LocationFreshnessTest、CaptureOperationCoordinatorTest、AppIconPickerPackageManagerTest；平台目录另有 MediaAvailabilityPlatformTest、AppIconPickerPlatformTest。HarmonyOS 已有 DataParityChecks、Review031DataChecks、LocationServiceChecks，但静态存在不等于当前版本已执行通过。

本轮新增问题仍需对应的定向验证：删除取消的三个断点；系统回收站原件与独立标注的工程删除；大屏双栏删除/移入/移出竞争；鸿蒙样式重启恢复、快捷水印与工程/画质异步写入交错；文件删除失败及数据库提交失败。权限和系统目录还需真机验证大致位置、仅本次授权、回收站恢复、不同文件提供方写入失败、图标切换与系统桌面反映。不得把这些待测项目写成已通过或已失败的运行结论。
