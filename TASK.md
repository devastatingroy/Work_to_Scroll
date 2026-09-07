# Task：WorkToEarn Android Gate 1 真机原型

## 当前结论与停点

2026-09-07：WorkToEarn 0.1.2-gate1 的核心功能已完成本机技术验证，等待 Roy 体验验收；不是完整 MVP，也没有完成长期或跨机型可靠性验证。

- 最新用户体验反馈（2026-09-07）：Roy 已亲手恢复强行停止后的保护，评价为“中等麻烦”，走了一次弯路、找错一次入口；更重要的是，无障碍授权的系统警告造成明显不信任与劝退感。手动路径已体验，不等于体验验收通过；当前讨论重点为授权信任门槛及同类产品对照，尚未确定改动方案。下文先前 ADB 测试时“手动体验待验收”的描述属于当时记录。
- 当前工作（设计探索，2026-09-07）：已完成不使用无障碍权限的 API、同类产品说明与源码调查，见下方“无障碍替代路线调查”。候选为使用情况访问 + 普通悬浮窗 + 前台服务；有公开 API 依据，尚无替代原型或真机闭环证据。本轮未改功能、权限或正式产品要求；是否制作独立验证版尚未确定。

- 本阶段为可侧载的自用测试 APK；人工加入余额，不验证工作行为或自动发放工作奖励。
- 2026-09-07 本次强停复测收尾时已恢复保护：`enabled=true`，余额 `15636 ms`，累计扣减 `1320912 ms`，浮层次数 `19`；无障碍服务重新绑定。上述数值是复测后的设备快照，不代表以后手机状态不变。
- 使用情况访问未在本次复测中改动；无障碍配置在测试前读取、测试后按原值通过 ADB 恢复。没有测试脚本继续运行。
- 支撑文档已按源码、测试日志及本轮真机记录核对，本机文档链接与两轮消费摘要检查通过；本轮不改 App 代码和 SPEC 产品规则。项目此前无 Git 仓库，本轮按用户要求建立本地版本基线，构建产物与本机配置保持忽略。

## 已确认范围

- 名称为 WorkToEarn；保留旧项目目录和应用 ID `com.roy.worktoscroll`，便于覆盖更新并保留已有测试数据与配置。
- 第一阶段用手边 Android 真机验证，不等待 iPhone；这不等于最终只发布 Android。
- 仅验证标准版小红书 `com.xingin.xhs` 的“零余额拦截 → 人工加入 5 分钟 → 按前台使用扣减 → 耗尽再拦截”。
- 当前不加入摄像头、PC 扩展、云同步或应用商店上架；后续候选顺序见 [DESIGN](docs/DESIGN.md) 第 19 节，不视为已授权实施。

## 本阶段验收清单

- [x] 构建测试 APK、完成安装和必要的用户授权。
- [x] 零余额打开小红书出现阻止界面；首页和浮层显示 WorkToEarn。
- [x] 人工加入 5 分钟后可以使用小红书。
- [x] 连续及分段消费均扣满 300000 ms 后归零并重新拦截。
- [x] 桌面暂停期间余额不变且监测游标推进；回到小红书继续扣减。
- [x] 首次实验观察到锁屏后至少 20 秒余额不变；不是完整锁屏/解锁组合矩阵。
- [x] “返回 WorkToEarn”与“回到桌面”按钮真机回归通过。
- [x] 强行停止再冷启动后余额与累计扣减保留；无障碍服务会被系统移出启用列表，冷启动不会自动恢复。本次复测通过 ADB 恢复测试前配置；Roy 尚未验收真实手动恢复路径。
- [x] 使用情况访问暂时关闭时明确提示失效，恢复原授权后识别正常。
- [x] 完成计时对照、三次零余额拦截延迟粗测并记录测量边界。
- [~] 拦截速度、退出路径（回应用/回桌面）经 Roy 体验确认可接受（2026-09-07）；Roy 已完成手动恢复，反馈中等麻烦、找错一次入口，且授权警告明显劝退，恢复与授权体验尚未通过。既有样本中强停与模拟崩溃需要手动恢复；重启恢复、`am kill` 未终止进程，不代表所有实际内存回收路径均已验证。
- [ ] 扩展可靠性验证：普通进程回收、手机重启、跨日、长时间后台、反复锁屏/解锁；不与强行停止测试混为一谈。
- [ ] 建立可接受误差与延迟标准，并补测切换边界；当前粗测不构成精度承诺。

