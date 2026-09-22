# H1 鸿蒙原生视频时间戳实现

日期：2026-09-13

## 实现

`harmonyos/entry/src/main/cpp/video.cpp` 的视频合成仍使用 H.264 编码器的
Surface 输入和 EGL 绘制。解码输出的 `OH_AVCodecBufferAttr::pts` 是微秒；在提交
当前 EGL 帧前，通过 API 12 基线可用的
`OH_NativeWindow_NativeWindowHandleOpt(window, SET_UI_TIMESTAMP, uint64_t)` 写入
编码 Surface。API 12 兼容路径把这个值按微秒传递；没有启用 API 20 的 PTS-based
rate-control 选项，因此不在这里套用该可选特性的纳秒约定。原先通过
`eglGetProcAddress("eglPresentationTimeANDROID")` 查找扩展的路径已移除，失败时
仍由现有清理逻辑删除临时输出并保留原片。

时间戳调用位于当前帧绘制完成、`eglSwapBuffers` 提交之前。这个顺序不是对
`RequestBuffer` 时机的猜测，而是复用 OpenHarmony EGL 包装层的同一实现：
`EglWrapperDisplay::PresentationTimeANDROID` 在
[`egl_wrapper_display.cpp` 的 1250–1271 行](https://github.com/openharmony/graphic_graphic_2d/blob/master/frameworks/opengl_wrapper/src/EGL/egl_wrapper_display.cpp#L1250-L1271)
调用 `NativeWindowHandleOpt(..., SET_UI_TIMESTAMP, time)`；入口表在
[`egl_wrapper_entry.cpp` 的 1389–1397 行](https://github.com/openharmony/graphic_graphic_2d/blob/master/frameworks/opengl_wrapper/src/EGL/egl_wrapper_entry.cpp#L1389-L1397)
把 `eglPresentationTimeANDROID` 接到该函数，随后
[`egl_wrapper_display.cpp` 的 804–826 行](https://github.com/openharmony/graphic_graphic_2d/blob/master/frameworks/opengl_wrapper/src/EGL/egl_wrapper_display.cpp#L804-L826)
把 `eglSwapBuffers` 转交给底层 EGL。应用直接调用 NativeWindow 操作后再 swap，
走的是平台扩展的实际前置操作，而不是另一个时间戳协议。

显式 NativeWindow 的复制链路可在
[`native_window.cpp` 的 184–202 行](https://github.com/openharmony/graphic_graphic_surface/blob/master/surface/src/native_window.cpp#L184-L202)、
[`215–244 行`](https://github.com/openharmony/graphic_graphic_surface/blob/master/surface/src/native_window.cpp#L215-L244)、
[`247–277 行`](https://github.com/openharmony/graphic_graphic_surface/blob/master/surface/src/native_window.cpp#L247-L277)
核对：`SET_UI_TIMESTAMP` 先写入 window，`RequestBuffer` 返回时复制到
`OHNativeWindowBuffer::uiTimestamp`，`FlushBuffer` 再把该 buffer 值放进 flush
配置。操作分发和 `uint64_t` 读取位于
[`386–390 行`](https://github.com/openharmony/graphic_graphic_surface/blob/master/surface/src/native_window.cpp#L386-L390) 和
[`550–558 行`](https://github.com/openharmony/graphic_graphic_surface/blob/master/surface/src/native_window.cpp#L550-L558)。
官方 NativeWindow 指南也明确 EGL 适配的 `eglSwapBuffers` 负责申请并提交
Buffer（[`native-window-guidelines.md` 10–15 行](https://github.com/openharmony/docs/blob/master/zh-cn/application-dev/graphics/native-window-guidelines.md#L10-L15)）。
所以在 EGL swap 前设置值是平台约定的当前 swap 输入；但底层 EGL 驱动的
Request/Flush 内部时机不是该 wrapper 源码公开的契约，不能仅凭这些源码宣称
厂商实现已完成零偏移映射。真机产物仍必须用 `ffprobe` 检查输出帧 PTS，若出现
错帧或零 PTS，当前实现不能算验收通过，需要针对该驱动改用其支持的 buffer 路径。

单位证据存在版本边界，不能把 API 12 统一写成纳秒：本机 SDK 的
`OH_AVCodecBufferAttr::pts` 是微秒；OpenHarmony 5.0/6.0 的 Surface 编码样例在
设置 `SET_UI_TIMESTAMP` 时把帧间隔从毫秒乘 1000 转为微秒，随后按该值执行显式
`RequestBuffer`/`FlushBuffer`。同一代 HEncoder 从 `AcquireBuffer` 取得时间戳后
直接作为输入 PTS 使用。当前实现沿用 API 12 兼容路径的微秒值；本机 SDK 中 API
20 的 PTS-based rate-control 说明只约束启用该可选特性时的纳秒 Surface PTS，
本项目没有启用它。MatePad 的实际输出时长和帧 PTS 仍以真机 `ffprobe` 为准。

依据：

- [OpenHarmony EGL wrapper：PresentationTimeANDROID](https://github.com/openharmony/graphic_graphic_2d/blob/master/frameworks/opengl_wrapper/src/EGL/egl_wrapper_display.cpp#L1250-L1271)
- [OpenHarmony EGL wrapper：入口表](https://github.com/openharmony/graphic_graphic_2d/blob/master/frameworks/opengl_wrapper/src/EGL/egl_wrapper_entry.cpp#L1389-L1397)
- [OpenHarmony EGL wrapper：eglPresentationTimeANDROID 注册](https://github.com/openharmony/graphic_graphic_2d/blob/master/frameworks/opengl_wrapper/src/EGL/egl_wrapper_entry.cpp#L1557-L1560)
- [OpenHarmony EGL wrapper：SwapBuffers](https://github.com/openharmony/graphic_graphic_2d/blob/master/frameworks/opengl_wrapper/src/EGL/egl_wrapper_display.cpp#L804-L826)
- [OpenHarmony NativeWindow：Request/Flush 时间戳复制](https://github.com/openharmony/graphic_graphic_surface/blob/master/surface/src/native_window.cpp#L184-L202)
- [OpenHarmony NativeWindow：RequestBuffer](https://github.com/openharmony/graphic_graphic_surface/blob/master/surface/src/native_window.cpp#L215-L244)
- [OpenHarmony NativeWindow：FlushBuffer](https://github.com/openharmony/graphic_graphic_surface/blob/master/surface/src/native_window.cpp#L247-L277)
- [OpenHarmony NativeWindow API](https://github.com/openharmony/openharmony-docs/blob/master/zh-cn/application-dev/reference/apis-arkgraphics2d/capi-external-window-h.md)
- [EGL_ANDROID_presentation_time 规范](https://www.khronos.org/registry/EGL/extensions/ANDROID/EGL_ANDROID_presentation_time.txt)
- [OpenHarmony 5.0 Surface 编码样例（微秒）](https://gitee.com/openharmony/multimedia_av_codec/blob/OpenHarmony-5.0-Release/test/unittest/video_test/video_test/sample/encoder/video_encoder_sample.cpp#L79-L110)
- [OpenHarmony 5.0 HEncoder Surface PTS 消费](https://gitee.com/openharmony/multimedia_av_codec/blob/OpenHarmony-5.0-Release/services/engine/codec/video/hcodec/hencoder.cpp#L1051-L1156)
- 本机 SDK `native_avbuffer_info.h`（`OH_AVCodecBufferAttr::pts`，微秒）
- 本机 SDK `native_avcodec_base.h`（API 20 PTS-based rate-control 开启时的 Surface PTS 纳秒约定）

## 本地构建证据

当前稳定 `video.cpp` 增量使用独立英文目录
`/tmp/sitecam-031-video-addtrack-build` 运行 `harmonyos/scripts/build.sh`，
`BuildNativeWithCmake`、`BuildNativeWithNinja`、ArkTS 编译、HAP 打包和构建收口均成功。
本轮设备复测使用主路按既有签名流程生成的对应主包和测试包；本任务未自行安装或操作
设备。

## 本轮 `eglSwapBuffers` 失败定位

前一版只在 `OH_VideoEncoder_GetSurface` 后创建 EGL window surface，没有为该编码
Surface 设置 `SET_FORMAT`、`SET_BUFFER_GEOMETRY` 和 `SET_USAGE`。随后增量曾将
CPU 编码样例的 YCBCR420 格式照搬到 RGBA EGL 配置，MatePad 实测在
`eglCreateWindowSurface` 返回 `EGL_BAD_MATCH (0x3009)`，确认两种生产格式不兼容。
当前在 `GetSurface` 成功后、`Prepare` 前设置与 EGL RGBA8 配置匹配的 RGBA_8888、
目标输出宽高，并读取并保留编码器原有用途位后只追加 GPU 写入所需的
HW_RENDER，用途操作逐项检查返回值。

这三组参数的上游依据需要分开看：编码器 Surface 样例的
`SET_BUFFER_GEOMETRY` 和 DMA 用途来自 OpenHarmony
[`venc_sample.cpp` 的 218–235 行](https://gitee.com/openharmony/multimedia_av_codec/blob/OpenHarmony-5.0-Release/test/unittest/video_test/vcodec_framework_test/stable_sample/sample/encoder/venc_sample.cpp#L218-L235)，该样例也是在
`GetSurface` 后设置；该样例的 YUV 格式只适用于其 CPU 填充路径，本实现不照用，
也不覆盖编码器原有用途位。
EGL GPU 写用途来自
[`yuv_viewer.cpp` 的 87–94 行](https://gitee.com/openharmony/multimedia_av_codec/blob/OpenHarmony-5.0-Release/test/unittest/video_test/video_test/sample/yuv_viewer/yuv_viewer.cpp#L87-L94)，SDK 的
[`native_buffer.h` 用途定义](https://github.com/openharmony/third_party_nativeapi/blob/master/sysroot/usr/include/native_buffer/native_buffer.h#L75-L86)也将其标为 GPU write。
EGL wrapper 的[窗口 Surface 系统测试](https://github.com/openharmony/graphic_graphic_2d/blob/master/frameworks/opengl_wrapper/test/systemtest/opengl_wrapper_api_test.cpp#L228-L245)
只显式设置用途和几何、保留默认 RGBA，这与当前 GPU 生产路径相符。本轮 MatePad
成品已验证编码器正确消费该 RGBA 路径。

同时，GLES3 路径改为 VBO/VAO 提交顶点，绘制后检查 GL 错误；swap、线程切换和
解绑失败均带 EGL 错误码，解码回调异常先解绑当前线程 EGL context 再由收尾逻辑
释放资源。视频时间戳仍在当前帧绘制后、swap 前直接写入 `SET_UI_TIMESTAMP`，保持
API 12 兼容路径的微秒输入。本轮 MatePad 成品已完成文件级 PTS、时长、方向、
水印和音轨复核。

## 真机验收状态

同一 `video_fixture.mp4` 已在 MatePad Mini MLR-AL10（HDC
`5KPBB25C16200207`，API 26）上完成真实转码，主路最终结果为 53/53 通过。
结构化文件证据见
[`harmony-video-file-review.json`](./harmony-video-file-review.json)。源和成品均为
90 帧 H.264、320×240；源视频首帧 PTS 为 0、末帧为 2.966667 秒，成品首帧 PTS
为 0、末帧为 2.933333 秒，二者 PTS 均严格递增。成品视频时长为 2.966667 秒，
AAC 单声道 48 kHz 音轨为 141 帧、3.008 秒；源 AAC 为 141 帧、3.000 秒。

FFmpeg 对成品完整音视频解码退出码为 0，错误输出为空；已实际查看成品首、中、尾
帧，动态测试图和完整水印均存在。该结果覆盖夹具转码、文件结构、PTS、解码和
首中尾帧水印检查，不等同于物理摄像录像验收，也不宣称已完成听觉声画同步主观
验收。
