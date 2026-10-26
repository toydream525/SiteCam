# v0.4.0 前端设计图归档清单

当前采用编号 01–34 共 34 个图位。01–22 中需要统一导航的图采用版本化新稿；23–31 是新增适配补图。03 与 22 使用同一已采用构图，分别保留结构与紧凑尺寸参考图位。32–33 为 2026-10-05 已提交问题反馈对应的新参考图。当前图位不等于实现或设备验收状态。

实际尺寸、既有字段和业务规则以 [DESIGN_CONTRACT.md](DESIGN_CONTRACT.md) 为准；独立布局与状态复用见 [ADAPTIVE_COVERAGE.md](ADAPTIVE_COVERAGE.md)，本轮提示词见 [PROMPTS.md](PROMPTS.md)。

桌面图标 A/B/C/D 的高清资源与生成提示词见 [launcher-icons/README.md](launcher-icons/README.md)，对应 `launcher-icons/icon-{a,b,c,d}-v040.png`。图 12 保持原图不变，实际资源按其视觉方案同步。

| 编号 | 分组 | 当前采用文件 | 内容 | 来源 |
|---:|:---:|---|---|---|
| 01 | A | `01-phone-main-v2.png` | 手机三主页面；工程名同行 chevron 与三独立相机图标 | `/Users/ninja/.codex/generated_images/01a0fcae-22e8-7782-bb1a-b0d6406c023c/exec-38711f47-a71f-44c3-b13f-94eedb3caea7.png` |
| 02 | A | `02-tablet-project.png` | 平板工程 | `/Users/ninja/.codex/generated_images/01a0fcae-22e8-7782-bb1a-b0d6406c023c/exec-c325d28a-03ba-4e83-ab36-55431da6f8b2.png` |
| 03 | A | `03-landscape-compact-v2.png` | 横屏相机紧凑布局；右侧拍摄栏 | `/Users/ninja/.codex/generated_images/01a0fcae-22e8-7782-bb1a-b0d6406c023c/exec-91109f2a-aa1c-4a61-89a3-5f69b41f8487.png` |
| 04 | A | `04-video-project-orientation-v3.png` | 录像/工程切换/方向四选；录像中不可用操作置灰 | `/Users/ninja/.codex/generated_images/01a10218-30f4-7102-bd8c-25702e04b84f/exec-a9a6bf3f-9692-4607-9256-5898ce470aa7.png` |
| 05 | A | `05-location-lock-archive-v2.png` | 定位刷新/未授权/锁定/归档状态与新导航 | `/Users/ninja/.codex/generated_images/01a10206-840f-7092-a267-a5fb9f4a966f/exec-a53f6c7a-b053-4fcd-993c-8cf3062be58d.png` |
| 06 | A | `06-cover-screen-v2.png` | 小外屏相机与新导航 | `/Users/ninja/.codex/generated_images/01a10206-840f-7092-a267-a5fb9f4a966f/exec-12270b82-f477-42f3-af95-3499756199d8.png` |
| 07 | B | `07-project-sort-batch.png` | 工程排序表单批量 | `/Users/ninja/.codex/generated_images/01a0fcae-a9ec-7bd3-9191-a6c48afe91ce/exec-e034e8b1-5c88-4e7d-9bb5-e1ca32ac4d48.png` |
| 08 | B | `08-project-category-empty.png` | 工程分类更多删除空态最终 | `/Users/ninja/.codex/generated_images/01a0fcae-a9ec-7bd3-9191-a6c48afe91ce/exec-22d5bfa7-16bd-4ffd-9fa5-1557c48e9e3b.png` |
| 09 | B | `09-watermark-fields.png` | 水印内容字段 | `/Users/ninja/.codex/generated_images/01a0fcae-a9ec-7bd3-9191-a6c48afe91ce/exec-8d85c57b-a2d7-41d4-a9cc-ff4d4b9bb359.png` |
| 10 | B | `10-large-screen-watermark-styles.png` | 大屏水印与8样式 | `/Users/ninja/.codex/generated_images/01a0fcae-a9ec-7bd3-9191-a6c48afe91ce/exec-70f78b6e-090c-429d-9476-33c49fb4eb9c.png` |
| 11 | C | `11-wide-settings-theme.png` | 阔屏设置明暗最终 | `/Users/ninja/.codex/generated_images/01a0fcae-cda2-76d0-988a-29bf6875dcb6/exec-0b8d7e73-9711-496e-9324-0d9992f8f02c.png` |
| 12 | C | `12-quality-icons-about.png` | 画质图标关于 | `/Users/ninja/.codex/generated_images/01a0fcae-cda2-76d0-988a-29bf6875dcb6/exec-4c0aa6fa-49b6-49f3-9134-25a249d79ff9.png` |
| 13 | C | `13-permissions-v2.png` | 应用权限未授权/已授权状态与新导航 | `/Users/ninja/.codex/generated_images/01a10218-30f4-7102-bd8c-25702e04b84f/exec-6fdb2a4a-ab9f-45dc-a741-2cfce35d44e3.png` |
| 14 | C | `14-help.png` | 帮助 | `/Users/ninja/.codex/generated_images/01a0fcae-cda2-76d0-988a-29bf6875dcb6/exec-4ca19a22-f08d-4c5e-8303-5b44eeac5a4e.png` |
| 15 | C | `15-first-launch-steps-v2.png` | 4步首启；示例相机新导航 | `/Users/ninja/.codex/generated_images/01a10218-30f4-7102-bd8c-25702e04b84f/exec-6420ccc7-77be-4bf3-857d-cc6ace21b5ac.png` |
| 16 | C | `16-hover-large-text-dark-album-v2.png` | 悬停/大字号表单/暗色相册；方向图标修正 | `/Users/ninja/.codex/generated_images/01a10218-30f4-7102-bd8c-25702e04b84f/exec-7d593871-3a99-423a-bf27-d7b366084b0d.png` |
| 17 | D | `17-album-filter-multi-select.png` | 相册筛选多选 | `/Users/ninja/.codex/generated_images/01a0fcc4-c6cc-7a41-9d13-185576c41ea6/exec-d32d03a6-2da0-4518-b0a5-e417e8313e1b.png` |
| 18 | D | `18-photo-annotation-crop.png` | 照片详情标注裁剪最终 | `/Users/ninja/.codex/generated_images/01a0fcc4-c6cc-7a41-9d13-185576c41ea6/exec-f18b3bf2-dccc-468d-b2be-e08b1f165212.png` |
| 19 | D | `19-video-issues.png` | 视频问题 | `/Users/ninja/.codex/generated_images/01a0fcc4-c6cc-7a41-9d13-185576c41ea6/exec-f013c8c7-57de-48b5-afbd-088dac2feea3.png` |
| 20 | D | `20-export-move-date.png` | 导出移动日期最终 | `/Users/ninja/.codex/generated_images/01a0fcc4-c6cc-7a41-9d13-185576c41ea6/exec-183140d3-4684-48be-8ffb-420cc48977a3.png` |
| 21 | D | `21-key-feedback-state.png` | 关键反馈状态 | `/Users/ninja/.codex/generated_images/01a0fcc4-c6cc-7a41-9d13-185576c41ea6/exec-92360892-90e6-4e20-a850-9d3a825e0d32.png` |
| 22 | E | `22-large-screen-compact-reference-v2.png` | 大屏紧凑控件补充参考；尺寸以契约为准 | `/Users/ninja/.codex/generated_images/01a0fcae-22e8-7782-bb1a-b0d6406c023c/exec-91109f2a-aa1c-4a61-89a3-5f69b41f8487.png` |
| 23 | F | `23-tablet-portrait-main.png` | 平板竖屏相机/工程/完整设置 | `/Users/ninja/.codex/generated_images/01a10218-30f4-7102-bd8c-25702e04b84f/exec-3fd0199d-a50c-4375-aaeb-6705aeb8fb8a.png` |
| 24 | F | `24-tablet-portrait-media.png` | 平板竖屏相册多选/自由裁切编辑/视频问题与键盘 | `/Users/ninja/.codex/generated_images/01a10218-30f4-7102-bd8c-25702e04b84f/exec-28c6826e-28b1-4887-b36d-2d5ca556b2c9.png` |
| 25 | F | `25-tablet-portrait-editor-sheets.png` | 平板竖屏水印样式与字段入口/工程表单/方向四选/长筛选 | `/Users/ninja/.codex/generated_images/01a10218-30f4-7102-bd8c-25702e04b84f/exec-7bad6874-37df-49e8-994f-6789a9296f92.png` |
| 26 | F | `26-window-width-transitions.png` | 工程与相册的宽窄窗口列表/详情、网格/预览状态衔接 | `/Users/ninja/.codex/generated_images/01a10218-30f4-7102-bd8c-25702e04b84f/exec-2ff17826-bcb9-4f2d-859b-b208f114412b.png` |
| 27 | F | `27-fold-posture-layouts-v2.png` | 近平方内屏右栏/竖向铰链工程媒体/悬停上下布局 | `/Users/ninja/.codex/generated_images/01a10218-30f4-7102-bd8c-25702e04b84f/exec-095014f7-aac8-43d0-bb79-09fa9847ea7c.png` |
| 28 | F | `28-short-height-large-text.png` | 手机短高自由裁切/键盘表单/窄窗大字号方向2×2 | `/Users/ninja/.codex/generated_images/01a10218-30f4-7102-bd8c-25702e04b84f/exec-f96963a5-3042-4f49-aa40-fa1f9e87ad59.png` |
| 29 | F | `29-cutout-insets-window-chrome-v3.png` | 顶部偏置挖孔/左刘海/右侧遮挡/自由窗口标题栏 | `/Users/ninja/.codex/generated_images/01a10218-30f4-7102-bd8c-25702e04b84f/exec-d5f9c8de-dcef-495c-b8d6-cceb8b32ae00.png` |
| 30 | F | `30-tablet-portrait-export-move-permission.png` | 平板竖屏真实导出选项/移动目标/应用权限状态 | `/Users/ninja/.codex/generated_images/01a10218-30f4-7102-bd8c-25702e04b84f/exec-fb6a54b4-a800-4c0c-b8e7-f6f3c2998533.png` |
| 31 | F | `31-tablet-portrait-help-first-launch.png` | 平板竖屏使用指南/首启 | `/Users/ninja/.codex/generated_images/01a10218-30f4-7102-bd8c-25702e04b84f/exec-292c23ba-dec1-4c17-95cb-7e1dfae0322c.png` |
| 32 | G | `32-camera-feedback-layouts-v2.png` | 普通长条手机横竖屏握持、紧凑工程入口、独立刷新、右栏倍率、方向锚定小菜单 | `/Users/ninja/.codex/generated_images/01a107e8-3e63-75e3-bf49-8c1095ebca13/exec-cda42e90-684e-4980-9750-1d4c3b154a6e.png` |
| 33 | G | `33-android-appearance-style-footer.png` | Android 普通/透明选项；双端样式网格滚动与安全区内完整 footer | `/Users/ninja/.codex/generated_images/01a107e8-3e63-75e3-bf49-8c1095ebca13/exec-18f53f45-f885-40ef-ab1a-3d4adcb6e1b7.png` |

