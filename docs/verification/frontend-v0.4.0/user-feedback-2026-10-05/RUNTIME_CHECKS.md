# 本轮执行与界面检查

## 构建与逻辑检查

- Android：46 个测试套件、171 项测试通过；无失败、错误或跳过。包含最终大字号预算修正，Release/Debug 构建通过，最后一轮耗时 1 分 52 秒。
- HarmonyOS：保留原签名的主包构建通过。当前源码的定位新鲜度、注入服务边界、地址避让检查通过；镜头 18 条、三区布局 28 条断言通过。加入大字号实际内容预算后，普通手机握持 8 条、布局边界 50 条断言再次通过，主包重新构建成功，最后一轮耗时 5.960 秒。
- Android 指南源文档与构建资产一致；鸿蒙内置 18 节指南与 rawfile 正文一致。
- F7 首次界面复测仍发现裁切。之后定点修正对话框尺寸测量和系统主题，重新构建后完成横竖屏、默认/两倍字号截图与实际取消/确认操作，均通过。

## Android 界面

使用既有 `SiteCam_API37_Pixel8` 模拟器，Android API 37，默认 1080 × 2400、420 dpi、字号 1.0。仅启动一台，1.5 GB 内存、2 核，与重型构建串行。相机彩色测试图来自模拟器，不是真实现场或真实镜头视野证据。

| 检查 | 当前结果与证据 |
| --- | --- |
| 竖屏工程入口与握持 | 工程名与箭头收紧；快门下方有独立留白。[01](01-android-initial.png) |
| 方向四选菜单 | 竖屏锚定方向图标；横屏朝取景侧展开，没有覆盖右侧拍摄栏。[02](02-android-direction-menu.png)、[04](04-android-landscape-direction.png) |
| 横屏取景与倍率 | 4:3 取景保持；倍率在右侧操作区，不与水印重叠；右侧保留握持留白。[03](03-android-landscape.png) |
| 普通／透明 | 设置可选两项；切透明后控件底板明显减淡，图标保留。[06](06-android-appearance-options.png)、[07](07-android-transparent.png)，普通外观见 01。 |
| 四步指引 | 四幅采用图原插图均显示，下一步依序到末页“开始使用”。[08](08-android-guide-step1.png)、[09](09-android-guide-step2.png)、[10](10-android-guide-step3.png)、[11](11-android-guide-step4.png) |
| 平板竖屏指引布局 | 同一模拟器临时设置 1600 × 2560、240 dpi。首轮 [12](12-android-guide-tablet-window.png) 暴露标题与页点/按钮间距过大；修正后 [18](18-android-guide-tablet-fixed.png) 显示连续组合、居中标题与紧凑主按钮。独立目检通过，不算真实平板验收。 |
| 样式选择器 | 首轮横屏仍裁切：[05](05-android-style-landscape.png)。修正后竖屏默认 [13](13-android-style-portrait-fixed.png)、竖屏两倍字号 [15](15-android-style-portrait-font2.png)、横屏两倍字号 [16](16-android-style-landscape-font2.png)、横屏默认 [17](17-android-style-landscape-fixed.png)，四边与两个按钮完整，系统浅色主题生效。横屏默认八款样式可见，点“使用此样式”实际返回编辑页；取消也实际可用。 |
| 相机两倍字号 | [14](14-android-camera-font2-before.png) 发现模式/工具行被固定快门区裁切。修正普通手机竖屏的实际内容预算并补几何回归后，[19](19-android-camera-font2-fixed.png) 确认模式/工具行全文完整，快门下方握持留白保留，取景等比缩小。[20](20-android-camera-default-final.png) 确认默认字号画面完整，两图经独立目检通过。 |

结束时读回确认模拟器恢复原生 1080 × 2400、420 dpi、字号 1.0；应用恢复普通控件外观与自动方向，本轮模拟器已关闭。19/20 均使用原生 420 dpi；先前平板测试的密度覆盖已清除。

## 设备边界

本轮 Android 与 HarmonyOS 真机均未连接。实际多镜头视野与切换、跨镜头连续缩放、录像输出组合，以及鸿蒙静置持续定位、地址超时与刷新入口恢复仍待真机确认。宿主逻辑检查和模拟器画面不替代这些结果。
