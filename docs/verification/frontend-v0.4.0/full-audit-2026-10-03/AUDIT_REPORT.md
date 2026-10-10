# SiteCam v0.4.0 双端全面检查总报告

检查：2026-10-03；汇总：2026-10-04。目标版本：v0.4.0（20）。本报告将前端审查扩展到拍摄、媒体加工、数据保存、导出、定位与权限的实际调用链。原前端问题保留编号，不因补充证据重新计数；修复安排见 [REPAIR_PLAN.md](REPAIR_PLAN.md)，各项产品修复待实施。

合并去重后共 **44 条产品问题：P1 15 条、P2 29 条**。本轮新增 18 条，其中 AND-CM01 已在当前 Android debug 包实际复现；其余新增项保留源码或条件性风险口径。两端当前源码构建均通过，Android 现有测试为 145/146 通过。

| 来源 | 条数 | P1 | P2 |
| --- | ---: | ---: | ---: |
| 原前端审查 | 26 | 9 | 17 |
| 本轮 Android 相机与媒体 | 7 | 3 | 4 |
| 本轮 HarmonyOS 相机与媒体 | 5 | 1 | 4 |
| 本轮双端数据、存储与设置 | 6 | 2 | 4 |
| 合计 | **44** | **15** | **29** |

QA-01 图标测试失配属于验证维护项，HCM-C01 帧率问题属于待设备确认候选，两者均不计入 44 条产品问题。

## 范围、证据与资源安排

| 范围 | 本轮检查 | 证据边界与来源 |
| --- | --- | --- |
| 两端页面、导航、窗口适配、设置、弹窗、编辑与前端状态 | 承接已完成的 26 条前端审查 | [原总报告](../audit-2026-10-03/AUDIT_REPORT.md)；旧 Android 运行证据仍只对应其当时的 release 包 |
| Android 相机、录像、媒体保存、水印、标注 | 核心实现全文及直接调用链；覆盖清单到文件 | [android-camera-media.md](android-camera-media.md) |
| HarmonyOS 相机、录像、原生转码、照片、水印、声音、系统相册 | 核心 ArkTS/C++ 全文及调用/错误链；覆盖清单到文件 | [harmony-camera-media.md](harmony-camera-media.md)；本轮无鸿蒙设备运行 |
| 两端数据库、工程/媒体资料、导出、定位、权限、设置 | 独立逐文件审查，覆盖迁移、事务、删除/移动、存储、导出与定位/授权归属 | [data-storage-location.md](data-storage-location.md)；具体全文和入口补读范围见该报告，不等于运行全部通过 |
| Android 现有单元/集成测试 | 146 条：145 通过、1 失败、0 跳过 | [结果 JSON](evidence/android-unit-test-results.json)、[测试日志](evidence/android-unit-tests.log)；具体失败见下文 |
| 当前 Android 源码 debug 构建 | `assembleDebug` 成功 | [构建日志](evidence/android-debug-build.log)；构建通过不等于所有运行路径通过 |
| 当前 HarmonyOS 源码 debug HAP 构建 | 增量完整构建流程通过，含 ArkTS、Native、资源与打包；33 tasks，19 executed / 14 up-to-date | [构建日志](evidence/harmony-debug-build.log)；生成 unsigned HAP，仅在缓存，未安装/发布/覆盖 dist；无设备运行结论 |
| Android 录像切后台运行 | 单实例已复现 Home 后 ERROR_SOURCE_INACTIVE，临时录像被删除且无新资料入库 | 下节给出包、事件、缓存与数据库对照；不声称被删原片已播放验证 |
| 主机资源 | 先关闭两台既有模拟器并停止旧 Gradle daemon；仅启动一个 Pixel 8，配置 1536 MB / 2 核，取证后已关闭；鸿蒙构建在模拟器全关时执行 | 初启观测 memory pressure 2，随后及收尾为 1；最终 swap 6031 MB。收尾 adb 无设备，不由此宣称 swap 已释放或所有资源完全恢复 |

“全文阅读”表示分报告清单中的完整实现；交叉模块按各报告标明的追踪范围。全面检查不等于所有设备、异常和硬件路径都已运行。源码根因、受条件限制的风险、实际测试结果与实际运行现象分别记录。

本轮环境、构建参数、运行步骤及资源收尾的完整记录见 [runtime-record.md](evidence/runtime-record.md)。

### 本轮测试失败的准确含义

