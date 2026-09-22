# 鸿蒙默认 D 图标：深浅色背景

使用内置 image_gen 编辑原图，并规范导出为华为官方标准的 1024×1024 资源尺寸（符合华为应用市场单层图规范：1024px*1024px，方角，无透明底）。原始透明图保留在 assets/design/icon_d_generated.png。

light.png / dark.png 为生成原图；工程 AppScope 和 entry 的 base/media/app_icon.png、dark/media/app_icon.png 分别对应浅色与深色资源（1024×1024）。采用同名资源限定目录，不改动其他三个候选图标。应用内界面仍维持既有深色模式，因此应用内图标预览使用深色资源；桌面资源选择由系统负责，实际切换刷新行为待设备验证。

## 浅色提示词

Edit target: existing SiteCam app icon. Preserve EXACT same yellow hardhat sitting on navy camera, lens, perspective, proportions, materials, colors, all fine details. No redesign. Enlarge whole existing subject uniformly about 15 percent relative to canvas (existing solid subject ~52% canvas width -> new ~60% width), precisely centered. Replace transparent background with perfectly solid warm off-white #F4F5F7 covering the ENTIRE square edge to edge. No rounded corners, no border, no lettering, no additional elements. Square 1024x1024 app icon. Background must be opaque. This is precise-object-edit.

## 深色提示词

precise-object-edit. Input is final light-mode SiteCam application icon. Change ONLY the flat offwhite background to completely opaque solid dark charcoal #252A32 edge to edge. Keep the camera+yellow hardhat subject EXACTLY unchanged: identical pixel positioning, size, silhouette, colors, geometry, lighting and details. Do not enlarge, recenter, redraw or redesign anything. Square icon, no rounded corners, no text, no framing. Dark-mode variant with the exact same icon foreground.

