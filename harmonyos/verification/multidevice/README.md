# 折叠屏与大屏适配 — 2026-09-10

## 本轮改动

- 保留 600 / 840 vp 窗口断点；工程页将搜索和筛选移入列表栏，840 vp 起右侧完整展示详情，初始显示当前工程。
- 相册宽屏增加默认提示页；点击资料显示详情，窗口变窄时保留正在查看的资料。默认提示不显示其他工程的历史资料。
- 相机宽屏保留取景/操作并排，工程选择限制宽度；侧边水印面板整体滚动，窄面板将字段名称与输入框上下排列，完成按钮固定可达。
- 水印样式按实际面板宽度显示 1 / 2 / 3 / 4 列。
- 无有效折痕区域时回退到普通响应式布局，避免依据半折叠状态猜测折痕位置。

## 实测

- SiteCamWideQA 三折屏展开 3184×2232，密度 460（约 1107×776 vp）：工程左右分栏、左栏搜索筛选、右侧当前工程详情通过，截图 projects-wide.jpeg。
- 同一模拟器转为 2232×3184（约 776×1107 vp）：相册由双栏变单栏，当前照片保留；转回后详情保留。
- 相册选中 5 项，横竖屏切换后仍为 5 项，随后取消选择。
- 260 vp 相机侧边水印面板：上滑后能访问六项字段及新增字段入口，完成按钮始终可见。
- SiteCamPhoneQA 360×800 vp：相册/详情返回、方向弹窗、新建弹窗、教程、水印样式取消、当前工程保留全部通过（phone-smoke.json）。
- Debug 与 Release 同源构建通过。

## 验收边界

当前无已下载的平板镜像，使用三折展开屏验证平板宽度布局，不能替代平板设备行为验收。Mate X5 模拟器能报告 HALF_FOLD，但 getCurrentFoldCreaseRegion 返回 undefined；因此悬停实景未通过验收，保留普通响应式回退。现有布局测试覆盖有效折痕上下 16 / 40 vp 避让及无效区域回退。

HarmonyOS 5/6、真实平板、悬浮窗/分屏全部组合、真实悬停和录像期间硬件输出连续性仍需对应设备验证。

## 官方参考

[Navigation 分栏](https://developer.huawei.com/consumer/cn/doc/doccenter-capabilities/arkts-navigation-split-mode)、[默认分栏页面](https://developer.huawei.com/consumer/cn/doc/doccenter-dev-faq/faqs-arkui-1276)、[折叠屏体验标准](https://developer.huawei.com/consumer/cn/doc/doccenter-ux-design/ux-guidelines-foldable-screen-0000001807866557)。保持 API 12 基线，未引入 API 20 的 splitPlaceholder。

大屏编辑实测：图片与裁剪/标注工具面板左右并排，控件边界记录在 editor-wide.json。窄侧栏样式选择页可打开及取消。普通手机模拟器已安装最终 Debug 包。
