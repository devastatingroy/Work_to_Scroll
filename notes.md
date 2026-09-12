# 调试经验与运行注意事项

## 摄像头检测状态读取

- 分辨率目标不等于实际输入：本机 0.4.2 首次配置试跑中，旧 ImageAnalysis.setTargetResolution(640,480) 与新低分辨率预览组合仍协商为 2448×2448；应读取 ImageProxy 实际宽高并与 media.camera 的流尺寸交叉核对。已改用传感器坐标下的 ResolutionSelector/ResolutionStrategy，实际结果见发热任务。不要仅凭源码写了“640×480”就认定图像转换负载很小，也不要把一次组合实测直接推广为所有旧版运行尺寸。
- 断线对照可用 scripts/camera-thermal-record.sh：推送到 /data/local/tmp 后以 nohup 启动，输出必须为唯一的 /data/local/tmp/wts-thermal-*.txt，参数 armed 600；待“不充电且有新鲜监测”再开始，每分钟一份共 11 份端点/过程数值。完成后检查 COMPLETE、实际时长、每份 powered/fresh_at_sample 及版本；smoke 5 只验证记录器。后台 shell 能否在本次断线后存活必须由实际结果确认，不能用连线短测替代。
适用范围：0.4.1 起主 App 的前置人体检测。使用[读取脚本](scripts/camera-health-check.ps1)查看最新数值状态，例如在项目根目录执行 `./scripts/camera-health-check.ps1 -Samples 6 -IntervalSeconds 1`。需要设备通过 ADB 连接并已授权；脚本只读，不会打开摄像头、切换页面或修改账本，也不读取预览图像。

- 核对的是主 App `com.roy.worktoscroll.overlaytest` 的私有 `files/camera-status.json`，不要混用独立实验应用的 `status.json`。主 App 固定前置，读数应为 `lens=front`。
- 先看 `live`：进程存在、监测开启、心跳年龄不超过 2.5 秒且采集结果年龄不超过 1.5 秒时才输出有效检测结论。`live=false`、文件不存在或读取失败只说明无法确认当前检测，不等于没人；暂停后磁盘上的旧快照不能当作实时状态。
- `people` 是人体候选数，`reliable` 是可靠人体数。多人或只有弱候选时计时可能暂停；“欢迎归来”只表示出现可靠人体，不证明身份、工作行为或计时已经恢复。
- `audio_status` 是播放器报告，不能替代实际听觉确认。提示音反映首次明确结果和检测变化，计时另有 1 秒确认与 3 秒离开宽限，核对异常时需分别记录。
- 结合用户描述的实际动作、姿势、机位和光照解释数值；不为诊断保存相机截图或录像。每秒两次分析是软件限频，相机仍持续采集，不能据此推定发热或耗电已达标。

版本、试用反馈及待验项目统一见[侧后方任务](tasks/active/side-rear-presence-exploration.md)，本节只保留读取方法与判断边界。

## 工作会话调试日志

适用范围：0.5.11 起的主 App 调试构建。工作结束或重新连接手机后，在项目根目录运行 `./scripts/work-session-debug.ps1 -Serial '<设备号>'`，可只读查看最近一次摘要及事件尾部；脚本不打开摄像头、不切换页面、不修改账本。

- `test_valid_ms` 是本次开始后的历史累计增量，跨三分钟轮次重置仍连续；`round_valid_ms` 只代表当前轮，不能代替本次总量。
- `ROUND_RESET` 记录当前轮因连续中断达到三分钟而归零；`INTERRUPTION_START/END` 记录中断边界；`CLOCK_STATE` 记录有人、宽限、离开等状态变化；`CHECKPOINT` 约每分钟保底记录一次；`WORK_FINISH` 和 latest 的 `outcome=finished` 是正常结束证据。
- 日志只在调试构建中启用，位于应用私有目录，超过 512 KiB 后保留一份轮转旧文件。没有 `WORK_FINISH` 时仍可用 latest 查看截至最后一次成功写入的累计，但不能把缺失尾段包装成已完整记录。

## 小米后台冻结导致零余额拦截失效

