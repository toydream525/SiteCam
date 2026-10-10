# SiteCam 后端全面审查与修复报告（2026-10-10）

范围：Android 端（`app/src/main/java/com/sitecam/app/`）与鸿蒙端（`harmonyos/entry/src/main/ets/`、`entry/src/main/cpp/`）的**非 UI 层**。
前端 Compose / ArkUI 布局与视觉不在本次范围（由前端负责人处理）。本文只记录结论、已落地修复与未修项。

## 一、方法

- 8 路只读深度审查并行执行：Android（相机层 / 定位与偏好 / 媒体与导出 / 数据库与水印 / 业务逻辑 VM）+ 鸿蒙（相机与定位 / 存储与媒体 / 原生转码）。
- 另有跨模块卫生自查：`GlobalScope`、`runBlocking`、`Thread(`、阻塞式 `get()/join()/await()`、宽泛 `catch` 与取消传播。
- 审查结论逐条回到代码复核；无法证实或属既有设计取舍的条目已在第四节标注为驳回。
- 修复后统一验证（见第六节）。

## 二、验证结果

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| Android 编译 | `./gradlew :app:assembleDebug` | 通过 |
| Android 单元测试 | `./gradlew :app:testDebugUnitTest --no-build-cache` | **179/179 通过** |
| 鸿蒙合并构建 | `hvigorw -p module=entry@default -p buildMode=debug assembleHap` | BUILD SUCCESSFUL（含 `BuildNativeWithCmake`/`BuildNativeWithNinja`/`CompileArkTS`/`PackageHap`） |

未做：真机/模拟器实跑（相机、定位、转码、导出）。所有修复均只到编译与单测/构建级别。

> 注意：本次踩到一个 Gradle 构建缓存陷阱——主源码在构建过程中被另一进程修改后，`compileDebugUnitTestKotlin` 曾从缓存还原**旧的**测试字节码，导致 `PendingVideoStoreTest` 持续报 `NoSuchMethodError`。验证时请加 `--no-build-cache`（必要时先删除 `app/build/tmp/kotlin-classes/debugUnitTest`）。

## 三、已落地修复

### 3.1 Android · 定位（本次两个原始缺陷之一）

| # | 缺陷 | 修复 |
| --- | --- | --- |
| L1 | 实时回调把过期/未来时间戳改写成 `now`，过期坐标冒充实时定位并被刻入水印与 EXIF（架空 `LocationFreshness` 与 `locationStatus`） | 新增 `LocationFreshness.evidenceTimestamp()`：保留平台测量时间，仅当时间为 0/负时回落到接收时刻；`updateLocation` 统一走它 |
| L2 | 手动「刷新地址」后 fused 流被永久注销且不再重建，整段会话只剩 GPS/网络/被动三源并发 | 抽出 `startFusedUpdates(generation)`，`refreshLocation()` 重新注册 fused + 平台 provider |
| L3 | 低精度网络点覆盖高精度 GPS 点，坐标与地址来回抖动 | 新增 `LocationAcceptance`：坐标有限性/范围/0,0 哨兵校验 + 精度替换规则（明显更粗的点只能在旧点已过期时接管） |
| L4 | 陈旧 last-known 因 `_currentLocation.value == null` 守卫屏蔽更优候选 | 去掉该守卫，改由统一接受门槛裁决 |
| L5 | DataStore 损坏会让相机页 `combine(21 条流)` 整体失败 | `preferencesDataStore` 加 `ReplaceFileCorruptionHandler` |
| L6 | Geocoder 缓存无界无 TTL；线程池 `AbortPolicy` 会直接拒绝请求；Binder 调用可永久挂起 | 缓存改 64 条 LRU + 10 分钟 TTL；6 秒超时；有界队列 + `CallerRunsPolicy` |
| L7 | `accuracy` 为 0/负数仍被保留 | 仅接受 `> 0` |

测试：`LocationFreshnessTest` 扩展 + 新增 `LocationAcceptanceTest`。

### 3.2 Android · 相机

