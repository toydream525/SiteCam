# Luna implementation handoff

本批次收尾 H2–H5（鸿蒙端）：

- `harmonyos/entry/src/main/ets/core/Store.ets`：视频状态、视频处理结果、系统相册 URI、问题与编辑成品均走窄字段更新；检查影响行数，已删除媒体拒绝迟到写入；问题关联按当前媒体工程同步；旧设置读取补默认字段。
- `harmonyos/entry/src/main/ets/core/Models.ets`：内置海拔字段明确默认关闭；设置重开使用保留式迁移，保留用户字段标签、值、开关和顺序；完整旧内置字段序列才插入默认关闭的海拔字段。
- `harmonyos/entry/src/main/ets/core/Store.ets`：旧模板字段迁移仅对包含地址和 GPS 的完整字段集执行，并将海拔插入地址后，保留既有字段和部分模板不变。
- `harmonyos/entry/src/main/ets/core/VideoService.ets`：处理开始前重新读取媒体；失败只更新状态，处理期间数据库读取也受 `finally` 保护。
- `harmonyos/entry/src/main/ets/pages/Index.ets`：刷新按结束时当前详情 ID 同步，避免异步刷新回滚并清理已删除详情；相册副本冻结媒体 ID；编辑入口、预览、保存统一使用冻结编辑目标，跨资料切换时丢弃迟到预览；选中媒体导出开始时冻结原工程并重新读取媒体和工程，媒体删除/移动时提示刷新，正常情况下按最终媒体所属工程导出。
- `harmonyos/entry/src/ohosTest/ets/testability/pages/Index.ets`：已注册 `Review031DataChecks` 的移动后处理/问题保留与删除后拒绝迟到写入两项回归检查。

验证：

- `SITECAM_HARMONY_BUILD_DIR=/tmp/sitecam_harmony_build bash harmonyos/scripts/build.sh`：`BUILD SUCCESSFUL`（33 tasks，17 executed，16 up-to-date）。
- 上述迁移修正后已再次通过同一鸿蒙构建；本次构建 `BUILD SUCCESSFUL`（33 tasks，17 executed，16 up-to-date）。
- 构建输出仍为未签名 HAP；本批未操作设备。主代理负责签名测试包及华为平板真机回归。
- 主代理随后已用当前源码生成并覆盖安装签名测试包；平板自动锁屏，系统拒绝启动测试，迁移真机结果待设备解锁后复测。
- 本批范围外的闪光模式图标/持久化及普通直屏倍率临时草稿已撤回，后续批次按完整目标继续。
