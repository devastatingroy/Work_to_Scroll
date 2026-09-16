# 后台冻结与消费拦截耐久验收

状态：0.5.7-xiaomi-recents-guide/versionCode 29 已覆盖安装并保留 0.5.6 的单任务修复；0.5.6 真机系统证据已通过单任务、单最近任务计数及目标 STOPPED，待在 0.5.7 完成视觉、停声和退出回归。迁移日期：2026-09-08。

入口：[任务索引](../README.md)。维护人：接续本任务的 agent；关键步骤、阻塞、验证或完成时更新本文件，状态迁移同步索引。

下一步从不先回主应用唤醒的真实后台状态检查阻止效果，不能仅凭服务存在判断正常。诊断方法见[调试经验](../../notes.md)。

## 剩余验收

- [ ] 零余额阻止的真实触摸、返回应用及回桌面路径，记录首次不一致与漏拦情况。
- [ ] 连续/分段 5 分钟消费、锁屏暂停及耗尽重拦；事先约定误差/延迟口径，记录实际结果。
- [ ] 后台等待、长时间正常使用、反复锁屏/解锁和跨日边界；进程回收、崩溃和手动强停分别记录。
- [ ] 停止保护和恢复后余额/历史保留；系统重启与通知由[默认保护与提醒验收](default-protection.md)接续。

旧版未完验收从[悬浮窗阶段历史](../completed/android-overlay-history.md)接续，仅在当前实现上验证；声音问题单列[音频处理](audio-blocking.md)。

## 已有记录与证据

## 2026-09-11 零余额持续漏拦：上滑清理将应用强制停止

- [x] Roy 报告当前可用娱乐时间为 0，但打开小红书持续没有拦截；在未先打开主应用的现场读取到：`balance_ms=0`、`enabled=true`、小红书前台、阻止窗口/阻止页均不存在，监测点已落后约 146—152 秒，WorkToScroll 进程和前台服务完全不存在。余额和权限并非本次直接原因。
- [x] 系统退出记录确认最近一次退出发生于 2026-09-11 08:56:54：`reason=USER REQUESTED`、`subreason=FORCE STOP`，说明为 `due to SwipeUpClean`；包状态同时为 `stopped=true`。因此这次不是旧的“进程仍在但被冻结”，而是小米上滑清理把应用置为强制停止，系统禁止 `START_STICKY` 服务自行重启。
- [x] 只启动 WorkToScroll 主页面恢复保护，未改余额、累计消费、保护开关或权限。恢复后包状态 `stopped=false`，ProtectionService 为前台服务，进程 PID 8613 未冻结，监测点连续保持新鲜；`balance_ms=0`、`consumed_ms=4980000` 保持不变。
- [~] 随后从电脑启动一次小红书，连续三次采样均由 WorkToScroll 的全屏阻止页保持前台，小红书未成为前台；Roy 随后确认手机上已经出现阻止画面，零余额视觉阻止链路恢复。触摸阻止和用户主动再次打开小红书仍未单独验收。
- [x] Roy 打开最近任务后截图显示两个 WorkToScroll 卡片：下方为主页任务，上方为“暂无可用时间”阻止页任务；截图中的主页小锁是 Roy 在本次漏拦恢复后刚添加，不能倒推 08:56 漏拦发生时主页已锁。代码事实与截图一致：BlockerActivity 使用独立 `taskAffinity` 和 `singleTask`；虽然声明了 `excludeFromRecents/noHistory`，HyperOS 仍会把当前活跃的阻止任务临时显示为第二张卡片。
- [~] 最近退出记录 `SwipeUpClean → FORCE STOP` 已证明本次漏拦由上滑清理强停引起；但由于主页小锁是事后添加，现有证据不能证明重复的阻止页卡片造成了 08:56 这一次强停。两个同名卡片仍会让用户误判哪个任务应保留，并增加未来清理错卡导致保护失效的风险，属于发布前必须解决的核心体验与可靠性缺陷。
- [x] Roy 明确要求先好好设计再实施。采用最小结构修正：保留全屏 Activity、旧浮层、音频焦点、结算和耗尽动效，只移除阻止页的独立taskAffinity/excludeFromRecents，将singleTask改为singleTop；服务的NEW_TASK请求因默认亲和性复用WorkToScroll任务。同步把阻止页所有finishAndRemoveTask改为finish，防止合并后退出时连主页任务一起删除。未采用“只保留浮层”，因为既有真机证据表明目标不退后台时不能可靠停声；也未把“让用户识别并锁两个卡片”当产品修复。
- [x] 0.5.6-single-task-blocker/versionCode 28 已完成107项单元测试、debug APK与androidTest APK构建、lint（0错误/15警告）；最终合并清单确认BlockerActivity只有singleTop/noHistory，没有独立taskAffinity或excludeFromRecents。androidTest新增两次阻止页与主页taskId一致的断言，但尚未安装运行测试组件。
- [x] debug APK SHA-256为`A6A88BB5726AA73F1A23F8256B9475CE7642B62D4F7FCBF7DDEA3509652BDCF4`，APK v2签名验证通过；手机35c2f60f保留数据覆盖安装成功，读回0.5.6/versionCode28。安装前设备APK备份为`app/build/device-before-single-task-blocker.apk`，SHA-256为`EBA7F17C8E3B9351F259BFC1838205DC6F174BF4F84AC8F4ADB2355EB5958EC7`。
- [x] 安装前后账本快照为`app/build/ledger-before-single-task-blocker.xml`与`app/build/ledger-after-single-task-blocker-install.xml`；balance_ms=0、consumed_ms=4980000、work_total_ms=13109463、work_session_ms=2138、work_reward_ms=0、work_upgrade_credit_ms=180000、enabled=true、work_active=false及15分钟档位均一致。
- [~] 0.5.6 真机回归：首次尝试时设备已Dozing且锁屏，NotificationShade持有当前窗口，不能作为失败证据。Roy解锁后，从WorkToScroll切入小红书，阻止页连续三次采样均focused/visible；WorkToScroll任务592同时包含MainActivity根页和BlockerActivity顶页，系统最近任务计数为1，小红书Activity为STOPPED且不可见。进入最近任务后BlockerActivity按noHistory退出，任务592仅保留MainActivity，最近任务计数仍为1。系统结构证据通过；停声、返回WorkToScroll、回桌面、系统返回、重复进入和上滑清理仍待验收。
- [x] Roy 要求在设置的后台保障区补充锁定最近任务提醒，并进一步要求把“打开最近任务”拆成三步配图操作。0.5.7 保留 0.5.6 单任务修复，新增原生图解；107 项单测、debug/androidTest 构建与 lint 通过，覆盖安装及账本保留通过。该引导降低再次 SwipeUpClean 的误操作风险，但不能替代单任务结构修复，也不能保证系统不强停应用。
- 当前停点：0.5.7 已安装且数据保留，本地实现闭环；待设备解锁后核对图解排版，并在当前安装版本复测零余额阻止、最近任务实际单卡片、停声与退出路径。发布前必修问题尚未完成全部真机验收。

