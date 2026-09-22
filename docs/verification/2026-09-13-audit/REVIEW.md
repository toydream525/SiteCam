> 本文保留审查与方案形成时的历史状态；当前实施和设备验证结果以 [FINAL-STATUS.md](FINAL-STATUS.md) 为准。

# v0.3.1 双端审查与改进方案

审查日期：2026-09-13。对象：SiteCam / 工程水印相机当前工作区、华为平板和小米 14 上的 0.3.0（13）。

**本轮归档 13 项问题、1 项界面调整要求，以及双端新增“地址刷新”和“海拔水印”两项功能，目标版本 v0.3.1。仓库业务代码、原有测试代码与版本号均未修改；本文只记录问题、证据、拟修方案和验收条件。** 工作区原有未提交改动保留，本报告结论针对本次读取、构建和测试的工作区内容。鸿蒙镜头补查使用构建缓存中的临时测试页面，调用未修改的生产相机服务；完成后已恢复鸿蒙测试入口与设备上的原测试模块。新增的[双端工程包切换方案](/Users/ninja/Documents/工程水印相机/docs/verification/2026-09-13-audit/PROJECT-SWITCHING-PLAN.md)包含切换、查看、锁定及验收规则。

按用户要求先审查鸿蒙，随后在连接的小米 14 上开始安卓检查。安卓已完成设备能力、正式版取景界面、安装包教程和应用内公开相机枚举。最初测试安装被拒绝，后续显式覆盖安装已成功恢复调试版并安装测试组件；手动测试取得枚举结果，但未完成实际拍摄或控件断言，详见“安卓实体设备补查”。此前已完成的安卓代码对照和自动化结果保留。证据分为“真机复现”“代码路径确认”“用户已确认”；代码路径确认表示入口、状态变化与写入结果可以从现有实现确定，并不表示已逐项在用户资料上操作复现。

## 结论表

P1：可能改写已有工程数据，或主要功能在已测设备上不可用。P2：局部功能、界面或交付范围错误。

| 编号 | 问题 | 级别 | 鸿蒙确认方式 | 安卓版对照结论 |
|---|---|---|---|---|
| H1 | 视频水印处理在华为平板失败 | P1 | 真机测试报“编码时间戳接口不可用” | 使用不同转码实现，没有相同的 EGL 扩展依赖；硬件转码未实测 |
| H2 | 已移动的视频重试处理后，工程归属变回拍摄时的工程 | P1 | 代码路径确认，失败分支同样会写入错误归属 | 重试读取当前媒体记录并保留当前工程 ID，未发现相同机制 |
| H3 | 分栏详情保留旧数据，另存相册可能覆盖刚更新的问题记录 | P1 | 代码路径确认 | 详情订阅数据库更新，问题单独保存；相关数据事务测试通过 |
| H4 | 编辑成品重新进入编辑器，初始显示原图，旧编辑到下一步才出现 | P2 | 代码路径确认 | 进入编辑器会恢复并渲染编辑步骤；保存后重开集成测试通过 |
| H5 | 只导出选中媒体时，包含未选工程的信息文件 | P2 | 代码路径确认 | 从选中媒体推导工程集合，未发现相同入口缺陷；导出引擎测试通过 |
| H0 | 拍照后 UI 上下颠倒 | P2 | 用户已确认，按最新说明归类为界面方向问题 | 有三向界面方向过滤，但缺少此次拍摄前后真机验证，不能认定已排除 |
| H7 | 广角入口与独立长焦能力 | P2 | 界面固定 1/2/3/5×；真机服务层 0.54× 广角拍摄成功 | 小米 14 正式版已有 0.6× 入口；当前后摄组不含独立长焦，未完成实际变焦拍摄 |
| H8 | 教程章节切换异常，且包含过时的平台与版本说明 | P2 | 用户确认切换异常；代码与 ArkUI 更新规则定位，旧文案直接确认 | 已安装的正式版教程仍标 0.2.8；未发现相同正文更新机制，滚动与切章体验待实测 |
| H9 | 工程包不能顺畅地自由切换，查看与拍摄选择入口混淆 | P2 | 用户已确认；当前卡片用于查看，切换藏在更多菜单；菜单混用两种按钮配置 | 用户同样报告；卡片进入资料，切换隐藏且拒绝锁定或归档项；选择成功没有一致的返回相机流程 |
| H10 | 闪光灯图标不随模式切换，常亮状态未同步到界面 | P2 | 用户已确认；固定黄色图标、二值界面状态及长按回调路径直接确认 | 关闭／自动／开启已有不同图标，未发现同一机制；开启和常亮仍共用图标，实际灯光待真机验证 |
| H11 | 普通直屏手机的鸿蒙竖屏倍率布局需与安卓统一 | P2 | 倍率按钮当前平分整行宽度；仅普通直屏手机改用安卓方案，平板、阔屏与折叠内屏保持现有间距 | 竖屏采用固定宽度按钮并整体居中，作为普通直屏鸿蒙的对齐基准 |
| H12 | 没有拍照声音，设置中也没有声音开关 | P2 | 用户已确认；拍摄链路未接入快门音播放，设置模型和页面没有对应开关 | 已有“拍照快门声音”开关，默认开启，并调用系统快门声；实际听感待真机验证 |
| H13 | “问题”按钮选中后仅文字变色，图标不变色 | P2 | 用户已确认；固定双色图标与文字分别绘制，只有文字绑定选中颜色 | 图标颜色已随问题模式状态变化，未发现相同机制 |

H6 为用户明确要求的界面调整：常用工程部位／施工标签保持横向滑动，隐藏滚动条。它不作为新增功能故障计数。

安卓已确认当前后摄分组能力限制和教程版本文案过时，尚未完成广角、高倍率拍摄及拍照后 UI 方向验收。既有代码对照和测试结果不等于所有硬件和操作组合均已通过验收。

## H1：视频水印处理失败

**触发与实际结果：** 在已连接的华为 MatePad Mini（MLR-AL10，系统报告 OpenHarmony 7.0.0.105 / API 26）运行现有“原生视频水印与音轨”测试，进入原生视频处理后失败，返回“编码时间戳接口不可用”。这是本次真机结果。