唯一失败为 `AppIconVectorTest.iconCPathsCanBeParsedWithoutFallingBackToTheApplicationIcon`：测试仍读取已不存在的 `app/src/main/res/drawable/ic_launcher_c_foreground.xml`，抛出 FileNotFoundException。此前用户批准的 A–D 图标资源已切换为 PNG，测试仍固定旧矢量文件。这是 **QA-01：图标测试与当前资源形式失配**，单列为验证维护项，不把它当作“运行中的图标显示失败”，也不把 146 条写成全绿。

修正应验证当前真实图标绑定与可加载资源，保留有意义的图标检查；不能仅删除断言或跳过测试取得通过。本轮尚未修改测试。

### 当前包录像中断的实际复现：AND-CM01

本轮将当前源码构建的 `com.sitecam.app.debug` 0.4.0（20）更新安装到 Pixel 8 / emulator-5554，保留该 debug 应用数据；包记录显示更新时间 2026-10-03 23:41:48，minSdk 26 / targetSdk 36。使用新建独立工程 `FullAudit-20261003`（ID 2），不以真实工程做删除实验。见 [包记录](evidence/runtime-package.txt) 与 [初始样本状态](evidence/runtime-before.json)。

1. 正常主动停止的对照录像成功产生媒体 ID 4、时长 39,992 ms、状态 READY；这是数据库保存结果，不扩写成所有音视频内容已验收。见 [对照记录](evidence/runtime-baseline.json)。
2. 第二次录像 23:49:29.608 收到 Start；[Home 前截图](evidence/01-recording-before-home.png)显示录制 00:10。此时缓存有 `temp_record_1791042567441.mp4`，大小 57,355 字节，见 [Home 前缓存](evidence/recording-cache-before-home.txt)。
3. 按 Home 后，23:49:49.819 视频源变为 INACTIVE，23:49:54.366 收到 `Finalize [error: ERROR_SOURCE_INACTIVE]`，见 [事件摘录](evidence/recording-events.log)与 [完整相机日志](evidence/android-runtime-camera.log)。
4. [Home 后缓存](evidence/recording-cache-after-home.txt)不再有该临时录像；[数据库结果](evidence/runtime-after-home.json)仍只有先前 ID 4，无第二条记录。[返回工程相册画面](evidence/02-return-after-home.png)也显示总计 1 项、照片 0、视频 1，与仅正常停止录像入库一致。由此直接证实该错误路径删除已录临时文件且未保存新资料。

没有在删除前另行复制并播放临时原片，因此本轮不声称已证明该特定临时文件的可播放性。其包含已录帧的可保留语义依据来自 CameraX 官方 Finalize 契约，源码与官方依据详见 [AND-CM01 分析](android-camera-media.md)。Home 路径已复现不等于锁屏、存储耗尽或所有录制错误都已测。

## 新增问题汇总

具体触发、完整源码定位、官方依据、反证和最小修复详见对应分报告。优先级表示影响；“源码确认”并不表示本轮设备复现。

### Android 相机、媒体与标注：7 条

| 编号 / 级别 | 已确认根因与影响 | 证据类型 | 修复批次 |
| --- | --- | --- | --- |
| AND-CM01 / P1 | Finalize 只按 `hasError()` 分支，任意错误均删除原片；生命周期中断可能仍有可用已录帧 | 当前 debug 包 Home 路径已复现；有实际 ERROR_SOURCE_INACTIVE、删文件和无新入库证据 | R1 |
| AND-CM02 / P1 | 文件写入/Room 插入失败后，清理路径可能删掉原片与全部成品，现有重试入口无法发现未持久化素材 | CameraViewModel.kt:939–986；MediaStoreManager.kt:183、302；未注入故障 | R1 |
| AND-CM03 / P1 | “脱敏遮盖”预览与成品使用 alpha 245/230，单次覆盖仍保留底图细节 | PhotoAnnotationViewModel.kt:477；PhotoAnnotationScreen.kt:679；源码确定，未宣称全部原文可恢复 | R1 |
| AND-CM04 / P2 | 旋转/裁剪先改配方并清空元素，预览失败不回滚，后续保存可得到未显示的新状态 | PhotoAnnotationViewModel.kt:102–123、227；失败路径确认 | R2 |
| AND-CM05 / P2 | 标注成品重建 EXIF 未传拍摄海拔，原有海拔不进入新 JPEG | PhotoAnnotationViewModel.kt:262；ExifPreserver.kt:70；参数缺项确认 | R2 |
| AND-CM06 / P2 | 过期坐标仍供相机预览 GPS 使用，拍摄时 freshLocation 却将其移除 | CameraViewModel.kt:234–252、1000–1007；WatermarkData.kt:72–76；跨模块只计一次 | R2 |
| AND-CM07 / P2 | 快照存 epoch 未冻结拍摄时区，重试按系统当前时区格式化，日期时间文本可能变化 | WatermarkSnapshotCodec.kt:17；WatermarkData.kt:39；跨时区运行待测 | R2 |

