# 鸿蒙核心修复交接（2026-10-04）

本文件记录本轮核心修改和定向验证入口。未在本代理运行构建、测试或设备；统一由主任务调度。版本、签名和既有其他工作区修改未改动。

| 编号 | 已落地修改 | 验证入口／剩余验证 |
|---|---|---|
| HCM-01 | `CameraService` 将照片 resolver、超时与 output 代次绑定；超时/捕获失败先重建输出，再允许重试；照片异步读取前后均核对请求归属，旧 capture 拒绝不结束新请求 | 真机或延迟注入：A 超时后 B 拍照，A 的晚到成功/失败不能结束 B；关闭后旧回调丢弃 |
| HCM-02 | `VideoService` 按任务代次保存取消意图，在预处理、原生转码和成品检查各异步阶段之后检查；原生调用前取消仍有效；数据库最终提交开始后按完成处理 | 分别在读取源、生成水印、原生转码及验证后取消，确认原片保留、未交付新成品 |
| HCM-03 | 新增共用快照元数据填充；首次 `Store.putMedia` 自动填入视频坐标、位置状态、地址和拍摄时间；旧无 GPS 快照也补回手动地址 | `CoreRepairChecks.videoMetadataAndLegacyAddress` |
| HCM-04 | 原生看门狗使用单调时钟记录有效解码绘制/编码写入活动，与 95% UI 进度封顶分离 | 长视频尾部持续处理超过 45 秒应继续；真正无帧活动仍应超时 |
| HCM-05 | NV12 转换读取源/解码格式的矩阵和范围，支持 BT.601/709 全/限幅；RGBA Surface、编码器及封装写入相符颜色信息 | `video_color_test.cpp` 覆盖黑白/红色色块与范围组合；实际视频颜色仍需原片/成品无水印区域比对 |
| HAR-DATA-01 | 恢复 `cameraControlStyle` 的 NORMAL/BLUR 持久化值 | `CoreRepairChecks.settingsOrderAndReopen` |
| HAR-DATA-02 | 普通设置与快捷水印保存共用一条队列，在调用时捕获各自完整快照，关闭数据库前等待队列 | 同上：交错保存工程、画质、水印后重开核对 |
| HAR-DATA-03 | 文件先重命名暂存；持久化删除日志先于文件移动；数据库事务确认当前资料路径和工程成员后删除；失败/重启按记录存在与否恢复或清理 | `CoreRepairChecks.deletionRollbackAndRecovery`：注入提交失败、提交前中断、提交后中断、工程删除、保留独立相册副本 |
| HAR-S14（核心部分） | open/close/start/stop 串行；立即使旧代次失效；每个资源获取 await 后检查代次；半途失败清理；过期镜头切换不再自动重开旧相机 | 前端代理接入页面生命周期；真机快速前后台/切模式/切镜头验证 |
| HAR-S06（接口协作） | `onFocusChanged:(state:string)=>void` 转发真正的 focusStateChange，值为 focusing/ focused/ unfocused/ unsupported/ idle | 前端代理接入；成功样式必须等 focused 回调 |

录像 profile 的帧率同时限定在所选档位的 min/max 内，修正审计候选 HCM-C01 的配置不一致。没有扩展 HDR 支持；不支持的颜色格式保留原片并返回现有处理失败流程。缺失颜色矩阵时按高清 BT.709、标清 BT.601 默认；该推断不能替代设备颜色标记检查。

## 需要主任务接入的测试

在 `entry/src/ohosTest/ets/testability/pages/Index.ets` 导入 `CoreRepairChecks`，分别通过现有 `check` 包装调用：

```typescript
await CoreRepairChecks.settingsOrderAndReopen(this.ctx);
await CoreRepairChecks.videoMetadataAndLegacyAddress(this.ctx);
await CoreRepairChecks.deletionRollbackAndRecovery(this.ctx);
```

主机颜色数值夹具（无需模拟器）从仓库根目录运行：

```sh
c++ -std=c++17 harmonyos/entry/src/test/cpp/video_color_test.cpp -o /tmp/sitecam-video-color-check
/tmp/sitecam-video-color-check
```

该夹具验证实际供给着色器的转换参数，不证明设备编码器保留色彩、音频或帧连续性。原生编译与 ArkTS 编译使用项目现有构建入口统一执行。

## 页面协作接口

- 打开 Store、创建 MediaService 后，在首轮资料刷新前 `await files.recoverDeletions()`。
- 删除资料统一 `await files.deleteMedia(ids)`；删除工程统一 `await files.deleteProjects(ids)`，替换文件删除和数据库删除的分散调用。
- `Store.putMedia` 已处理新视频元数据，页面不必重新查询实时定位。
- `CameraService.onFocusChanged` 为字符串状态回调，`focus(x,y)` 保持原签名。
- 页面与 ExportService 的取消收尾由前端代理负责，本代理未编辑 ExportService。

## 本代理修改文件

已有：`CameraService.ets`、`VideoService.ets`、`Models.ets`、`Store.ets`、`ImageProcessor.ets`、`MediaService.ets`、`video.cpp`。

新增：`MediaDeletionCoordinator.ets`、`video_color.h`、`CoreRepairChecks.ets`、`entry/src/test/cpp/video_color_test.cpp`。