**原因与范围：** [video.cpp 第 51 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/cpp/video.cpp:51) 通过 `eglGetProcAddress("eglPresentationTimeANDROID")` 查找 Android EGL 扩展；本设备返回不可用，随后直接抛错。正常录像和详情页重试均会经过该转码路径。现有失败处理保留原片并标记失败，但无法生成带水印的成功成品。未把其他系统、其他机型也判定为必然失败。

**拟修方案：** 替换为受鸿蒙编码输入 Surface 支持的时间戳提交方式。可评估本机 SDK 已声明的 NativeWindow `SET_UI_TIMESTAMP` 接口，实施时必须确认其适用场景、时间单位与输入帧 PTS 的映射，不能仅以编译成功作为完成依据。保留 HarmonyOS 5 / API 12 兼容目标，继续保留失败原片、可重试状态和临时输出清理。

**验收条件：** 先使用同一带音轨样片回归，再测试真实录像；首、中、尾帧均有完整水印，音轨存在且声画同步，时长和方向正确；中途取消、失败后重试均保留有效原片。当前错误解除后才可继续判断后续编码、封装或音轨环节是否还有问题。

**安卓对照：** [VideoWatermarkTranscoder](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/media/VideoWatermarkTranscoder.kt:45) 使用 Media3 Transformer，不调用此 EGL 扩展。本轮未在安卓实体设备完成视频转码与播放验收。

## H2：视频重试回写旧工程归属

**触发步骤：** 在工程 A 录制一个待处理视频 → 将视频移动到工程 B → 在详情页重试水印。

**确定的结果：** [Store.moveMedia](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/Store.ets:43) 更新媒体及问题记录的当前工程；拍摄快照仍保存 A 的历史信息。重试时，[VideoService 第 13 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/VideoService.ets:13) 调用 `hydrateMedia`，[第 22 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/VideoService.ets:22) 把 `snapshot.projectId` 写回当前媒体并立即持久化。失败恢复只还原文件、尺寸等字段，没有还原 `projectId`，所以即使随后触发 H1，归属也会变回 A。若 A 已被删除，还可能产生指向不存在工程的媒体记录。

**拟修方案：** 将拍摄时的水印信息与当前工程归属分开；重试从数据库读取当前媒体，只更新处理状态、输出路径、尺寸、时长等处理字段。当前工程归属及问题关联由移动操作维护，禁止通过历史快照恢复。

**验收条件：** A → B 后，重试成功、失败、取消都仍归属 B；问题记录同步属于 B；原始拍摄水印仍保留 A 的历史信息。再覆盖 A 已删除、媒体已删除的情况，避免生成孤立记录。

**安卓对照：** [PhotoDetailViewModel 第 88 行](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/gallery/PhotoDetailViewModel.kt:88) 在互斥操作内读取最新记录，转码成功只复制更新输出字段，保留当前 `projectId`。本轮未运行“真实视频移动后重试”的端到端操作；结论是未发现鸿蒙这条历史快照覆盖归属的路径。

## H3：分栏详情旧数据覆盖新问题记录

**触发步骤：** 宽屏相册打开一项无问题的资料 → 在左侧卡片添加问题 → 在仍打开的右侧详情选择另存到系统相册，并完成系统保存。

**确定的结果：** [openDetail](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:277) 深拷贝当前媒体；左侧卡片的 [onIssue](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/GalleryPageView.ets:68) 操作列表对象。[保存问题](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:246) 后的 [refresh](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:203) 只刷新列表，未更新详情副本。右侧另存相册成功后，[第 492 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:492) 将旧详情整条写回；[Store.putMedia](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/Store.ets:42) 因旧标题为空而删除刚创建的问题记录。旧标题非空时也可能覆盖新标题、说明、等级或处理状态。

同一状态失步还会使批量删除后右侧保留已删除资料；单项详情删除已主动清空，不属于这个触发条件。

**拟修方案：** 详情保存选中 ID，展示数据库最新记录；列表刷新时同步详情，记录不存在则清空详情和导航。另存相册只写 `albumUri`，问题编辑只写问题字段；更新已删除媒体时应明确失败，不能由旧副本继续写入关联数据。

**验收条件：** 左侧修改问题后右侧立即一致；随后另存、编辑、重试处理均不回滚问题内容；移动选中项后详情工程正确；批量删除选中项后右侧清空。

**安卓对照：** [详情媒体和问题订阅](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/gallery/PhotoDetailViewModel.kt:36) 随数据库更新；[IssueDao 第 69 行](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/database/dao/IssueDao.kt:69) 保存问题前读取真实工程，拒绝已删除媒体。本次 `moveAndIssueChangesKeepBothProjectAndIssueFlagConsistent` 测试通过，包含持有旧工程信息后保存问题的情况；它不代替分栏界面的完整操作验证。

## H4：编辑器重开没有显示已有成品

**触发步骤：** 照片裁剪、旋转或标注后保存 → 再次进入编辑器。

**确定的结果：** [beginEdit 第 284 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:284) 加载已有编辑步骤后，将预览直接设置为原图路径和原图比例；进入时未调用渲染。直到下一次操作执行 [renderEdit](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:287)，旧步骤才与新步骤一起显示。已有裁剪或旋转时，用户第一次新标注所依据的画面与实际应用标注的画面不一致。

**拟修方案：** 进入编辑器时恢复编辑步骤并生成正确预览、尺寸，预览就绪后再允许标注；可先显示有效成品作为加载画面。同步重置本次编辑状态，保留独立原件。

**验收条件：** 裁剪 + 标注 + 旋转保存后重开，首屏与上次成品一致；第一笔标注位置正确；撤销、重做、连续裁剪有效；原件保持不变。

**安卓对照：** [PhotoAnnotationViewModel 第 174 行](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/annotation/PhotoAnnotationViewModel.kt:174) 恢复步骤后立即刷新预览。本次 `opensRealContentUriTransformsSavesAndReopensWithoutChangingOriginal` 集成测试通过，覆盖打开、变换、保存、重开与原件保留。

## H5：选中媒体导出带入其他工程信息

**触发步骤：** 应用有工程 A、B，只选择 A 中一张照片，在相册点击导出。

**确定的结果：** [相册导出入口第 478 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:478) 将所有工程 ID 和选中媒体 ID 一起传入；[exportNow](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:276) 只缩小媒体集合，没有缩小工程集合。[ExportService 第 34 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/ExportService.ets:34) 仍为 B 建目录和 `project_info.json`，包含名称、地址、备注等信息。确认的是未选工程信息进入交付包；未选的 B 照片不会因此被全部复制。

