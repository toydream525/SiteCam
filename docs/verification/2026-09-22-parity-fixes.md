# 0.3.2 双端规范修复记录（2026-09-22）

本轮同时修鸿蒙版与安卓版。鸿蒙侧的完整记录在
[`harmonyos/verification/REVIEW-0.3.2-权限与海拔.md`](../../harmonyos/verification/REVIEW-0.3.2-权限与海拔.md)，
本文件记录**安卓侧的对等修复**及其验证证据。版本号两端均保持 `0.3.2 (15)`。

## 判定依据

先对安卓版做了一次只读审计，逐条对照鸿蒙已确认的缺陷类别。结论：鸿蒙那条"横屏左侧 #121212 色带"在安卓上**不会出现**（根容器 `background` 写在 `windowInsetsPadding` 之前，纯黑覆盖含 inset 的整窗，且 CameraX `PreviewView` 默认背景也是纯黑）；但其余类别存在等价问题，其中**权限误判**属于同类上架风险。

## 已修项

| # | 问题 | 位置 | 修复 |
| --- | --- | --- | --- |
| 1（P0，上架风险） | `markPermissionsRequested` 在系统弹窗**之前**落盘。若弹窗未真正呈现（进程被杀、`launch` 抛异常），下次冷启动「有标记 + `shouldShowRequestPermissionRationale == false`」被误判为永久拒绝，直接跳系统设置、按钮变成「去系统设置开启」——与华为驳回鸿蒙版的表现同源 | `PermissionGuideScreen.kt`、`CameraScreen.kt`、`PermissionAccess.kt` | 标记改到 launcher 结果回调内、只写本次实际发起的权限；新增会话级「先重试一次系统弹窗，二次才跳设置」的兜底与对应文案「重新申请所需权限」；`PermissionAccess.blockedPermissions()` 暴露被判定为 blocked 的权限；`launch` 异常时回滚本地重试标记。对象级文档写明 Android 11+ 闲置权限自动重置、设备策略受限等同样返回 `rationale == false`，故必须"先重试再跳设置" |
| 2（P0） | 倍率组固定高（32/36/38/44dp）+ `overflow = TextOverflow.Clip`，大字号裁字；且 `fontScale >= 1.25` 时反而把盒子改小（与仓库既定设计相反） | `ZoomPillGroup.kt`、`CameraBottomBar.kt` | 去掉固定高度，改 `widthIn(min)` + `heightIn(min = 48.dp)`，`Clip` → `Ellipsis`，倍率组加横向滚动并让当前项始终可见；`fontScale` 只再影响间距，尺寸跟窗口而非字号走；`zoomHeight` 按 `maxOf(48f, lineHeight * fontScale)` 计算 |
| 3（P1） | 工程列表 10+ 处固定高/固定宽容器裁字（含 `width(82.dp)` 装四字中文，大字号横向也裁） | `ProjectListScreen.kt` | 固定 `height(...)` → `heightIn(min = 48.dp)`（Material 最小触控目标）；`width(82.dp)` → `widthIn(min = 82.dp)` 并移除子按钮的 `fillMaxWidth()`（否则父约束仍是整行宽会把左列挤成 0 宽）；文字统一 `maxLines` + `Ellipsis` |
| 4（P1） | 相机顶栏工程 chip 与外屏工程选择器固定高裁字 | `CameraTopBar.kt`、`CameraScreen.kt` | 顶栏宽度上限随 `fontScale` 放大（`coerceIn(1f, 2f)`）、高度改 `heightIn(min = 48.dp)`；外屏选择器去掉 `max = 58.dp` 上限并补 `Ellipsis` |
| 5（P2） | letterbox 与页面底色硬编码混用；**同一路由内**未授权态整页 `#121212`、授权后变纯黑，返回时整屏跳色 | `Color.kt`、`CameraScreen.kt`、`CameraTopBar.kt`、`CameraBottomBar.kt` | 新增语义常量 `Letterbox = Color(0xFF000000)`，替换 18 处相机相关底色（含三处遮罩与未授权整页）；在 `background(...)` 处加注释锁定"必须写在 `windowInsetsPadding` 之前"这一顺序 |
| 6（P2） | 宽屏分栏时两个 `contentDescription = "返回"` 同屏共存但动作不同；相册按钮/视频缩略图一个控件产出多个语义节点 | `GalleryScreen.kt`、`PhotoDetailScreen.kt`、`CameraBottomBar.kt`、`VideoThumbnail.kt` | 改为「退出相册」「关闭预览」；相册按钮 `semantics(mergeDescendants = true)`；装饰性角标描述置 `null` |
| 7（P2） | 死代码 | `ProjectCreateDialog.kt`、`QuickIssueDialog.kt` | 删除零调用 Composable（182 行）与包装函数；四个"仅被单测引用"的生产函数保留并加文件头说明，避免破坏既有测试 |

## 验证证据

| 项 | 结果 |
| --- | --- |
| 编译 | `:app:compileDebugKotlin` / `:app:assembleDebug` / `:app:assembleRelease` 全部 `BUILD SUCCESSFUL` |
| 单元测试 | `:app:testDebugUnitTest` **138 项，0 失败，0 跳过** |
| 独立 grep 复核 | `TextOverflow.Clip` 在 `main` 中为 0；`ZoomPillGroup` 无固定 `.height(`；`Letterbox` 18 处引用；`ProjectCreateDialog` 0 残留；`markPermissionsRequested` 仅出现在 launcher 结果回调内 |
| Release 签名 | `apksigner verify`：`CN=SiteCam, OU=Personal App, O=SiteCam`，证书 SHA-256 `4d616e6bf51185bf5176fb6121b266af05a95f480a25f0d7f4097c15c8fcf7c` |
| 产物 | 桌面 `SiteCam-0.3.2-Android-Release.apk`（18,057,858 B）与 `SiteCam-0.3.2-Android-Debug.apk`（26,345,133 B） |

构建期间修掉两处引入的编译错误：`"开启$name权限"`（Kotlin 把 CJK 并入标识符，应为 `"开启${name}权限"`）与 `target.coerceIn(0, maxValue)`（Float 接收者配 Int 边界，重载不匹配，改 `target.toInt()`）。

## 未在真机验证（如实说明）

- fontScale 1.3 / 2.0 / 3.2 下倍率组横向滚动与"当前项可见"依赖 `onGloballyPositioned` 实测坐标，只有编译与单测保证，未上设备。
- 权限三条链路（首次弹窗 / 二次不再弹窗 / Android 11+ 主动重置权限后再授权）需要设备或模拟器手动走查。
- `HandledPermissionGuidePlatformTest`（androidTest）需模拟器且相机权限已被拒绝，本轮未执行（本轮执行的是 JVM 单测）。
- 相机页 1 Hz 时钟本轮**只做了功耗收敛**（鸿蒙：进入后台即 clearInterval；安卓：未改，`CameraViewModel` 的 1 秒循环仍无条件运行）。真正的"只让水印时间文本重组"需要把时钟与快照从页面级状态里拆出去（鸿蒙侧 `watermarkBound()` 命中区域依赖父组件持有的快照，拆分要先解开这层耦合），风险高于收益，故留作后续优化项。
- 有意未做（不在本轮清单、避免顺手重构）：`CameraTopBar.ProjectNameTool` 的宽度/高度上限、`PermissionCard` 的文案随可重试状态变化、全仓 `collectAsState()` → `collectAsStateWithLifecycle()`、横屏倍率竖栏滚动。
