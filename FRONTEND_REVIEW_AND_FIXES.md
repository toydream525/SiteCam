# 前端全形态审阅与修复说明

本文档汇总 Android 与 HarmonyOS 前端界面在各设备形态（普通长条手机、折叠屏各姿态、平板）下的审阅结论、设计图对齐规范与代码修复细节，供技术评审与验收交接。

---

## 一、双端缺陷修复与核心机制对齐

### 1. 变焦倍率跳回 1× 问题
* **Android 端根因与修复**：
  * **根因**：现代 Android 平台的后置主摄为 `LOGICAL_MULTI_CAMERA`。此前逻辑在选择子倍率时，误将物理子摄作为独立 Camera ID 调用了 CameraX 的 `bindCameraUseCases()` 解绑与重绑。强行重绑逻辑子摄导致底层 HAL 抛错，触发 CameraX 错误恢复机制，重置回默认 1.0f。
  * **修复**：在 [`CameraManager.kt`](file:///Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/camera/CameraManager.kt) 中增加判定：目标倍率在当前逻辑相机缩放范围内时，直接调用 `applySessionZoom()` / `CONTROL_ZOOM_RATIO` 平滑变焦，禁止解绑重绑相机；同时在物理镜头异常重连回调中保留 `pendingDisplayZoom`，禁止重置回 1.0f。
* **HarmonyOS 端实现机制**：
  * 鸿蒙端直接基于 `@kit.CameraKit` 的 `camera.PhotoSession` 会话。在 [`CameraService.ets`](file:///Users/ninja/Documents/工程水印相机/harmonyos/entry/src/main/ets/core/CameraService.ets) 中，倍率通过 `session.setZoomRatio()` 在当前会话平滑调控，多摄联动依赖 `requiresNativeLensSwitch()` 严格判定，不会发生解绑回跳问题。

### 2. 现场地点与经纬度丢失问题
* **Android 端根因与修复**：
  * **根因**：此前系统定位监听受限于 `satelliteOnly = hasFine` 限制，获得精确位置权限时过滤了基站/网络定位；而国内环境下 GMS `fusedLocationClient` 在无 Google 服务网络时无法正常返回网络回调，导致室内或弱卫星信号下经纬度与现场地址完全无法显示。此外，缓存读取未注册 `PASSIVE_PROVIDER`，且时钟微差导致被判定过期置空。
  * **修复**：在 [`LocationTracker.kt`](file:///Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/location/LocationTracker.kt) 中移除该限制，系统 `LocationManager` 始终并发注册 `GPS_PROVIDER` 与 `NETWORK_PROVIDER`；补充 `PASSIVE_PROVIDER` 缓存兜底；对实时回调加入 `isLive` 保护，防止时钟抖动判定过期。
* **HarmonyOS 端实现机制**：
  * 鸿蒙端采用华为原生 `@kit.LocationKit` (`geoLocationManager`)，系统服务自动融合卫星、蜂窝基站与 Wi-Fi 定位，室内外均稳定派发坐标并调用逆地理编码服务解析地址，此前已在真机完成完整测试用例验收。

### 3. 退出第二页面（设置/工程包）预览窗口“先大后小”跳动与截断问题
* **现象与根因**：
  * 用户进入设置页或点击左上角工程包弹层退出返回时，预览窗口会瞬间先变大再变小，造成明显卡顿感，且在变大期间底部操作区被压缩，水印和记问题行被截断。
  * 根因在于普通长条手机竖屏下，底部操作区依赖的测量高度 `minimumPortraitControlsHeight` 在跨路由重入或弹窗关闭导致重组时丢失（回退为 0f），计算逻辑退回至上限仅 254dp 的临时值，导致第一帧取景器错误膨胀且底部 Dock 被严重截断；待一两帧后动态测量回调触发，才突变恢复至实际的 290dp+，产生肉眼可见的“先大后小”抽搐。
* **修复**：
  * 在 [`CameraGeometry.kt`](file:///Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/layout/CameraGeometry.kt) 中为普通长条手机竖屏确立保底基准操作区高度（280dp + 字号增量），即使未完成测量也直接预留充足高度，彻底消灭首帧取景器膨胀与操作区截断；
  * 新增 `CameraLayoutCache` 全局单例与 `rememberSaveable` 双重高度持久化记忆，在跨页面返回和工程包弹窗关闭时即时复用已测得的精确数值，消除二次测量带来的尺寸跳变，使预览窗口在进出第二页面时保持绝对静止稳定。

---

## 二、前端全形态与示例设计图对照审阅

### 1. 普通长条手机横屏（Phone Landscape）
* **对齐设计图**：[`34-landscape-harmony-camera-reference-v3.png`](file:///Users/ninja/Documents/工程水印相机/assets/design/frontend-v0.4.0/34-landscape-harmony-camera-reference-v3.png)（彻底停用旧横屏顶栏整行与横排按钮方案）。
* **左侧工具列**：约 56dp 窄工具列，闪光灯、设备轮廓拍摄方向、设置齿轮三枚图标垂直居中排布。
* **取景区域**：4:3 沉浸画幅；短工程入口半透明悬浮于取景左上方；现场水印按文本紧凑收紧于左下。
* **变焦倍率悬浮**：透明底板、黄色细圈与高亮文本垂直居中悬浮于取景画面右边缘，具备至少 48dp 触区，不占用右侧黑色操作栏宽度。
* **右侧控制栏**：
  * 上部：“拍照”与“录像”两列模式选择，下接相册圆角缩略图；
  * 中部：白圈快门按钮居中，与相册、下方的翻转镜头在垂直中轴线上**绝对同轴**；
  * 下部：“水印”与“记问题”两列对称分布，列中心与顶部模式选择完全对齐；
  * **握持留白**：右侧保留约 48dp 专属物理握持空白区，快门在操作区内居中而不贴屏幕右边缘。
* **修复细节**：修复了 Android [`CameraGeometry.kt`](file:///Users/ninja/Documents/工程水印相机/app/src/main/java/com/sitecam/app/core/layout/CameraGeometry.kt) 中握持空间的计算条件（修正因 `referenceLandscape` 导致的逻辑判定偏差），确保普通手机横屏在各类比例下均稳定享有 48dp 握持空间。

### 2. 平板横屏（Tablet Landscape）
* **结构统一**：同样遵循 34-v3 沉浸式结构（左侧 56dp 工具列、画面右缘透明倍率、右侧同轴操作栏），替换原本的平板横屏顶部整行和相册/快门/切镜头横排。
* **留白差异**：严格依照契约，平板横屏不附加普通长条手机的 48dp 额外握持空白，右侧操作栏紧贴取景右边缘，最大化大屏可用视野。

### 3. 普通长条手机竖屏（Phone Portrait）
* **对齐设计图**：`01-phone-main-v2.png`、`32-camera-feedback-layouts-v2.png`。
* **顶部导航**：工程名与向下箭头组成紧凑胶囊，右侧紧跟闪光灯、方向小菜单与设置。
* **操作区与握持**：快门按钮（72dp）居中并抬高，下方保留 64dp 明确握持空白；相册位于快门左侧、切换镜头位于右侧，变焦与模式位于快门上方；大字号或短高窗口弹性分配，严禁挤压底部握持留白。

### 4. 折叠屏展开态（Fold Inner / 近平方内屏）
* **对齐设计图**：`27-fold-posture-layouts-v2.png`（左图）。
* **布局**：4:3 取景器居左，右侧为垂直拍摄操作栏；变焦胶囊与模式置于取景器下方，相册、快门、翻转前后摄、水印与记问题垂直分布于右侧栏，避让内屏折痕区域。

### 5. 折叠屏横向悬停态（Half-folded / Hover Posture）
* **对齐设计图**：`27-fold-posture-layouts-v2.png`（右图）。
* **上下分区**：水平折痕将屏幕自然分成上下两半，上半部为完整 4:3 取景画面，下半部独立承载变焦、模式切换、相册、快门、翻转镜头、水印与记问题，交互控件绝不横跨折痕。

### 6. 折叠屏外屏（Cover Screen）
* **对齐设计图**：`06-cover-screen-v2.png`。
* **紧凑适配**：小外屏精简次级元素，右侧窄 Dock 承载相册、快门与镜头切换，长按进入更多菜单，保障单手快速拍摄。

### 7. 平板竖屏（Tablet Portrait）
* **对齐设计图**：`23-tablet-portrait-main.png`、`31-tablet-portrait-help-first-launch.png`。
* **布局**：顶部整行工程与三图标，3:4 取景器居中，下方依次为变焦、模式与同轴操作区，不附加普通手机的额外握持垫高。

---

## 三、动效体验与鸿蒙商店规范符合性

### 1. 动效体验分析
* **快门物理按压反馈**：
  * 按下瞬间缩放至 90%（45ms），松开平滑回弹至 100%（70ms），总耗时 115ms。触感利落跟手，无拖沓滞后感。
* **取景器曝光反馈（Viewfinder Flash）**：
  * 拍照瞬间屏幕叠加 28% 半透明白色遮罩，并在 100ms 内淡出，模拟真实相机快门开合，为用户提供清晰直观的成片感知。
* **媒体存盘入库微动效（Thumbnail Bounce）**：
  * 照片/视频处理完毕后，缩略图触发一次 0.86 → 1.10 → 1.0 的弹性回弹动效（110ms + 90ms），明确传达媒体已成功存入相册。
* **页面转场平滑性**：
  * 鸿蒙端全站页面转场采用官方推荐的物理弹簧曲线 `curves.springMotion(0.35, 0.9)`，自然平滑无突兀跳变。

### 2. 鸿蒙应用市场（AppGallery）规范合规性
* **直观操作反馈（UX Guidelines）**：快门收缩、取景闪光、缩略图跳动三位一体，完全符合商店设计规范中对拍摄与关键业务操作应具备直观反馈的要求。
* **主线程无阻塞与响应性**：所有保存、缩放与转场均异步化，动效耗时控制在 100ms 左右，不阻碍用户连续拍摄。
* **生命周期与资源泄露防范**：所有动画计时器均纳入集中管理数组，组件销毁（`aboutToDisappear`）时自动全量清理，避免内存泄漏。
* **权限合规（华为 3.1 规则）**：严格遵循最小必要与平稳过渡原则，未申请前不跳转系统空白页，支持拒绝后友好提示与按需再次授权。

---

## 四、验证状态

1. **自动化测试**：
   * Android 单元测试（46 个套件，包含 `ScreenAdaptationTest` 针对手机横屏握持空间与平板横屏的对比验证）全部通过。
2. **签名身份合规**：
   * Android 端使用官方正式密钥库 `sitecam-release.jks`（DN: `CN=SiteCam, OU=Personal App, O=SiteCam`）签署 Release APK。
3. **真机运行状态**：
   * 已安装至已连接的真实设备（`351953d0`），启动运行正常，GPS/网络双定位与相机功能均已实机核验。