| # | 缺陷 | 修复 |
| --- | --- | --- |
| C1 | 显示倍率上下界可能反向（只给 max 加 30 上限），`coerceIn(min,max)` 在 min>max 时抛 `IllegalArgumentException` → 捏合/点档位崩溃 | `capabilityFromZoomState` 先收敛出合法区间；`setZoomRatio`/`applySessionZoom` 入口再做一次有限性与序关系清洗 |
| C2 | TORCH 未同步 `ImageCapture.flashMode`，常亮火炬与 AE 闪光互斥，部分 HAL 直接拒绝拍照 | `setFlashMode`/`setTorchEnabled`/`restoreFlashMode` 三处无条件同步 |
| C3 | 拍照协程被取消时已解码的全分辨率 Bitmap 无引用泄漏 | `continuation` 非活动时 `bitmap.recycle()` |
| C4 | 全分辨率解码 `OutOfMemoryError` 绕过 `catch(Exception)`，协程永不 resume，快门永久卡死 | `capturePhoto` 改 `catch (Throwable)` 并把异常 settle 给调用方 |
| C5 | IO 线程读 `imageCapture`/写 `targetRotation`，与主线程重绑构成 TOCTOU 与数据竞争 | `capturePhoto` 的取用例、设旋转、`takePicture` 收进 `withContext(Dispatchers.Main)`；`camera/imageCapture/videoCapture/activeRecording/activeRecordingToken` 加 `@Volatile` |
| C6 | 异步会话失败恢复只覆盖「逻辑相机的物理成员」，独立超广/长焦机型无恢复；恢复时丢 `requireVideo`；在 `cameraState` 派发栈内重入重绑；无可回退时静默 | 观察所有公开会话；恢复绑定带 `requireVideo`；恢复动作经主线程 executor 延后；用失败集合约束回退目标避免两镜头互相回退；无可回退时报错 |
| C7 | 应用级单例永久持有 `PreviewView` → Activity 视图树随进程泄漏 | `unbindCamera()` 清 `previewViewRef` |
| C8 | 设备诊断单台相机读取失败即中断整份报告 | 逐台 `runCatching`，失败记一行继续 |
| C9 | 无工程时按快门静默丢弃；全分辨率 OOM 会把进程带崩 | VM 侧补用户可见提示；补 `OutOfMemoryError` 分支（仍走 finally 复位） |
| C10 | 相机快捷面板新增字段绕过 FIFO 队列、用 UI 缓存算 `displayOrder` → 重复 order、上下移失效 | 改走 `watermarkTemplateMutations.addField`（队列内读库取号），字段列表由 Flow 回流 |

### 3.3 Android · ViewModel 业务逻辑

- `capturePhoto` 的宽泛 catch 吞掉 `CancellationException`（失败提示永不出现、真实原因被覆盖）→ 取消重抛。
- 录像启动在 IO 线程二次读 `uiState`，可能「预留 A 工程、写 B 工程水印」→ 主线程取一次快照传参。
- `GalleryViewModel.operate` 忙时静默丢弃用户操作 → 补提示。
- `PhotoDetailViewModel.removeIssue/delete` 无异常处理（Room 异常直冲 `viewModelScope` 崩溃）→ 取消重抛 + 错误事件。
- `retryVideoWatermark` 吞取消、补偿删除自身可能抛错 → 修正。
- `finalizeVideoSession` 的 `NonCancellable` 清理可能顶掉真实错误 → 清理包 `runCatching`。
- `activeVideoSession` 跨线程读写 → `@Volatile`。
- 闪光灯切换非原子（快速连点丢档）→ 本地 `StateFlow` 原子推进 + 版本守卫，再异步落库。
- 标注视图渲染失败后永久锁死编辑/保存 → 同尺寸最多自动重试 1 次，失败后放行并提示一次。

### 3.4 Android · 媒体与导出

- 视频转码的文件探测/删除/元数据解析在 `Dispatchers.Main.immediate` 上执行（ANR）→ 移入 IO，仅 `Transformer` 构建与启动留在主线程。
- 导出重压照片时 EXIF 拷贝失败会**删掉已写好的照片**并计为缺失 → EXIF 段独立 `runCatching`，只记一条 failure；尺寸先于 EXIF 返回。
- 待发布录像在工程被删后永久卡死（外键失败 → 既不入库也不清理、每次进相机重试）→ 识别外键失败后走 `discard` 清理；另加可选 `projectExists` 前置校验接线点。
- `IS_PENDING` 清零结果从不校验却上报成功 → 照片/视频/导出 ZIP 三处改 `check(update(...) > 0)`，失败走既有失败清理。
- EXIF 写入失败完全静默 → 补 `Log.w`（保持「失败不影响保存成功」的既有策略）。
- API 26 录像首帧校验解码全尺寸位图（4K 约 33MB）→ API < 27 跳过帧探测，仅用宽高/时长校验。

