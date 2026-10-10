# 本轮构建与单模拟器运行记录

日期：2026-10-03。主审执行；仅记录本轮取得的结果。

## 构建和自动测试

- Android 当前工作区执行既有 `:app:testDebugUnitTest`：146 项，145 通过、1 失败、0 跳过。失败是 `AppIconVectorTest` 仍读取已被 PNG 资源替换的 `ic_launcher_c_foreground.xml`；没有将测试失配等同于桌面图标运行失败。详见 `android-unit-test-results.json` 和 `android-unit-tests.log`。
- Android 当前源码 `:app:assembleDebug` 成功。构建使用单 worker、无并行任务、1536 MB Gradle 堆；单元测试进程限制为单实例、1024 MB 堆。测试及构建时模拟器全部关闭。
- HarmonyOS 当前源码同步到既有构建缓存，用已有 SDK 和依赖执行 debug `assembleHap`，关闭守护进程和并行任务，采用 memory 策略、1024 MB Node 堆。33 个任务中 19 执行、14 为增量复用，ArkTS、原生构建、资源及打包均完成，结果 `BUILD SUCCESSFUL`。产物为构建缓存内的未签名 HAP，未安装或发布；详见 `harmony-debug-build.log`。编译通过不表示鸿蒙设备运行通过。

## Android AND-CM01 定向复现

环境为既有 SiteCam_API37_Pixel8、emulator-5554、Android API 37，启动参数为 1536 MB 客体内存、2 核；不同时运行其他模拟器。当前源码 debug APK 保留数据更新安装，包名 `com.sitecam.app.debug`，版本 0.4.0（20），系统记录更新时间 23:41:48，见 `runtime-package.txt`。未替换独立的 release 包。

1. 通过正常界面新建独立工程 `FullAudit-20261003`（id 2），初始媒体数为 0，见 `runtime-before.json`。
2. 正常开始并按停止结束第一段录像；保存为媒体 id 4，`READY`，数据库时长 39,992 ms，见 `runtime-baseline.json`。本记录仅确认完成和入库，没有用它声称全部音画品质验收通过。
3. 第二段录像日志在 23:49:29.608 确认 `VideoRecordEvent Start`。`01-recording-before-home.png` 显示正在录制、计时 00:10。切后台前缓存存在 `temp_record_1791042567441.mp4`，大小 57,355 字节，见 `recording-cache-before-home.txt`。
4. 按 Home 后日志先在 23:49:49.819 记录视频源 `INACTIVE`，再于 23:49:54.366 记录 `Finalize [error: ERROR_SOURCE_INACTIVE]`。关键日志见 `recording-events.log`，完整应用进程日志见 `android-runtime-camera.log`。
5. 此后录制临时文件消失，见 `recording-cache-after-home.txt`；数据库仍只有正常停止产生的媒体 id 4，没有新增第二条，见 `runtime-after-home.json`。返回相册的 `02-return-after-home.png` 同样显示“1 项 · 照片 0 · 视频 1”。

这组证据确认了“Home 中断 → SOURCE_INACTIVE → 删除临时原片且未生成媒体条目”的路径。被删原片没有事先复制或播放，不能据此称其已通过可播放性验证。缓存大小是切后台前的观测值，不是最终封装文件大小。

## 资源收尾

开始本轮全面检查时已关闭原有两台模拟器并停止旧 Gradle 构建守护进程；运行补证阶段仅开启上述一台。启动后曾观察到 macOS 内存压力值 2，后续为 1，交换空间使用约 6 GB。补证结束已正常关闭该实例，`adb devices -l` 为空；随后才执行鸿蒙构建。没有退出无关用户应用，未删除既有工程和媒体，未计算文件哈希。
