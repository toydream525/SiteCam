# v0.4.0 补图与定向修订提示词

## 2026-10-10 最终采用：34-landscape-harmony-camera-reference-v3

使用内置 image_gen 编辑。用户最终补正采用来源 `exec-fda0cbd2-c856-43f5-b107-aa2824c49c1d.png`，保存为 `34-landscape-harmony-camera-reference-v3.png`。未重新生成，未采用其后缩距草稿。

```text
Use case: precise-object-edit.
Edit the supplied SiteCam landscape camera design, preserving its photo, 4:3 viewfinder, narrow left toolbar, project dropdown and construction watermark. Make the following three coordinated corrections, using the current 1872×841 canvas geometry as reference.

1. ALIGN THE UPPER AND LOWER PAIRS SYMMETRICALLY. Keep “拍照 / 录像” in a horizontal row at the TOP of the right black operation region. Keep “水印 / 记问题” with their icons at the BOTTOM. The top “拍照” must be exactly above the bottom 水印 icon and label, on the SAME vertical centerline. The top “录像” must be exactly above the bottom 记问题 icon and label, on the SAME vertical centerline. Both two-item rows have equal horizontal spacing, and their shared midpoint is exactly the shutter's vertical axis. 拍照 is yellow with its existing small yellow selection dot underneath, 录像 white. No giant pills.

2. MOVE ZOOM ONTO THE PHOTOGRAPH WITH TRANSPARENCY. Remove the current 0.5 and 1× controls from the black side region, remove their old opaque yellow disk, and remove the orphan thin vertical divider in the black region. Place the existing two zoom choices in a compact VERTICAL floating stack just INSIDE the RIGHT EDGE of the actual construction photo, centered around x1210, y340 and y420. Their touch areas and backgrounds are transparent: the construction image remains plainly visible beneath and around them. “0.5” is white and “1×” is yellow, with at most a very subtle translucent dark backing or thin selected ring for legibility, NEVER an opaque yellow circle or a solid black toolbar. Use modest 28-30px text. Do not move the zoom outside the photo, and do not place it over the bottom-left watermark. Only these existing two zoom values.

3. NARROW THE DISTANCE FROM ZOOM TO SHUTTER. Compact and shift the whole right shooting group towards the preview edge. On the current canvas, put the shutter's vertical centerline around x1470, y400 instead of x1660, so the floating zoom to shutter center gap is approximately 260px rather than the current broad gap. Keep the white shutter at its current diameter, do not enlarge it. The gallery thumbnail stays ABOVE the shutter and the camera-flip icon BELOW the shutter on this SAME x1470 axis, preserving their original vertical positions. Align the paired upper and lower controls symmetrically around that axis: first column around x1380, second column around x1560. Keep all of them fully inside the right black region (photo ends near x1270). This produces a compact, coherent shooting group next to the viewfinder and preserves a clear intentional black grip margin on the far right. No negative overlap, no shutter on the photograph, no controls touching the screen edge.

Preserve exactly the current image dimensions, photo viewfinder rectangle, entire construction scene, upper-left 滨江路改造 dropdown, three left utility icons, watermark text and styling, white native line icons, engineering-yellow accent and black chrome. All Chinese labels upright and clear. Do NOT redraw the entire scene or add camera features, captions, measurements, helper text or footnotes. The only changes are the symmetric upper/lower control alignment, transparent photo-overlaid zoom, and compacted right shooting group.
```

## 2026-10-05 已提交问题反馈补图

本轮均使用内置 image_gen，未使用 CLI；本节提示词各自完整，不额外叠加旧版通用倍率/方向面板描述。最终图已在本地逐张查看，首稿方向菜单遮挡右栏的问题通过一次定向编辑修正。原图 15-v2 的四步插图使用原生源矩形复用，不重新生成，坐标见 onboarding-artwork/regions.json。

### 32-camera-feedback-layouts-v1

生成；来源 `/Users/ninja/.codex/generated_images/01a107e8-3e63-75e3-bf49-8c1095ebca13/exec-0142d08e-9a5e-41c1-803e-f09480c65566.png`。参考图 01-v2 / 03-v2。首稿仅留作定向修订来源，当前采用 32-v2。