## 当前交付与构建证据

| 项目 | 已核对值 |
|---|---|
| 设备 | Xiaomi 14 Pro / 23116PN5BC，Android 16 / API 36，HyperOS OS3.0.307.0.WNBCNXM |
| 本次系统条件 | 用户设置 WorkToEarn 省电策略为“无限制”，两项必要权限由用户授权 |
| 应用 | `com.roy.worktoscroll`，`0.1.2-gate1`，versionCode `3` |
| APK | `app/build/outputs/apk/debug/app-debug.apk`，debug 签名，仅供测试 |
| SHA-256 | `FFF088C3758EB6EA1E7DA766EA16C0479F954934FCDDFC9466865EFEB4A68004` |
| 构建环境 | Java 17、Gradle 8.11.1、AGP 8.10.1、compile/target SDK 36、Build Tools 36.1.0；min SDK 29 不代表 API 29 已真机验证 |
| 静态验证 | `assembleDebug` 成功；`testDebugUnitTest` 9 项、0 失败；`lintDebug` 0 errors、6 warnings |

构建和测试报告位于 `app/build/`。这些结果来自当前 APK 的构建记录，本轮文档更新未重新构建。

## 真机证据摘要

下列日志和图片均为 2026-09-07 本轮调试产物。`app/build/` 被 Git 忽略且可能被清理；表内摘要保留在本文件中，原始文件仅在当前本机可复查，不能假设克隆仓库后仍存在。未另行创建归档目录。

| 场景 | 结果与证据 |
|---|---|
| 分段 5 分钟 | 14:20:15–14:26:16；余额 300000 → 0，累计扣减 286156 → 586156 ms，浮层次数 2 → 3；[分段日志](app/build/device-check-0.1.2-unrestricted.log) |
| 桌面暂停 | 14:22:22–14:22:42，余额均为 176734 ms，游标推进约 20 秒；返回小红书后继续扣减，见同一分段日志 |
| 连续 5 分钟 | 14:36:19 开始，14:41:20.665 记录耗尽浮层；最终余额 0，累计扣减 586156 → 886156 ms，浮层次数 5 → 6；[连续日志](app/build/device-check-continuous-0.1.2.log)、[耗尽画面](app/build/wte-continuous-exhausted.png) |
| 返回按钮 | 0.1.2 浮层上的返回按钮进入本应用首页；再次拦截后回桌面按钮进入 MIUI Launcher；[浮层](app/build/wte-block-review.png)、[返回首页](app/build/wte-return-review.png) |
| 使用情况权限 | 临时把本应用 GET_USAGE_STATS 从 allow 改为 ignore，首页正确显示失效；随后恢复并读回 allow，未改其他应用权限；[失效画面](app/build/wte-permission-off.png) |
| 强行停止与恢复 | 旧截图显示冷启动后“无障碍未授权/服务未连接”及恢复后的运行状态，但不能证明恢复动作由 Roy 亲手完成；[失效状态](app/build/wte-restart.png)、[恢复状态](app/build/wte-restart-recovered.png)。2026-09-07 再次用 ADB `am force-stop` 复测：服务列表由目标组件变为 `null`、`accessibility_enabled` 由 `1` 变为 `0`，冷启动后仍未恢复；余额 `15636 ms`、累计扣减 `1320912 ms`、浮层次数 `19` 均未变化。为避免留下失效状态，本次用 ADB 按测试前值恢复并确认服务重新绑定；真实手动恢复体验仍待 Roy 验收。 |
| 锁屏暂停 | 首次旧版本实验在自动锁屏时余额 181796 ms，之后至少 20 秒未变；来源为本轮早期 ADB 采样记录，不能冒充 0.1.2 完整锁屏回归 |
| 收尾 | 停止保护、余额 0、两项授权保留、超时恢复；[收尾画面](app/build/wte-handoff.png)，精确账本值见当前停点 |

