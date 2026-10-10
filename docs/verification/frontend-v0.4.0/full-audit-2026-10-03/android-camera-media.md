# Android 拍摄、媒体、水印与标注完整源码审查

日期：2026-10-03。对象：当前工作区的 Android 源码；本分报告负责拍摄与素材处理底层，不把既有 APK 的表现当作当前源码构建证明。遵循本轮“先全面检查、给出修复方案”的范围，本子任务没有修改产品代码、构建、运行测试或操作模拟器。测试与设备由主审串行调度；本报告另收录主审对 AND-CM01 的当前源码 debug 包运行补证，其余条目保持源码证据口径。

本轮新增 **7 条问题：3 条 P1、4 条 P2**。AND-CM01 已由主审运行确认收到 `ERROR_SOURCE_INACTIVE` 后原片被丢弃且未生成媒体条目；删除的原片未复制或播放，不将其可播放性写成实测结论。该错误允许输出此前帧的语义由 CameraX 官方契约支持。其余 6 条来自完整调用与异常分支，尚未获得定向运行复现。既有 AND-001～008、HAR-S01～S18 不重复编号。

## 覆盖与方法

下面“全文”表示读完文件的完整实现，而非仅查看搜索命中；交叉调用文件按列出的范围读取。

| 模块 | 全文覆盖文件 | 重点检查 |
| --- | --- | --- |
| `core/camera`，10 个文件 | `CameraCapability.kt`、`CameraCapabilityInspector.kt`、`CameraDiagnostics.kt`、`CameraManager.kt`、`CameraRotation.kt`、`CaptureOperationCoordinator.kt`、`CaptureOrientation.kt`、`OrientationManager.kt`、`OrientationStabilizer.kt`、`WindowOrientation.kt` | 能力选择与降级、绑定/解绑、观察者归属、缩放映射、对焦、图像回调、录像 Start/Stop/Finalize、方向稳定与资源释放 |
| `feature/camera` | `CameraViewModel.kt` 全文 | 工程预约与锁定、捕获快照、权限变化、照片/录像保存、失败回退、取消、异步事件与忙碌状态 |
| `core/media`，6 个文件 | `VideoWatermarkTranscoder.kt`、`VideoOrientation.kt`、`PhotoCompression.kt`、`ExifPreserver.kt`、`PhotoQualityProfile.kt`、`MediaStoreManager.kt` | Media3 转码、旋转与贴图、JPEG 尺寸/质量、EXIF、公开/私有媒体存储、输入文件所有权、异常清理 |
| `core/watermark`，8 个文件 | `engine/WatermarkLayoutEngine.kt`；`model/WatermarkData.kt`、`WatermarkFieldResolver.kt`、`WatermarkSnapshotCodec.kt`、`WatermarkStyleCatalog.kt`；`renderer/WatermarkBitmapRenderer.kt`、`WatermarkCanvasPainter.kt`、`WatermarkPreviewCanvas.kt` | 八样式、内置/自定义字段、长内容与顺序、预览/照片/视频共享绘制、快照兼容与重试 |
| `feature/annotation`，7 个文件 | `PhotoAnnotationViewModel.kt`、`PhotoAnnotationScreen.kt`、`EditRecipe.kt`、`ImageContentRect.kt`、`MosaicGeometry.kt`、`TextNoteGeometry.kt`、`model/AnnotationModels.kt` | 读原图、预览、画图/变换顺序、撤销重做、尺寸变化、裁剪、文字与遮盖、编辑保存与旧成品替换 |
| 直接依赖 | `AppContainer.kt`、`core/layout/CameraGeometry.kt`、`core/location/LocationFreshness.kt` 全文；`CameraScreen.kt:135–320,395–446,660–715`；`PhotoDetailViewModel.kt:60–169` | 实际容器实例、生命周期、当前取景比例、位置过期策略、原片重试入口与提交清理 |