```text
Use case: ui-mockup.
Create a precise new SiteCam camera UI reference sheet, 2304 by 1536 landscape canvas. Use the two reference images for the same engineering camera visual style and construction photography, while applying the explicitly requested layout corrections below. Only app screens, no annotations, panel labels, descriptive fine print, dimensions, arrows outside the UI, or page title.
Composition: ONE tall ordinary PHONE portrait screen at left (about 620 by 1390); TWO ordinary PHONE landscape screens stacked at right (each about 1540 by 710). Thin neutral gaps separate screens. Flat front-on screenshots, no perspective, no bulky device frames. Deep black camera surfaces; real construction site excavator/scaffolding photos; compact native white controls and restrained engineering yellow.
Every camera header: compact 滨江路改造 immediately followed by native down chevron, with natural content width, NEVER a full-width project pill. Three independent trailing icons: flash bolt, tall phone outline WITH curved rotation arrows, gear. Avoid text characters masquerading as icons.
LEFT portrait: below compact safe header, true portrait 3:4 photo viewfinder, no stretching. Modest existing yellow-stripe watermark fits its text at bottom-left of PHOTO: 工程名称：滨江路改造 / 拍摄时间：2026-10-05 09:41 / 现场位置：北段施工区. Address refresh is a separate small refresh icon near upper-left of photo, below header, not overlapping header controls. Below photo, compact zoom with only 0.5 and 1× (1× selected yellow). Below zoom compact modes 拍照 / 录像. Below modes a single balanced capture row with album thumbnail on LEFT, 80dp-style white ring shutter CENTER, lens-switch circular arrows on RIGHT. Smaller 水印 and 记问题 tools sit above or beside this row without crowding. The shutter is visibly raised; BELOW capture row leave an obvious empty BLACK handgrip band of about 70dp, in addition to bottom gesture safe area. No white gesture line. No control at bottom edge.
TOP RIGHT landscape: preserve a true LANDSCAPE 4:3 construction preview fitted at LEFT with black letterboxing when necessary, never stretched to fill widescreen. Compact header is a distinct black row above content, project at far left, flash/rotation/gear in separate slots toward right; a location refresh button is separately inside photo near upper-left, clearly below header. To right of photo a compact BLACK capture rail. All ZOOM controls must be in this rail, completely OUTSIDE photo/watermark, only 0.5 and 1× selected. Compact photo/video modes at rail top; zoom below them; thumbnail / small shutter / lens switch in a balanced central row or tight nonoverlapping grouping; 水印 and 记问题 below. Beyond the capture rail on FAR RIGHT leave a visibly empty BLACK handgrip band, around 60dp. Shutter centers in usable rail, does not touch right edge. No giant buttons, no overlapping text. PHOTO bottom-left watermark remains fully visible and cannot intersect zoom.
BOTTOM RIGHT: same landscape arrangement, with direction button selected and a SMALL native dark anchored popup directly beneath/beside that rotation icon. Popup is exactly four choices 自动 / 竖屏 / 左横屏 / 右横屏, a compact 2 by 2 menu with clear device-rotation icons; 自动 has subtle yellow selection. Menu is roughly 230 by 130dp, not a large bottom sheet and not screen-wide. Popup may overlap a little upper photo but must not cover or collide with flash, gear, project, refresh, zoom, shutter or other controls. Keep underlying camera layout and the separate right handgrip band.
All controls remain ordinary native size with touch separation. Include no invented 2×/3×/5× lens buttons. No decorative captions, no tiny explanatory text. This sheet is camera UI only.
```

### 32-camera-feedback-layouts-v2

内置 image_gen 定向编辑；目标 `32-camera-feedback-layouts-v1.png`。最终保存 `32-camera-feedback-layouts-v2.png`；来源 `/Users/ninja/.codex/generated_images/01a107e8-3e63-75e3-bf49-8c1095ebca13/exec-cda42e90-684e-4980-9750-1d4c3b154a6e.png`。

```text
Use case: precise-object-edit. Edit the supplied 1536x1024 design sheet. Change ONLY the small direction menu in the LOWER RIGHT landscape screen, preserving the left portrait screen and upper right landscape screen exactly and preserving all lower camera photo/header/controls geometry.
The current menu around x1084..1290, y577..745 incorrectly covers part of mode and zoom controls. Move the complete popup LEFT, to approximately x923..1155, y578..747. Its right edge must stay LEFT of the vertical rail boundary at x1170. The popup now lies entirely over the upper-right portion of the construction photo plus adjacent black photo inset, never the capture rail. Place its small anchor tip near its upper-right corner pointing toward the rotation icon centered at x1165,y551. Keep exactly the compact 2x2 choices 自动 / 竖屏 / 左横屏 / 右横屏, same native device icons and yellow 自动 selection.
Restore the underlying rail fully to match the upper right screen: complete yellow 拍照 chip and 录像 text at y629, complete 0.5 and yellow 1× zoom pill at y706. No menu pixel can overlap these rail controls. Keep shutter/album/lens, watermark/issue, far-right empty handgrip band, left compact engineering name, flash/rotation/gear and separate refresh unchanged. No other edits, no captions, no new text.
```

### 33-android-appearance-style-footer

内置 image_gen 生成；参考图 11 / 10。最终保存 `33-android-appearance-style-footer.png`；来源 `/Users/ninja/.codex/generated_images/01a107e8-3e63-75e3-bf49-8c1095ebca13/exec-18f53f45-f885-40ef-ab1a-3d4adcb6e1b7.png`。

```text
Use case: ui-mockup.
Create a focused new SiteCam design sheet using the reference settings and watermark styles. Canvas approximately 1536 by 1536, exactly TWO normal portrait phone app screens side by side, simple front-on screenshots with a narrow neutral divider and no captions outside apps.
LEFT: Android light native 设置 page scrolled to its 外观 group. Keep existing compact visual language, white cards and very light cool gray backdrop, restrained engineering yellow #FFC400. Header back chevron and 设置. Upper visible group 水印 with existing 水印样式与字段 row. Then 权限与隐私 with 权限设置 and 隐私政策. Then 外观 with desktop icon row 桌面图标 value 工程现场; camera controls row 相机控件外观 opens an in-page compact choice group with exactly 普通 and 透明, native radio buttons, 普通 selected yellow. No 毛玻璃 option on this Android screen. Lower group 帮助与关于 with 使用指南 and 关于 SiteCam. Body scrolls in available area, all regular native text and icons, no helper paragraphs or explanatory fine print. Preserve natural compact spacing and existing settings semantics.
RIGHT: light 选择水印样式 page, back chevron and title in fixed header. Use the EIGHT existing colored mini watermarks and same names as reference: 经典工程水印, 极简水印, 工程信息板, 蓝白工程记录, 施工表格, 巡检记录, 验收记录, 大字时间地点. Arrange TWO columns by FOUR rows in a compact SCROLLABLE grid. Regular compact style card labels, varied previews preserve the existing eight style identities. First selected card thin yellow border plus yellow check. A subtle native scrollbar on grid right. Immediately below grid, fixed bottom action row: gray text 取消 at LEFT and yellow rounded 使用此样式 at RIGHT. Entire button with its corners must be visible, comfortably above system bottom gesture inset. No large unused gap between grid and footer. Do NOT squash or clip buttons. Bottom native gesture line may appear within its own reserved safe area under the action row.
Use realistic tiny engineering watermark previews, but no invented style names or footer options. No captions, notes, fine print, warnings, dimensions, or implementation explanations anywhere. Keep this sheet specific to Android appearance options and safe watermark selection footer; no new page features.
```


