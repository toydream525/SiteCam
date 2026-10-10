# 本轮审查采用的官方接口依据

核对日期：2026-10-03。实现方案以项目当前编译 SDK 和最低支持版本为准；文档中新接口的可用性须单独核对。

- Android：`compileSdk = 36`、`targetSdk = 36`、`minSdk = 26`；已依赖 `androidx.window:window:1.5.1`，见 `app/build.gradle.kts:30–35,125`。
- HarmonyOS：编译与目标 SDK 为 `26.0.0`，兼容基线为 `5.0.0(12)`，见 `harmonyos/build-profile.json5:7–9`。编译可用不等于 API 12 设备运行可用。

| 范围 | 官方依据 | 用于本轮审查的规则 |
|---|---|---|
| Android 自适应布局 | [Adaptive apps](https://developer.android.com/develop/adaptive-apps/guides/get-started-with-adaptive-apps) | 按应用当前窗口空间决定布局；折叠、旋转和窗口变化时保留页面与编辑状态。 |
| Android 折叠姿态 | [Make your app fold aware](https://developer.android.google.cn/develop/adaptive-apps/guides/foldables/make-your-app-fold-aware?hl=en) | 使用官方折叠信息表达姿态及分隔区；可交互内容避开铰链。 |
| Android 安全区 | [Window insets](https://developer.android.com/develop/ui/compose/system/insets) | 区分绘制、系统手势和键盘区域；交互层应用实际四边安全区。 |
| Android 挖孔 | [Display cutouts](https://developer.android.com/develop/ui/views/layout/display-cutout?hl=en) | 使用系统给出的安全边距与挖孔边界，不能用固定顶部留白覆盖全部方向。 |
| Android 桌面图标 | [Adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive) | 前景/背景分别提供；核心造型位于系统蒙版安全区，单色图保持同一视觉身份。 |
| Android 对焦坐标 | [PreviewView](https://developer.android.com/reference/androidx/camera/view/PreviewView)、[CameraX 配置](https://developer.android.com/media/camera/camerax/configuration) | PreviewView 的 meteringPointFactory 接受其局部坐标，并随预览缩放方式及布局变化更新映射；预览就绪后再创建对焦请求。 |
| HarmonyOS 多窗口 | [Adapting the App Layout to Multi-Window](https://developer.huawei.com/consumer/en/doc/harmonyos-guides-V14/multi-window-layout-adapt-V14) | 依据当前窗口尺寸及变化事件更新布局。 |
| HarmonyOS 动态避让 | [沉浸式窗口](https://developer.huawei.com/consumer/cn/doc/HarmonyOS-Guides/immersive-window-feature) | 读取并监听系统、挖孔、导航区域等四边避让信息；窗口旋转和折叠变化后更新。 |
| HarmonyOS 状态传递 | [状态管理术语](https://developer.huawei.com/consumer/cn/doc/doccenter-capabilities/arkts-state-management-glossary) | 按值传递不保留状态依赖；需要动态更新的 UI 必须使用合适的状态传递方式。 |
| HarmonyOS UI 构建 | [@Builder](https://developer.huawei.com/consumer/cn/doc/doccenter-references/api/ts-universal-builder-dynamic) | 核对设置分类及复用组件的参数传递与刷新。 |
| HarmonyOS 对焦 | [自定义相机预览](https://developer.huawei.com/consumer/cn/doc/best-practices/bpta-custom-camera-preview) | 区分触摸坐标、预览裁剪和相机坐标；核对对焦模式、状态反馈和焦点显示。文档公式须结合本项目实际预览旋转方式使用。 |

项目内的尺寸、导航和已有业务约束见 `assets/design/frontend-v0.4.0/DESIGN_CONTRACT.md` 及本轮用户确认的更新要求。
