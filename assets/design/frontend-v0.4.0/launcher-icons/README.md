# v0.4.0 桌面图标资源

设计基准：[12-quality-icons-about.png](../12-quality-icons-about.png)。保留该图不变；实际 A–D 图标按图中的造型与颜色提取重制。所有图像均使用内置 image_gen，未使用 CLI/API fallback。

Android 使用同一主图作为设置预览，由自适应图标资源负责安全边距；HarmonyOS 的 AppScope、entry 和深色限定资源保持同一图形。平台的系统图标蒙版由各自桌面处理。

## 生成提示词

### A

Use case: precise-object-edit. Asset type: one production launcher icon bitmap. Extract and faithfully upscale ONLY the FIRST row icon A in the CENTER panel: glossy blue circular camera lens, four WHITE viewfinder corner brackets, exactly THREE short vertical engineering-yellow bars centered below the lens. Deep navy background #10294B. Preserve the same lens shape, circular blue highlight, clean polished rendering and corner layout. Do not add a camera body or blueprint grid. The source screenshot is a fixed approved design; do NOT redesign it or create a new style. Output one standalone square 1024x1024 icon, completely opaque, flat solid background filling the whole canvas edge to edge, square outer corners (the OS applies its own mask), no rounded tile silhouette, no outer drop shadow. Center the entire foreground motif with its full bounds within the middle 76 percent of width and height; retain source proportions. No surrounding interface, labels, letters, text, selection controls or border. Match approved icon color, shape and rendering as closely as possible.

### B

Use case: precise-object-edit. Asset type: one production launcher icon bitmap. Extract and faithfully upscale ONLY the SECOND row icon B in the CENTER panel: small golden-yellow camera body, dark glossy round lens with blue highlight, small dark indicator on camera upper-right, top hump. Dark charcoal background #202323. Preserve the simple yellow body silhouette and exactly the same lens style. The source screenshot is a fixed approved design; do NOT redesign it or create a new style. Output one standalone square 1024x1024 icon, completely opaque, flat solid background filling the whole canvas edge to edge, square outer corners (the OS applies its own mask), no rounded tile silhouette, no outer drop shadow. Center the entire foreground motif with its full bounds within the middle 76 percent of width and height; retain source proportions. No surrounding interface, labels, letters, text, selection controls or border. Match approved icon color, shape and rendering as closely as possible.

### C

Use case: precise-object-edit. Asset type: one production launcher icon bitmap. Extract and faithfully upscale ONLY the THIRD row icon C in the CENTER panel: WHITE outline of a camera, circular white lens outline, yellow location pin overlapping the upper-right corner. Deep teal background #105567. Preserve thin clean white lines, correct pin silhouette and relative positions. The source screenshot is a fixed approved design; do NOT redesign it or create a new style. Output one standalone square 1024x1024 icon, completely opaque, flat solid background filling the whole canvas edge to edge, square outer corners (the OS applies its own mask), no rounded tile silhouette, no outer drop shadow. Center the entire foreground motif with its full bounds within the middle 76 percent of width and height; retain source proportions. No surrounding interface, labels, letters, text, selection controls or border. Match approved icon color, shape and rendering as closely as possible.

### D

Use case: precise-object-edit. Asset type: one production launcher icon bitmap. Extract and faithfully upscale ONLY the FOURTH row icon D in the CENTER panel: glossy navy compact camera with blue circular lens and a bright yellow construction hardhat on top. Deep navy background #112C49. Preserve the specific camera/helmet identity, frontal view, lens highlight, helmet ribs and proportions seen in the source. The larger about-page camera on right also shows this same subject. The source screenshot is a fixed approved design; do NOT redesign it or create a new style. Output one standalone square 1024x1024 icon, completely opaque, flat solid background filling the whole canvas edge to edge, square outer corners (the OS applies its own mask), no rounded tile silhouette, no outer drop shadow. Center the entire foreground motif with its full bounds within the middle 76 percent of width and height; retain source proportions. No surrounding interface, labels, letters, text, selection controls or border. Match approved icon color, shape and rendering as closely as possible.