## 既有图 01–31 的提示词归档

既有几何修订采用 27-v2 与 29-v3；对应提示词附在文末，已知像素比例偏差见 ADAPTIVE_COVERAGE.md。2026-10-05 新增图 32–33 的完整提示词见本文件开头。

桌面图标 A/B/C/D 的高清资源及独立生成提示词见 [launcher-icons/README.md](launcher-icons/README.md)；原设计图 12 保持不变，实际桌面图标资源按该方案同步。

本轮使用内置 image_gen；每张图单独调用。新图使用下方通用规范加各图提示词，编辑图使用对应定向提示词及指定目标图；采用版本以 manifest.md 为准。既有稿 01-v2、03-v2/22-v2、05-v2、06-v2 来自已生成候选，本轮只目检归档，不虚构其原始提示词。旧版本保留，未采用候选不进入规范。

## 新图通用规范

```text
Use case: ui-mockup. Create a high fidelity Chinese SiteCam engineering watermark camera app design sheet. Clean native Android/HarmonyOS visual language, restrained engineering yellow accents #FFC400, charcoal camera pages, system-light management pages with white cards and very light cool gray backdrop. Crisp regular system font, actual vector-style native line icons; no typed v chevrons, no typed three-dot fake icon, no Apple branding. All boards only contain app UI, never explanatory captions, diagram labels, dimensions, footnotes, warnings, implementation text or delivery status. Geometry should be visually compact: regular 14–16sp text, 24–28dp icons within minimum 48dp touch slots; primary shutter no greater than 100dp, short-height shutter no greater than 80dp; thumbnail 64dp; zoom bar 44–48dp tall and 180–220dp wide. Extra space is spacing, columns and content, never enlarged controls. Camera header precisely one current project name 滨江路改造 with a small native down chevron beside it, then three separately recognizable icons: flash bolt; screen-rotation icon drawn as a rectangular phone with curved rotation arrows; cog gear. Gear directly opens settings. Camera has no persistent app bottom tabs. Existing camera controls: photo/video modes 拍照 录像, thumbnail, shutter, lens-switch circular arrows, 水印, 记问题, zoom. No new features. Camera content uses construction worksite excavator and scaffolding with modest compact bottom-left charcoal watermark fitting its text and yellow side stripe. Preserve a 4:3 capture viewfinder or rotated 3:4 portrait presentation, fit rather than distort. App actions and content avoid system status/navigation/gesture areas, rounded corners, cutouts, keyboard and hinges. Use exact short Chinese action labels, no incidental explanation text. A neutral thin separation between panels; no panel titles outside the app.
```

## 26-window-width-transitions

生成：通用规范 + 下方提示词。

```text
Composition: landscape high resolution four-panel sheet arranged 2 by 2. Each panel is an actual app window. Top left a WIDE engineering management window: split view compact list of projects at left and selected 滨江路改造 project details/media at right. List has 进行中 已归档, 搜索工程, small 新建; each project compact card exposes separate 去拍摄 编辑 导出. Selected project row lightly yellow. Detail header 滨江路改造, subtype 市政 · 北段雨水管, photo/video counts, grid of worksite photos. Top right a narrow portrait app window rendering the SAME selected 滨江路改造 project detail as single pane with native back button; same title and first photo. Bottom left WIDE album window: header 滨江路改造, 全部 今天 问题 filter chips, grid at left with selected excavator photograph outlined yellow and preview of the SAME photograph on right, bottom preview toolbar 编辑 分享 另存相册 删除 with native icons. Bottom right narrow single-pane photo preview of the SAME excavator photograph, native back, preserved selection, same bottom toolbar wrapped appropriately. All panes retain the same project or photo selection; display NO arrows or captions describing the transition, no invented camera entry on album. Management theme light. The narrow panes should genuinely be narrow, the wide panes wider, balanced 2x2 composition.
```

## 27-fold-posture-layouts

生成：通用规范 + 下方提示词。

```text
Composition: three app states across one wide sheet. LEFT: near-square fully opened foldable INNER screen, dark camera with a 4:3 viewfinder fitted on the left, and slim black full-height capture rail on right; project name and three navigation icons do not cross viewfinder border, compact shutter and 64dp thumbnail, 水印 and 记问题 reachable at bottom of rail. No giant buttons. MIDDLE: upright book foldable showing light engineering/media management split into two tall panes, a visibly empty narrow vertical black hinge GAP between them. Left pane project list with selected 滨江路改造, direct 编辑 导出 and 去拍摄. Right pane selected project photo grid titled 滨江路改造, same worksite photos, no text/button/photo crosses the hinge. RIGHT: tabletop half-fold posture shown as front-on UI, with upper half camera preview letterboxed in black preserving exact 4:3 geometry, thin horizontal hinge band entirely free of touch content, and lower half dark compact controls including small horizontal zoom, modes, thumbnail, shutter, lens-switch, 水印 记问题. Camera top header consistent name+chevron, flash, rectangular-phone curved-arrow rotation icon, gear. All three surfaces have subtle hardware borders. Show responsive layout differences, no diagram labels, no captions.
```

## 29-cutout-insets-window-chrome

生成：通用规范 + 下方提示词。