## 当前修复：后台冻结导致零余额放行（2026-09-08）

- [x] Roy 明确要求查明原因、修复、汇报并排查同类失效；本轮进入修复，保留全部既有工作余额和未提交改动。
- [x] greezer 原生冻结历史已命中当前 UID 10110 / PID 19452，包括 tobg、screen off 和启动其他应用时的 LM FZ；当前 frozen 列表包含19452。确认来自本机厂商冻结路径，非仅凭 AOSP 的 isFrozen 字段推测。具体单应用策略正在核对。
- [x] 13:30 Roy再次反馈零余额可进入；现场小红书在前台、balance_ms=0、PID16019 frozen=true、监测点落后31111ms且无阻止窗口，确认首项验收失败。此时手机已解锁。
- [x] 进入本应用“省电策略”，实际选中“智能限制后台运行（推荐）”，其系统说明包括后台CPU占用限制；已按修复授权仅改本应用为“无限制”，读回checked=true，原智能限制checked=false。未改全局冻结开关或其他应用设置。
- [~] 恢复主应用一次后重新进入小红书；13:31:10与13:31:19采样均balance_ms=0、consumed_ms=1080000、frozen=false、target_active=true、监测点新鲜且阻止窗口存在。WindowManager显示阻止层shown=true、isOnScreen=true、isVisible=true，覆盖内容区。已请Roy核对实际画面/滑动，后台耐久仍需继续验证。
- [x] 13:32:27—13:32:59继续观察到小红书在前台且阻止层可见、进程未冻结。13:33后目标离开；13:33:48起短时连续采样未冻结、消费和余额均不变，但随后系统焦点为WorkToScroll首页，不能算完整后台停留验收。实际安装仍0.2.5/versionCode9；此前系统应用详情缓存显示0.2.4，不能以旧页面文字替代包管理器版本读数。
- [x] 只读诊断补充blocker_window_visible，要求系统窗口可见且Surface已显示；不再仅凭窗口被列出判断覆盖。仍须Roy确认实际滑动与退出体验，未采集小红书正文或摄像头画面。
- [x] 实现0.2.5-protection-fix / versionCode9：服务启动、权限恢复、异常与超过5秒的心跳停顿重建前台状态；最近24小时事件只恢复状态，不追扣中断或750ms缓冲内未知时间。新增中断计数/最近时长、系统后台受限提示及本应用设置入口。没有新增权限、摄像头后台运行、常驻唤醒锁或高频闹钟。
- [x] 45项单元测试全部通过（余额14、保护策略11、工作时钟7、奖励13），包括已打开小红书后的恢复识别、冻结时间和事件缓冲尾段不扣费、离开事件及正常轮询边界；assembleDebug/lintDebug通过，lint0错误7警告，APK签名验证通过。APK SHA-256：A28D2DD3D7B72F1EF9057E6787C931E9BE6C7C368C4B76509435E0F17C94F523。
- [x] 新增只读scripts/protection-health-check.ps1并实跑，能报告旧进程cgroup_frozen=true、do_freezer_trap和过期监测点，不因服务记录仍存在误报健康；不启动应用、不留页面正文或完整应用历史。已更新README与实际ARCHITECTURE说明。
- [x] 已从设备保留0.2.4基线APK到app/build/baseline-0.2.4-before-protection-fix.apk，SHA-256与原记录一致。0.2.5普通覆盖安装Success，读回versionCode9；随后冷启动首页Status:ok、前台服务已恢复，12:47:37首次采样cgroup_frozen=false、checkpoint_age_ms=875。balance_ms仍0、consumed_ms仍1080000、work_total_ms仍2162008、work_upgrade_credit_ms仍180000，未启动相机。此为启动证据，不算后台稳定验收。
- [x] 12:48:40仍锁屏时复测，新进程PID16019再次cgroup_frozen=true、do_freezer_trap，监测点落后44564ms；明确证明应用内恢复补丁不能解除厂商冻结。当前服务尚不能正常保护，必须继续设备后台配置修复，不能交付为问题已解决。
- [ ] 真机验证零余额阻止、后台等待、锁屏恢复、停止保护与余额保留；记录实际测试范围和剩余边界。
- 当前停点：已修正本应用后台策略并恢复短时零余额阻止；等待Roy实际触摸反馈，继续后台等待/锁屏恢复验证。关键故障尚未完成耐久验收，不把短时恢复当作持续可靠。