来源：[Android 完整分报告](android-camera-media.md)。

### HarmonyOS 相机与媒体：5 条

| 编号 / 级别 | 已确认根因与影响 | 证据类型 | 修复批次 |
| --- | --- | --- | --- |
| HCM-01 / P1 | 超时/关开后的旧照片回调和旧 capture 拒绝访问当前 pending，可能完成或拒绝下一次拍摄；照片与新快照错配 | CameraService.ets:349、413–414、539；明确异步归属缺口，迟到复现待测 | R1 |
| HCM-02 / P2 | 取消只写原生全局标志，前处理收到取消可被 native 启动重置；提交前也不检查取消 | VideoService.ets:10–29；video.cpp:246–247；路径确认 | R2 |
| HCM-03 / P2 | 新视频未从冻结快照填地址/GPS 等结构列，当次 CSV 读空值；无 fix 的手工地址重启也不回填 | Index.ets:632；Store.ets:51–53、78；ExportService.ets:40；已与数据审计交叉确认，仅计一次 | R2 |
| HCM-04 / P2 | 95% 封顶的 UI 进度被当成活动心跳，末段正常处理超过 45 秒仍触发超时 | video.cpp:74、240；明确条件性逻辑缺陷，长视频待测 | R2 |
| HCM-05 / P2 | NV12 转 RGB 固定 BT.601 limited range，未跟随实际源矩阵/范围及相应输出元数据 | video.cpp:44、86、234–239；色彩处理缺口确认，当前设备源与可见偏差待测 | R2 |

来源：[HarmonyOS 完整分报告](harmony-camera-media.md)。HCM-C01“录像档位忽略最小帧率”仅列设备能力候选，不纳入上述 5 条；当前设备的档位、拒绝/接受/降帧结果尚未证明。

### 数据、存储、导出与定位

新增 6 条：P1 2 条、P2 4 条，均为源码调用链确认，取消/竞争/故障时序未在本轮设备复现。

| 编号 / 级别 | 已确认根因与影响 | 关键定位 | 修复批次 |
| --- | --- | --- | --- |
| AND-DATA-01 / P1 | 先删索引后调用物理删除；退出相册取消 ViewModel 协程时，文件操作或失败恢复可能不执行 | MediaDeletionCoordinator.kt:45–90；MediaStoreManager.kt:220；GalleryViewModel.kt:91；AppNavHost.kt:209–224 | R1 |
| AND-DATA-02 / P2 | 删除工程复用过滤不可用媒体的展示查询；级联清索引却遗漏其独立标注文件 | ProjectViewModel.kt:338–346；MediaItemDao.kt:14；MediaAvailabilitySync.kt:46 | R1 |
| AND-DATA-03 / P1 | 双栏工程删除与相册移动没有共同完整操作边界；移动出 A 的资料仍可被 A 的旧删除列表消除 | ProjectViewModel.kt:336–346；GalleryViewModel.kt:96；MediaDeletionCoordinator.kt:36；ProjectListScreen.kt:606 | R1 |
| HAR-DATA-01 / P2 | 控件 BLUR 已保存，但 Store.open 逐字段恢复遗漏 cameraControlStyle，重开退回 NORMAL | Models.ets:125；Store.ets:14、39；Index.ets:944 | R2 / B4 |
| HAR-DATA-02 / P2 | 快捷水印 FIFO 写旧整份 settings，独立 saveSettings 不在队列内，可覆盖后来成功保存的工程/画质 | Store.ets:33–39；Index.ets:357、692、907、944 | R2 / B4 |
| HAR-DATA-03 / P2 | 详情/批量/工程先逐文件删除后提交数据库；中途删除或最终事务失败可留指向已删文件的记录 | Index.ets:674、682；ImageProcessor.ets:45–46；Store.ets:56、59 | R1 |

完整交错步骤、反证、最小修复和覆盖清单见 [数据分报告](data-storage-location.md)。HCM-03 的视频结构化位置与 AND-CM06 的过期 GPS 预览差异在数据侧已交叉核对，仍由上表原编号计数。

## 旧 26 条的保留映射