### 3.5 Android · 数据库与水印

- 「不透明度」只作用于卡片底色，文字与强调条仍 100% 不透明 → alpha 收敛到 painter 单点施加，预览/成品/视频叠加三条路径共用同一布局结果。
- 自适应缩放无下限，极端模板下字号趋近 0、水印静默消失；表格标签退化为逐字竖排 → 加 `MIN_SCALE = 0.4f` 与列宽下限（正常数据走 early return，逐位不变）。
- 三个父表 `@Insert(REPLACE)` 与 `ON DELETE CASCADE` 组合：同主键重插会静默清空子表 → 改 `ABORT`（已审计全部 10 个调用点不依赖 REPLACE）。
- 水印设置写入队列的消费者会因非 `Exception` 的 `Throwable` 永久停摆，失败对用户不可见 → `catch (Throwable)` 且循环不死。
- 快照 `fontSizeScale/opacity` 非有限值：`decode` 接受而 `encode` 抛异常（拍摄整段失败、MediaStore 留孤儿）→ 两侧对称兜底；`marginDp` 收敛。
- 快照 `version` 不校验上界、解析失败静默 → 版本过新判定不可用并 `Log.w`；解析失败也记日志。
- GPS 行缺 0/0 与 NaN 防护（存量数据会输出 `0.000000, 0.000000` / `NaN, NaN`）→ 复用 `LocationAcceptance.isValidFix`。
- 相册批量移动超过 999 个 id 会触发 SQLite 变量上限 → 事务内按 500 分批。

### 3.6 鸿蒙 · 原生转码（`video.cpp` + `VideoService.ets`）

NAPI 创建/入队返回值未校验（Job 泄漏、Promise 永不落定、`busy` 永久 true）；`busy` 在 Promise 结算之后才复位；`j->error` 无锁读写；`glInit` 半途失败与 `cleanup` 时 GL 句柄不释放/不复位；音轨缓冲区未走 RAII 且 `GetBufferAttr` 未校验；输入输出同路径会被 `O_TRUNC` 清空再 unlink；水印缓冲区尺寸校验过严（行跨距填充时全部转码直接失败）；`stringArg` 不校验类型；`stride/slice` 被 0 覆盖。以上全部修复，并用真实构建确认 C++ 与 ArkTS 两侧改动都进了产物。

### 3.7 鸿蒙 · 存储与媒体（core/）

EXIF 失败会把刚拍好的照片连文件一起删掉；工程名过长时 `path()` 同步死循环（快门永久禁用）且丢扩展名；任一处坏 JSON 或启动步骤失败会让应用永久停在「打开失败」；RDB 事务体内 await 过久导致其它写「搭车」进事务被连带回滚；每次启动全量回填快照；崩溃/被杀留下的无主中间产物永不清理；`ImageSource` 建在 try 之外且 release 未嵌套；拍照显式关闭 taskpool 零拷贝导致整图多复制一次；删除流程全量加载 + 同步 IO 探测。以上全部修复（无主文件清理挂在既有启动恢复入口，无需改 pages）。

### 3.8 鸿蒙 · 相机与定位（core/）

镜头切换失败一次即永久标记该镜头不可用（设备事实与会话状态混用）；相机被占用/断开只上报而不失效会话状态；`syncZoom()` 绕过读回代次导致 UI 回跳；捏合缩放按手势帧率写诊断文件；持续定位订阅失败被静默吞掉且无重试入口；每条定位回调都重建整份快照（无节流）；连续逆地理编码失败完全静默。以上全部修复。

## 四、驳回的审查结论（附理由）