**拟修方案：** 选中媒体导出从最终选中媒体推导工程集合；整工程导出保留独立入口。导出开始时固定选择范围，若媒体已移动、删除或选择为空，应提示刷新，避免扩大导出范围。

**验收条件：** 仅选 A 的媒体只输出 A 的工程信息和对应文件；跨 A、B 选择时只输出 A、B；不带入第三个未选工程的目录、信息或清单；整工程导出维持完整交付行为。

**安卓对照：** [GalleryViewModel 第 139 行](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/gallery/GalleryViewModel.kt:139) 已从选中媒体推导工程集合，[导出引擎第 179 行](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/export/ProjectExportEngine.kt:179) 再检查选择有效性。本次导出引擎两项集成测试通过，其中包含未选工程被排除、缺失文件报告、压缩副本与拍摄 EXIF 保留；未把这些引擎测试表述为完整导出选择器操作验收。

## H0：用户已确认的拍照后 UI 上下颠倒

**确认对象是 UI 朝向。** 根据用户 2026-09-13 的更正，本条不再归类为照片像素或 EXIF 方向故障。症状已经确认，不作为本轮额外复现的重点。

相关状态链：拍照前 [第 236 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:236) 临时锁定窗口方向，拍照结束 [第 244 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:244) 恢复自动策略；[applyWindowOrientation 第 247 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:247) 用拍摄角度映射窗口方向，设置操作异步执行且未等待。当前证据能定位需要审查的转换链，但尚不足以认定倒置的唯一根因。

**拟修方案：** 分开维护窗口方向、传感器方向和拍摄输出方向；拍照期间冻结已经生效的 UI 方向，不重新用拍摄输出角度推导 UI。统一且串行地处理临时锁定、成功或失败后的恢复及离开相机页的恢复，避免晚到回调覆盖新状态；核对平板自然方向与左、右横屏枚举的对应关系。

**验收条件：** 竖屏、左横屏、右横屏分别拍照，在按下、保存中、保存结束三个时点 UI 均保持正确朝向；连拍操作、保存失败、拍摄中切后台、相册返回，以及系统旋转锁定状态下结果一致。分别覆盖普通手机、自然横屏平板和折叠内外屏，不以照片方向测试代替 UI 验收。

**安卓对照：** [CameraScreen 第 176 行](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/camera/CameraScreen.kt:176) 在拍摄期间不改窗口方向，空闲时显式选择三种允许方向；[CaptureOrientation 第 27 行](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/camera/CaptureOrientation.kt:27) 过滤倒置角度。相关方向逻辑测试通过。随后连接的小米 14 已显示正常横屏取景 UI，但没有完成拍照前后 UI 朝向验证，故本条安卓状态仍为待真机核对。

## H7：广角入口与长焦能力核查

**当前平板的实测结果：** 通过未修改的 `CameraService`，依次调用 0.54× → 1× → 2× → 3× → 5× → 0.54× → 1×，7 次均返回可解码 JPEG，没有相机错误。服务返回后置变焦范围 **0.54–10×**。测试只保存了倍率、尺寸和镜头元数据，没有将测试照片写入工程相册。

| 请求倍率 | JPEG 中实际焦距 | 35 mm 等效焦距 | JPEG 数码倍率字段 | 结论 |
|---|---|---|---|---|
| 0.54×，两次 | 1.9 mm | 14 mm | 0.54 | 广角可以被现有服务调用，并能切回再次拍摄 |
| 1×，两次 | 7.0 mm | 25 mm | 1.00 | 主摄正常返回 |
| 2× | 7.0 mm | 50 mm | 2.00 | 主摄数码变焦 |
| 3× | 7.0 mm | 75 mm | 3.00 | 主摄数码变焦 |
| 5× | 7.0 mm | 125 mm | 5.00 | 主摄数码变焦 |