保留 **AND-001～008、HAR-S01～S18：P1 9 条、P2 17 条**。详细长正文继续使用 [原总报告](../audit-2026-10-03/AUDIT_REPORT.md)、[Android 前端分报告](../audit-2026-10-03/android/android-audit.md) 与 [HarmonyOS 前端分报告](../audit-2026-10-03/harmony/harmony-static-audit.md)。其中只有 AND-001～005 含前一阶段的实际运行现象，不能扩展成当前 debug 包全部复现。

| 旧编号 / 级别 | 保留问题 | 证据与本轮补充 | 后续批次 |
| --- | --- | --- | --- |
| AND-001 / P1 | 折叠右栏录像入口缺失、半折内容挤压 | 旧包运行 + 源码 | B1 |
| AND-002 / P1 | 横屏日期 7–12 月不可达 | 旧包运行 | B3 |
| AND-003 / P2 | 无录音/定位提示覆盖工程入口 | 旧包运行 | B1 |
| AND-004 / P2 | 转横屏后焦圈偏离触点 | 旧包运行 + 旧几何闭包 | B5 |
| AND-005 / P2 | 三点中间设置不符最新导航 | 旧包运行 + 设计差异 | B2 |
| AND-006 / P2 | 对焦工厂未用预览显示变换、未观察异步 AF | 源码；光学效果待真机 | B5 |
| AND-007 / P2 | 管理新建保存隐式切换拍摄工程 | Android 源码；鸿蒙同语义调用链为补充，不新增编号 | B2 |
| AND-008 / P2 | 水印字段整实体异步写回可能覆盖较新字段 | 源码竞争风险；外观已有 FIFO 不能混同 | B4 |
| HAR-S01 / P1 | 设置组 Builder 按值传参缺少有效刷新依赖 | 源码/官方机制；不等同已证明线程死锁 | B2 |
| HAR-S02 / P1 | 全窗口与可用区/浮层坐标混用 | 源码 | B1 |
| HAR-S03 / P2 | 仅单一水平折痕与 Y 偏移 | 源码能力缺口 | B1 |
| HAR-S04 / P2 | 机型/默认屏幕分类与固定网格侧栏 | 源码规则 | B1 |
| HAR-S05 / P1 | 文本箭头、三点中间页、缺独立方向/设置 | 设计差异 | B2 |
| HAR-S06 / P2 | Down 即对焦、双指冲突、无 AF 状态 | 源码；镜头效果待测 | B5 |
| HAR-S07 / P1 | 真实导出流程无进度/取消页面入口 | 调用链确认 | B6 |
| HAR-S08 / P1 | 工程/问题弹窗在异步提交前关闭 | 源码失败路径 | B3 |
| HAR-S09 / P1 | 裁剪边界跨图片/连续操作残留 | 源码状态链 | B3 |
| HAR-S10 / P1 | 水印深层字段原地写不通知父预览 | 源码/官方状态机制 | B4 |
| HAR-S11 / P2 | 日期范围重开后默认当前月份 | 源码状态链 | B3 |
| HAR-S12 / P2 | 首启权限完成后返回 guide | 源码导航链 | B2 |
| HAR-S13 / P2 | 工程内层栈与根系统返回分离 | 源码；实际分发待测 | B2 |
| HAR-S14 / P2 | 相机导航等待释放、打开流程归属失效窗口 | 本轮补充部分打开失败未即时清理、旧后台关闭与新前台打开交错；不新增计数 | B5，与 R1 归属接口协同 |
| HAR-S15 / P2 | 多个设置保存未等待/处理失败 | 源码失败路径 | B4 |
| HAR-S16 / P2 | 浅色弹窗深字配深底 | 资源与源码确认 | B3 |
| HAR-S17 / P2 | 标注无取消/活动指针归属，空文字改历史 | 源码分支 | B3 |
| HAR-S18 / P2 | 辅助触区、裁剪呈现及解释小字不符契约 | 设计差异 | B1、B3 |

## 合并规则与应保留的保护