| 结论 | 理由 |
| --- | --- |
| [P0] 录像中变焦会 `unbindAll` 重建会话、静默截断录像 | 不成立。`CameraManager.setZoomRatio` 的重绑分支整体被 `_videoRecordingState.value == VideoRecordingState.IDLE` 包裹；录像中只走 `applySessionZoom`。 |
| [P0] `onCleared` 的匿名协程释放失败会让工程永久锁定 | 被夸大。`CaptureOperationCoordinator.active` 是纯内存集合，进程结束即清空；`finish` 内部已是 `NonCancellable`。不引入 `runBlocking` 阻塞主线程。 |
| [P1] 手动刷新地址复用 ≤5 分钟的旧定位点是缺陷 | 属刻意设计（代码注释明确：先把已有新鲜点逆地理编码，避免室内再等一次取点）。属产品取舍，本次未改。 |
| [P2] 水印快照 `@Insert(REPLACE)` 用于子表也危险 | `issues`/`annotations` 无子表，不触发级联；按要求未改。 |

## 五、未修复项与剩余风险

Android：
1. 每次绑定做两遍全量相机枚举（`cameraIdList` + 逐 ID `getCameraCharacteristics`，主线程 Binder）→ 建议缓存 `CameraCapabilityInspection` 或移到 IO。
2. 录像 `STARTING/FINALIZING` 无看门狗；HAL 不回调 `Finalize` 时状态可能停住、`unbindWhenIdle` 不清零。
3. 相同相机错误重复上报被 `StateFlow` 去重，第二次不弹提示（需 UI 改事件通道）。
4. `MediaAvailabilitySync` 监听整个 `content://media`、全表读取 + 逐行跨进程查询、全程持全局媒体锁。
5. 删除非本安装拥有的 MediaStore 条目缺少 `RecoverableSecurityException` / `createDeleteRequest` 授权路径（用户会「怎么也删不掉」）。
6. 工程删除不是事务：中途失败会留下「工程还在、部分媒体已永久删除」。
7. `media_items.filePath` 在 MediaStore 分支写相对路径、私有分支写绝对路径（语义不统一，需迁移）。
8. `media_items.contentUri` 无索引，每次录像发布全表扫描（需 schema 迁移）。
9. 快照「损坏」与「版本过新」对上层不可见（只能看 logcat）；要界面感知需改 API 签名。
10. `WatermarkBitmapRenderer` 对全尺寸帧连续复制多份 ARGB，峰值内存高（OOM 已兜底，未做采样/复用）。
11. 真机行为未验证（相机链路、定位出点、转码、导出）。

鸿蒙：
1. `pages/Index.ets` 仍需把 `location.ensureListening()` 接进 1 Hz 时钟，并接收 `onListenFailed`。
2. 「后台→前台」快速交错时后台收尾缺状态复检，会关掉刚建立的前台会话/定位/时钟。
3. 纯离线版只申请 `LOCATION` 而未与 `APPROXIMATELY_LOCATION` 同批申请，拿不到精确授权。
4. 相机被占用/断开后页面仍无自助重试入口。
5. 水印面板每次拖动/输入都写一次 DB + `preferences.flush`（需去抖）。
6. `settings` 整包 blob 的读-改-写非原子，并发入口会互相覆盖。
7. 大图库下 `refresh` 全量重建 + 每帧 O(n·m) 过滤。
8. 非事务的单条写仍可能与事务共用连接而「搭车」（当前实现的已知边界）。
9. `CameraService`/`ShutterSoundService`/`WindowEnvironment` 无自动化覆盖。

## 六、验证命令

```bash
# Android
cd 工程水印相机
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest --no-build-cache

# HarmonyOS（独立构建目录，避免污染仓库）
BUILD_DIR=/tmp/sitecam-hos-final
DEVECO=/Applications/DevEco-Studio.app/Contents
rsync -a --exclude 'build/' --exclude '.hvigor/' --exclude 'oh_modules/' \
  --exclude 'local.properties' --exclude 'dist/' --exclude 'verification/' harmonyos/ "$BUILD_DIR/"
printf 'sdk.dir=%s/sdk\n' "$DEVECO" > "$BUILD_DIR/local.properties"
cd "$BUILD_DIR" && ohpm install
"$DEVECO/tools/hvigor/bin/hvigorw" --mode module -p product=default \
  -p module=entry@default -p buildMode=debug assembleHap --no-daemon
```