### 测量口径

- 连续日志采样 1–9：主机时间差 `243931.1908 ms`，账本扣减 `243899 ms`，差约 `-32.19 ms`。包含读取时延、缓冲和两端采样误差，不能宣称全程精度为 32 ms，也不能代表切换/锁屏边界误差。
- 三次零余额热启动：启动命令前的设备 uptime 分别为 `198566000`、`198586650`、`198603660 ms`，对应 `BLOCK_SHOWN elapsed` 为 `198566328`、`198586997`、`198603998 ms`，差为 `328`、`347`、`338 ms`。方向为“浮层日志时间减去启动前时间”；包含 ADB 启动开销，终点不是屏幕首帧。原始值来自本轮终端输出，不是独立保存的延迟日志。
- 脚本 PASS 只证明被检查的账本与浮层添加条件；不自动代表视觉验收或用户体验验收。连续测试是本轮一次性流程，现有 `scripts/gate1-device-check.ps1` 只覆盖分段场景。

## 已知限制与下一步

- 0.1.1 曾在桌面停留后冻结：服务仍绑定，但主线程 `wchan=do_freezer_trap`，adbd 报告 cgroup.freeze=1。0.1.2 在“无限制”下的两轮受控测试未复现；不据此保证长期稳定。复用排查方法见 [README](README.md)。
- 无障碍保护在进程终止后的恢复，2026-09-07 已按场景实测（Xiaomi 14 Pro，adb 驱动）：手机重启会自动重绑恢复、余额保留；普通内存回收（`am kill`）杀不动本进程（服务保护其优先级）；仅崩溃式死亡与用户主动强行停止需手动“先关再开”无障碍才恢复，恢复后余额不丢；真·SIGKILL 因无 root 未验证。据此不构成 Gate 1 阻塞。详见子任务 [tasks/accessibility-recovery.md](tasks/accessibility-recovery.md)。
- 旧版返回按钮的 HOME/启动 Activity 竞争已修复并在 0.1.2 回归，不再是当前待办；实际机制见 [ARCHITECTURE](ARCHITECTURE.md)。
- 当前拦截是检测后浮层覆盖，不是系统暂停小红书；不承诺停止音频、防绕过或支持分身、多窗口、画中画及其他机型。
- 用户体验确认前不启动 Gate 2。下一步先反馈拦截与恢复体验，再确定摄像头原型的详细方案；CameraX 等候选不自动升级为已经确定。

## 无障碍替代路线调查（2026-09-07，候选，未实施）

用户目标：尽可能不用无障碍权限，同时保留识别小红书前台使用、按实际用时扣余额、零余额阻止继续使用的核心流程。验证范围仍是标准版小红书普通全屏，不新增防主动绕过要求。