| 34 | G | `34-landscape-harmony-camera-reference-v3.png` | 普通手机及平板横屏：左侧窄工具列、画面内透明倍率、右上模式与下方工具对称、相册/快门/切镜头同轴及紧凑间距 | `/Users/ninja/.codex/generated_images/01a0fcae-22e8-7782-bb1a-b0d6406c023c/exec-fda0cbd2-c856-43f5-b107-aa2824c49c1d.png` |

| 35 | H | `35-watermark-width-controls.png` | 双端外观页水印宽度滑块；长内容换行与短内容底板收紧 | `/Users/ninja/.codex/generated_images/01a12ee3-5347-7130-bce8-59c32abb1267/exec-adb2a534-d2a8-47ee-9225-d082ed3d35e0.png` |
| 36 | I | `36-guide-large-window-two-column.png` | 双端首启教程大窗口两栏（插图在左撑满高度、标题/圆点/按钮在右栏）；平板竖屏单栏内容居中 | 本地按图 15-v2 四步插图素材合成的布局稿 |
| 37 | I | `37-quick-watermark-width.png` | 双端相机页快速修改水印面板在字号下方新增水印宽度行；编辑页面板沿用图 35 | 本地按图 35 控件与两端深色面板样式合成 |

## 版本保留

旧版 01、03、04、05、06、13、15、16、22 及本轮定向修订前的 04-v2、27 原图、29 原图及 v2、32-v1 保留在本目录，仅供历史对照，不与表内当前版本并用。未纳入表内的来源目录草稿不参与实现。图 12 的桌面图标方案保持原图不变，实际桌面资源需按该视觉同步。