源码根目录：[app/src/main/java/com/sitecam/app](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app)。位置服务、数据库、导出与权限模块的独立全面审查由其他分报告负责；本报告仅追踪本调用链需要的边界。

## 新增问题

| 编号 | 优先级 | 结论 | 证据类型 |
| --- | --- | --- | --- |
| AND-CM01 | P1 | 将可能保有有效帧的中断录像按任意错误直接删除 | 主审运行确认中断原片被丢弃且未成条目；未播放核验被删原片；源码与官方错误语义支持根因 |
| AND-CM02 | P1 | 录像持久化失败后同时清理原片与成品，无法重试 | 明确失败分支；未注入存储/数据库故障 |
| AND-CM03 | P1 | “脱敏遮盖”使用半透明颜色，保留底图细节 | 预览与成品绘制源码确认；未将全部内容可还原作为结论 |
| AND-CM04 | P2 | 编辑预览失败后保留已提交的操作，后续保存与画面不同 | 明确状态与错误分支；未注入暂时读图失败 |
| AND-CM05 | P2 | 标注成品写入 EXIF 时丢失已记录海拔 | 参数传递缺项确认；未读取本轮成品 EXIF |
| AND-CM06 | P2 | 定位过期后预览仍显示旧 GPS，拍摄成品移除 GPS | 同一时刻两条值构造路径确认；未做五分钟无定位更新运行 |
| AND-CM07 | P2 | 视频水印重试使用重试时区，不能复现原拍摄时刻文本 | 序列化与格式化路径确认；未进行设备时区切换 |

### AND-CM01 / P1：可能保有有效帧的中断录像被直接删除