```text
Composition: high resolution landscape sheet of four device/window states in a 2 by 2 arrangement. TOP LEFT a portrait dark camera phone with a small visibly off-center top punch-hole camera to the left of the center; header icons/name start below status/cutout area and cannot overlap the punch hole. TOP RIGHT a landscape dark camera phone with a wide hardware notch intruding from the LEFT short edge; preserve dark inset around notch, move project chip right beyond notch-safe boundary, full 4:3 worksite viewfinder and compact black right capture rail, no bottom white gesture bar. BOTTOM LEFT SAME landscape camera rotated 180 degrees with notch now intruding from RIGHT edge; right capture rail and gear must be moved LEFT beyond notch-safe boundary and outside rounded corners, no controls under notch, 4:3 viewfinder fit. BOTTOM RIGHT resizable desktop/tablet freeform app WINDOW showing light 工程包 management; visible OS-neutral thin native window title bar with app name SiteCam and ordinary minimize/maximize/close glyphs, separate from app header below; status/title bar, window corner radius, bottom system navigation reserved, two compact columns of project cards each with 去拍摄 编辑 导出. UI remains within local window bounds. Maintain actual native icon imagery and header screen-rotation symbol in every camera panel. No explanatory dimension labels, no captions outside UI, no decorative safety callouts.
```

## 23-tablet-portrait-main

生成：通用规范 + 下方提示词。

```text
Composition: exactly three equal PORTRAIT TABLET app screenshots side by side, each has a genuinely 3:4 portrait tablet shape, not tall skinny phones. Minimal thin device edges, no camera holes. Each models a local window around 700dp wide, single content column fitting the tablet, controls remain compact and plentiful breathing room. LEFT dark camera: header project 滨江路改造 + native chevron, flash, clearly phone-with-curved-arrows screen rotation, gear. Fit a large exact 3:4 rotated photo viewfinder, modest watermark lower left, then compact zoom centered, 拍照 录像, and capture row with 64dp thumbnail / circular shutter 88dp / lens-switch icon. 水印 and 记问题 are distinct compact buttons. MIDDLE 工程包 management: header back title plus 新建, 搜索工程 / 线路, 进行中 已归档, recent sort icon. Four compact project cards 滨江路改造, 东城雨污分流, 南站道路养护, 科技园管线迁改; first shows 当前 and subtitle 市政 · 北段雨水管. Every card exposes three direct actions 去拍摄 编辑 导出 with real camera/edit/export icons and no oversized buttons; project name row separate tap region with chevron. RIGHT complete settings page using HarmonyOS setting semantics, title 设置. Include all five group headings and all rows, in one scrollable single column at ordinary compact scale: 拍摄与保存: 照片画质 (标准省空间), 快门声音 on toggle, 另存到系统相册 off toggle. 水印: 水印样式与字段. 权限与隐私: 权限设置, 隐私政策. 外观: 相机控件外观 (普通), 桌面图标 (工程现场). 帮助与关于: 使用指南, 关于 SiteCam. Complete actual settings entries must fit this tall tablet without tiny text. NO login/cloud/device sync, no descriptions beneath row names, no footnotes. Tablet portrait is the purpose; do not make any tablet landscape.
```

## 24-tablet-portrait-media

生成：通用规范 + 下方提示词。

```text
Composition: exactly three equal PORTRAIT TABLET app screenshots side by side, each 3:4 tablet shape, not narrow phone. LEFT light album multiselect: header 取消, 已选 3 项, 全选; project 滨江路改造; tabs 全部 今天 问题; four-column grid of real construction photos with exactly three yellow check circles selected, small video play+duration on one tile; bottom fixed actions 分享 移动 删除 with compact actual icons, no huge controls. MIDDLE light photo EDIT page titled 编辑照片 with back/取消 and yellow 完成; portrait photo within central dark editing canvas, natural free rectangular cropping handles and subtle grid, no ratio preset chips. Compact toolbar beneath photo: native free crop icon 裁切, pencil 标注, undo curved arrow, color circles and ordinary line width choices where space fits; normal controls no enormous shutter-like tool. RIGHT light existing video problem form with a small selected video thumbnail and duration 00:18 near top, title 记录现场问题, pinned top-right yellow 保存. Form actual labels: 问题名称 (管道接口复查), 严重等级 with 一般 重要 严重, 处理状态 with 待处理 处理中 已完成, 问题原因, 整改要求, 备注. Show a scroll position where 问题原因 is focused with Chinese user text 接口处有渗水, remaining fields visible or can continue scrolling. A small native-looking neutral keyboard occupies only bottom 28% of the tablet; form viewport is resized above keyboard and top 保存 remains visible and reachable. Keyboard is not an invented app feature. Do not add question editing action to album top bar; 记问题 entry is camera-only. No app-made operating-system permission/date/share sheets. No explanatory fine print anywhere.
```

## 27-fold-posture-layouts-correction

编辑：下方完整提示词；保留目标图其余内容。

```text
Use case: precise-object-edit. Edit this design sheet only to correct camera viewport geometry and two water印 icons. Keep the same three devices, same app state, typography, colors, project selection and construction photo subject. LEFT near-square inner screen: fit the camera viewport as an exact horizontal 4:3 rectangle with black letterboxing if needed, not vertically stretched, keep compact capture rail RIGHT. Leave project name+chevron/flash/screen-rotation/gear in header. Watermark stays inside image. RIGHT tabletop device upper camera region: fit an exact horizontal 4:3 photo within upper screen using black side or top/bottom bars as required, never stretch. No text/control on horizontal hinge. In BOTH camera panes replace the mistakenly drawn person-outline icon for 水印 with a standard framed-landscape/image line icon. Keep 记问题 pen/edit document icon. MIDDLE vertical hinge management preserve empty hinge gap and project/media two-pane layout, make each project row directly expose small native edit and export glyphs while selected-project 去拍摄 stays visible. Do not add captions or explanations. Preserve compact camera controls.
```

## 29-cutout-insets-window-chrome-correction

编辑：下方完整提示词；保留目标图其余内容。

