# Android 数据与字段修复

日期：2026-10-04。依据全面检查的 AND-DATA-01/02/03 及前端 AND-007/008，修改当前工作区，保留既有 WIP、应用标识、签名、版本和用户资料。未改动真实媒体；未运行构建、测试或模拟器，执行验证由主任务串行调度。

| 问题 | 已实施改动 | 相关文件 |
|---|---|---|
| AND-DATA-01 | 每项删除先在可取消阶段读取当前媒体/问题/标注，确认任务仍活动；开始移除索引后使用有限的 NonCancellable 临界区，完成物理删除或补偿。媒体与问题恢复同一 Room 事务；标注恢复失败不再声称恢复成功。批量项目之间/媒体之间仍可取消 | MediaDeletionCoordinator、ProjectViewModel、GalleryViewModel |
| AND-DATA-02 | 新增工程删除专用查询，包含 isUnavailable 记录，统一处理原件及独立标注文件。正常相册可见查询不变 | MediaItemDao、MediaDeletionCoordinator |
| AND-DATA-03 | 工程成员枚举、逐项删除、最终项目删除使用一次媒体锁；内部调用不再次取同锁。移动在相同锁内确认目标工程存在及每项原工程归属后提交；过期删除请求不能删除已移到其他工程的媒体 | MediaOperationCoordinator、MediaDeletionCoordinator、GalleryViewModel、ProjectViewModel |
| AND-007 | createProject 新增 selectForCapture，默认 false；管理创建只保存并发出新行事件。显式 true 才执行既有“所有拍摄完成后选择新工程”语义，最近工程也只随显式选择更新 | ProjectViewModel；页面来源由主任务接线 |
| AND-008 | 字段内容和开关改成列级 DAO 更新，进入既有应用级 FIFO；标签、排序、增删、恢复呈现也按同一输入顺序完成。移动在执行时读取最新排序，不带回旧实体。已接受写入不依赖编辑页存活 | WatermarkDao、WatermarkTemplateMutations、WatermarkEditorViewModel；CameraViewModel 两个快捷字段入口由相机负责人接入同一 fieldValue/fieldEnabled API |

锁顺序保持为拍摄协调锁→媒体锁。工程删除仍由 ProjectViewModel 先确认没有拍摄预约，再调用协调器；相册移动只取得媒体锁。没有非重入媒体锁嵌套。取消不会强制等待整个批量工程任务，只完成已经开始的单项文件/索引收尾；最终工程删除与清除该工程选择一并收尾。

配合录像恢复负责人，MediaItemDao 另增按 contentUri 读取现有记录的定向查询，不筛 isUnavailable，用于已有原片恢复去重；本分任务没有修改 MediaStoreManager、PhotoDetailViewModel 或视频恢复实现。

## 新增与调整的验证

- `MediaDeletionIntegrationTest`：5 个测试使用真实内存 Room 与独立临时媒体文件，覆盖标注/媒体索引提交后取消、物理删除失败期间取消后恢复媒体及问题、不可用原件关联的标注清理、删除期间双向移动等待并拒绝过期请求、移动先完成时保留移往新工程的文件与问题。
- `ProjectCreationSelectionTest`：使用真实 Room 与独立 DataStore，确认管理创建 B 后当前工程和最近列表仍为 A；显式创建并使用 C 才切换选择。
- `WatermarkTemplateStateTest`：新增受控延迟字段写入，交错内容/开关/标签/排序并退出编辑页后核对最终记录；既有恢复默认测试改为注入共享应用级写入队列。
- 修改文件的空白差异检查已通过。以上新增测试尚未执行，不将静态检查记作测试通过。

## 尚待主任务验证

执行上述测试及既有数据事务、媒体可用性、删除、拍摄协调、水印状态相关测试；大屏工程/相册双栏实际交互、系统相册回收站提供方和页面退出时序仍需设备验收。物理删除与数据库分属不同存储，本次补齐的是页面协程取消与既有失败补偿；未引入全目录扫描、数据库迁移或额外媒体副本，也不宣称进程强杀下具备跨存储事务原子性。
