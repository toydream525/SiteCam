# SiteCam 鸿蒙原生开发版

独立 ArkTS / ArkUI Stage 工程，基于当前 Android v0.2.8 工作区迁移。Android 源码保留。此目录的实现可以构建并在本地 API 26 开发模拟器安装；**尚未达到完整迁移计划的全部验收条件**。最新核心验证为 **36 项中 35 项通过**，视频水印与音轨因模拟器没有 H.264 编解码器而阻塞。普通手机、折叠外屏和 10.2 英寸展开屏已完成相关界面回归；真实平板、悬停、HarmonyOS 5/6、混合媒体分享和系统相册完整回归仍待验收。

[下载 v0.2.8 开发包](https://github.com/toydream525/SiteCam/releases/tag/v0.2.8) · [官网说明](https://yuriaqua.com/sitecam/) · [完整验证记录](VERIFICATION.md)

系统相册入口为“另存到系统相册”：保存独立副本，取消授权不影响工程原件，删除互不联动。

## 构建与安装

本机已使用 DevEco Studio 26.0.0.821、SDK API 26、Hvigor 6.26.4、OHPM 26 和 Node 24 构建。最低兼容配置为 API 12，编译及目标 API 为 26，当前 Native ABI 为 arm64-v8a。

```sh
cd harmonyos
./scripts/build.sh debug
./scripts/build.sh release
```

脚本从现有 DevEco 安装读取工具，在 `~/Library/Caches/SiteCamHarmonyBuild` 编译，再将产物复制回来，解决 Hvigor 对中文工程路径的限制。可设置 `DEVECO_HOME` 和 `SITECAM_HARMONY_BUILD_DIR`。修改应在本目录完成；缓存目录为构建镜像。

- `dist/debug/entry-default-unsigned.hap`：调试应用。
- `dist/release/entry-default-unsigned.hap`：发布构建模式的未签名应用。
- `dist/debug/entry-ohosTest-unsigned.hap`：专用测试模块，不是正常应用入口。

这些 HAP 未使用个人证书签名，已由本机开发模拟器接受安装。鸿蒙真机安装需用户在 DevEco 完成开发者登录、设备授权与签名配置；不能直接将未签名包当作真机发行包。没有应用市场发布。

```sh
HDC=/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/toolchains/hdc
"$HDC" list targets
"$HDC" -t <目标地址> install dist/debug/entry-default-unsigned.hap
"$HDC" -t <目标地址> shell aa start -b com.sitecam.app -a EntryAbility
```

## 实现内容

- `entry/src/main/ets/pages/Index.ets`：工程、相册、拍摄、照片编辑、设置、模板、问题记录、导出和离线指引。窗口宽度 600 / 840 vp 分档，手机工程列表四卡及右侧编辑/导出，宽窗口列表与详情双栏。
- `core/Store.ets`：ArkData 关系型工程、媒体及模板数据，Preferences 设置。媒体保留原片、编辑成品、可选系统相册副本 URI、冻结的拍摄配置及处理状态。
- `core/WatermarkRenderer.ets`：八款水印共享测量与布局，预览与照片使用同一绘制代码；处理多行和长地址。
- `core/ImageProcessor.ets`、`ImageJobs.ets`：照片质量、方向、EXIF、标注和裁剪；照片处理进入任务池。原图与编辑成品独立保存。
- `core/CameraService.ets`、`LocationService.ets`：原生相机和可选定位，硬件能力不足时提示；定位/录音拒绝不作为工程资料保存条件。
- `core/VideoService.ets`、`cpp/video.cpp`：录制后经 AVCodec NDK 和 OpenGL ES 处理水印、封装和音轨，串行处理、进度、取消及失败重试。**Native 代码已编译，完整视频输出未在现有模拟器验证成功。**
- `core/ExportService.ets`：ZIP、授权下载目录、照片/视频/编辑成品、CSV 和结果报告。目录 URI 通过系统 `FileUri` 转成可访问路径，不手工解析内部 URI。
- 系统相册使用授权弹窗保存独立副本；取消不影响原件。API 26 桌面备选图标 A/B/C/D，默认 D；旧版固定 D。

## 可重复验证

```sh
./scripts/verify.py --target 127.0.0.1:5557
./scripts/ui_smoke.py --target 127.0.0.1:5557
```

脚本构建测试模块并在指定模拟器运行。它会停止该模拟器上的 SiteCam 进程，请勿在有未保存工作的设备上运行。测试数据库与主应用分离。最新报告为 `verification/api26-results.json`（36 项中 35 项通过）；视频编解码器缺失时会如实返回非零退出码。

测试页另有“验证相册副本”和“验证文件夹导出”，用于系统交互，需在模拟器操作授权。测试报告和完整限制见 [VERIFICATION.md](VERIFICATION.md)。离线用户说明在 `entry/src/main/resources/rawfile/USER_GUIDE.md`。

## 官方参考

- [Camera Kit](https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/camera-preparation)
- [原生视频后台导出实践](https://developer.huawei.com/consumer/cn/doc/best-practices/bpta-video-background-export)
- [AVTranscoder 接口及版本边界](https://developer.huawei.com/consumer/cn/doc/harmonyos-references/arkts-apis-media-avtranscoder)
- [系统文件选择与保存接口](https://developer.huawei.com/consumer/en/doc/harmonyos-references/js-apis-file-picker)
- [文件 URI 的使用](https://developer.huawei.com/consumer/cn/doc/doccenter-capabilities/user-file-uri-intro)
- [多端布局实践](https://developer.huawei.com/consumer/cn/doc/best-practices-V14/multi-video-app-V14)
- [应用配置及备选图标](https://developer.huawei.com/consumer/cn/doc/doccenter-getting-started/app-configuration-file)

开发中还直接核对了本机 SDK 的 ArkTS 类型声明和 Native 头文件。编译警告包含旧版兼容路径所用接口的弃用提示；没有将 API 26 `AVTranscoder.addWatermark` 用于基础视频路径。