```text
Use case: precise-object-edit. Edit only necessary details of this four-panel UI design. Preserve scene composition, clean colors, app windows and fourth management panel. TOP LEFT move circular punch-hole visibly to about 30% of the screen width from left, with status glyphs avoiding it; not centered. Ensure camera header phone-rotation glyph has curved arrows, and compact 0.6 1× 2 zoom control remains available. TOP RIGHT keep LEFT notch and dark reserved inset. BOTTOM LEFT shows RIGHT notch, but the shooting control rail must STILL be on the RIGHT of viewfinder, inset to the LEFT of the notch-safe black strip; do NOT move shutter to far left. Layout order left to right: clean 4:3 construction viewfinder, slim black capture rail with mode, thumbnail, compact shutter, lens-switch, 水印 and 记问题, then narrower empty dark notch inset on far right. Header project name 滨江路改造 + native chevron at top-left safe part, flash, clearly phone-with-curved-rotation-arrows icon, gear all visible and clear of notch. Keep 4:3 viewfinder fit without stretching or cropping controls. Both landscape panels include compact zoom 0.6 1× 2 and no white bottom gesture bar. Do not add any new functions, text footnotes, captions or labels outside app.
```

## 23-tablet-portrait-main-correction

编辑：下方完整提示词；保留目标图其余内容。

```text
Use case: precise-object-edit. Correct ONLY the tablet aspect ratios and responsive spacing of this UI sheet. Output a very WIDE 2304 × 1024 pixel image, roughly 2.25:1 landscape canvas. Three equal portrait TABLETS aligned horizontally. Each individual tablet must be approx 720px WIDE by 960px TALL (3:4), NOT the skinny phone-like 450 by 1000 shape in the reference. Reflow and expand every screen horizontally to this tablet shape, do not stretch text or controls. Keep all existing real UI content and headings, same three main pages camera / project manager / COMPLETE settings. Each tablet screen has much more room left/right; compact camera buttons retain original same physical touch size, project cards and setting rows can widen gracefully. Camera shows a 3:4 portrait preview sized within its content area with black side breathing room as needed; never stretching photo. Every single settings item remains present, including 相机控件外观 普通 and 桌面图标 工程现场. Keep header project name+chevron, flash bolt, PHONE with curved arrows rotation icon, gear. Exact capture zoom labels 0.6, 1×, 2 (not 0.5). Remove the incidental 天气 watermark field; watermark only 工程名称：滨江路改造 / 拍摄时间：2026-10-03 09:41 / 现场位置：北段施工区 / 拍摄人员：张工. All text ordinary compact scale, no captions or explanatory text. The mandatory result is three genuinely broad 3:4 portrait tablet shapes on a 2.25:1 wide canvas.
```

## 24-tablet-portrait-media-correction

编辑：下方完整提示词；保留目标图其余内容。

```text
Use case: precise-object-edit. Correct ONLY the overall tablet shapes and reflow. Output a very WIDE 2304 × 1024 pixel image (2.25:1 canvas). Three equal portrait TABLETS side by side, each approx 720px WIDE by 960px TALL (3:4). They MUST be BROAD tablets, never skinny phones like the reference. Reflow UI into wider tablet content area without stretching fonts/icons/photos. Keep same three states: 3 selected album tiles in four-column grid with bottom share/move/delete; free photo crop with crop handles and complete compact crop/annotation toolbar; video problem form with top 保存 always above keyboard. Keep ALL actual problem labels 问题名称 / 严重等级 / 处理状态 / 问题原因 / 整改要求 / 备注, option labels unchanged. Right keyboard occupies bottom28% and content scrolls separately above it. Retain yellow accent, native compact controls, light management theme, and same construction imagery. No new functions or any explanatory captions. Mandatory output is three broad 3:4 portrait tablet panels with generous side spacing on a 2.25:1 ultrawide canvas.
```

## 25-tablet-portrait-editor-sheets

生成：通用规范 + 下方提示词。

```text
Composition: four equally sized portrait TABLET screens, each shape 3:4, arranged in a 2-by-2 grid. Overall output portrait 1536 × 2048 or same 3:4 aspect. NO outside labels. TOP LEFT 水印编辑 in a tablet: upper 4:3 photo preview with compact watermark, under it tabs 内容 字段 外观, fields content list and scroll indicator; show all existing eight selectable style cards in compact TWO columns by FOUR rows accessible in the same pane: 经典工程水印 / 极简水印 / 工程信息板 / 蓝白工程记录 / 施工表格 / 巡检记录 / 验收记录 / 大字时间地点. Different colored small watermark previews preserve their style identities. Top 完成 remains visible. No new watermark styles. TOP RIGHT light 编辑工程 tablet form with top 保存, fields 工程名称 滨江路改造, 线路 北段雨水管, 类别 市政, 地点 北段施工区; spacious single content column with compact fields, no cloud functions. BOTTOM LEFT dark camera tablet behind a compact centered or bottom direction sheet 拍摄方向, exactly four real native icon choices 自动 / 竖屏 / 左横屏 / 右横屏 in a 2x2 grid, 自动 yellow selected; directions icons are clear device silhouettes rotated correct ways with arrows; top camera header name+chevron flash rotation gear, no more menu. BOTTOM RIGHT light album tablet with a long FILTER sheet 筛选, fixed top title/close, scrollable body, real fields 工程类别 / 开始日期 / 结束日期 and appropriate category chips, fixed bottom 重置 and 确定 safely above gesture area. Sheet content tall with small scroll thumb to show it scrolls; don't invent a native date calendar, only date field rows with calendar icons. No small descriptive subtitles or instructional text; UI only.
```

## 28-short-height-large-text

生成：通用规范 + 下方提示词。