- **根因与位置**：[CameraViewModel.kt:858](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/camera/CameraViewModel.kt:858) 仅 `!event.hasError()` 进入烧录/保存；任何错误均在第 870 行删除 `session.tempFile`，并释放工程预约。`CameraManager.kt:644–651` 将 `Finalize` 正常转发，没有预先区分文件是否仍可用。
- **具体触发**：开始录像且已有有效帧后按 Home/锁屏，绑定生命周期停止、CameraX 以 `ERROR_SOURCE_INACTIVE` 完成录像。`CameraScreen.kt:405–421` 只处理恢复，未在停止事件主动结束并接入保存；系统 Back 的忙碌拦截不能阻止 Home 或锁屏。生命周期导致的源停止只是一个具体入口，存储在录制中耗尽也不能仅由 `hasError()` 判断无有效文件。
- **依据与影响**：CameraX 明确说明生命周期关闭相机产生 `ERROR_SOURCE_INACTIVE` 时，输出包含此前帧；`hasError()` 对非 `ERROR_NONE` 均为真。现代码会将这段可能可播放的现场素材删除。[CameraX Finalize 官方文档](https://developer.android.com/reference/androidx/camera/video/VideoRecordEvent.Finalize#ERROR_SOURCE_INACTIVE)
- **已核对反证**：`CameraManager.stopVideoRecording()` 已处理正常主动停止及 STARTING 窗口，这不能覆盖上述任意 Finalize 错误分支；不能把所有录制错误一律改成成功，编码失败、无有效数据等仍需清理无效输出。
- **主审补证**：主审以当前源码构建的 debug 0.4.0（20）运行，包更新于 23:41:48；只启动 Pixel 8 / emulator-5554，配置 1536 MB、2 核。新测试工程 `FullAudit-20261003`（id 2）正常停止的一条录像已生成 `READY` 媒体 id 4，时长 39,992 ms。第二次录像日志于 23:49:29.608 收到 Start，按 Home 前截图显示 00:10；Home 后 23:49:49.819 为 INACTIVE，23:49:54.366 收到 `Finalize [error: ERROR_SOURCE_INACTIVE]`。此前缓存文件 `temp_record_1791042567441.mp4` 为 57,355 字节，随后消失；数据库前后均只有正常停止那条录像，未新增中断录像条目。由此确认错误分支丢弃文件及未成条目；被删原片未复制、未播放，不能声称本次实测证明它可播放。主审结束后已关闭模拟器。
- **运行证据**：[录制事件](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/full-audit-2026-10-03/evidence/recording-events.log)、[Home 前截图](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/full-audit-2026-10-03/evidence/01-recording-before-home.png)、[运行基线](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/full-audit-2026-10-03/evidence/runtime-baseline.json)、[Home 后数据库](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/full-audit-2026-10-03/evidence/runtime-after-home.json)、[Home 前缓存](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/full-audit-2026-10-03/evidence/recording-cache-before-home.txt)、[Home 后缓存](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/full-audit-2026-10-03/evidence/recording-cache-after-home.txt)、[包信息](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/full-audit-2026-10-03/evidence/runtime-package.txt)、[完整相机日志](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/full-audit-2026-10-03/evidence/android-runtime-camera.log)。
- **最小修复**：按 `Finalize.error` 与实际媒体可读性决定“保存已有片段”或“清理无效文件”，可用片段进入现有冻结会话的保存链；生命周期结束时明确停止录制，同时保留错误分类作为兜底。中断提示不能代替素材保存。
- **修复后必要验证**：一台设备顺序执行“录制数秒 → Home → 返回”和锁屏情形，核对相册条目、时长、可播放片段及水印；错误注入只覆盖 SOURCE_INACTIVE 有帧、无有效帧及编码失败三类。本轮主审已记录 Home 路径的具体错误码，但锁屏和修复后的素材恢复尚未验证。

### AND-CM02 / P1：保存失败清理掉唯一可恢复录像

- **根因与位置**：[CameraViewModel.kt:939](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/camera/CameraViewModel.kt:939) 保存文件后插入 Room；插入异常在第 971 行删除保存 URI。外层 `finally` 第 985–986 行不区分成败，删除录制原片及烧录文件。[MediaStoreManager.kt:183](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/media/MediaStoreManager.kt:183) 和第 302 行还在数据库接管前删除调用者输入文件。
- **具体触发**：正常录完、原片可用，但公开相册写流失败/空间不足；或者文件复制成功而 Room 插入失败。前一种情况外层清理删除原片；后一种情况已保存 URI 与剩余缓存也全部被清理。转码失败回退原片时同样经过该链。
- **影响**：用户已完成的录像不可再次保存或重试。第 928 行先提示“已保留原片，可稍后重试”，但此时尚未成功持久化，后续失败会使提示与实际结果相反。
- **已核对反证**：`PhotoDetailViewModel.kt:84–159` 的重试只面向已成功写入 Room 且原 URI 存在的素材，无法恢复这一分支中从未建立或已回滚的条目。其自身“先保存新 URI → 更新索引 → 清旧 URI”的成功路径不能修复首次录制的源丢失。
- **最小修复**：将“物理文件复制”“数据库接管”“源文件清理”分开；只有可靠持久化及索引提交完成后才能删除最后一份原片。失败时保留可恢复素材与对应冻结快照，并让现有重试/再次保存入口能够找到它；“已保留原片”应在保留动作成功后发出。媒体存储方法不应自行提前消费调用者的唯一输入。
- **必要验证**：对写流失败和 Room 插入失败分别做定向故障注入，确认至少一份可播放原片与恢复所需工程/水印信息仍在；随后恢复存储/数据库，能再次保存且不会生成重复工程条目。成功链仍及时清理临时文件。

### AND-CM03 / P1：“脱敏遮盖”仍透出原像素

- **根因与位置**：[PhotoAnnotationViewModel.kt:477](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/annotation/PhotoAnnotationViewModel.kt:477) 的棋盘遮盖使用 `Color.argb(245,24,24,24)` 与 `Color.argb(230,72,72,72)`；[PhotoAnnotationScreen.kt:679](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/annotation/PhotoAnnotationScreen.kt:679) 预览使用相同半透明颜色。工具名称明确是“脱敏遮盖”。
- **具体触发**：遮盖笔划外缘或短笔划存在仅被一个采样块绘制一次的区域；正常叠加保留底图权重 `1−245/255` 或 `1−230/255`，即约 4% 或 10%。无需特殊文件或异常路径。
- **影响**：被遮盖内容的亮暗结构仍进入导出成品；提高局部对比度可能重新显露原有字形/图案。此结论是“没有完全遮盖”，不宣称 JPEG 中全部信息可无损恢复，也不把保留可编辑原图本身列为缺陷。
- **已核对反证**：重复采样会使部分内部区域更接近不透明，但不能保证每个覆盖像素都被多次叠加；连续笔划插值只解决几何连续性，未改变两种颜色的透明度。
- **最小修复**：预览和成品使用完全不透明的块遮盖，确保遮盖内部输出与底图像素无关；保持现有几何、撤销与配方保存行为，不增加整图副本。
- **必要验证**：在黑白细字/棋盘底图上作短、长与对角笔划，检查单次覆盖块内部的成品像素不再随底图变化，并确认预览和成品范围一致。无需开展无关安全扫描。

### AND-CM04 / P2：预览失败后保存了用户没看见的编辑

- **根因与位置**：[PhotoAnnotationViewModel.kt:102](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/annotation/PhotoAnnotationViewModel.kt:102) 先将现有绘画合入 `steps`、追加变换、清空 `elements`，再生成预览。第 121–123 行失败仅发出消息并解除 `isSaving`，未回滚配方/元素/历史，也未将当前预览标为无效。第 227 行保存直接使用已经改变的 `steps`。
- **具体触发**：已有可见预览和标注，执行旋转或裁剪；本次内容提供者读图短暂失败，或预览因内存不足失败。界面仍显示先前底图，却重新允许保存。资源恢复后点击保存，会执行新配方；先前元素还可能因被清空而暂时不再显示。
- **影响**：失败后界面与待保存操作状态不一致，用户会得到未在当前预览中确认的旋转/裁剪/绘画结果，后续在旧坐标上继续绘制还会扩大偏差。
- **已核对反证**：`previewGeneration` 可防止旧成功结果覆盖新预览，但没有撤销失败操作；禁止忙碌期间操作只覆盖生成过程，不能覆盖失败后重新开放的保存。原图永久丢失会让保存也失败，但暂时读图/内存失败恢复后仍可到达上述路径。
- **最小修复**：编辑变更与成功预览作为同一个可提交状态；生成失败还原操作前的配方、元素与历史，或保留明确的未成功预览状态并先重试成功再开放保存。不能仅关闭错误消息。
- **必要验证**：已有标注时让下一次预览读取失败、之后恢复，核对画面、撤销栈与最终成品一致；另覆盖横竖屏尺寸变化触发重绘失败的同一状态处理。

### AND-CM05 / P2：标注图遗漏拍摄海拔 EXIF

- **根因与位置**：[PhotoAnnotationViewModel.kt:262](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/annotation/PhotoAnnotationViewModel.kt:262) 创建用于新 JPEG 元数据的 `WatermarkData` 时传了原始时间、经纬度、地址，却未传 `media.altitude`。默认海拔为 null；[ExifPreserver.kt:70](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/media/ExifPreserver.kt:70) 只在有值时写 `GPSAltitude`。
- **具体触发与影响**：拍摄时已有有效海拔的照片进入标注保存；新成品 JPEG 不继承原 EXIF，而由该对象重建，所以失去原拍摄海拔，尽管工程媒体实体仍保留海拔。原图未被破坏，但标注成品的元数据不完整。
- **最小修复**：重建标注成品元数据时同时传递已冻结的 `media.altitude`；不得使用编辑时的新定位替换拍摄值。保持现有已转正像素对应的正常方向标签。
- **必要验证**：对正、零、负海拔各保存一张标注成品，读取 EXIF 并确认与原拍摄值一致；原海拔 null 时仍不写虚假的零值。

### AND-CM06 / P2：过期 GPS 在预览与成品中不同

- **根因与位置**：[CameraViewModel.kt:234](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/feature/camera/CameraViewModel.kt:234) 已根据当前时钟将过期定位标为 `UNAVAILABLE`，但第 250–252 行仍将旧坐标交给预览。`WatermarkData.kt:72–76` 的 GPS 格式化只检查非空，未检查新鲜度。拍照第 1000–1007 行及录像快照则调用 `freshLocation()`，第 180 行会清空过期坐标。
- **具体触发与影响**：打开 GPS 字段后，超过 `LocationFreshness.MAX_AGE_MS`（五分钟）没有新定位，且定位对象仍保留在状态中；相机预览继续显示旧 GPS，立即拍摄却移除这一行。海拔已有新鲜度检查，不能据此推断 GPS 同样受控。
- **最小修复**：预览和捕获快照统一使用同一份位置有效性规则；无有效定位时不要把旧经纬度当成当前自动值。保留手填地址、已有字段开关和明确的用户文本覆盖语义。
- **必要验证**：固定一个有效位置，分别在过期边界前后比较预览布局与拍摄冻结数据；返回新定位后两者同时恢复。此条已与定位审查分工沟通，主表只计一次。

### AND-CM07 / P2：重试烧录没有冻结拍摄时区

- **根因与位置**：[WatermarkData.kt:39](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/watermark/model/WatermarkData.kt:39) 的日期/时间每次通过默认时区格式化；[WatermarkSnapshotCodec.kt:17](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/watermark/model/WatermarkSnapshotCodec.kt:17) 保存 epoch 时间但没有时区或当时渲染文本。`PhotoDetailViewModel.kt:109–119` 解码后在当前环境重新烧录。
- **具体触发与影响**：原录像因水印转码失败而保留，之后系统时区改变，再重试同一录像；虽然 epoch 不变，水印显示的时分秒甚至日期会改变，与拍摄当时预览的现场时间文本不同。已设置日期时间自定义文字的字段不受此动态格式化问题影响。
- **最小修复**：新拍摄快照冻结格式化所需的拍摄时区或最终日期时间文本，重试使用该快照；旧快照缺字段时保留现有兼容策略，不把未知历史时区随意补成某个地区。标注 EXIF 时间同样依赖默认时区，统一修复时应对照冻结值处理。
- **必要验证**：同一快照在两个系统时区烧录，确认日期时间文本相同；同时验证旧快照仍可读、自定义日期时间覆盖不被改写。

## 已排除的误报与必须保留的保护

- **重复项去重**：硬件对焦坐标工厂仍引用 AND-006；字段整实体异步写回仍引用 AND-008。模板外观的 FIFO 和字段写回不是同一路径。
- **工程归属**：`CaptureOperationCoordinator` 从开始捕获直到持久化完成持续预约工程，全局切换与工程锁定受同一协调器保护；照片/视频已冻结工程、位置、水印、问题模式和相册保存目标，不能仅因异步调用就断言会串工程。
- **录制状态机**：现有 token 过滤迟到事件，STARTING 期间停止请求在录制对象返回与 Start 回调两处兑现，Finalize 前不允许下一次开始。这些保护应保留，CM01 针对的是错误分类与素材处理。
- **硬件降级**：镜头绑定尝试公共镜头、默认组合与照片降级；缩放比例包含独立长焦相对于主摄的换算。能力可见和实际可绑定已分开，模拟器无前摄不证明真机切换失败。
- **资源与方向**：照片 ImageProxy 在 finally 关闭；转码元数据提取器释放，成功/失败转码有输出清理，配置的贴图会释放。没有把“某处未手动 recycle”直接记成常驻内存泄漏；也未把可能的 Transformer 跨线程取消写成已存在可达缺陷。
- **ViewPort 怀疑未列问题**：初始化可能复用已有绑定，但普通竖屏 3:4 与横屏 4:3 的转置配合旋转可对应同一传感器区域；现有证据不足以证明仅旋转会产生双重错误裁剪，故不新增此项。具体镜头与编码方向仍在设备验证范围内。
- **水印**：共享布局与 Canvas painter 保持八样式、字段身份、标签与顺序；长文本通过实际字体度量、换行与缩放保留，不因某些小画布字体变小就断言字段丢失。日期时间文本冻结问题不等于 epoch 时间戳丢失。
- **标注成品提交**：已有“先新文件 → Room 替换指针 → 旧成品清理”，第 289 行明确转移文件所有权，后续清理异常不会回滚成已删除指针。原图不可读时明确报错；不会把旧已标注成品冒充原图重复叠水印。CM02 不套用于这一已有正确归属处理的提交链。

## 已读现有测试及其证明边界

本分报告全文阅读以下现有测试，不自行启动测试进程。主审本轮执行结果须单独引用，不能从文件存在推断已通过。

| 测试文件 | 当前测试覆盖 | 不能据此证明 |
| --- | --- | --- |
| `CameraCapabilityTest`、`CameraRotationTest`、`CaptureOperationCoordinatorTest`、`OrientationStabilizerTest`、`WindowOrientationTest` | 能力映射、镜头排序、工程预约、合法方向与稳定阈值 | 真实多摄绑定、光学对焦、Home/锁屏后的 Finalize 文件处置 |
| `PhotoQualityProfileTest`、`PhotoQualitySaveTest`、`VideoOrientationTest` | 质量档与尺寸、实际 JPEG 保存参数/成品差异、视频显示尺寸/旋转数学 | 真机视频编码、声音、Media3 贴图进入编码流的最终方向、磁盘与数据库失败恢复 |
| `WatermarkFieldResolverTest`、`WatermarkFieldPresentationTest`、`WatermarkSnapshotCodecTest`、`WatermarkLayoutEngineTest` | 字段开关/覆盖、标签/顺序、旧快照兼容、海拔、长文本与布局边界 | 定位过期后 CameraViewModel 的预览/捕获一致性、跨时区重试、全部真实媒体输出 |
| `UnifiedWatermarkRenderingTest` | 原生位图绘制、八样式、预览/照片/显示方向视频 overlay 的像素一致性 | `orientOverlayForInput` 与真实 Media3 解码/编码管线、音轨保留和取消 |
| `PhotoAnnotationViewModelIntegrationTest`、`EditRecipeTest`、`ImageContentRectTest`、`TextNoteGeometryTest` | 内容 URI 读图→翻转/旋转→保存→重开、原图保持、配方序列化顺序、基础裁剪与坐标、文字边界 | 半透明遮盖的脱敏结果、预览读图失败后恢复、标注 EXIF 海拔与时区、所有工具组合 |

测试根目录：[app/src/test/java/com/sitecam/app](/Users/ninja/Documents/工程水印相机/app/src/test/java/com/sitecam/app)。现有集成测试是有价值的基础；新增缺陷需要在修复时补针对结果的验证，不增加简单镜像实现的测试。

## 修复次序与尚未覆盖的硬件证据

1. **先保护已完成素材**：CM01、CM02 联合调整录制完成分类与文件所有权；分别覆盖有帧中断、无有效数据、保存失败、数据库失败。
2. **修复遮盖结果**：CM03 修改两端 Android 绘制入口，验证单次覆盖区域与最终 JPEG。
3. **保证所见所得和元数据**：CM04、CM06 统一成功状态与数据有效性；CM05、CM07 补齐冻结拍摄信息。保持已有八样式、原始媒体和编辑配方语义。

本轮源码不能替代以下硬件证据：真实前后摄与独立长焦/广角、闪光照明、点按 AF 的清晰度、录音声道与同步、真实编解码器 90°/270° 输出、长录像与压力下中断、不同系统版本的公开相册持久化。模拟器只补可明确验证的生命周期路径，逐台运行，关闭不需要的实例；不因“全面”而并发启动更多模拟器。
