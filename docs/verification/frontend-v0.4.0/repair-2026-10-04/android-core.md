# Android 拍摄、媒体与标注修复记录

日期：2026-10-04。依据 [Android 相机媒体审查](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/full-audit-2026-10-03/android-camera-media.md) 与 [修复计划](/Users/ninja/Documents/工程水印相机/docs/verification/frontend-v0.4.0/full-audit-2026-10-03/REPAIR_PLAN.md)。本分工完成以下源码与测试代码；构建、测试执行和单台模拟器验证由主审统一安排，本记录尚不标记通过。

| 问题 | 实施内容 | 主要文件 |
| --- | --- | --- |
| AND-CM01 | 根据 Finalize 错误种类判断是否可能保有有效帧；正常完成、源停止、时长/文件大小限制、录制中空间不足进入素材检查；编码失败/无有效数据等不会伪装成功。新增后台停止入口，保留原录制 token 与 STARTING 停止保护 | `CameraViewModel.kt`、`core/camera/RecordingFinalization.kt`、`MediaStoreManager.kt`；页面 ON_STOP 接入由主审负责 |
| AND-CM02 | 录制前持久保存冻结信息，原片直接录入应用私有目录；完成后先建立现有非 READY 媒体条目，再烧录/保存成品并更新同一媒体 ID。首次数据库写失败可在相机恢复时定向重放；复制不再提前删除调用者输入，最终索引接管后才清理原片 | `core/media/PendingVideoStore.kt`、`CameraViewModel.kt`、`MediaStoreManager.kt`；DAO 定向 URI 查询由数据分工提供 |
| AND-CM03 | 成品棋盘块使用完全不透明的两种既有颜色 | `PhotoAnnotationViewModel.kt`；预览颜色由主审同步修改 `PhotoAnnotationScreen.kt` |
| AND-CM04 | 变换、撤销/重做、清空及尺寸变换所需配方与历史在预览成功后共同提交；失败不改已显示图对应的操作。窗口变化期间排队处理；窗口预览失败后的下一次编辑/保存先重试预览，成功后才接受后续操作 | `PhotoAnnotationViewModel.kt` |
| AND-CM05 | 标注成品 EXIF 传递原媒体的正/零/负/缺省海拔与已冻结拍摄时区，保留正常方向标签 | `PhotoAnnotationViewModel.kt`、`core/media/ExifPreserver.kt` |
| AND-CM06 | 相机预览与拍摄统一使用现有位置新鲜度策略；过期的自动经纬度不再进入预览，手工地址和字段覆盖保持原语义 | `CameraViewModel.kt` |
| AND-CM07 | 新照片/录像快照冻结拍摄时区；视频额外保留原系统相册目标，私有原片重试后仍保存到原目标。快照升级 v3，海拔启用边界仍固定为 v2，旧快照缺时区继续按旧规则处理 | `WatermarkData.kt`、`WatermarkSnapshotCodec.kt`、`CameraViewModel.kt`、`PhotoDetailViewModel.kt` |
| AND-006 / B5 | 使用实际 PreviewView 的 meteringPointFactory；仅在 STREAMING、有效局部坐标及支持测光时启动，观察 CameraX 异步 AF 结果，忽略旧镜头/旧请求回调 | `core/camera/CameraManager.kt`；焦圈与局部坐标由主审连接 |
| AND-008 / B4 衔接 | 相机快捷内容/开关调用应用级 FIFO 的列级 fieldValue/fieldEnabled，保留即时预览，不再写回捕获的整行实体 | `CameraViewModel.kt`；DAO、队列和编辑页由数据分工负责 |

源码根目录：[app/src/main/java/com/sitecam/app](/Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app)。

## 原片接管与恢复边界

