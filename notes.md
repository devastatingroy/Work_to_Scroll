# 调试经验与运行注意事项

## 同图多次回放时的MediaPipe位图所有权

适用范围：v49–v52已结项人体诊断代码；当前v46源码不包含`PresenceReplayCheck`，仅在恢复stash `c61d048`或复核历史证据时使用。

- 本项目所用tasks-core 1.0.0的`BitmapImageContainer.close()`会调用`Bitmap.recycle()`；已对本地依赖classes.jar用javap核实。关闭MPImage后不能再把原Bitmap拿去第二次检测或裁剪，否则可能进入JNI崩溃，而不只是抛Java异常。
- 离线多变体回放应保留解码原图，每次检测创建独立ARGB_8888副本，并在交给SDK前用sameAs确认像素相同。该检测结束后关闭MPImage，只释放其自有副本；原图到本张全部变体完成后再释放。不要通过长期不关闭MPImage规避问题。
- 回放先核对PNG/模型哈希，再让所有原图复现原日志分数和候选数量，基线不一致时不继续解释裁剪收益。测试专用入口为androidTest的`PresenceReplayCheck`，不属于日常监控链路。

## 摄像头检测状态读取

- 分辨率目标不等于实际输入：本机 0.4.2 首次配置试跑中，旧 ImageAnalysis.setTargetResolution(640,480) 与新低分辨率预览组合仍协商为 2448×2448；应读取 ImageProxy 实际宽高并与 media.camera 的流尺寸交叉核对。已改用传感器坐标下的 ResolutionSelector/ResolutionStrategy，实际结果见发热任务。不要仅凭源码写了“640×480”就认定图像转换负载很小，也不要把一次组合实测直接推广为所有旧版运行尺寸。
- 断线对照可用 scripts/camera-thermal-record.sh：推送到 /data/local/tmp 后以 nohup 启动，输出必须为唯一的 /data/local/tmp/wts-thermal-*.txt，参数 armed 600；待“不充电且有新鲜监测”再开始，每分钟一份共 11 份端点/过程数值。完成后检查 COMPLETE、实际时长、每份 powered/fresh_at_sample 及版本；smoke 5 只验证记录器。后台 shell 能否在本次断线后存活必须由实际结果确认，不能用连线短测替代。
适用范围：0.4.1 起主 App 的前置人体检测。使用[读取脚本](scripts/camera-health-check.ps1)查看最新数值状态，例如在项目根目录执行 `./scripts/camera-health-check.ps1 -Samples 6 -IntervalSeconds 1`。需要设备通过 ADB 连接并已授权；脚本只读，不会打开摄像头、切换页面或修改账本，也不读取预览图像。

- 核对的是主 App `com.roy.worktoscroll.overlaytest` 的私有 `files/camera-status.json`，不要混用独立实验应用的 `status.json`。主 App 固定前置，读数应为 `lens=front`。
- 先看 `live`：进程存在、监测开启、心跳年龄不超过 2.5 秒且采集结果年龄不超过 1.5 秒时才输出有效检测结论。`live=false`、文件不存在或读取失败只说明无法确认当前检测，不等于没人；暂停后磁盘上的旧快照不能当作实时状态。
- `people` 是人体候选数，`reliable` 是可靠人体数。至少一个可靠候选即可作为在场线索，候选较多本身不会暂停；只有弱候选、零候选或无效结果才按各自的确认/宽限规则暂停。“欢迎归来”只表示出现可靠人体，不证明身份、工作行为或计时已经恢复。
- `audio_status` 是播放器报告，不能替代实际听觉确认。提示音反映首次明确结果和检测变化，计时另有 1 秒确认与 3 秒离开宽限，核对异常时需分别记录。
- 结合用户描述的实际动作、姿势、机位和光照解释数值；当前v46日常监控不保存相机截图或录像。每秒最多一次推理只是软件限频，相机仍持续采集，不能据此推定发热或耗电已达标。v49–v52曾有经明确授权的限时静态图诊断，已结项且不在当前源码中。

版本、试用反馈及待验项目统一见[侧后方任务](tasks/completed/side-rear-presence-exploration.md)，本节只保留读取方法与判断边界。

## 工作会话调试日志