## 采用范围

补图用于表达独立布局、导航、操作可达性和状态复用。像素级画幅、尺寸、原生图标细节、偶然示例文案和页签高亮以文字契约为准；具体图像偏差记录在覆盖说明中。

### 2026-10-05 反馈补图采用顺序

- 图 32 覆盖旧图 01、03、04、22、25、28、29 中相冲突的相机工程入口宽度、倍率/水印位置及大型方向面板。方向菜单和避免重叠的原则适用于双端各窗口；图 32 的额外底部/右侧握持留白仅适用于普通长条手机，平板及折叠屏仍保留各自采用图的布局结构。
- 图 33 左半只规定 Android 的普通/透明选项；HarmonyOS 继续普通/毛玻璃。右半与图 10、25 共同规定双端样式选择的滚动网格和固定安全 footer，不改变八款样式身份。
- 图 15-v2 的四步插图是首启设计来源，原源矩形详见 [onboarding-artwork/README.md](onboarding-artwork/README.md) 与 [regions.json](onboarding-artwork/regions.json)。2026-10-18 按用户最新要求采用该目录 `hd/` 四幅原生高清重画（约1168×1347）；第二张采用修正手机外形后的版本。原图放大尝试不采用。平板排布继续遵循图 31；不另绘简笔替代插图。