结合[华为官方规格](https://consumer.huawei.com/cn/tablets/matepad-mini/specs/)列出的主摄和广角配置，以及上述焦距结果，这台平板没有可供本轮验收的独立长焦镜头。高倍率拍摄成功不能表述为独立长焦切换成功。此次完成的是照片服务层验证；录像中的镜头切换和视角变化未完成验收。

**确定的问题：** [竖屏倍率入口第 347 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:347) 和 [横屏倍率入口第 418 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:418) 固定为 `[1,2,3,5]`，未提供设备已经支持的广角倍率。现有双指缩放与相机服务可以接受低于 1× 的倍率，因此应修复能力到界面入口的映射。

**拟修方案：** 依据当前镜头和拍摄模式的真实变焦范围生成倍率入口，低于 1× 时提供明确的广角选择；按钮使用设备实际支持值。切换前后置或照片／录像模式后，重新读取范围并同步界面值。存在单独可访问长焦设备时核对物理镜头、焦距与可用输出配置；仅数码变焦时按实际能力展示。

**双端镜头识别要求：** 将硬件配置、应用可访问镜头、当前模式可调用镜头和实际活动镜头分开记录。广角／主摄／长焦由相机类型、逻辑与物理成员、等效视角及有效帧信息共同确认，不能按按钮倍率或不同传感器的实际毫米焦距直接猜测。小米 14 应识别为具有长焦硬件，但本次调试包公开后摄组未暴露该长焦；不得显示成“没有长焦”，也不得把 3×／5× 数码变焦写成已调用长焦。完整能力识别、API 兼容和验收矩阵见[双端广角与长焦方案](/Users/ninja/Documents/工程水印相机/docs/verification/2026-09-13-audit/LENS-CAPABILITY-PLAN.md)。

**验收条件：** 在当前平板从界面直接选广角、切主摄、再切广角，拍摄元数据与服务层结果一致；倍率高亮与实际请求一致；前后置和模式切换后不保留失效倍率。独立长焦需在配有长焦的手机上验证实际焦距／活动镜头变化，不能只检查“3×”“5×”按钮能点。

**安卓代码边界：** [CameraCapability 第 25 行](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/camera/CameraCapability.kt:25) 先选择 0.5／0.6 候选，再过滤出设备范围；最低倍率为 0.54 等边界值时，候选可能被全部过滤，导致广角入口缺失。小米 14 本次读取的范围为 0.6–10×，正式版界面已有 0.6× 按钮，没有在这台手机复现广角入口缺失；实际镜头调用边界见下文。

## H6：常用工程部位／施工标签隐藏滚动条

保留横向滑动和点击填入能力，隐藏该行的滚动条。定位于 [WatermarkPanels 第 108 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/WatermarkPanels.ets:108)，现有横向 `Scroll` 没有关闭滚动条。

拟对该行设置滚动条关闭，并核对滑动首尾、长标签、大字号和横竖屏，保证不裁掉标签和点击区域。安卓版对应行使用横向滚动布局且未绘制专门滚动条，后续手机上确认视觉结果。

## H8：教程章节切换与内容问题

**已确认症状：** 用户报告从“使用技巧与教程”进入后不能正常切换教程。

**代码定位：** [HelpPageView 第 21 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/HelpPageView.ets:21) 使用 `@Builder section(s)` 接收普通值；[第 22 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/HelpPageView.ets:22) 改变 `selected` 后，两个有效章节仍处于同一个条件分支，正文继续依赖传入的旧 `s`。编译输出也显示正文更新回调捕获 `s.title` 和 `s.lines`。[ArkUI 官方参数传递规则](https://raw.githubusercontent.com/openharmony/docs/master/zh-cn/application-dev/ui/state-management/arkts-builder.md)说明，按值传递不会随着外部状态变化刷新 Builder 内的 UI。这是与“选了新章节但正文没有正常切换”一致的代码原因。

章节导航还放在正文的同一个纵向滚动区中，读长文后切换入口会离开视野；返回按钮直接退出至设置，未提供明显的“回目录”操作。

**内容同时存在确定错误：** 页面仍写着 v0.2.8、Android 8.0、下载 APK，以及“鸿蒙未签名开发包”等过时内容；这与当前鸿蒙 0.3.0 已安装包和 v0.3.1 目标不一致。

**拟修方案：** 将章节正文改为直接依赖选中章节状态的组件或受支持的引用传递；单独管理正文滚动，切章回到顶部。手机保留容易到达的目录入口，平板使用可见目录与正文，明确章节返回和退出教程的层级。统一更新鸿蒙的安装、版本、功能和保存说明，以实际可用功能为准。

**验收条件：** 连续切换全部章节，选中标题、正文和目录同步；长章末尾仍能换章，切换后显示新章开头；回目录、返回设置和重看首次指引均正常；横竖屏、大字号、窄窗口下不丢失导航。

本次另作了组件交互测试尝试，但设备中测试 Driver 未初始化，未执行点击用例，不能记为自动化复现。上述症状来自用户确认，原因依据代码与框架规则定位；实际点击回归仍列入修复后的验收。

**安卓对照：** 从小米 14 当前已安装的正式版 APK 读取 `assets/docs/USER_GUIDE.md`，其中仍写“本版：0.2.8”，确认教程版本文案未更新。Android 8.0 / API 26 是安卓版当前支持下限，不是安卓版的文案错误。[HelpScreen 第 136 行](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/help/HelpScreen.kt:136) 从选中状态重新取章节并渲染正文，未发现鸿蒙按值 Builder 捕获旧章节的机制。

安卓目录与正文在 [第 143 行](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/help/HelpScreen.kt:143) 共用一个滚动状态；切章仅改变选中项和目录显示状态，没有滚动复位，且“返回目录”位于正文滚动区顶部。因此存在从目录后部进入章节时首屏位置不理想、长文末尾返回目录不便的风险，尚未记为真机复现。拟分别维护目录／正文滚动位置，进入新章节回到开头，并保留可达的返回目录入口；更新双端各自正确的教程内容。

## H9：工程包自由切换与锁定关系

**已确认症状与范围：** 用户报告鸿蒙和安卓无法顺畅地自由切换已有工程包。本条审查工程选择及其与锁定的关系，尚未确认锁定字段保存失败、重启后丢失或锁定后仍能拍摄，不另计为独立故障。

**共同入口问题：** 安卓 [ProjectListScreen 第 514 行](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/projects/ProjectListScreen.kt:514) 正常点击工程卡片进入该工程相册，[第 671 行](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/projects/ProjectListScreen.kt:671) 才在“更多”中提供“设为拍摄工程”。选择会保存当前工程，但没有配套的成功后返回相机流程。鸿蒙当前使用的 [ProjectsPageView](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/ProjectsPageView.ets:8) 在窄屏进入资料、宽屏聚焦详情，实际根页面没有直接的切换回调。两端均限制已锁定或归档的工程成为当前拍摄工程，容易把浏览、当前选择和拍摄许可混在一起。

**鸿蒙额外风险：** [openProjectMenu 第 270 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:270) 同时设置 `primaryButton`、`secondaryButton` 与 `buttons`，而 SDK 与[官方接口说明](https://raw.githubusercontent.com/openharmony/docs/master/zh-cn/application-dev/reference/apis-arkui/arkui-ts/ts-methods-alert-dialog-box.md)将双按钮和按钮数组分开定义。需要改成一种明确的动作面板；当前没有运行时按钮展示证据，不能把哪组按钮被忽略认定为唯一根因。

**拟修方案：** 相机顶部持续显示当前工程名，点名称、点目标工程，两次点击完成切换并回到拍摄。工程卡片显式区分“切换到此工程”和“查看资料”，保留可见的编辑、导出与锁定／解锁。当前工程选择与拍摄许可分开：锁定工程可以选中、查看和导出，新增拍摄保持禁用；解锁是独立操作。归档恢复不清除锁定。切换及状态更新成功后才反馈成功；保存期间冻结拍摄归属，受限操作给出原因。

**验收重点：** A → B → A 的顶部、水印、成片归属一致；查看 B 不改变当前 A；锁定 A 后仍能切换 B；选中锁定 C 不自动解锁；恢复归档保留锁定；拍照保存和录像中不串工程；重启持久化、失败重试、长工程名与横竖屏均符合规则。完整流程、改动落点及验收矩阵见[双端工程包切换方案](/Users/ninja/Documents/工程水印相机/docs/verification/2026-09-13-audit/PROJECT-SWITCHING-PLAN.md)。目前只完成用户反馈、代码核对与交互示意，不计为应用真机修复通过。

## H10：鸿蒙闪光灯图标与模式不同步

**已确认症状：** 用户报告鸿蒙闪光灯图标不切换。此次代码核对可以直接解释该界面现象，不需要假设照片闪光硬件已经失效。补查时华为设备已断开，未进行新的真机灯光测试。

**确定原因：** [Index.ets 第 330 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:330) 无论当前模式都显示同一个 `ic_flash` 图标、同一段“闪光”文字。[图标资源](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/resources/base/media/ic_flash.svg:1) 还固定填充黄色，页面没有按模式更换图形。点击后 [第 332 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:332) 把相机返回的模式压成“是否非关闭”的布尔值，因此自动和开启都是 `true`，连文字颜色也无法区分这两种状态。

**常亮同属这条状态问题：** [第 333 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:333) 长按直接调用常亮开关，没有回写页面状态；松手还会关闭常亮。比如界面之前是自动／开启状态，松手后相机服务已经为关闭，页面的布尔值仍可能保持开启。这是代码路径确认，尚未把物理灯光的实际变化记为真机通过。

**拟修方案：**

1. 页面使用完整模式状态，与相机服务共享同一组关闭、自动、开启、常亮值；图标、文字、颜色和无障碍提示都由这个状态生成。
2. 关闭显示带斜线的闪光图标和“关闭”；自动显示带 A 标记的图标和“自动”；开启显示闪电和“开启”；常亮使用独立标记和“常亮”。不能只靠颜色表达状态。
3. 保留短按循环，按当前会话实际支持的模式生成顺序。不支持的模式不进入循环；设置失败保留原状态并提示，设置成功后读取服务实际模式再更新界面。新会话、切换镜头、拍照／录像模式及返回前台后重新同步；能力变化导致回落关闭时，界面同样显示关闭。
4. 长按行为建议与安卓版及现有教程对齐为“切换常亮”，再次长按关闭；释放手指不再产生第二次模式切换。长按与短按避免同时触发。相机关闭或会话重建后若常亮已关闭，界面同步关闭，不显示虚假的常亮状态。

**安卓对照：** [CameraTopBar 第 67 行](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/camera/components/CameraTopBar.kt:67) 已分别使用关闭、自动、开启图标，没有鸿蒙“所有模式都用固定图标”的问题。当前 `TORCH` 与 `ON` 仍共用开启图标，无障碍提示也固定为“闪光灯，长按常亮”；方案中一并明确常亮标记与当前模式描述。[CameraViewModel 第 383 行](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/camera/CameraViewModel.kt:383) 已实现长按切换常亮。实际灯光、切镜头和失败回退仍需真机验收。

**验收条件：** 在支持的模式间连续循环两轮，图标、文字、读回状态始终一致；自动模式按环境决定是否补光，不能把“选中自动”误写成“灯已点亮”。检查长按常亮开／关、抬手、再次短按、前后摄与广角切换、拍照／录像切换、前后台切换、无闪光灯设备、设置失败和横竖屏。拍照用真实灯光与成片验证，常亮用持续照明验证，不能只看图标变了就判定硬件通过。

## H11：普通直屏手机的鸿蒙竖屏倍率与安卓统一

**最终适用范围：** 平板、阔屏手机、折叠屏内屏的竖屏倍率间距保持现有方案，不执行收紧改造。只有普通直屏手机的鸿蒙版，改用与安卓一致的紧凑居中排列。其他折叠外屏沿用既有适配。本轮仍未修改应用。

**已确认症状与原因：** 用户报告竖屏焦距切换的文字相距太远。[Index.ets 第 346 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:346) 的倍率行虽然只设置 2 vp 的行内间隔，但每个倍率按钮在 [第 348 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:348) 使用 `layoutWeight(1)`，父行在 [第 353 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:353) 填满 100% 宽度。因此四个文字中心实际上平分整条操作区，增加屏幕宽度就会继续拉大间距；只把行内间隔从 2 改成 0 无法解决。

**普通直屏手机方案：** 对齐安卓 `ZoomPillGroup` 的固定尺寸按钮与整体居中方式，不再把每个按钮拉伸至等分整屏。常规每项 44 × 44 vp，项间不再额外分摊屏幕宽度；四项整体约 176 vp，五项约 220 vp。窄空间下沿用安卓的紧凑视觉规则，同时核对足够且不重叠的有效点击区域。数字使用可读字号，选中项以高亮及明确的选中状态表示。

保持“倍率 → 拍照／录像模式 → 快门”顺序和独立的垂直间距；不通过负偏移或压缩快门握持空间制造紧凑效果。普通手机的大字号先按文字实际宽度扩展按钮，空间不足时允许倍率组横向滑动并保持当前项可见，不强行缩小字体或把文字挤到下一排。本条不将普通手机参数套到平板、阔屏、折叠内屏或横屏镜头栏。

**设备分档：** 采用已有[设备档位与折叠状态](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/DeviceLayoutProfile.ets:5)判断普通直屏手机，结合设备类型与是否可折叠；不能仅凭当前窗口宽度或“不是大屏”判断，以免平板分屏、阔屏竖屏或折叠外屏误入新方案。设备原有布局分类不因这次倍率改造被整体重写。

与 H7 联动时，各类设备都应提供当前会话实际支持的广角、主摄和长焦入口；平板、阔屏与折叠内屏按原布局规则放置，不因此整体改成手机的紧凑样式。镜头识别与布局分档相互独立，倍率按钮不代表一定使用独立长焦。

**安卓对照：** [ZoomPillGroup 第 33 行](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/camera/components/ZoomPillGroup.kt:33) 竖屏默认按钮宽高为 44 dp，紧凑模式为 38 × 36 dp；父级 [CameraBottomBar 第 243 行](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/camera/components/CameraBottomBar.kt:243) 将倍率组整体居中，没有鸿蒙的整屏等分行为。这里只确认布局机制不同，未将安卓各尺寸实际显示和触区判为真机通过；大字号与紧凑模式的可读性仍按同一目标验收。

**验收条件：** 普通直屏手机上，鸿蒙与安卓的倍率组对齐相同的紧凑居中规则；平板、阔屏手机和折叠内屏竖屏与改动前对比，保留原间距与操作区布局。再检查四／五个倍率、真实镜头变化、长倍率文字、大字号、分屏和横竖屏切换，确保设备档位不误判，倍率不遮水印、不撞模式栏或快门，底部握持余量仍保留。当前依据为用户确认及代码定位，尚未实施或真机回归。

## H12：鸿蒙缺少拍照声音及开关

**已确认症状与代码范围：** 用户确认没有拍照声音，也找不到声音开关。[shoot 第 233 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:233) 目前仅触发按压／闪屏动画，然后提交拍摄并保存；[CameraService](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/CameraService.ets:59) 没有快门音调用。主业务目录中没有短音效播放器或快门音资源接入；[Settings 模型](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/Models.ets:95)及[设置页面](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/components/SettingsPageView.ets:3)也没有声音开关。不能据此认定麦克风权限或设备扬声器故障。

**拟修方案：**

1. 在设置中增加“拍照快门声音”开关，默认开启，与安卓一致；说明为“拍照时播放快门声，关闭不影响录像声音”。状态持久化，重新进入相机与重开应用后保持，不增加声音素材选择等额外功能。
2. 使用鸿蒙支持的短音效播放能力，预加载一个可随应用分发的快门音；实施时确认 HarmonyOS 5 / API 12 兼容。播放不应申请麦克风权限，也不修改系统音量或静音设置。
3. 权限、工程拍摄许可和相机就绪检查通过后，在相机接受拍摄请求／发出捕获开始反馈时播放一次。被拒绝的点击、保存中的重复点击、录像开始／停止均不播放照片快门声。音效失败不阻断照片保存，系统已经发声的路径避免重复叠加。
4. 快门音只表示这次拍摄已开始，最终保存成功仍由现有保存反馈表达；拍摄开始后后处理失败，仍需明确提示实际结果，不能用声音冒充保存完成。

**安卓对照：** [SettingsScreen 第 277 行](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/settings/SettingsScreen.kt:277) 已提供同名开关，[AppSettingsDataStore 第 74 行](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/preferences/AppSettingsDataStore.kt:74) 默认开启；[CameraBottomBar 第 95 行](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/camera/components/CameraBottomBar.kt:95) 加载系统快门声，并在空闲且工程允许拍摄时调用。没有鸿蒙的功能和开关均未接入问题；设备实际听感与失败时序尚未验收。

**验收条件：** 开启时真实拍照恰好响一次，关闭时无应用快门音，重开后状态保持；快速重复点击、无权限、锁定工程、相机未就绪时不误响；录像音轨不受开关影响。检查静音、不同音量、耳机／蓝牙与前后台恢复，遵循系统实际音频策略；可听结果必须由设备现场听音确认，构建、日志或浏览器示意不算通过。

## H13：“问题”按钮图标未跟随选中状态

**确定原因：** [Index.ets 第 334 行](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:334) 的文字根据 `quickIssueEnabled` 切换颜色，旁边的 `Image(ic_issue)` 未设置对应状态颜色。[ic_issue.svg](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/resources/base/media/ic_issue.svg:1) 内部固定使用浅色三角形和深色感叹号，因此点按只改变文字，不能带动图标一起变化。这与用户所述现象一致。

**拟修方案：** 图标和文字由同一个 `quickIssueEnabled` 状态驱动：未选中为普通前景色，选中同步为现有工程黄色；再次点按同时恢复。图标内部的感叹号保持对比，可使用明确的选中／未选中资源或支持分层着色的组件，不能把双色图标整体填成一块颜色。补充“问题标记已开启／已关闭”的无障碍状态；维持既有按钮位置、大小及问题标记业务逻辑，不额外改动其他工具栏布局。

**安卓对照：** [CameraTopBar 第 90 行](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/camera/components/CameraTopBar.kt:90) 已按 `isQuickIssueMode` 改变问题图标颜色，未发现鸿蒙的固定资源颜色机制。安卓沿用其现有问题强调色，本条只要求各平台自己的图标与选中状态一致。

**验收条件：** 连续开启／关闭，图标和文字每次同步变化；横竖屏与返回相机后状态一致；录制中等禁用状态不误切换。选中后实际拍摄的问题标记行为与此前定义一致，取消后不误加标记；图标内部符号在选中和未选中时都清晰可辨。

## 安卓实体设备补查

**设备与安装包：** 小米 14（23127PN0CC），Android 16 / API 36。正式版 `com.sitecam.app` 为 0.3.0（13），最后更新时间仍为 2026-09-12 23:12:48。本次没有替换正式版，没有在正式版工程中保存测试照片。

### 镜头事实与能力限制

相机服务记录显示，正式版正在使用后摄逻辑相机 0。取景界面可见 0.6×、1×、2×、3×、5×；在读取的 1× 帧中，实际焦距为 6.55 mm、活动物理镜头为 2。

后续从调试版应用进程调用公开 `CameraManager.cameraIdList`，实际只枚举到后摄 0 与前摄 1；后摄 0 的物理成员为 2、3，范围为 0.6–10×，未暴露独立长焦 4。该结果比 HAL 静态列表更接近应用实际能力，但来源是调试包，正式包仍需分别验收；也不等于所有厂商特有接口都不可用。诊断没有获得任何有效成片。

| 相机／分组 | 系统元数据 | 本次能确认的范围 |
|---|---|---|
| 当前后摄组 0 | 变焦范围 0.6–10×；物理成员为 3、2 | 当前绑定组包含超广角与主摄，不包含独立长焦 4 |
| 物理镜头 2 | 后摄，焦距 6.55 mm | 当前 1× 取景实际使用的主摄 |
| 物理镜头 3 | 后摄，焦距 2.16 mm | 当前组声明的超广角成员；尚未完成 0.6× 实际拍摄 |
| 物理镜头 4 | 后摄，焦距 9.0 mm | HAL 中存在独立长焦，但不在当前绑定组内 |
| 后摄组 5／6／8 | 成员包括 3、2、4 | 这些 HAL 分组未出现在服务列出的普通相机映射 0／1／7 中；不能据此承诺第三方应用可访问 |

[小米官方规格](https://www.mi.com/global/product/xiaomi-14/specs/)确认这款手机具备主摄、超广角和独立长焦硬件。系统 HAL 列出某个镜头，不代表应用能够通过公开接口使用它。根据 [Android 活动物理镜头字段说明](https://developer.android.com/reference/android/hardware/camera2/CaptureResult#LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID)，验收需对照实际活动镜头及焦距。

工作区 [CameraManager 第 229 行](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/camera/CameraManager.kt:229) 仅按前／后置选择相机；[第 404 行](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/camera/CameraManager.kt:404) 调整同一个相机的变焦，没有选择其他后摄组的逻辑。**当前绑定路径没有独立长焦成员；3×、5× 按钮不构成长焦已调用的证据。** 尚未取得这些倍率的有效变化读数或成片，不能把本条写成高倍率拍摄已通过。

**补充方案：** 实施时先通过应用实际可访问的 Camera2／CameraX 能力枚举确认后摄组，再选择能提供所需输出尺寸和镜头的会话；不硬编码其他机型的相机 ID。若系统只开放主摄与广角组，保留可用变焦并如实表达能力，不承诺独立长焦。最低倍率入口使用真实下限，修正 0.54 等边界值被候选过滤的问题。照片与录像分别核对活动物理镜头、实际焦距、成片和切换稳定性。

### 未完成测试与调试版状态

最初测试使用未修改的生产 `CameraManager` 和现有四项界面控件测试；编译通过，但测试 APK 安装返回 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`。该次 XML 记录明确为 **执行 0 项**，是最初安装失败的历史结果，不能将构建成功记作真机通过。

这次测试启动后的清理导致先前安装的 `com.sitecam.app.debug` 不再存在；包含保留数据的包列表也查不到该包。正式版仍在且版本及更新时间未变。随后显式覆盖安装已成功，当前设备包列表同时存在正式版、调试版与测试组件。**调试版安装已恢复，但此前调试数据是否仍可恢复未能确认。** 先前移除是本次审查操作造成的影响，不列为应用功能问题，已向用户说明。

后续手动镜头诊断已开始首个用例并写入公开相机枚举，但卡在界面启动阶段，未获得照片和最终断言结果。四项控件测试也仅报告首项开始，没有完成断言；为结束停滞测试，主动停止了调试进程，末尾“Process crashed”属于该终止过程，不能单凭此记录判定应用自行崩溃。修改启动方式后的临时诊断 APK 已编译，但未再次安装、运行，不计入验证结果。

后续只使用显式覆盖安装和手动启动测试，不再在已有用户安装上运行带自动清理的连接测试任务，也不在结束时卸载应用。按用户最新要求，USB 提示处理已停止，本阶段转为完成方案。正式版上的远程倍率点击未建立有效的倍率变化证据，不能据此判断按钮故障。实际广角与高倍率拍摄、教程连续切章、拍照前后 UI 朝向、录像与播放仍未完成。

## v0.3.1 双端新增功能

### F1：地址不可用时手动刷新

定位权限已开启、当前地址为空或不可用时，在相机取景画面的空闲角落显示“刷新地址”。以水印的实际边界和系统安全区选择位置，避开水印及其点击区域、快门和其他常用控制；此按钮属于操作界面，不写进照片。当前方案按拍摄前的地址刷新设计，不自动重写已有照片的拍摄记录。

点击后显示“刷新中”，同一请求完成前不重复发送。有新鲜坐标时先重试地址解析；没有有效坐标时重新请求定位再解析地址。成功更新预览地址并收起刷新入口，失败保留可重试入口及简短原因；系统定位关闭、网络或地址服务失败应能区分。刷新期间仍允许拍照，使用按下快门时已经取得的信息；晚到的地址不能改写已冻结的拍摄记录。

鸿蒙 [LocationService](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/LocationService.ets:5) 目前只在定位回调后解析地址，错误被忽略，需要增加明确刷新入口、结果状态和过期请求处理。已有 [watermarkBounds](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/pages/Index.ets:94) 可作为避让依据。安卓在 `LocationTracker`、`ReverseGeocoder` 与相机状态中接入同一行为，主动刷新应能绕过不适用的地址缓存。

验收覆盖：已授权但地址为空、定位关闭、离线、服务返回空地址、恢复后重试成功、连续点击、刷新期间拍照；四个水印角落、长地址、大字号、横竖屏和折叠布局中按钮均不遮水印。

### F2：当前海拔水印，默认关闭

新增内置“海拔”字段，放在“现场位置／拍摄地点”下方，默认关闭。开启后显示当前有效定位提供的海拔，单位为米；保留正常的零值和负值，未提供海拔或定位过期时显示“暂不可用”，不以默认 0 冒充测量值。

两端已有媒体／拍摄快照海拔字段，但尚未接入内置水印字段和默认排序。鸿蒙还需将 [Fix.altitude](/Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/Models.ets:119) 的默认 0 与“未取得值”区分。增加字段定义、开关、格式化、位置后的排序，并接入预览、照片与视频统一水印。拍摄时冻结海拔，后续编辑和导出沿用该次记录。

已有模板升级时补入关闭的海拔字段，保留用户其他字段、文字和排序；旧照片仍使用旧快照。用户开启后，海拔跟随位置字段排列。验收覆盖正值、0、负值、缺失值、陈旧定位、模板重开、升级默认关闭，以及八种样式中的预览与成品一致。

### 版本目标

后续实施统一使用 **v0.3.1**，更新双端版本名称并递增各自构建号，同时更新对应教程与面向用户的变更说明。本轮仍运行 0.3.0（13）；没有改版本文件、打发布包或发布。安卓实体阶段已经开始，安装及操作阻塞和未完成项如上所述。

## 本轮验证覆盖与未决事项

| 范围 | 已完成 | 结论与边界 |
|---|---|---|
| 鸿蒙真机 | 构建并运行现有独立测试模块，44 项中 42 通过、2 失败 | 原生视频时间戳错误已确认；另一项为马赛克像素断言失败，见下文 |
| 鸿蒙镜头 | 现有生产相机服务连续 7 次拍摄，7 次成功 | 0.54× 广角与主摄可来回调用；2/3/5× 为主摄数码变焦；未验收独立长焦或录像切镜头 |
| 鸿蒙代码 | 相机与窗口生命周期、照片处理、水印、编辑、视频、工程与问题事务、分栏相册、导出、权限与设置、宽屏折叠布局 | 新确认 H1–H5；不能把代码审查等同所有界面均真机操作通过 |
| 安卓本地测试 | 37 个测试套件、103 项，全部通过 | 包含工程事务、编辑重开、导出、定位、权限、水印、方向及布局逻辑；使用 JVM / Robolectric |
| 安卓模拟器 | API 37 Pixel 8；首轮水印平台渲染 1 项通过，界面控件 4 项未通过 | 首轮 System UI 无响应弹窗遮挡测试窗口；重启一次后，Launcher 无响应弹窗再次阻塞。失败是找不到测试操作区，未进入控件边界断言，不能据此确认应用布局缺陷 |
| 安卓实体设备 | 小米 14 已连接；读取当前后摄组、镜头焦距、正式版界面与教程；恢复调试版和测试组件，并取得调试包公开相机枚举 | 后摄 0 只有主摄与广角成员；教程仍标 0.2.8；手动测试启动但未完成拍摄或控件断言，不计通过 |
| 双端工程包切换 | 核对当前卡片、切换、锁定与拍摄归属路径，完成统一方案与交互示意 | 确认共同的隐藏入口与状态混淆；未修改应用，未完成真实工程切换和锁定回归 |
| 硬件与系统差异 | 已使用一台华为平板与一台小米 14 | 未完成 HarmonyOS 5 / API 12、其他手机与真实折叠状态、安卓实体拍摄、长视频及声画播放矩阵 |
| 系统交付流程 | 现有文件内容、元数据、失败报告测试与代码核对 | 本轮未完成系统相册选择器、文件夹选择器与第三方分享目标的所有最终界面操作 |

马赛克测试返回“马赛克未沿轨迹改变局部像素”，但检查合成测试成品能看到沿轨迹的像素块（[处理前](/Users/ninja/Documents/工程水印相机/docs/verification/2026-09-13-audit/harmony-mosaic-fixture-before.jpg)、[处理后](/Users/ninja/Documents/工程水印相机/docs/verification/2026-09-13-audit/harmony-mosaic-fixture-after.jpg)）。现有断言比较两次 JPEG 编码结果，并同时要求远离轨迹的像素差异足够小；本次采样局部变化 1872、远处变化 810，远处变化超过了断言阈值 624。因此目前只能确认该断言未通过，不能确认马赛克功能失效。后续应增加相同编码条件的空编辑对照，分开评估压缩误差与轨迹效果，再决定是否需要改功能。此次未修改断言。

另外，相机切换模式或镜头后，界面保留的变焦值是否与新会话实际变焦一致，需要实际会话读数与取景画面对照；目前只有状态同步疑点，未计入已确认问题。

## 建议实施顺序

1. **工程数据保全：H2、H3。** 先阻止旧状态回写工程归属与问题内容，并建立移动、删除、异步保存的明确验收场景。
2. **视频可用性：H1。** 在当前已复现设备上打通时间戳与输出链路，再继续音轨、方向、取消及重试验证。
3. **工程切换与相机界面：H9、H10–H13、H0、H7、H8、H6。** 明确当前工程、查看资料和锁定拍摄的规则，增加快捷切换；同步闪光灯及问题图标状态，只将普通直屏鸿蒙的倍率排列与安卓统一，保留平板、阔屏与折叠内屏现有竖屏间距；补齐拍照声音与开关，验证 UI 朝向、广角入口、教程连续切章和标签滚动条隐藏。
4. **编辑与导出：H4、H5。** 校验编辑器首屏、第一笔标注，以及最终导出范围。
5. **双端新增功能：F1、F2。** 接入地址刷新、海拔开关与水印排序，按上述权限、定位状态和布局条件验收。
6. **安卓手机阶段与 v0.3.1。** 调试版和测试组件已装好，后续解决测试启动停滞后检查实际广角、高倍率调用、教程切章、工程切换及拍摄前后 UI；独立长焦以应用能够访问的硬件能力为前提。完成后续实施与验收后，再更新版本和交付包。

以上为后续方案，尚未执行修复。

交互示意已检查工程切换、锁定后禁止拍摄、查看其他工程不改变拍摄归属，以及声音开关与闪光模式的界面状态。“问题”按钮选中前后，读取到图标和文字使用相同颜色并同步变化；320 px 预览中的五个倍率点击区均为 44 × 44 px，未出现横向溢出。上述结果仅验证方案示意，不代表鸿蒙应用已修改，也不代表设备声音或真实镜头已经验收。

## 本次证据

- [鸿蒙平板 44 项原始结果](/Users/ninja/Documents/工程水印相机/docs/verification/2026-09-13-audit/harmony-tablet-results.json)
- [鸿蒙测试完成画面](/Users/ninja/Documents/工程水印相机/docs/verification/2026-09-13-audit/harmony-tablet-test-screen.jpeg)
- [安卓 103 项测试汇总与用例名](/Users/ninja/Documents/工程水印相机/docs/verification/2026-09-13-audit/android-unit-results.json)
- [安卓平台首轮测试结果](/Users/ninja/Documents/工程水印相机/docs/verification/2026-09-13-audit/android-platform-first-run.xml)
- [安卓首轮系统界面阻塞画面](/Users/ninja/Documents/工程水印相机/docs/verification/2026-09-13-audit/android-system-ui-blocker.png)
- [安卓控件重试结果：4 项仍未通过](/Users/ninja/Documents/工程水印相机/docs/verification/2026-09-13-audit/android-platform-retry.xml)
- [重启后 Launcher 阻塞测试窗口的画面](/Users/ninja/Documents/工程水印相机/docs/verification/2026-09-13-audit/android-launcher-blocker.png)
- [鸿蒙 7 次镜头拍摄的倍率、焦距和结果](/Users/ninja/Documents/工程水印相机/docs/verification/2026-09-13-audit/harmony-lens-results.json)
- [教程组件测试未初始化的记录](/Users/ninja/Documents/工程水印相机/docs/verification/2026-09-13-audit/harmony-help-test-attempt.json)
- [小米 14 镜头分组、当前取景帧与安装包教程证据](/Users/ninja/Documents/工程水印相机/docs/verification/2026-09-13-audit/android-device-capabilities.json)
- [安卓真机测试安装被拒绝：执行 0 项](/Users/ninja/Documents/工程水印相机/docs/verification/2026-09-13-audit/android-device-install-blocker.xml)
- [安卓安装尝试、调试版影响与恢复状态](/Users/ninja/Documents/工程水印相机/docs/verification/2026-09-13-audit/android-device-test-attempt.json)
- [调试包实际公开相机枚举，尚无成片](/Users/ninja/Documents/工程水印相机/docs/verification/2026-09-13-audit/android-public-camera-enumeration.json)
- [双端工程包切换、锁定与验收方案](/Users/ninja/Documents/工程水印相机/docs/verification/2026-09-13-audit/PROJECT-SWITCHING-PLAN.md)
- [双端广角、主摄与长焦识别及验收方案](/Users/ninja/Documents/工程水印相机/docs/verification/2026-09-13-audit/LENS-CAPABILITY-PLAN.md)