适用范围：Xiaomi 14 Pro 上的 WorkToScroll，应用 ID `com.roy.worktoscroll.overlaytest`。证据来自2026-09-08、0.2.5-protection-fix及其前一版本的真机排查，不代表所有机型或后续版本已经验证。

### 现象与原因

- 余额为零、保护开关开启、使用情况访问和悬浮窗权限正常，小红书仍可以继续浏览。
- 系统仍列出前台服务，ActivityManager甚至显示`isFrozen=false`，但实际`cgroup.freeze=1`、`cgroup.events`中的`frozen=1`，进程等待位置为`do_freezer_trap`；监测更新时间持续落后。
- 小米`greezer`记录了应用退到后台、熄屏和应用切换时的冻结。本应用省电策略实际选中“智能限制后台运行（推荐）”，其系统说明包含限制后台CPU占用。
- 进程被冻结后，监测、扣减和显示阻止页的代码无法继续执行。服务存在、通知存在和保护意愿开启都不能证明实际保护有效；进程未死亡时，`START_STICKY`也不能替它解冻。

### 处理方法

1. 先保留故障现场，运行[只读保护诊断](scripts/protection-health-check.ps1)，核对监测点新鲜度、实际冻结状态、目标前台状态和阻止窗口可见性。不要先打开WorkToScroll，以免前台唤醒掩盖故障。
2. 进入系统的WorkToScroll应用信息 → 省电策略，将本应用从“智能限制后台运行”改为“无限制”，并读回选中状态。只调整本应用，不关闭系统全局冻结机制。
3. 必要时打开WorkToScroll一次恢复服务，再直接进入小红书验证。之后的后台测试不应先回WorkToScroll，避免靠手动唤醒才能通过。
4. 应用恢复时须重建当前前台状态，不补扣未监测区间；应用内恢复代码不能代替系统后台配置修正。实际机制见[ARCHITECTURE.md](ARCHITECTURE.md)。

### 已有证据与验证边界

- 调整后短时连续采样显示：进程未被冻结，监测点持续更新，小红书在前台时阻止层可见，余额仍为0，历史累计消费仍为1080000毫秒。
- 系统窗口记录为`shown=true`、`isOnScreen=true`、`isVisible=true`；实际滑动阻止和退出体验仍需用户确认。小红书能够启动本身不算失败，当前方案应通过覆盖页阻止继续浏览。
- 尚未完成长时间后台停留、锁屏恢复及半天到一天正常使用的稳定性验收，不能把短时恢复表述为彻底解决或跨机型可靠。
- 测试中若回到WorkToScroll首页，该段不能算纯后台耐久证据。系统应用详情可能缓存旧版本文字，应以包管理器读数核对安装版本。
- 后续验收与当前停点统一见[任务索引](tasks/README.md)，本文件保留可复用经验，不维护测试流水。

## 小米开机后保护未恢复的取证

适用范围：0.5.10-boot-recovery 起的 `com.roy.worktoscroll.overlaytest`；只适用于“小红书保护”恢复，不适用于必须手动开始的摄像头工作监测。

- 小米默认自启动限制涵盖开机自启动和接收系统广播。“无限制”省电策略、锁定最近任务和自启动是三个独立条件；应用不能可靠读取或代开自启动，应由用户在系统设置中搜索“自启动”后确认。
- 复现时先正常重启并解锁，**不要打开 WorkToScroll**；连接已授权 ADB 后运行 `./scripts/protection-health-check.ps1`。只有 `boot_recovery_outcome=STARTED`、`boot_recovery_matches_current_boot=true`、保护进程存在且监测点新鲜，才可将本次恢复作为系统结构证据；随后仍须用目标 App 的实际扣减或阻止核对体验。
- `NO_BOOT_RECORD` 表示已建立过启动编号基线、保护仍开启、而当前启动编号没有最小恢复记录；它提示优先核对小米自启动，但不能单独证明系统一定没有投递广播。`START_REJECTED`、`MISSING_PERMISSION`、`STORAGE_FAILED` 分别缩小到服务启动、授权或存储路径；先保存该只读结果，再打开应用恢复，避免前台唤醒覆盖现场。