适用范围：0.5.11 起的主 App 调试构建。工作结束或重新连接手机后，在项目根目录运行 `./scripts/work-session-debug.ps1 -Serial '<设备号>'`，可只读查看最近一次摘要、关键诊断事件及原始事件尾部；脚本不打开摄像头、不切换页面、不修改账本。

- `test_valid_ms` 是本次开始后的历史累计增量，跨三分钟轮次重置仍连续；`round_valid_ms` 只代表当前轮，不能代替本次总量。
- `ROUND_RESET` 记录当前轮因连续中断达到三分钟而归零；`INTERRUPTION_START/END` 记录中断边界；`CLOCK_STATE` 同时注明 `observation_kind`（无人、弱候选、多候选等）；`AVAILABILITY_LOST/RESTORED` 记录窗口焦点、锁屏、交互状态与触发入口；`PAUSE_ALERT_DUE/MUTED` 记录首次异常暂停提醒，`PAUSE_ALERT_REPEAT_DUE/MUTED` 记录从30秒起按倍增间隔触发的后续提醒及周期，`PAUSE_FLASH_*` 记录视觉慢闪，`AUDIO_*` 分别记录声音请求、实际开始、完成、取消、抑制或错误。`CHECKPOINT` 约每分钟保底记录一次；`WORK_FINISH` 和 latest 的 `outcome=finished` 是正常结束证据。
- 0.5.18 起，latest 和 `WORK_END_REQUESTED` / `WORK_FINISH` 包含 `start_battery_*`、`end_battery_*` 与 `battery_temperature_delta_c`。起点取本轮首次开始监测，暂停后继续不会改写；终点在点击“结束工作”后、释放相机前读取。`*_battery_temperature_available=false` 或 `*_battery_read_error` 表示读取失败，不能据此计算温升。温度是系统电池温度，不是机身表面、CPU 或摄像头芯片温度；充电状态不同的两端不能作为干净的发热比较。
- 日志只在调试构建中启用，位于应用私有目录，超过 512 KiB 后保留一份轮转旧文件。没有 `WORK_FINISH` 时仍可用 latest 查看截至最后一次成功写入的累计，但不能把缺失尾段包装成已完整记录。

### 发热测试记录卡

以后每轮监控发热观察按下列固定格式记录。先从设备和工作日志读取客观字段，再向Roy核对现场字段；Roy没有提供的现场条件必须逐项提醒补充，暂时写“未提供”，不得沿用上一轮或自行推断。

| 类别 | 必记内容 |
| --- | --- |
| 身份 | 日期、开始/结束时间、手机型号、App版本、测试类型（日常耐久/单变量对照/定位） |
| 工作量 | 墙钟时长、有效记录时长及比例、中断/重置次数、相机释放和重绑情况 |
| 相机与显示 | 前/后摄像头、请求与实际分辨率、帧率/推理间隔、预览全程开启/关闭/中途变化、诊断采样是否开启；亮度必须单列自动/手动模式、系统亮度值或滑块、应用窗口亮度覆盖，以及关闭预览后是否实际变暗、结束后是否恢复 |
| 现场环境 | 使用活动（静坐、开车等）、手机摆放位置和朝向、支撑物/接触面材质、手机壳、室内/车内、空调或其他冷热源、阳光直射、可取得时记录大致环境温度 |
| 供电与网络 | 起止是否接电及供电来源、起止电量/电压/电荷计数、网络类型及导航/高负载网络活动 |
| 温度与体感 | BatteryService起止温度和温差；有过程记录时列10分钟节点；20/40分钟或结束时的触感、系统温控提示、监控是否异常 |
| 结论边界 | 本轮能回答的问题、与历史样本可比/不可比的条件、缺失字段、是否达到对应验收门槛及下一步 |

小米14 Pro / HyperOS 3 的实测边界（2026-09-14，v52）：全机自动亮度开启时，`WorkActivity`的窗口`screenBrightness=0.05`能够实际触发系统`OverrideBrightnessStrategy`，系统记录约72.7 nit降至26.5 nit，同时自动亮度模式保持开启；结束工作撤销覆盖后重新由环境光自动调整。系统亮度滑块或`screen_brightness`设置值不会等比例反映窗口覆盖，更不能当作实际面板亮度。开/关预览对照会同时改变Preview绑定和工作页亮度，若要区分二者贡献，必须另做固定亮度的单变量试验。

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
