# SiteCam 工程水印相机 · HarmonyOS 0.3.2（15）权限与海拔改动 交叉终审报告

审查日期：2026-09-22
审查对象：`harmonyos` 工作区未提交改动（6 个文件）+ `dist/appgallery/SiteCam-0.3.2-HarmonyOS-AppGallery-Signed.hap`
审查方式：源码逐行审查 + 真机不可用条件下的可复现证据（编译 / 单测 / 并发实验 / 包体符号校验）

---

## 0. 结论速览

| 项目 | 结论 |
| --- | --- |
| 终审评级 | **【通过但有微调建议】（可提审；提审阻断项 0 项）** |
| 华为 3.1 权限驳回项 | **修复成立**，且经包体校验确认已进入已签名提审包 |
| 水印防闪烁 | **成立**，链路完整 |
| 海拔刷新 | **未达提示词宣称的“100% 穿透缓存”**，存在 3 处可复现缺陷 + 1 处需真机验证的假设风险 |
| 本次改动是否引入回归 | **是，1 处**（删除 `isNewer` 导致新广播可被较旧的硬件结果覆盖） |
| 是否已在工作区复现并验证补丁 | 是。3 处最小改动（约 10 行）后三个缺陷全部转为期望行为 |

一句话：**这次答复华为权限驳回的修复是正确且完整的；但同批次夹带的定位改动里有 3 个可复现的并发/兜底缺陷，建议在 0.3.3（或在提审前）按第 5 节清单修掉。**

---

## 1. 审查方法与已核实的事实

| # | 证据 | 结果 |
| --- | --- | --- |
| E1 | `git diff` 6 个文件（LocationService / Models / Store / Index / PermissionGuidePageView / LocationServiceChecks） | 已逐行审查，diff 与提示词引用一致 |
| E2 | hvigor + SDK 26.0.0.105 编译 `entry@default` | **BUILD SUCCESSFUL**（34 tasks，0 up-to-date） |
| E3 | 同工具链编译 `entry@ohosTest` | **BUILD SUCCESSFUL** |
| E4 | 将 `LocationServiceChecks` 原样在 Node 24 执行（仅替换 `@kit.*` 导入） | `check(): PASS` / `serviceBoundaries(): PASS`（含新增海拔断言） |
| E5 | 将 `LocationService.ets` 原文（仅替换 `@kit.*` 导入，函数体逐字节相同）在 Node 上跑并发场景 | 复现 D1/D2/D3 三个缺陷 |
| E6 | 对 E5 施加第 5 节补丁后重跑 | A/B/C 三个场景全部转为期望行为 |
| E7 | 解包 `SiteCam-0.3.2-HarmonyOS-AppGallery-Signed.hap` 检索 `modules.abc` | 含 `systemPermissionsRequested`、`地址与海拔已更新`、`去系统设置补充权限`；**`isNewer` 已消失** → 提审包确实包含本次改动 |
| E8 | 包内 `app_gallery`×2、`CERT`×2（未签名包均为 0） | 0.3.2 提审包为 AppGallery 发布证书签名 |
| E9 | 图标 `sips` 校验 | AppScope/entry 的 base+dark 四个 `app_icon.png` 与 `store-icon-1024.png` 均为 **1024×1024、hasAlpha: no** |
| E10 | `AppScope/app.json5` | `versionCode 15 / versionName 0.3.2`，与审核说明一致 |

E2/E3 注意事项：工程路径含中文时 hvigor 直接报 `00306003 Invalid project path`，必须复制到纯 ASCII 路径构建（`scripts/build.sh` 的 cache 目录方案即为此）。这是既有约束，非本次改动引入。

---

## 2. 维度一 · 华为 AppGallery 合规性（审核指南 3.1 项）

### 2.1 根因判断：正确 ✅

“应用在生命周期内从未调用 `requestPermissionsFromUser`，系统设置的应用详情里就不会注册该权限开关节点”这一判断与华为官方 FAQ 一致：