## 七、前端改动交叉审查（2026-10-10）

### 7.1 前端本轮改了什么（按 mtime）

| 时间 | 文件 | 内容 |
| --- | --- | --- |
| 10:53–11:32 | `feature/camera/CameraScreen.kt`、`components/CameraBottomBar.kt`、`components/ZoomPillGroup.kt`、`components/CameraTopBar.kt`、`core/layout/*`、`feature/camera/AddressRefreshPlacement.kt` | 手机/平板横屏 34-v3 沉浸布局、右栏同轴、握持留白、倍率悬浮轨道 |
| 11:18–11:32 | `assets/design/frontend-v0.4.0/{DESIGN_CONTRACT,manifest,ADAPTIVE_COVERAGE,PROMPTS}.md`、`docs/verification/frontend-v0.4.0/**` | 设计契约与清单同步、模拟器截图与构建日志 |
| 11:30 | `harmonyos/entry/src/main/ets/{pages/Index.ets,core/CameraLayout.ets,core/CameraCapability.ets}`、`core/AddressRefreshPlacement.ets` | 鸿蒙侧同一套横屏位置关系 |
| 12:52 | `core/layout/CameraGeometry.kt`、`test/.../ScreenAdaptationTest.kt` | 握持空间判定条件修正 + 适配测试 |
| 12:59 | `FRONTEND_REVIEW_AND_FIXES.md` | 前端审阅与修复说明 |

### 7.2 结论：接口未被打断

- Android：前端 12:52 的几何改动与后端全部改动一起编译并通过 **181 个单测（0 失败）**。
- 鸿蒙：前端 11:30 的页面改动与后端 12:51–12:56 的 core 改动一起 `assembleHap` **BUILD SUCCESSFUL**（含 CMake/Ninja 与 ArkTS）。
- 前端 12:54:35 的签名 Release APK（`app/build/outputs/apk/release/app-release.apk`）经 dex 内容核对，**确已包含本轮后端修复**：`内存不足，拍摄失败，请重试`、`相机会话异常，请重新进入相机`、`请先选择工程包` 文案各命中 1 处，且 Room 生成 SQL 为 `INSERT OR ABORT INTO media_items`（旧语义 `INSERT OR REPLACE INTO projects` 命中 0）。该包未归档到 `dist/`。

### 7.3 发现的契约偏离（已修）

`CameraGeometry.kt` 的横屏分支在 12:52 被改为「只要 `isLandscapeWindow && min(w,h) < 600f` 就进入 34-v3 沉浸布局并给 48dp 握持」，丢掉了原有的「非折叠屏」前提（`referenceLandscape`）。后果是 **Flip 类折叠屏内屏横屏（短边 < 600dp，如 1015×415dp）会被拉进手机横屏布局并附加 48dp 手机握持区**，与契约「图 34-v3 不替换折叠屏、小外屏或竖屏已确认结构」及 AGENTS.md「折叠屏内外屏和小外屏均沿用各自确认的布局，均不附加普通手机握持区」冲突。

修复：把该条件收紧为 `referenceLandscape && isLandscapeWindow(...) && minOf(w,h) < 600f`，保留前端「普通手机在各类比例下都拿到 48dp 握持」的意图（`referenceLandscape = !foldableDevice`，非折叠屏一律为真），同时不放宽到折叠屏。平板路径不变（短边 ≥ 600dp → 握持为 0，紧贴取景右缘）。

新增两条测试固定两侧行为：
- `phoneSizedLandscapeKeepsGripWithoutTallPhoneEligibility`：非折叠屏 + 手机尺寸横屏窗（含 1.5 比例的 720×480），即使 `phoneGripSpace=false` 也必须沉浸且握持 ≥ 48dp；
- `foldableLandscapeKeepsItsOwnStructureWithoutPhoneGrip`：折叠屏横屏不得进入沉浸分支，右栏紧贴右缘（无手机握持空白）。

### 7.4 前端交付说明中与现状不符或缺证据之处