```text
Composition: three UI states on a wide landscape sheet, two short-height LANDSCAPE PHONE windows stacked on the LEFT occupying 70% width, one NARROW PORTRAIT app window at right occupying30%. No titles outside UI. Upper left short-height phone photo editor: title 编辑照片, 取消 and 完成 stay visible; free crop photo centered, arbitrary movable rectangular crop with corner handles, tools dock on right or compact bottom row native crop/annotation/undo with colors, no fixed aspect presets, nothing cut off by short height. Lower left short-height phone 工程/问题 form titled 记录现场问题 with top-right 保存 fixed reachable; a native neutral keyboard occupies lower half. Only focused input 问题原因 with text 接口处有渗水 visible in scrollable form viewport above keyboard, plus next visible 整改要求 row, small scroll indicator; title and 保存 stay outside scrolling and unobscured, no extra helper text. RIGHT narrow portrait window dark camera with LARGE accessibility text and a 拍摄方向 panel: exactly four options arranged TWO BY TWO 自动 / 竖屏 / 左横屏 / 右横屏 with device and curved-rotation icons. Every option has readable large font with wrapping if necessary; close icon reachable in top corner, no clipping. Camera title may wrap gracefully; all icons remain separate and touchable, no ellipsis replacing controls. No giant shutter or new functions. No explanatory footnotes, grid labels, or dimensions.
```

## 30-tablet-portrait-export-move-permission

生成：通用规范 + 下方提示词。

```text
MANDATORY canvas shape: very wide 2304 × 1024, 2.25:1 output. Three equal broad portrait TABLET screenshots side by side, each 720px wide × 960px high (3:4 portrait tablet, not skinny phone). Compact native controls and regular text, tablet breathing space. LEFT existing app 导出工程档案 panel centered in a light tablet page with project header 滨江路改造. Actual radio options exactly 保持原文件 selected / 更小文件 1280 / 标准省空间 1920 / 较清晰 2560 / 原尺寸高清. Then two visible compact equal-width buttons 导出 ZIP 压缩包 and 导出到文件夹, with appropriate archive and folder icons, plus 取消. No invented formats/reports, no compression-percentage or video-quality options, no operating-system folder chooser. MIDDLE light album underlay with three selected thumbnails, centered scrollable app dialog 移入工程, radio target rows 东城雨污分流 · 市政 ; 南站道路养护 · 市政 ; 科技园管线迁改 · 建筑 plus existing state badge 拍摄锁定 on one and 已归档 on another. Direct small bottom 取消 / 移动 actions always visible. No copy switch or new options. RIGHT app 权限设置 page on portrait tablet, exactly three native icon rows 相机 / 位置 / 麦克风, respective current statuses 已允许 / 未开启 / 未开启 with clear status distinction. Each row offers correct compact existing action such as 开启 for missing optional permission; a small 完成 button at bottom. Do not draw or fake system permission prompt, do not add a Settings jump when first request is available; no gray descriptive lines under permissions. App UI only, no explanation captions.
```

## 31-tablet-portrait-help-first-launch

生成：通用规范 + 下方提示词。

```text
MANDATORY canvas: landscape 1536 × 1024, exactly TWO equal broad portrait TABLET screens, each 720×960 shape 3:4, not phones. LEFT light tablet 使用指南 page with back and title, normal body structure a compact list of existing guide topics with native icons and disclosure chevrons: 工程管理 / 拍照与录像 / 水印设置 / 相册与问题记录 / 导出工程档案 / 权限设置. Render only topic names, no explanatory small text or new functionality. RIGHT light tablet first-launch welcome step: top title 工程水印相机, small 跳过 at right. Main central artwork is clean app UI preview of a portrait camera and landscape camera over construction site, using correct project name+chevron, separate flash bolt / PHONE with CURVED ROTATION ARROWS / cog gear, no three-dot camera menu. Below artwork a large existing welcome heading 横拍竖拍，自己选择. Four pager dots with second active yellow, compact yellow 下一步 button safely above bottom gesture inset. Preserve existing onboarding step semantics; do not invent account login, cloud sync, new controls, extra helper text or descriptive subtitle. Extra tablet space remains intentional clean breathing room; button and text are normal size, not giant.
```

## 04-video-project-orientation-v2

编辑：下方完整提示词；保留目标图其余内容。

```text
Use case: precise-object-edit. Target reference is a four-panel Chinese camera design sheet. Make only these targeted changes, keep all other construction photos, UI layout, labels, direction sheet with exactly 自动/竖屏/左横屏/右横屏, current project selector, and colors intact. In EVERY camera top header replace ambiguous second icon between flash and gear by a clearly recognizable native SCREEN ROTATION icon: a tall rectangular phone outline with small curved rotation arrow on each side, not a page, camera or monitor. Keep project name 滨江路改造 plus same-line native chevron, flash, rotation and gear separate. In second panel RECORDING state 00:18, render the 记问题 icon AND its label and lens-switch circular-arrow icon visibly DISABLED dark gray exactly like other disabled controls; do not remove them. Red square stop remains enabled and bright. First ready-to-record panel keeps those controls enabled white. No more menu, no new functions, no captions or small explanatory text.
```

## 16-hover-large-text-dark-album-v2

编辑：下方完整提示词；保留目标图其余内容。

```text
Use case: precise-object-edit. In this three-panel screenshot sheet change ONLY the wrong camera glyph in the TOP LEFT camera header, between flash and gear, into a clearly recognizable native SCREEN ROTATION glyph: tall rectangular phone with CURVED ARROWS on both sides. It must not look like a camera body. Keep folder/project name/native chevron, flash, gear and all other camera controls and labels unchanged. Preserve second large-font keyboard form and third dark album grid/photo preview exactly. No new features, no explanatory text, no other redesign.
```

## 13-permissions-v2

编辑：下方完整提示词；保留目标图其余内容。