- 新录像文件保留问题 AND-CM01/02 分别是“录制完成错误分类”与“保存接管/文件所有权”，触发和最小修法不同，分别计数；两者放在同一资料保留批次实施。
- Android 工程预约和录制 token 已存在，不能套用鸿蒙 HCM-01 直接称 Android 串工程；鸿蒙当前问题是 JPEG 返回归属，不是缺少拍摄快照。
- 鸿蒙生命周期补充继续归 HAR-S14；HAR-S06 的点击/双指问题不因完整阅读 CameraService 再计一条。
- HAR-DATA-01 是成功保存后的读取遗漏；HAR-S15 是保存失败未处理，两者独立。HAR-DATA-02 即使各次 Promise 都成功也会回写旧设置，与 HAR-S10 的界面状态通知、HAR-S15 的失败处理及 AND-008 的字段实体覆盖分别计数。
- **修正旧保护结论**：鸿蒙 `saveCurrentWatermark` 的 FIFO 只保证水印任务彼此顺序，不能保护与独立 `saveSettings` 的交错。原前端报告对该入口的保护描述按本轮 HAR-DATA-02 收窄；保留顺序机制，同时修复写入字段归属。
- 视频处理结果列级更新、编辑保存先提交新指针再清旧成品、已正确持久化原片的重试入口、快门音加载与释放屏障等已有保护须保留。Android 首次录像保存失败的原片保留缺口另由 AND-CM02 修复，不能把既有重试入口当作该路径已有保护。
- 色彩风险 HCM-05、长尾超时 HCM-04 与设备候选 HCM-C01 都写明输入/设备条件。未把未开启的 API 20 Surface PTS 契约套用到 API 12 路径，也未凭“有 release 接口”新增未经证明的图像泄漏。

## 设计与既有交付状态

执行仍以 [DESIGN_CONTRACT.md](/Users/ninja/Documents/工程水印相机/assets/design/frontend-v0.4.0/DESIGN_CONTRACT.md)、[manifest.md](/Users/ninja/Documents/工程水印相机/assets/design/frontend-v0.4.0/manifest.md) 和 [ADAPTIVE_COVERAGE.md](/Users/ninja/Documents/工程水印相机/assets/design/frontend-v0.4.0/ADAPTIVE_COVERAGE.md) 为准。当前 01–31 图位，其中 23–31 为 9 张新增补图；采用 04-v3、27-v2、29-v3 等 manifest 指定版本。原前端总报告提及 29-v2 的文字已过时，本轮按当前清单。

工程名与真实 chevron 同排；闪光、方向、设置独立；设置直达完整页；方向只有自动/竖屏/左横屏/右横屏；管理保存不隐式切拍摄工程；适配使用当前窗口与 pane 约束、四边避让、二维折叠信息和系统字号。取景比例按文字契约精确实现，不能把 27-v2/29-v3 的画框当作精确尺寸标尺。图 25 误高亮也不改变样式归属。

图 12 原稿未改。A–D 两端资源及资源编译已在前一阶段完成；本轮当前 Android debug 构建与 HarmonyOS debug HAP 增量完整构建流程均成功，但不自动升级为四套桌面图标全部切换/重启已验收。鸿蒙构建使用同步当前源码的既有 `/Users/ninja/Library/Caches/SiteCamHarmonyBuild`，未安装依赖、未改签名，限制为无 daemon/无并行/内存优化与 1024 MB 堆；未安装 unsigned HAP，也未写入发布目录。

本轮没有改变应用标识、签名、版本、用户工程或真实资料。公开更新文案仍只写“更新了什么”；界面不加入实现说明、交付状态或未经要求的解释小字。本报告中的技术细节用于内部修复审查，不进入产品页面或公开更新说明。

## 未覆盖的运行证据

| 尚缺证据 | 当前能成立的结论 | 后续只补必要路径 |
| --- | --- | --- |
| 鸿蒙当前包全部运行 | 源码与 SDK 核对完成，`hdc` 当前没有设备 | 接回后按 R1/R2 与 B 批次逐项验证；不用历史截图代签 |
| 真机前后/多摄、闪光、光学 AF、有声录像与中断 | 模拟器只覆盖入口、状态或部分生命周期 | 有对应硬件再测，不把无硬件模拟器行为推成真机结论 |
| 写流/数据库失败、迟到照片、编辑预览失败 | 具体异常调用链已确认 | 对受影响分支做可控故障或延迟，不用真实资料做破坏实验 |
| 长视频末段、源色彩/成品偏差、跨时区重试 | 有可定位条件和修法 | 顺序使用一台目标设备与必要样本 |
| 真平板竖屏、真实分屏/浮窗、多折痕和各方向 cutout | 旧手机/部分折叠窗口证据与静态几何 | 只补修复涉及的当前窗口及姿态，不要求同时开多个实例 |
| 全部八水印照片/视频、导出内容、真实声音 | 现有单元/集成检查覆盖基础布局/保存，不覆盖全部实际媒体链 | 成品实际生成后再检视对应字段、方向、时长、声音与导出内容 |