## 历史诊断：零余额仍可刷小红书（2026-09-08 12:35）

- [x] Roy 报告零余额仍能刷小红书。本轮只读检查已连接 Xiaomi 14 Pro（35c2f60f），安装版本仍为 0.2.4-camera-test / versionCode 8；未修改代码、余额、权限、保护开关或设备设置。
- [x] 现场确认 balance_ms=0、consumed_ms=1080000、enabled=true；使用情况访问与悬浮窗 appops 均 allow。系统焦点为 com.xingin.xhs/.index.v2.IndexActivityV2，未发现当前阻止浮层。
- [x] ProtectionService 仍列为 isForeground=true，进程 PID 19452 存在，但 /sys/fs/cgroup/apps/uid_10110/pid_19452/cgroup.freeze=1、cgroup.events 的 frozen=1；以本应用身份读取 /proc/19452/wchan 为 do_freezer_trap。账本 cursor_ms=1788841970711 在重复读取时不推进，12:34:49 设备时间已为1788842089秒，已落后约118秒；target_active=false 也是冻结前旧状态。直接失效原因已确认是进程冻结导致监测/拦截不执行，不是零余额显示误差或这两项权限缺失。
- [x] ActivityManager 同时显示 isFrozen=false，与实际 cgroup 冻结状态不一致；今后诊断不能仅凭前台服务存在或该字段认定保护在运行。具体由哪条厂商后台策略触发仍未核实，不把冻结直接等同于已经证实的某项省电设置。
- [ ] 后续若进入修复：核实当前包的后台/省电策略及冻结触发条件，验证恢复后零余额拦截、离开应用后持续保护和再次冻结风险。不得用服务存在、短时恢复或旧版验收替代当前版本耐久验收。
- 当前停点：已向 Roy 说明确认的拦截失效与直接原因；该诊断轮未实施修复，后续修复见本文件前节，未重启/解冻进程或切换手机页面。