```text
Use case: precise-object-edit. Update this three-panel app permission sheet with minimal targeted changes. LEFT camera header must show 滨江路改造 with SAME LINE native down chevron, then three separate icons flash lightning / tall rectangular PHONE WITH CURVED ROTATION ARROWS / gear cog, replacing old three dots. Preserve disabled shutter and permission empty-state. Remove the small gray explanation line 用于拍照和录像 under the central main heading, keep main heading 开启相机权限 and yellow button. Remove white bottom gesture bar from dark camera. MIDDLE and RIGHT app permission settings preserve three rows, native camera/location/microphone icons, clear statuses 未开启 in middle, 已允许 green checks in right. Remove all gray secondary explanatory subtitles under the three permission names; don't draw system permission dialogs. Middle primary 开启所需权限 still requests OS permissions, secondary 稍后再说. Right all-allowed page bottom action must be 完成, NOT 去系统设置, because settings jump is only for actual denied state requiring it. No new capability, no explanatory footnotes anywhere. Preserve clean native white pages and yellow accents.
```

## 15-first-launch-steps-v2

编辑：下方完整提示词；保留目标图其余内容。

```text
Use case: precise-object-edit. Keep this four-step onboarding sheet, its major illustrations, four main headings, pager dots, next/start buttons and layout. Correct ONLY embedded camera navigation and remove secondary explanatory fine print. In SECOND panel all tiny phone/landscape camera previews must show compact current project name+native chevron, then separate flash icon, phone-with-curved-rotation-arrows screen rotation icon, gear cog. NO three-dot more menu in camera preview. Other central artwork intact. Remove both lines of small gray explanatory text below the large black heading in EACH of all four panels; leave clean whitespace, retain all large main headings exactly and button labels 下一步, 开始使用, top title 工程水印相机 and 跳过. No captions, no technical notes, no new onboarding step or features.
```

## 29-cutout-insets-window-chrome-v2

编辑：下方完整提示词；保留目标图其余内容。

```text
Use case: precise-object-edit. Change ONLY the TOP LEFT portrait phone panel in this four-panel sheet; keep ALL other three panels pixel-faithful in composition, icons and insets. In top-left portrait camera phone: reflow content so the worksite preview is a true PORTRAIT 3:4 rectangle (width to height = 0.75), not the current almost square preview. Inside screen width approx280px, use preview about280px wide×373px tall beneath safe top camera header. Keep off-center punch hole and no control overlap. Add the missing compact 拍照 / 录像 mode row below image (拍照 selected yellow), retain zoom 0.6 / 1× / 2, thumbnail, compact shutter, lens-switch, 水印, 记问题 by gently shortening gaps below; all fit before bottom gesture safe inset. Do not enlarge controls. Keep top name 滨江路改造 plus same-line chevron; flash, phone rotation with curved arrows, gear. If changing watermark text in this TOP LEFT panel, use only 工程名称：滨江路改造 / 拍摄时间：2026-10-03 09:41 / 现场位置：北段施工区; omit 天气. No other panel changes, no additional text or captions.
```

## 04-video-project-orientation-v3

编辑：下方完整提示词；保留目标图其余内容。

```text
Use case: precise-object-edit. This is a precise two-object color edit, nothing else. In the SECOND PHONE from LEFT, the recording state with red dot 00:18 and red STOP SQUARE, change BOTH the lower-right pencil-in-square 记问题 icon plus text label (approximately x773 y744 on 1659×948 source) AND lens-switch circular arrows below it (x763 y825) from bright WHITE to disabled DARK GRAY #444444. They must be obviously DIMMED and unavailable, same darkness as disabled 水印 and thumbnail in this second phone. Do not touch red stop square, timer or any icon in first, third, fourth phones. Keep all header navigation, construction photos, text and other content identical. The whole reason for this edit is those two lower-right icons in recording phone must be visibly dark disabled gray, not white.
```


## 34-landscape-harmony-camera-reference