- [Why only the permissions requested by the application are displayed in the system settings?](https://developer.huawei.com/consumer/en/doc/harmonyos-faqs-V5/faq-basics-service-kit-9-V5)
- [使用了某些权限但是系统设置页不存在，应用内如何引导用户修改权限](https://developer.huawei.com/consumer/cn/doc/harmonyos-faqs/faqs-access-control-17)

（两条均为 `developer.huawei.com` 域名，本机网络解析为内网地址无法抓取正文，仅以官方 FAQ 标题与检索结果佐证；建议提审前在浏览器打开二次确认其正文表述。）

### 2.2 修复闭环核查：三条入口全部覆盖 ✅

| 入口 | 代码位置 | 行为 | 结论 |
| --- | --- | --- | --- |
| 引导页主按钮 | `PermissionGuidePageView.ets:13` | `systemPermissionsRequested=false` 时 `some(!granted && !needsSettings)` 为真 → 文案“开启所需权限” → `onRequest` → `permissions(true)` | ✅ |
| 引导页“去系统设置开启 / 去系统设置补充权限” | `Index.ets:898` `onOpenSettings` | 前置 `if(!systemPermissionsRequested) await this.permissions(true)` 双保险 | ✅ |
| 相机页“打开权限设置” / 设置页“权限设置” | `Index.ets:834`、`Index.ets:907` | 只跳应用内 `permissions` 页，不直接跳系统设置 | ✅ |
| 授权返回前台 | `Index.ets:424` | `page==='permissions'` 时 `await this.permissions(false)` 重查 | ✅ |
| UI 即时刷新 | `PermissionGuidePageView.ets:13` ForEach 复合 key | 授权结果变化 → key 变化 → 卡片重建 | ✅（原理见 4.2） |

全仓检索 `com.huawei.hmos.settings` / `application_info_entry`：**仅有 `Index.ets:898` 一个调用点**，无绕过守卫的旁路；`requestPermissionsFromUser` 全仓也**仅有 `Index.ets:434` 一处**，收敛良好。

### 2.3 权限申请时机：符合规范 ✅

首启不弹窗（`aboutToAppear` 只调 `permissions(false)`），尊重“暂时跳过”，到实际功能页由用户主动点击才申请 —— 符合“按需申请、不骚扰”的审核预期。

### 2.4 残留合规风险

| ID | 风险 | 等级 | 建议 |
| --- | --- | --- | --- |
| R1 | “100% 杜绝”只是表述：目前依赖 `onOpenSettings` 单点内联守卫，未来新增入口易回退 | 低 | 抽成唯一 `openAppSettings()` 私有方法，所有跳转只走它 |
| R2 | 用户全程未点过任何权限按钮时，系统设置仍不会有开关（平台机制） | 低（非缺陷） | 客服/申诉口径统一为“需先在应用内触发一次系统权限询问” |
| R3 | 单次 `requestPermissionsFromUser` 批量申请相机+粗略定位+精确定位+麦克风 4 项 | 低 | `module.json5` 已按规范声明 `reason` + `usedScene`；仍建议拆分：相机先请，定位/麦克风在真正使用到时再请，进一步规避“过度申请”判定 |
| R4 | `requestPermissionsFromUser` 抛错时 `onOpenSettings` 不会跳转（fail-safe） | 低 | 保留该行为，但补一条明确 toast 文案 |

### 2.5 需要你们澄清的前提 ⚠️

仓库自有审核记录 `docs/appgallery/上架准备进度.md`（2026-09-21）记载的官方退回项是**三条**：单层图标尺寸/Alpha、挖孔区文字重叠（`[[0, 2497, 1600, 2565]]`）、宣传图文案含“Beta”。**其中并没有 3.1 权限项**。请确认本次要回应的是哪一轮驳回；若这是新一轮驳回，建议把该轮驳回原文补录进该文档，避免申诉文案答非所问。

---

## 3. 维度二 · 逻辑正确性、并发安全与现场极限边界

### 3.1 “手动刷新是否 100% 穿透缓存拿到最新经纬度与海拔？” → **否**

三条独立理由：

1. `currentFix()` 使用 `geoLocationManager.getCurrentLocation({priority: ACCURACY, scenario: UNSET, timeoutMs: 10000})`，**未设置 `maxAccuracy`**，且 `isFreshFix` 允许 **5 分钟**内的定位被判为“新鲜”。平台完全可以把一个几分钟前的缓存解当作“当前位置”返回。SDK 的 `CurrentLocationRequest` 只有 `priority/scenario/maxAccuracy/timeoutMs`（无 `maxAge`），所以 `maxAccuracy` 是当前唯一可用的收紧手段。
2. `performRefresh` 的 `catch` 分支在硬件查询失败时用 `this.fix` 顶替，最终**返回 `true`**（见 D2）。
3. 提示词中“显式执行 `await this.currentFix()` 向底层硬件重新请求”这句话是对的，但“拿到的一定是最新硬件解”不成立 —— 请求 ≠ 保证。

### 3.2 已复现的三个缺陷（E5 证据）

`LocationService.ets` 原文（函数体逐字节一致，仅把 `@kit.*` 导入替换为桩）在 Node 24 上执行的输出：

```
[A] refresh= true | committed ts offset= 0 ms | committed altitude= 12 | newer broadcast ts offset= 5000 ms (altitude 88.5)
[A] VERDICT: newer broadcast WAS CLOBBERED by the hardware result
[B] first refresh= true | second refresh= true | refreshState= success | committed altitude= 10
[B] VERDICT: a failed hardware query IS REPORTED AS SUCCESS with the cached altitude
[C] refresh= false | refreshState= failed | failureReason= address-unavailable | committed altitude= 7 | committed ts offset= 2000
[C] VERDICT: two consecutive broadcasts during geocoding made the whole refresh FAIL although a valid fix exists
```

**D1（回归 · 中低危）** 删除 `isNewer` 的同时丢掉了它的顺序守卫。新代码在 `currentFix()` 返回后**无条件** `this.fix = candidate`，若期间 `receive` 已投递了更新的广播解，就会被这个**较旧**的硬件解覆盖（时间戳回退 5 秒）。旧代码的 `isNewer(candidate, this.fix)` 正是防这个的 —— 它需要被保留，但**不要**恢复“`this.fix` 新鲜就跳过硬件请求”的旧行为（那才是本次要修的 bug）。

**D2（中低危）** 硬件查询失败时，只要 `this.fix` 还在 5 分钟新鲜期内就继续走逆地理并返回成功，UI 因此弹出「地址与海拔已更新」，而**海拔很可能是旧的** —— 恰好是本次要解决的“楼层升高海拔不更新”痛点，被兜底逻辑重新掩盖了。对以“工程留档”为卖点的应用，向用户断言“已更新”而数据未更新属于数据可信度问题。

**D3（中低危）** `for (attempt < 2)` 的循环在第二次逆地理期间再收到一次广播时会**耗尽尝试次数**，直接 `finishFailure('address-unavailable')`，于是水印其实已经更新成功，用户却看到「地址暂不可用，请重试」。同时最坏耗时叠加：`10s(GPS) + 8s(逆地理) + 8s(逆地理) ≈ 26s`，而这期间刷新按钮 `enabled(false)`、无取消入口，弱网现场体验差。注：`distanceInterval: 5` / `timeInterval: 10` 的连续广播在车内移动时命中这个窗口的概率并不低。

### 3.3 海拔类型与“死海 / 海平面”边界

- 显示侧正确 ✅：`formatElevation` 要求 `locationStatus==='FRESH'` 且 `Number.isFinite(altitude)`，`-400` → `-400.0 m`，`0` → `0.0 m`，`null/NaN` → `暂不可用`。
- 但**继承侧有一个未验证的假设** ⚠️：SDK 中 `geoLocationManager.Location.altitude` 是**必填 `number`**（`@ohos.geoLocationManager.d.ts:1951`），另提供 `altitudeAccuracy?: number`（API 12 起）。`asFix()` 只在 `NaN/±Infinity` 时把海拔置 `null`。**如果设备用 `0` 表示“海拔不可用”**，那么：
  - `candidate.altitude === null` 的继承判断永不触发；
  - 真实的 88.5 m 会被 0 覆盖，水印永久写入 `0.0 m`。
  这需要真机验证（在有海拔读数后进入无垂直定位解的区域对比）。建议改为：仅当 `Number.isFinite(altitude)` **且**（`altitude !== 0` 或 `altitudeAccuracy > 0`）才视为有效读数，否则视为不可用并保留上一版真实海拔。`altitudeAccuracy` API 12 起可用，与 `compatibleSdkVersion 5.0.0(12)` 一致，可安全使用。

### 3.4 水印防闪烁：成立 ✅

链路已闭环：

1. `captureSnapshot`：`s.address = p.address || (fix?.address || '定位不可用')`（`Models.ets:177`）
2. `resolveFields`：`address` 取 `s.address`，`elevation` 取 `formatElevation(s.fix, s.locationStatus)`（`WatermarkRenderer.ets:14-33`）
3. `performRefresh` 在 `this.fix = candidate` **之前**先把旧 `address` 继承到 candidate（`LocationService.ets:297-299`），逆地理完成后再原子替换。

因此逆地理 200~800ms（弱网更长）期间地址不再退化为“定位不可用”。补充两点精确结论：
- 若 `project.address` 非空，位置地址本就不参与显示，此路径不存在闪烁 —— 提示词描述的痛点只在工程地址为空时成立；
- `receive` 侧同样做了继承（`LocationService.ets:385-387`），连续广播不会把地址冲空。✅

### 3.5 死锁 / 内存泄露 / 时序

- **无死锁**：`refreshPromise` 的三条出口（`finishSuccess` / `finishFailure` / 顶层 `.catch`）都会 settle；`invalidateLocation` 与 `stop()` 也会 `resolve(false)`；重复 resolve 是无害 no-op。
- **无泄露**：`aboutToDisappear` 先 `this.location.onChange=null` 再 `stop()`，`stop()` 内 `off` 三个监听；`refreshPromise` 在 settle 后置空。
- **可优化**：`receive` 每收到一次广播（静止时每 10s 一次）都会发起一次逆地理，且平台 API 无取消能力。移动中可能堆叠多个在途请求。建议对同一坐标去重/节流（例如坐标变化 < 20m 且地址已有则跳过）。
- **低危死状态**：`shouldShowAddressRefresh()` 在 `!locationPermission` 时直接返回 `false`，因此 `addressRefreshLabel()` 里的「请开定位」与 `permission-denied` 相关 UI 文案实际不可达。

### 3.6 补丁可行性（E6 证据）

按第 5 节 M1/M2/M3 施加约 10 行改动后重跑同一组场景：

```
[A] refresh= true | committed ts offset= 5000 ms | committed altitude= 88.5 | ...
[A] VERDICT: newer broadcast survived
[B] first refresh= true | second refresh= false | refreshState= failed | committed altitude= 10
[B] VERDICT: a failed hardware query is reported as failure
[C] refresh= true | refreshState= success | failureReason=  | committed altitude= 7 | committed ts offset= 2000
[C] VERDICT: two consecutive broadcasts during geocoding were handled
```

---

## 4. 维度三 · ArkTS 架构规范与多端适配

### 4.1 `lifecycleToken` / `canCommit` 对已卸载组件的保护：有效 ✅

失效点覆盖充分：`foregroundChanged()` 进后台 `lifecycleToken++`、`aboutToDisappear()` `++`、`stop()` `lifecycleEpoch++`、`invalidateLocation()` `permissionEpoch++`；`canCommit` 同时校验 token / lifecycle / permission / `refreshState==='refreshing'`，逆地理前后共 3 个提交点全部先校验。**未发现“已卸载组件被异步回调更新”的路径。** 需注意：真正兜住“组件已卸载”的是 `onChange` 被置空 + `stop()`，`lifecycleToken` 兜的是“页面已切换但服务仍活”的情况，两者不可互相替代 —— 当前实现两者都有。

### 4.2 `ForEach` 复合 Key：必要且正确 ✅

`(item: PermissionStatus) => \`${item.id}_${item.granted}_${item.needsSettings}\``

- 旧写法 `item.id` 时，`@Prop permissions` 虽被整体替换，但 ArkUI 的 `ForEach` 对**未变化的 key 会复用已建子组件、不再执行 item builder**，于是卡片文案停在旧授权状态 —— 这很可能正是“从系统设置返回后 UI 不刷新”的直接原因。复合 key 让状态变化即换 key，触发重建，**修得对**。
- 代价：权限状态每变化一次，三张卡片销毁重建。卡片是无内部状态的 `@Builder`，可接受；但请在注释里写明“此处依赖 key 变化触发重建”，避免后人误改成稳定 key 或给卡片加动画/局部状态。
- 更 ArkUI 惯用的替代方案：`@Observed` + `@ObjectLink` 模型，或 `Repeat`。当前列表只有 3 项，不必改。

### 4.3 `photoPermission` / `microphonePermission` 提升为 `@State`：正确且必要 ✅

否则 `permissionRows()` 依它们生成的 `needsSettings` 变化不会驱动父组件重建，`@Prop` 也就不会重新拷贝。`locationPermission`、`locationNeedsSettings` 已是 `@State`。注意：`permissionRootPage` 的 `hasRequested: this.settings.systemPermissionsRequested` 依赖 `@State Settings` 的**一层属性观察**能力 —— 当前成立，但若日后把 `settings` 改成普通字段会静默失效，建议镜像一个专用 `@State` 或在注释中固定该约定。

### 4.4 平板 / 折叠屏

本次改动与布局正交，未发现对 `windowWidth/Height`、分栏、外屏路径的回归。但“权限页 → 系统设置 → 返回”在**平板分栏与折叠展开**下的 `@Prop` 刷新，仓库内没有自动化或真机证据（`verification/` 内相关记录均为 0.3.0 的屏幕适配，不含权限回流）。建议在 `DEVICE-CHECKLIST` 增补一项。

### 4.5 编译与告警

- ArkTS 编译通过；`Number.isFinite(this.fix.altitude)`（`number|null` 实参）在 SDK 26 的 lib 声明下合法，无类型错误。
- 编译期仅有既有的重复组件 id 告警（`camera-shutter`、`camera-switch-lens`、`camera-controls`、`camera-gallery`，来自折叠外屏镜像控件），**非本次改动引入**，但会影响自动化 UI 定位，建议排期消除。

### 4.6 死代码清理：只兑现了一半 ⚠️

- `isNewer` 已删除 ✅
- 冗余构建器 `permissionsPage()` 已删除 ✅（全仓无引用，已 grep 确认）
- **但 `Index.ets:910` 的 `guidePage()` 仍是无引用死代码**：`build()` 在 `Index.ets:939` 用的是 `onboardingRootPage()`，`guidePage()` 与 `OnboardingPageView.ets` 的功能完全重复。提示词所称“清理了冗余视图构建器”并未覆盖它。建议删除。

---

## 5. 维度四 · 评级、微调清单与提审说明

### 5.1 评级

**【通过但有微调建议】** —— 可提审。

依据：
- 提审阻断项 **0 项**：权限驳回项闭环成立、包体已含修复、图标/版本/签名齐备；
- 但同批次改动存在 1 处回归 + 2 处兜底/时序缺陷（3.2），以及 1 处需真机验证的海拔语义假设（3.3）。这些**不会导致 AppGallery 驳回**（海拔字段默认关闭，且属功能正确性范畴），但会影响“海拔/地址刷新”这一卖点的数据可信度，建议随 0.3.3 一并修掉。

### 5.2 微调清单

| ID | 优先级 | 位置 | 问题 | 建议改动 |
| --- | --- | --- | --- | --- |
| M1 | 高 | `LocationService.ets:300` | 硬件结果无条件覆盖更新的广播（D1 回归） | `if (!this.fix \|\| candidate.timestamp > this.fix.timestamp) { this.fix = candidate; } else { candidate = this.fix; } this.notifyChange();` |
| M2 | 高 | `LocationService.ets:302-308` | 硬件失败被上报为成功（D2） | 去掉 catch 里的缓存顶替，直接 `return this.finishFailure(token, this.reasonFromFailure(e as Object, 'location-timeout'));`；若确要降级，请返回独立状态并让 UI 文案改为“地址已更新（海拔沿用上次）” |
| M3 | 高 | `LocationService.ets:314-338` | 尝试耗尽被误判失败（D3） | 循环内记录 `lastAddress`；耗尽后若 `isFreshFix(activeCandidate)` 则写回地址并 `finishSuccess`，仅在真的无解时失败 |
| M4 | 中 | `LocationService.ets:194` | `altitude` 必填 number，0 可能是“无海拔” | 结合 `altitudeAccuracy`：`Number.isFinite(a) && !(a === 0 && !(Number.isFinite(va) && va > 0))` 才视为有效，否则置 `null` 触发继承；真机验证后再定 |
| M5 | 中 | `LocationService.ets:245-262` | “穿透缓存”不足 | `getCurrentLocation` 增加 `maxAccuracy`（如 50）；刷新路径用独立的更短新鲜度阈值（如 30s），与 5 分钟的 `isFreshFix` 解耦 |
| M6 | 中 | `LocationService.ets:377-400` | 每次广播都发起逆地理，无节流/取消 | 按坐标去重（位移 < 20m 且有地址则跳过），或在途请求数上限 1 |
| M7 | 低 | `Index.ets:898` | 守卫内联在 builder 中 | 抽成唯一 `openAppSettings()`，便于后续新增入口时不再遗漏 |
| M8 | 低 | `Index.ets:910` | `guidePage()` 死代码 | 删除 |
| M9 | 低 | `Index.ets:284-285` | 「请开定位」分支不可达 | 合并/删除无用文案分支 |
| M10 | 流程 | `dist/appgallery/SIGNING-VERIFICATION.json` | 记录仍为 0.3.1（14），未覆盖本次提审的 0.3.2（15） | 提审前重新生成签名核验记录（`officialSignatureVerified` / `profileType` / `distributionType` / 版本号），否则证据链与提交包不一致 |
| M11 | 流程 | git 工作区 | 提审包由**未提交**的工作区构建 | 提交这 6 个文件并打 tag，保证提交物可追溯、可复现 |

### 5.3 可直接粘贴的提审申诉 / 版本更新审核说明（约 290 字）

> 本次更新（0.3.2，versionCode 15）已按审核意见完成修复：一、权限引导。用户跳过引导后，从相机页进入权限页时，应用会先调用系统权限询问完成权限注册，再跳转应用详情，系统设置中可正常显示并开启相机、麦克风、位置权限，不再出现无开关的情况；授权返回后页面状态即时刷新。二、定位与海拔。手动刷新地址会重新请求最新定位与海拔，并在逆地理过程中保留上一版有效地址，避免水印内容闪烁。三、同步修复单层图标为 1024×1024 无透明通道、公开素材移除“Beta”字样、挖孔区域文字重叠。应用仅在用户主动使用拍摄、录音、定位功能时按需申请对应权限，不申请后台定位、通知与存储权限，权限用途与隐私政策一致。请复核，谢谢。

---

## 6. 对那份“终审提示词”的勘误（本次一并核对）

| # | 提示词中的表述 | 实际情况 |
| --- | --- | --- |
| 1 | “目标平台 API 12 / SDK 5.0.0” | 实为 `compileSdkVersion 26.0.0`、`targetSdkVersion 26.0.0`、`compatibleSdkVersion 5.0.0(12)`。API 12 只是最低兼容，不是编译/目标 SDK |
| 2 | “100% 穿透缓存”“100% 杜绝”“牢不可破” | 三处绝对化结论均不成立（见 3.1、2.4-R1、3.3） |
| 3 | “清理了未使用的死代码 `isNewer` 与冗余视图构建器” | `isNewer` 与 `permissionsPage()` 确实已删；但 `guidePage()` 仍是死代码，清理只完成一半 |
| 4 | 未提及 | 删除 `isNewer` 引入的顺序回归（D1）、catch 成功兜底（D2）、尝试耗尽误判（D3）、`altitude` 必填 number 与 `altitudeAccuracy` 的语义缺口（3.3） |
| 5 | “驳回场景（第 3.1 项）” | 仓库自有记录（2026-09-21）的三项退回中不含权限项，需确认对应轮次 |
| 6 | 方法学 | 该提示词只要求“输出报告”，未要求**可复现证据**。结果就是满纸“符合/不符合”而无法验证。建议在提示词里固定要求：编译日志、单测输出、关键路径实验、包体符号校验，四者缺一不可 |

---

## 附录 A · 证据复现方式

```bash
# 1) 编译（工程路径必须为纯 ASCII，否则 hvigor 报 00306003）
cp -R harmonyos /tmp/sitecam-hbuild            # 或用 scripts/build.sh 的 cache 方案
cd /tmp/sitecam-hbuild
export DEVECO=/Applications/DevEco-Studio.app/Contents
export PATH="$DEVECO/tools/node/bin:$DEVECO/tools/ohpm/bin:$PATH"
export DEVECO_SDK_HOME="$DEVECO/sdk"
ohpm install --all
"$DEVECO/tools/hvigor/bin/hvigorw" --mode module -p product=default \
  -p module=entry@default -p buildMode=debug assembleHap --no-daemon
# 结果：BUILD SUCCESSFUL（34 tasks）
# 测试模块：-p module=entry@ohosTest

# 2) 并发实验（本仓库保留的可复现实验）
node --experimental-strip-types /Users/ninja/Documents/sitecam-review/race.ts      # 当前实现
node --experimental-strip-types /Users/ninja/Documents/sitecam-review/race2.ts     # 施加 M1/M2/M3 后
# 说明：LocationService.ts 是 LocationService.ets 的逐字节副本，仅把 @kit.* 导入换成桩
#       （可用 diff <(grep -v '^import ' 原文件) <(grep -v '^import ' 副本) 自证）

# 3) 包体校验
unzip -q dist/appgallery/SiteCam-0.3.2-HarmonyOS-AppGallery-Signed.hap -d /tmp/hap
python3 - <<'PY'
data=open('/tmp/hap/ets/modules.abc','rb').read()
for s in ['systemPermissionsRequested','地址与海拔已更新','isNewer','address-unavailable']:
    print(s, data.count(s.encode()))
PY
```

## 附录 B · M1/M2/M3 最小补丁

```diff
--- a/entry/src/main/ets/core/LocationService.ets
+++ b/entry/src/main/ets/core/LocationService.ets
@@ performRefresh — 硬件结果不得覆盖更新的广播
-      this.fix = candidate;
+      if (!this.fix || candidate.timestamp > this.fix.timestamp) { this.fix = candidate; } else { candidate = this.fix; }
       this.notifyChange();

@@ performRefresh — 硬件失败必须如实上报
-      if (this.fix && isFreshFix(this.fix)) {
-        candidate = this.fix;
-      } else {
-        return this.finishFailure(token, this.reasonFromFailure(e as Object, 'location-timeout'));
-      }
+      return this.finishFailure(token, this.reasonFromFailure(e as Object, 'location-timeout'));

@@ performRefresh — 尝试耗尽时提交最新解而非误判失败
     let activeCandidate: Fix = candidate;
+    let lastAddress: string = '';
     for (let attempt = 0; attempt < 2; attempt++) {
       ...
       const address = await this.reverseAddress(activeCandidate);
+      lastAddress = address;
       ...
     }
-    return this.finishFailure(token, 'address-unavailable');
+    if (isFreshFix(activeCandidate)) {
+      if (lastAddress) activeCandidate.address = lastAddress;
+      this.notifyChange();
+      return this.finishSuccess(token);
+    }
+    return this.finishFailure(token, 'address-unavailable');
```

> 提示：M2 若产品上希望保留“位置可用即算成功”的降级体验，请勿沿用 `finishSuccess`，而应新增 `degraded` 状态并把 toast 改为“地址已更新（海拔沿用上次）”，避免向现场人员断言未发生的事。

---

# 第二部分 · 修复实施与验证（2026-09-22）

上方报告是审查结论。以下记录按该结论落地的修改、在真机上取得的证据，以及本轮新发现的问题。

## 2.1 已落地修改

| 编号 | 文件 | 内容 |
| --- | --- | --- |
| M1 | `core/LocationService.ets` | 硬件解不再覆盖更新的连续定位解（恢复顺序守卫，但不恢复“有缓存就跳过硬件请求”） |
| M2 | 同上 | 硬件查询失败如实返回失败，不再用缓存顶替成功 |
| M3 | 同上 | 逆地理尝试耗尽时提交最新解与已取得地址，不再误判失败 |
| M4 | 同上 | 新增 `usableAltitude()`：`altitude` 为必填 number，只有 `0` 且无垂直精度时视为不可用并继承上一版真实海拔 |
| M5 | 同上 | 手动刷新增加 `maxAccuracy=50` 与 30 s 新鲜度门槛（连续广播仍保留 5 min 窗口） |
| M6 | 同上 | 新增 `movedMeters()` 与节流：静止且已有地址时 60 s 内不重复逆地理 |
| M7 | 同上 + 测试 | `receive` 拆为绑定箭头 + 公有 `receiveLocation()`，作为 ohosTest 注入点；`listening` 公开并注明用途 |
| M8 | `pages/Index.ets` | 系统设置跳转收敛为唯一 `openAppSettings()`，跳转前确保至少请求过一次 |
| M9 | `pages/Index.ets` | 删除死代码 `guidePage()`（与 `OnboardingPageView` 重复）与 `settingsPage()` |
| F1 | `components/PermissionGuidePageView.ets` | 权限卡片标题/状态行由居中改为左对齐（对齐 Android 版 `PermissionCard`） |
| F5 | `components/SettingsPageView.ets` + `Index.ets` | 设置页新增「权限设置」入口并接回 `navigate('permissions')` |
| F2 | `pages/Index.ets` | 权限页返回按钮与系统返回统一走 `permissionsReturn`，不再硬编码回工程页 |
| F7 | `components/PermissionGuidePageView.ets` | 权限页内容增加 `maxWidth 720`，平板横屏不再被拉满 |
| F3 | 新增 `testability/PermissionGuideChecks.ets` | 把「未申请过就绝不跳系统设置」固化为可执行断言 |
| — | `resources/rawfile/camera_shutter.wav` | 快门声替换为 CC0 真实单反快门录音（素材来源与许可见 `THIRD_PARTY_NOTICES.md`） |

## 2.2 验证证据

| # | 证据 | 结果 |
| --- | --- | --- |
| V1 | ASCII 路径下 hvigor 编译 `entry@default` / `entry@ohosTest` / release `assembleApp` | 三次 `BUILD SUCCESSFUL` |
| V2 | Node 24 逐字节副本执行并发场景 A/B/C | 三个场景全部转为期望行为（较新解存活 / 失败如实上报 / 不再误判失败） |
| V3 | Node 24 执行整个 `LocationServiceChecks`（含新增 T1–T4） | `check()` PASS、`serviceBoundaries()` PASS |
| V4 | 平板（HUAWEI MLR-AL10，API 26）安装签名包并跑完整运行时套件 | **54/54 通过**，含新增「权限引导未申请时不跳系统设置」，且 `原生视频水印与音轨` 在真机通过（模拟器因缺 H.264 曾失败）。结果见 `device-results-0.3.2-tablet.json` |
| V5 | 平板 prefs 实测 | `permissionHandled=true`、`systemPermissionsRequested` 缺失——正是华为截图对应的状态 |
| V6 | 平板实机走查 | 设置页新增的「权限设置」可进入权限页；卡片改为左对齐；三项授权显示「已允许」、主按钮「进入相机」 |
| V7 | 包体符号校验 | 三个 HAP 的 `modules.abc` 均含 `usableAltitude`/`movedMeters`/`receiveLocation`/`openAppSettings`/`systemPermissionsRequested`，`isNewer` 已消失 |
| V8 | 资源校验 | `camera_shutter.wav`（11968 B，0.135 s，mono PCM16 44.1 kHz）在三个 HAP 中与源文件字节一致；真机运行无 `SITECAM_SHUTTER_*` 错误 |
| V9 | 交付物 | 桌面 `SiteCam-0.3.2-HarmonyOS-AppGallery-Signed.hap/.app` 与 `sitecam-out/` 同哈希（`d4ec1436…3063`） |

## 2.3 本轮新发现（已修 / 待办）

- **F5（已修，重要）** 设置页的「权限设置」入口此前位于死代码 `settingsPage()` 中，实际生效的设置页没有任何权限入口；相机一旦正常打开，应用内就再也进不去权限页。这正是本次被驳回流程的脆弱点，现已补上入口。
- **F1（已修）** 权限卡片标题与状态行因 ArkUI `Column` 默认居中而被居中显示，与左对齐的描述文字不一致（Android 版为左对齐）。
- **F6（待办，低危）** `navigate()` 中 `if(page==='projects'||page==='settings')this.pageOrigin=this.page==='camera'?'camera':this.pageOrigin;` 在早返回之后 `this.page===page` 恒为 false，因此该赋值是恒等操作，`pageOrigin` 实际永不更新。当前靠 `permissionsReturn`/`galleryOrigin` 兜住，建议整体清理。
- **F4（待办，低危）** `shouldShowAddressRefresh()` 在无定位权限时直接返回 false，导致 `addressRefreshLabel()` 的「请开定位」分支不可达。
- **版本号待确认** 华为截图中的被拒包为 `0.3.2 (15)`。本次仍以相同版本号重打包（沿用仓库既有约定），若 AGC 要求驳回后提升 versionCode，需要改为 0.3.3 (16) 后重新签名打包。
- **真机未覆盖项（已补齐，见 2.4）** 早前该项无法在平板上复现，因为该机三项权限早已授权；经用户同意后卸载重装，已完整走通被驳回的那条链路的**反向证明**。
- **快门声** 已改为 CC0 真实单反快门录音；音色是否符合预期需人工试听（我可验证格式、包体、加载与播放无报错，但无法代替听感）。

## 2.4 华为驳回场景的真机反向证明（2026-09-22 18:19，HUAWEI MLR-AL10）

卸载并重装 debug 签名包，构造「应用从未申请过任何权限」的干净状态，逐步取证（截图见 `verification/appgallery-3.1-evidence/`）：

| 步骤 | 操作 | 实测结果 | 截图 |
| --- | --- | --- | --- |
| 1 | 全新安装后启动 | 首次指引第 1/4 页正常显示 | `fresh-1-onboarding.jpeg` |
| 2 | 点「跳过」进入权限页 | 主按钮为**「开启所需权限」**；三张卡片状态均为**「未开启」**；**没有任何「需前往系统设置」** | `fresh-2-permission-page.jpeg` |
| 3 | 点「开启所需权限」 | 系统**依次弹出 3 个权限弹窗**（相机 1/3 → 位置 2/3 → 麦克风 3/3），弹窗内正确显示 `module.json5` 声明的用途文案 | `fresh-3-system-dialog.jpeg` |
| 4 | 依次允许 | 页面即时刷新为三项「已允许」、主按钮「进入相机」、第三按钮「稍后补充，先管理资料」 | `fresh-4-after-grant.jpeg` |
| 5 | 读取持久化 | prefs 出现 `systemPermissionsRequested: true`、`permissionHandled: true` | — |
| 6 | 系统侧核对 | `atm dump -t -b com.sitecam.app` 列出 CAMERA / LOCATION / APPROXIMATELY_LOCATION / MICROPHONE，`grantStatus=0`（已授权） | — |

对照华为的驳回截图（应用内「去系统设置开启」＋系统设置页无开关），**同一条路径现在会先弹出系统权限询问**，权限节点随之注册，因此无法再出现「跳过去却没有任何开关」的情况。第 2 步同时证明：即便用户始终不点权限入口，权限页也不会再谎称「需前往系统设置」。

## 2.5 权限页/指引页多端适配修复（用户反馈「设置里权限界面显示不全」）

**根因**：权限页把主按钮、次要按钮、第三按钮和底部说明全部放在 `Scroll` **内部**，而滚动条被关闭（`BarState.Off`）。按 14 vp 字号估算，手机竖屏内容总高约 790 vp，而可用高度通常只有 600–700 vp，于是主按钮与底部说明掉到屏幕外且没有任何滚动提示 —— 平板横屏在更大字号下同样被截断（早前实机截图里底部说明确实不可见）。

**修复**：

| 组件 | 改动 |
| --- | --- |
| `PermissionGuidePageView` | 拆为「固定页头 + 可滚动说明区 + **固定在底部的操作区**」；操作区含主/次/第三按钮与免责说明，任何屏幕尺寸下主按钮都不会离开视野；内容区 `scrollBar(BarState.Auto)` 给出可滚动提示；新增 `viewportHeight` 入参，`< 560 vp` 时进入紧凑模式（图标 40、标题 16/20、卡片内边距 12、按钮 44/40/40、底部间距收紧）；内容与操作区均 `constraintSize({maxWidth:720})` 适配平板/阔屏 |
| `OnboardingPageView` | 原本**没有 Scroll**，短屏（手机横屏、悬停、小折叠内屏）会直接裁掉按钮；改为可滚动 + 同一套紧凑规则 + `maxWidth 560` 限宽 |
| `Index.ets` | 两处 Root 传入 `viewportHeight:this.windowHeight`（已扣安全区的可用高度） |

小折叠**外屏**仍沿用既有设计：非相机页显示「展开手机继续操作」提示（`coverCompanionPage`）。这是仓库既定策略，但注意此时用户无法在外屏进入权限页；如需要，可在相机页把「打开权限设置」直接改为触发权限询问（不必先跳页面）。



---

# 第三部分 · 多端规范修复（2026-09-22 第二轮）

## 3.1 横屏/竖屏「左侧摄像头区域与界面微妙色差」

**根因（像素级取证）**：共享根容器在 `Index.ets` 用 `$r('app.color.page_bg')`(#121212) 铺底，而相机页的工具栏、预览容器、控制区都是 `Color.Black`(#000000)。根容器的背景覆盖其 padding 区域，而 `padding` 正是 `environment.left/top` 等**避让区（挖孔/圆角）**。于是避让区那一条永远是 #121212，紧邻的相机区域是 #000000 —— 差值 18/255，肉眼即"微妙色差"。

实测（平板 HUAWEI MLR-AL10，2560×1600 / 1600×2560）：

| 方向 | 修复前 | 修复后 |
| --- | --- | --- |
| 横屏 | 中间行 `x 0–80 = (18,18,18)` 然后是黑 | `x 0–324 = (0,0,0)`，色带消失 |
| 竖屏 | 中列 `y 0–80 = (18,18,18)`，`80–216` 黑 | `y 0–216 = (0,0,0)`，色带消失 |

**修复**：`Index.ets` 根容器背景按页面切换 —— `this.page==='camera'?Color.Black:$r('app.color.page_bg')`。相机页所有留白（避让条、预览四周、工具栏间隙）从此与预览/工具栏同为纯黑，非相机页不受影响。截图：`appgallery-3.1-evidence/` 之外的 `before-portrait.jpeg` / `after-portrait.jpeg` / `app-launch.jpeg` / `after-landscape.jpeg`（位于 `/Users/ninja/Documents/sitecam-device-evidence/`）。

## 3.2 系统大字体（规范：不缩字、不截断）

**根因**：权限页/指引页按钮用固定 `height(44–50)` 配 16sp 文字，ArkUI 会按系统字号放大文字但盒子不变 → 裁字；权限页的按钮原本还在滚动区内，进一步放大问题。

**修复**：按钮改为 `Button(){Text(...)}` + `constraintSize({minHeight:...})`（对应 Compose 的 `heightIn(min=)`），随文字自然增高；不给按钮加 `maxFontScale` 上限，让字号与正文一致放大。相机页的无权限按钮同样改为 `minHeight`。权限页/指引页增加 `compact()`（可用高度 < 560vp）紧凑规则，并各自加了 `maxWidth 720 / 560` 限宽。

**实测**：把系统字号设为最大（设置 → 显示和亮度 → 字体大小和界面缩放 → 特大，应用读到 `SITECAM_FONT_CONFIG fontSizeScale=1.45`），权限页三张卡片、主按钮「进入相机」、第三按钮与底部说明全部可见且无裁字（`bigfont-permission.jpeg`、`bigfont-permission-v2.jpeg`）。该平板最大只给到 1.45 倍，更高倍率（应用上限 maxScale=3.2）由"不设固定高度"的结构保证，未实机覆盖。

## 3.3 其余规范修复

| 项 | 修复 |
| --- | --- |
| 重复组件 id | `FoldCoverControls` 的镜像控件改为 `-cover` 后缀；`Index` 内横屏 dock 变体改为 `-dock` 后缀。**构建期 duplicate id 警告由 4 条降为 0**；`scripts/ui_cover_smoke.py`、`scripts/ui_smoke.py` 已同步（后者对 `-dock` 自动回退） |
| 死代码 | 删除 `Index.ets` 中 4 个零引用 `@Builder`：`projectsPage` / `galleryPage` / `detailPage` / `watermarkPage`（共 28 行），加上此前的 `guidePage` / `settingsPage` |
| 外屏权限死路 | 相机页无相机权限时，外屏（`coverScreen`）直接调用 `permissions(true)` 拉系统弹窗，不再跳到只显示「展开手机继续操作」的权限页 |
| 地址刷新入口 | `shouldShowAddressRefresh()` 不再因缺定位权限直接隐藏按钮；缺权限时按钮显示「请开定位」并跳权限页（外屏直接请求），原先不可达的文案分支变成有效入口 |
| 系统返回语义 | `navigate()` 中原本恒等的 `pageOrigin` 赋值改为记录来源页；权限页系统返回实测从「回到工程页」变为「回到来源页」（实测：设置 → 权限设置 → 返回 → 回到设置页） |
| 资源冲突 | 删除 `entry` 中重复声明的 `app_name`（构建警告消失）；删除唯一未被引用的 `ic_chevron_down.svg` |
| 相机页 1 Hz 时钟 | 进入后台时清除 1 秒 `setInterval`（原先仅在前台页面切换时清理）。**残留**：每秒仍会重算快照并触发相机页重组；要真正收敛需把时钟从页面级 `@State snapshot` 拆出，而 `watermarkBound()` 的命中区域依赖父组件持有的快照，拆分需先解开该耦合，本轮未做 |

## 3.4 回归

- ASCII 路径下主模块 / ohosTest / release `assembleApp` 三次 `BUILD SUCCESSFUL`；`app_name` 冲突与 duplicate id 警告归零。
- 平板真机运行时套件 **54/54 通过**（改动后复跑），结果见 `device-results-0.3.2-tablet.json`。
- 权限闭环、色差、大字体、返回语义均在真机复测通过。