1. 录制开始前在 `filesDir/SiteCam/pending-video` 创建此次捕获的小清单，包含冻结工程、时间、位置、水印、目标相册与问题模式。MP4 直接录入对应目录，没有为每次录像额外复制一份原片。
2. Finalize 允许保留的错误类型还要经过尺寸及可解码画面检查。确认后先把原片作为 `FAILED_VIDEO_WATERMARK_RETRY` 条目交给 Room，既有工程相册与详情“重试视频水印”可找到它。
3. Room 接管原片后删除重放清单，再允许同一条目换成最终 URI；这样重启不会因旧清单重新创建第二条记录。发布按 URI 查找已有条目，处理插入已完成但清单尚未清理的中断窗口。
4. 转码、公开/私有成品复制或最终数据库更新失败时，原条目和原片仍有效。首次 Room 插入失败时，源和清单都保留；相机初始化/ON_RESUME 仅重放这一目录的清单，不扫描无关资料。恢复后进入同一个详情重试入口。
5. 文件复制方法保留输入所有权；最终数据库事务提交后清理原片。媒体锁退出后再结束拍摄预约，遵守工程操作的 capture→media 顺序，避免反向等待。

本流程没有把任意损坏文件标成成功录像。若素材无法读取，保留待恢复信息而不创建虚假可播放条目；真实编码错误/无有效数据的 Finalize 分支清理无效录制。原片可播放性仍须由实际录制/播放验证支持。

## 接口交接

- `CameraManager.focusOnPoint(x, y, onResult): Boolean`：坐标是当前 PreviewView 本地像素；返回是否接受请求，异步主线程回调报告 AF 成功/失败。页面负责焦圈位置、显示状态和布局变化代次。
- `CameraViewModel.stopRecordingForBackground()`：页面 ON_STOP 调用，Finalize 分支仍是处理最终文件的唯一入口。
- `CameraViewModel.recoverPendingVideos()`：初始化已调用，页面 ON_RESUME 再调用；运行中恢复任务去重，排除活动录制源。
- `WatermarkTemplateMutations.fieldValue(id,value)` / `fieldEnabled(id,enabled)` 与 `MediaItemDao.getMediaItemByContentUri(uri)`：已与数据分工对齐并接入。

## 有意义的回归测试代码

| 测试 | 新增/扩展的结果断言 | 当前状态 |
| --- | --- | --- |
| `PendingVideoStoreTest.failedDatabaseInsertSurvivesReopenAndPublishesOneVisibleRetryTarget` | 注入首次 SQLite 写入失败，重开恢复清单后只入库一次，冻结归属/快照保持，原文件字节仍在且条目可见。文件字节只用于所有权验证，不冒充真实编码/可播放测试 | 已写，待主审执行 |
| `PhotoAnnotationViewModelIntegrationTest.opensRealContentUriTransformsSavesAndReopensWithoutChangingOriginal` | 扩展真实 Content URI 读图失败：失败旋转不提交、不清已有标注、不污染撤销；最终 JPEG EXIF 保留负海拔和冻结时区 | 已写，待主审执行 |
| `PhotoAnnotationViewModelIntegrationTest.onePassMosaicProducesTheSameOpaqueBlocksOverBlackAndWhiteSourcePixels` | 通过实际 ViewModel 生成 JPEG，单次遮盖的同色块在黑底/白底上得到相同灰度；覆盖此前半透明块保留底图对比度的回归 | 已写，待主审执行 |
| `WatermarkSnapshotCodecTest.frozenCaptureZoneAndSaveTargetSurviveRetryInAnotherSystemZone` | 切换系统时区后新快照日期时间不变、目标相册仍在；旧 v2 快照仍保留海拔开关、缺省时区/目标不被猜测，自定义日期时间仍有效 | 已写，待主审执行 |

测试目录：[app/src/test/java/com/sitecam/app](/Users/ninja/Documents/工程水印相机/app/src/test/java/com/sitecam/app)。未自行执行构建或测试，也未启动模拟器。

## 仍需主审验证

先运行本次相关测试与必要编译；通过后用单台设备核对正常停止、Home/锁屏、有帧/无有效帧，以及恢复后的可播放片段、同一媒体 ID、水印与保存目标。还需检查真实保存写流失败、最终索引更新失败、四角 AF 与旋转后焦圈、窗口变化期间编辑失败重试。已有审查期的中断删片复现只能作为修复前证据。

真实前后/多摄、音轨、各硬件编解码器方向与效果尚未由本分工验证。若主审补充运行结果，应保持测试代码存在、测试执行通过、设备实际通过三个层次分别记录。