- 日期：2026-10-05
- 工具：内置 `image_gen`，新图生成。
- 项目文件：`34-landscape-harmony-camera-reference.png`
- 来源：`/Users/ninja/.codex/generated_images/01a0fcae-22e8-7782-bb1a-b0d6406c023c/exec-dc6c5346-94e8-41ad-bd07-b3da4488e4d7.png`
- 参考：[华为官方 HarmonyOS 5.1《拍照》](https://consumer.huawei.com/cn/support/content/zh-cn16058279/)，横屏模式和变焦环采用上下操作。官方页面示意图为竖屏，本稿依据其操作层次设计横屏排布。

```text
Use case: ui-mockup.
Create ONE polished, credible production UI concept for SiteCam 工程水印相机, a construction-site watermark camera. This is a NEW LANDSCAPE PHONE CAMERA interface inspired by the native Huawei HarmonyOS camera's interaction hierarchy: in landscape, zoom and mode choices form independent vertical tracks, and the shutter has its own vertical column with gallery and camera flip above/below. It is an original SiteCam adaptation, not a Huawei-branded screenshot.

CANVAS: exactly one edge-to-edge horizontal phone SCREEN, aspect ratio 20:9, target 2400x1080. No phone device shell, no hands, no presentation margins, no title, no explanatory captions, no arrows annotating the design, no repeated panels. All UI text stays upright for landscape reading. Extremely crisp vector-like UI details, clean restrained native system appearance. Black camera chrome, white text and icons, a very small amount of engineering yellow #FFD000 only for the active zoom, active photo label, and watermark stripe. Do NOT use large yellow pill buttons for modes or zoom.

GEOMETRY, important: A generous actual LANDSCAPE 4:3 camera preview is x=128..1568, y=0..1080 (1440x1080). It occupies full height and about 60% of the total screen width. The live scene is a realistic daytime Chinese urban construction site: concrete bridge structure, scaffolding, excavator, ordinary sky, documentary smartphone photography. The image is clear, not dimmed. Never stretch the photo. A very narrow black utility strip occupies x=0..128; the right camera operation region occupies x=1568..2400. No horizontal header or footer that reduces the preview height. No square/portrait preview.

LEFT UTILITY STRIP: exactly three small white native line icons, vertically and comfortably spaced: flash lightning at x64 y300; a rectangular PHONE WITH CURVED ROTATION ARROWS at x64 y540; a cog gear at x64 y780. These are separate touch targets, without large circular backing plates. This is a slim utility strip, NOT a wide panel, no project list, no left text labels.

PROJECT ENTRY: overlay a short low-contrast charcoal translucent capsule just INSIDE the preview at x174 y42, reading “滨江路改造” with a true same-line downward chevron closely following the name. About 250 pixels total width, modest 30px text. It must not span the screen. No other header.

WATERMARK: preserve SiteCam engineering identity. Place a compact translucent charcoal rectangle INSIDE the bottom-left of the photograph, x170..930 y820..1030, with a 6px yellow left stripe. Three readable left-aligned lines, no decorative heading and no extra helper copy:
“工程名称：滨江路改造”
“拍摄时间：2026-10-05 09:41”
“现场位置：北段施工区”
Use restrained 28-30px Chinese system text and comfortable leading. The box hugs its text; do not extend it across the photo. Nothing overlaps the watermark.

RIGHT REGION: separate native-camera vertical groups, arranged left-to-right: a slim zoom column, a slim mode column, and a shutter column, all on one continuous matte-black background without visible panel borders, boxes, cards, grids or separators.
1. ZOOM COLUMN centered x1640: two small choices vertically around y470 and y600, “0.5” white, “1×” selected in a SMALL 60px-diameter yellow circle. Keep zoom entirely in the black area OUTSIDE the preview, clear of the watermark. No horizontal zoom pill.
2. MODE COLUMN centered x1810: “拍照” at y492 in yellow with a tiny yellow selection dot, “录像” at y590 in white. 30-32px labels, upright and horizontally written, choices stacked vertically. No giant mode buttons. Do not add portrait, night, pro, panorama, AI or other modes.
3. SHUTTER COLUMN centered x2110: small rounded-square 105px gallery thumbnail at y240, plain white shutter at y540 with 180px white core and fine 208px outer ring, small white camera-flip circular-arrow icon at y830. These three elements lie on the SAME VERTICAL AXIS, not three side-by-side controls. The shutter is centered vertically within its operation area. Maintain about 180px clear black GRIP SPACE after the shutter's outer edge to the right screen edge: it must not hug the far right.
4. SITE TOOLS: at the bottom of the RIGHT BLACK REGION only, a compact image-outline icon with the short label “水印” centered x1810 y980, and a compact pencil-in-square icon with “记问题” centered x2110 y980. Each glyph about44px and label28px, one small row beneath the lower camera-flip position with plenty of separation. No tools on the photo bottom edge.

Style priorities: resembles a deliberate native camera held horizontally, not a portrait panel pasted beside the viewfinder. Strong visual priority to the scene and single white shutter. Airy but compact, consistent subtle strokes, realistic touch sizing. Black empty space is intentional hand space, but no huge dead rectangle separating controls. Keep all text, icons, and shutter fully inside safe edges. No visible bottom gesture bar. No watermark other than the in-app construction watermark. No logo, no marketing or legal text. Render ONLY the requested UI, with no explanatory small print.
```

## 最终几何局部修订

### 27-fold-posture-layouts-v2-final

```text
Use case: precise-object-edit. Final exact LOCAL geometry edit to this 1774×887 source. Keep all three device shells, all headers, text, icons, middle management device, right control rail and lower tabletop controls UNCHANGED. Only shorten the two PHOTO RECTANGLES and add BLACK LETTERBOX fill where their excess height was.
LEFT device current photo is approximately x34..552, y199..689 (518 wide,490 tall). Replace its photo region with an EXACT LANDSCAPE 4:3 rectangle x34..552, y240..629: width518, height389. This is substantially LESS TALL than current almost-square photo. The entire horizontal bands x34..552,y199..239 and x34..552,y630..689 MUST become SOLID BLACK letterbox bars. Photo remains worksite, resized/reframed without stretch; keep compact watermark INSIDE new bottom-left within y540..620. Black bands can remain empty; don't fill them with photo or artwork. Right-side shutter, thumbnail, controls stay exactly where they were.
RIGHT tabletop device upper photo also must be EXACT LANDSCAPE4:3: x1321..1708 width387; y185..475 height290. Add black letterbox where old photo extended y174..184 and y476..486. Keep all photo pixels ONLY inside this wider-than-tall rectangle and keep watermark inside. Header/hinge/lower controls unchanged.
The key result: TWO obviously horizontally rectangular camera views with visibly black letterbox above and below, width=4,height=3. NEVER create a square or portrait photo, and do not add captions or any new UI.
```

### 29-cutout-insets-window-chrome-v3-final

```text
Use case: precise-object-edit. Final exact LOCAL edit to this 1536×1024 four-panel sheet. ALL top-right, bottom-left and bottom-right panels must remain unchanged. Only TOP-LEFT PORTRAIT PHONE camera preview and its lower controls reflow; do not scale the entire phone or whole sheet.
Current top-left PHOTO rectangle is x145..424 width279 and y87..388 height301, almost square. It MUST become PORTRAIT 3:4: x145..424 width279 and y87..459 height372. Extend construction PHOTO content vertically DOWN TO y459. Keep punchhole, status and camera header exactly unchanged. This visibly taller 3:4 preview fills almost to current shutter top. Compact bottom-left watermark stays inside photo and reads 工程名称：滨江路改造 / 拍摄时间：2026-10-03 09:41 / 现场位置：北段施工区.
Rearrange only controls BELOW this taller preview: put small 拍照/录像 mode selector and 0.6/1×/2 zoom on a shared compact row centered around y480 (mode on left, zoom on right), then thumbnail/shutter/lens-switch row centered around y527, then 水印 and 记问题 icons/labels around y566. Keep touch regions usable, shutter visibly compact about50pixels diameter, no overlap with phone frame bottom at585. The modes row may be compact but must remain readable and visible. The construction photo bottom MUST land at y459, not y388. No changes to other three panels, no extra captions, no invented functions.
```