## 2026-10-10 横屏最终采用

图 34-v3 采用用户最终补正的来源图 exec-fda0cbd2-c856-43f5-b107-aa2824c49c1d.png，按 2026-10-10 最新平板反馈，覆盖普通手机及平板横屏。拍照/录像与水印/记问题对应两列，相册/快门/切镜头上下同轴，倍率透明悬浮于照片右缘，右栏保持紧凑并保留握持留白。平板不附加手机的额外握持区；折叠屏和竖屏结构不改。原 34 初稿保留供历史对照；误附的 exec-d6f0ede1-cf3c-456a-84d9-201c92fb4a9b.png 不作为采用依据。

## 2026-10-12 水印宽度

图 35 补充图 09、10、25 的外观设置：字号下方增加水印宽度滑块、当前百分比及 30%／90% 端点。只采用该项控件和内容收紧关系；不新增图中示例水印预览页面或更换既有水印样式。比例与实际排版以设计契约为准。

## 2026-10-12 首启教程大窗口两栏

## 2026-10-12 快速修改水印宽度

图 37 补充相机页「水印」快速面板：双端在字号下方增加「水印宽度」行，与图 35 的编辑页宽度共用同一取值和同一次保存；面板其余行、字段与样式选择不变。相机页仍为深色沉浸面板。

图 36 补充图 15-v2、31 的首启教程：可用宽 ≥600vp 且高 < 宽×1.35 的大窗口（大折叠内屏、大窗口横放）采用两栏，插图在左按可用高度撑满、标题与圆点与主按钮在右栏；平板竖屏与手机的单栏结构不变，单栏内容改为垂直居中。插图仍取图 15-v2 的同一批源矩形，不另绘替代插图；尺寸与判定以设计契约为准。


## 2026-10-14 审核反馈修复 · 图 38
采用 `38-review-fixes.svg` 作为状态补图：快门和既有控件位置不变；Android 保存成功保留缩略图动画与文字提示，文字移入取景区，不覆盖拍摄控件，错误照常提示；镜头档位与会话映射继续由设备实际能力生成，不按型号固定倍率。原始视角与会话映射保留；≥1.5×、向上误差≤2.5%、且不超真实范围时，快捷入口归一到邻近0.5×档位，显示值即请求值；其他非整数档位保留。鸿蒙相册删除过程中保持工具栏和选中集合稳定，完成后原位更新；API 26 以下隐藏不可用的桌面图标入口，API 26 及以上保持原图标页。此补图不替换其余布局。


## 2026-10-19 标注工具与视频完整回放 · 图 40

采用 `40-annotation-tools-and-video-fit.svg`：文字输入仅在文字工具选中时出现；其他标注工具不显示文字输入。视频详情、相册及工程预览均等比完整显示画面，允许留黑边；手机、平板、折叠屏及横竖窗口使用同一规则。照片网格缩略图与其他既有布局不变。

## 2026-10-19 Android 应用内视频预览 · 图 41

采用 `41-android-inline-video.svg`：Android 视频详情直接使用应用内播放器，提供播放/暂停、时间、可拖动进度与全屏入口；不再要求跳转系统播放器。详情与全屏均完整等比显示，切换保持播放位置；返回优先退出全屏，离开详情或切换视频结束原播放，进入后台暂停。保留原有分享、删除和问题记录入口，不增加自动连播、列表自动播放或说明性小字。

## 2026-10-17 Issue #5 / #6 · 图 39

采用 `39-import-and-text-edit.svg` 扩展工程相册导入照片入口、水印预览编辑与文字再次编辑状态。手机沿用现有深色、黄色主操作与安全区布局；宽窗复用现有双栏适配，预览与字段分栏，短高窗口字段可滚动、保存可达。相册单张导入，经系统选择器进入预览，目标工程明确，沿用水印样式/内容/外观编辑，保存新照片。文字工具点击已有文字回填全文并原位修改，拖动命中区域移动标注，拖动结束不弹输入框；取消保持原值，一次操作一次撤销。图中示例不写入用户数据；不新增说明性小字。其他相机和相册布局继续遵循此前采用图。实施细节与验证范围见 `docs/plans/2026-10-17-issues-5-6.md`（项目根目录）。

## 42 · 抓拍、连拍与天气（2026-10-26）

采用 `42-snapshot-burst-weather.svg`。抓拍开关与快门档位位于设置，默认开启；连拍开关紧邻闪光灯，点按切换。连拍中快门显示停止方块，点按停止并保存已拍帧。工程名称快捷入口置于快速水印编辑顶部。天气作为可编辑水印字段，预览和成片共用拍摄时快照。其他布局、握持留白与设备适配保持已有契约。