- **源码事实**：`ProtectionService.settleUntil` 已用 UsageEvents 结算，余额算法本身不依赖无障碍事件。当前无障碍承担服务运行、窗口切换快速拦截、`TYPE_ACCESSIBILITY_OVERLAY` 浮层和 HOME 全局动作；因此不能只删除权限声明或换一个浮层常量。
- **候选机制**：保留使用情况访问；以用户从可见首页启动的前台服务运行监测，带运行通知；以 `SYSTEM_ALERT_WINDOW` 授权的 `TYPE_APPLICATION_OVERLAY` 显示阻止页。返回首页/桌面需改为正常 Activity/Intent 导航并验证。Android 14+ 可评估 `specialUse` 类型，须如实声明用途，Google Play 发布需接受对应审核，不保证通过。
- **计时依据**：[UsageStatsManager.queryEvents](https://developer.android.com/reference/android/app/usage/UsageStatsManager#queryEvents(long,%20long)) 要求使用情况访问，不要求无障碍。它是查询接口，不保证切换事件即时可见。保留现有 750 ms 缓冲、500 ms 轮询但移除快速拦截后，首次零余额拦截可能更慢；这些参数不能推导成实际延迟上界。
- **显示依据与限制**：[普通悬浮窗](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY) 可显示在应用 Activity 之上，但在关键系统窗口之下，系统可调整其可见性；[Android 12+ 目标窗口可拒绝普通悬浮窗](https://developer.android.com/security/fraud-prevention/activities)。这是覆盖阻止机制，非系统暂停目标 App，不能保证停止音频或防绕过。若目标窗口拒绝覆盖，本候选浮层路径不通过，需要另行评估正常阻止 Activity 的兼容性，不能继续声称浮层可靠。
- **后台依据与限制**：[前台服务](https://developer.android.com/develop/background-work/services/fgs) 需带通知；[后台启动规则](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) 限制从后台新建服务，Android 15+ 单靠悬浮窗权限豁免还需可见浮层。优先从用户可见操作启动；不能据此保证崩溃、强停、重启或厂商冻结后自愈。[specialUse 说明](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use)。
- **信任边界**：移除无障碍可避免本应用的无障碍授权流程，但仍需使用情况访问、悬浮窗授权与运行通知；使用情况访问涉及 App 使用记录，悬浮窗也有安全风险。具体 HyperOS 警告和是否更易接受必须亲自体验，不能承诺“低风险权限”或“不再吓人”。恢复时是否只需回首页启动服务也待验证，不能承诺强停期间继续保护。
- **同类证据边界**：[AppBlock 官方权限说明](https://appblock.app/help/android/personal-data/) 把使用情况访问、显示于其他应用之上、无障碍高级拦截分开说明，但本轮没有实测其最新版关闭无障碍后的完整流程；不得据此声称竞品已证实同等可靠。[TimeLimit 官方说明](https://legal.timelimit.io/en/privacy/) 将当前应用判断与用时计算归于使用情况访问，将无障碍用途描述为拦截前回桌面、修复部分场景，支持“计时与无障碍可分离”，不证明我们的兼容性。
- **其他路线**：[本地 VPN](https://developer.android.com/develop/connectivity/vpn) 可按应用处理网络，但断网不能阻止已缓存内容或应用界面使用，且同一用户/配置文件只能运行一个 VPN；不等价替代现有核心体验。[设备/工作资料管理](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setPackagesSuspended(android.content.ComponentName,%20java.lang.String[],%20boolean)) 可真正暂停包，但需 owner/受委托管理等资格，非普通设备管理员授权即可；引入管理范围与设置成本，不优先用于降低普通个人用户授权门槛。
- **本轮设备边界**：ADB 一度列出连接设备，下一次只读查询小红书包信息时返回 device not found；未取得小红书 `HIDE_OVERLAY_WINDOWS` 声明结果，未读屏、未改设备授权、未安装新版本。
- **建议的下一步（待确定）**：制作独立验证 APK，不声明无障碍服务，使用独立应用 ID；不迁移现有余额，避免与旧保护同时测试造成假通过。第一步验收真实悬浮窗授权警告和零余额覆盖；通过后再验证 5 分钟连续/分段扣减、锁屏暂停、耗尽再拦截、返回路径、后台持续运行及强停后重新打开恢复。记录延迟分布、漏拦次数、计时偏差和耗电，而非先承诺阈值。若授权依然劝退、无法覆盖目标窗口或普通使用中持续漏拦，不把候选升级为采用方案。

## 子任务索引

- [进程终止/重启后的无障碍保护恢复](tasks/accessibility-recovery.md)：2026-09-07 实测——重启自动恢复、普通内存回收（`am kill`）杀不动本进程（均放心）；仅崩溃与用户主动强行停止需手动恢复（真·SIGKILL 无 root 未验证）。据此不构成 Gate 1 阻塞。

## 文档入口

- [README](README.md)：安装使用、构建、自测和故障排查。
- [SPEC](docs/SPEC.md)：已确认的完整产品行为，不是当前功能清单。
- [DESIGN](docs/DESIGN.md)：当前验证路线、原有候选方案和待确认选择。
- [ARCHITECTURE](ARCHITECTURE.md)：实际代码结构、数据流与恢复边界。
- [AGENTS](AGENTS.md)：项目规则与文件维护职责。