1. **`FRONTEND_REVIEW_AND_FIXES.md` §一.2 对定位的描述已过时**：文中称「对实时回调加入 `isLive` 保护，防止时钟抖动判定过期」，但该保护本轮已被后端移除——它会把过期/缓存定位点的时间戳改写为当前时间，从而通过 `LocationFreshness`（5 分钟）被当作实时点刻入水印与 EXIF。现状是「保留平台测量时间，时间为 0/负时才回落接收时刻；过期点直接丢弃」。该文档需按此更正。
2. **§四.3「已安装至已连接的真实设备（351953d0）」——设备状态已证实**：2026-10-10 13:07 现场核对，该设备（model 25102RKBEC，Android 17 / SDK 37 / arm64-v8a）当时安装的正是 `versionName 0.4.0 / versionCode 20`，`lastUpdateTime = 2026-10-10 12:54:47`，与前一轮签名 Release APK 的构建时间（12:54:35）吻合，说明前端确实安装过。需要补充的只是**仓库内没有留存证据文件**（无安装日志/截图，`dist/` 也未归档该包）；且 `landscape-approved-34-2026-10-10.md` 写明的「本轮设备列表中没有真机连接」与 11:12 的**模拟器**截图针对的是布局验证那一轮，两者不矛盾，但截图仍早于 12:52 的几何改动（该改动不影响手机路径，故手机截图依然有效）。
   *后续动作：13:07:36 已用本轮重新构建的签名 Release APK 覆盖安装到同一设备（签名身份一致，数据保留，`firstInstallTime` 仍为 2026-09-15），启动无崩溃。*
3. **§四.1「46 个套件全部通过」在当时成立**：当前实测为 **47 个测试类 / 181 个用例、0 失败**（多出的 1 个类是本轮新增的 `LocationAcceptanceTest`）。但那是 12:58 之后包含后端修复的结果；前端 11:32 那次构建早于本轮后端修复。
4. **§一.1/§一.2 把 Android 相机与定位修复记为前端成果**：这两处代码 (`CameraManager.kt`、`LocationTracker.kt`) 属后端域，本轮由后端修改；建议文档区分归属，避免后续维护误判。

### 7.5 仍未闭环的跨层交接项（前端 11:30 的改动早于本清单，故均未处理）

Android（已逐条核对代码现状）：
| 交接项 | 现状证据 |
| --- | --- |
| 相机错误重复上报被 StateFlow 去重，第二次不弹提示 | `CameraScreen.kt:405` 仍为 `LaunchedEffect(cameraError)` |
| 切后台应停止定位 | `CameraScreen.kt:423` ON_STOP 只调 `stopRecordingForBackground()`；停定位仍在 `onDispose`（:443） |
| 媒体删除需系统授权入口 | 全仓无 `createDeleteRequest` / `IntentSenderRequest` / `RecoverableSecurityException` |
| 设备诊断移出主线程 | `SettingsScreen.kt:157` 仍在点击回调里同步调用 `copyDiagnostics` |
| `operate` 忙时被拒需让调用方感知 | `GalleryViewModel.kt:92` 仍返回 `Unit` |

鸿蒙（`pages/Index.ets` 内 grep 无以下引用）：
| 交接项 | 现状证据 |
| --- | --- |
| `location.ensureListening()` 接入 1 Hz 时钟 | 无 `ensureListening` 引用 |
| 接收 `onListenFailed` 以显示「定位未启动」 | 无 `onListenFailed` 引用 |
| 离线版权限集合需精确+模糊同批申请 | `Index.ets:349` 仍为 `OFFLINE_BUILD ? ['ohos.permission.LOCATION'] : [...]` |
| 相机断开后提供重试入口 | `Index.ets:629` 的 `camera.onError` 仍无退避重试 |
| `aboutToDisappear` 的 `camera.close()` 需 catch | `Index.ets:295` 仍为裸 `this.camera?.close();` |

### 7.6 前端域内的一处同类遗留

`ScreenEnvironment.phoneGripEligible` 仍要求设备长短边比 ≥ 1.85，因此**竖屏**下比例在 1.6–1.85 的普通长条手机会拿不到底部握持留白——这与前端刚在横屏修掉的是同一个「按比例卡设备类别」的问题，只是竖屏分支未同步。该修复需要同时改 `core/layout/ScreenEnvironment.kt` 与 `CameraScreen.kt` 的 `phoneGripSpace` 计算，属前端域，本次未动。
