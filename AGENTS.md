# 全局执行边界

- 默认只执行用户明确提出且与当前任务直接相关的操作；不要为了“防御性编程”、预防性审计或习惯性流程自行增加扫描、备份、验证、安装、修改或交付步骤。
- 遇到会实质改变范围、数据口径、外部状态或结果含义的歧义，先简要说明问题并询问用户；不要自行扩大任务或替用户作关键选择。
- 默认不计算 SHA-256、MD5、文件哈希或校验和。绝大多数工作不需要任何哈希；不得把它作为例行步骤、审计步骤、文件处理默认动作或交付前置检查。
- 仅在用户明确要求，或当前任务明确涉及完整性校验、去重、签名/安全验证、制品发布校验或缓存键时，才计算或依赖哈希；此时应说明其必要性，并只对相关对象使用。

# 成果纯净

- 此规则适用于任何成果。除非用户明确要求，禁止在成果本体上擅自加入任何说明、说明性小字、免责声明、风险提示或其他防御性言辞。
- 前端界面禁止加入任何说明性小字，除非用户明确要求；不得把实现细节、交付状态或防御性说明放进用户界面。
- 代码可以保留必要注释和说明，但不得将这些说明渲染到前端或附加到其他成果本体。
- 交付前应确认成果纯净，移除未经用户要求的附加说明、小字及防御性言辞；这项确认仅针对成果纯净要求，不得据此扩展无关检查或验收流程。

--- project-doc ---

# 发布文案

- 面向用户的更新说明只写“更新了什么”：新增功能、修复的问题，以及用户能感知到的行为或体验变化。
- 不写“怎么做的”：不包含实现方法、技术架构、SDK 或接口接入细节、内部文件或工具，以及构建、签名、测试、验证和部署过程。
- 本规则适用于 GitHub Release、应用商店的版本更新说明、官网更新记录和首页更新摘要。默认沿用简洁的用户视角，无需每次重复询问。
- 下载和安装必需的说明（例如鸿蒙未签名包需要用户自行签名）放在下载说明中；权限、隐私声明及商店审核必填信息按实际情况准确填写，不混入更新说明。


## v0.4.0 前端实现契约

- 本版本前端工作须遵循 [`assets/design/frontend-v0.4.0/DESIGN_CONTRACT.md`](assets/design/frontend-v0.4.0/DESIGN_CONTRACT.md) 与该目录的 `manifest.md`。本引用不替代或删改上方发布文案规则。

## 设计图执行与界面变更

- 已有界面、功能指引及同类前端呈现须严格按照此前已采用的设计图实现，包括布局、控件位置、留白、插图和视觉样式；除非用户明确提出异议或新要求，不得自行简化、替换或偏离。
- 用户最新明确意见优先。用户此前或后续提出的问题提案若需要修改 UI，且修改后的界面与当前采用图不一致，须先生成对应的新设计图，再依据新图实现，并同步更新 `manifest.md` 与相关设计契约。
- 功能指引采用新版手机四步图 `15-first-launch-steps-v2.png`；平板竖屏采用 `31-tablet-portrait-help-first-launch.png` 的对应布局，不以旧图或简单图标替代新版插图。

## 拍摄控件位置与握持空间

- 握持留白要求仅适用于通常的长条形普通手机；平板与折叠屏继续沿用各自已确认的布局，不统一套用手机的额外握持留白，也不因此改动其布局。
- Android 与 HarmonyOS 普通长条手机竖屏下，快门应在底部操作区居中并适当抬高，下方保留明确的握持留白，不得贴近屏幕底边；仅避开系统导航栏或手势区不算满足此要求。
- 普通长条手机竖屏相册位于快门左侧、切换镜头位于右侧，变焦与拍照/录像模式位于快门上方；操作区的额外空间不能全部分配到快门上方而挤掉底部留白。
- 普通长条手机横屏下，右侧拍摄操作区也须保留握持空间，快门在操作区内居中，不得贴近屏幕右边缘；不得以负偏移挤压变焦或模式控件。
- 后续布局修改须对照当前采用的设计图检查上述位置与留白；平板、折叠屏内外屏及小折叠外屏均沿用其各自确认的布局。

## 本机签名用途与留存索引

以下为本项目已留存的签名身份与非秘密路径，2026-10-04 已核对配置及材料存在。后续直接复用，不重新生成身份；签名读取或构建失败不能当作原签名丢失。

| 签名身份 | 用途 | 本机配置与留存位置 |
| --- | --- | --- |
| Android Release，alias `sitecam` | 正式版 APK、官网/GitHub 发布及正式版覆盖升级 | 当前密钥库 `/Users/ninja/Documents/工程水印相机/sitecam-release.jks`；配置 `/Users/ninja/Documents/工程水印相机/keystore.properties`；固定留存目录 `/Users/ninja/.config/sitecam/signing/android-release/original-2026-09-27/`，内含同名密钥库和配置 |
| Android Debug，alias `AndroidDebugKey` | 调试版 APK，保持既有调试版的升级身份 | 当前密钥库 `/Users/ninja/.android/debug.keystore`；固定留存 `/Users/ninja/.config/sitecam/signing/android-debug/original-2026-09-27/debug.keystore` |
| HarmonyOS Debug，配置名 `sitecam-debug`，alias `debugKey` | 已登记设备的真机调试 HAP；连接的 MLR-AL10 平板使用此身份覆盖安装 | 配置 `/Users/ninja/.ohos/sitecam-debug/signing-config.json`；固定留存目录 `/Users/ninja/.ohos/sitecam-debug/original-2026-09-27/`，包含 `SiteCam-Original-Debug.p12`、`SiteCam-Original-Debug.cer`、`SiteCam-Original-Debug.p7b` 和原构建配置 |
| HarmonyOS AppGallery Release，配置名 `sitecam-release`，alias `debugKey` | 华为应用市场发布用签名 HAP/APP；Profile 为 `release / app_gallery`，不用于 USB 真机侧载 | 配置 `/Users/ninja/.ohos/sitecam-release/signing-config.json`；证书 `/Users/ninja/.ohos/sitecam-release/SiteCam-Release.cer`；Profile `/Users/ninja/.ohos/sitecam-release/SiteCam-AppGallery-Release.p7b`；私钥库以配置中的原 `storeFile` 路径为准 |

- Android Release 的 `storeFile` 相对项目根目录解析，沿用现有 `keystore.properties`；Debug 沿用现有 Gradle 调试身份，不能与 Release 互换。
- 鸿蒙 Debug 优先用 `harmonyos/scripts/build-debug-signed.sh` 读取固定配置；商店包用 `harmonyos/scripts/build-store.sh` 读取独立商店配置。Debug Profile 有设备白名单，2026-10-04 已确认连接的 MLR-AL10 在原白名单内；不得将它当作通用安装包。
- 鸿蒙商店配置当前引用的原私钥库为 `/Users/ninja/.ohos/config/default_SiteCamHarmonyBuild_MsYr_l3K-TCM8ExUj_ML_q6fcCqocKbTavev5mWwpN4=.p12`；原私钥同时留存在上述 Debug 固定目录中。调试证书/Profile 与商店证书/Profile 各有用途，不互换。
- 所有原件与固定留存材料都须保留，不得覆盖、删除、重建密钥或以新签名替代原身份；构建缓存不是签名材料的唯一保存位置。
- 不把密钥库、私钥、口令配置或凭据上传仓库、公开发布或写入工具输出；本文件仅保存用途、路径和别名。
