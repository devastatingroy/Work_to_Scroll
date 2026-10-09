# 画中画软件监控可行性研究

状态：**本机条件链路已通过，保留验收边界**；创建日期：2026-09-16。2026-09-16 Roy 已确认 WorkToWatch V1 必须关闭官方 YouTube 画中画，不再研究“保留 PiP 时强行监管”的路线；未授权新增 AccessibilityService、通知读取或设备管理权限。

父任务：[WorkToWatch 产品实施](overseas-target-apps.md)。维护人：实际继续本任务的 agent；在关键判断、验证、阻塞或结项时维护本文件与任务索引。

## 目标与范围

判断带画中画（PiP）功能的第三方娱乐软件能否满足两项不可拆分的核心要求：

1. 画中画实际播放期间，娱乐额度以同等口径持续减少；
2. 额度归零后，已存在的画中画视频/音频停止，且用户不能通过画中画播放键继续观看。

首个证据对象是 Android 官方 YouTube App；iOS 仅作为独立候选平台的对照研究。后续若考虑 TikTok 或其他 App，必须逐个建立真实设备、系统版本和 App 版本的验收记录，不能把 YouTube 结论外推。

本任务不授权新增 AccessibilityService、通知读取、设备所有者/特权权限、屏幕录制、页面内容读取或发布行为；也不将“可提示”“可暂停一次”表述为可靠的系统级封禁。

## 已归档证据与结论边界

- 2026-09-16，Roy 在小米 14 Pro 上观察到：YouTube 进入画中画后仍可播放，但 WorkToWatch 不再扣减额度；额度耗尽时虽出现阻止界面，画中画窗口仍可由用户再次点播放恢复。两项核心要求均未通过。
- 2026-09-16 同机补充观察：余额归零后，WorkToWatch 阻止页瞬间替代 YouTube 全屏界面，但 YouTube 没有退出，而是自动缩成右上角系统画中画；视频当下为暂停，单击画中画仍会显示上一条、下一条、播放/暂停等完整控制。该证据证明当前路径只造成一次初始暂停/界面降级，没有移除画中画窗口或封住后续播放入口。仅凭“按钮出现”不能证明播放命令最终被接受，但结合此前“可再次播放恢复”的实测，第二项核心要求仍明确未通过。
- 当前 Android 实现将 `UsageEvents.ACTIVITY_RESUMED` 当作目标在前台、将 `ACTIVITY_PAUSED` 当作离开；这不能代表画中画实际观看。Android 官方也说明 paused Activity 在多窗口等场景仍可能可见。[Activity lifecycle](https://developer.android.com/topic/libraries/architecture/views/activity-lifecycle-views)
- 2026-09-16 补充发现一条优先级更高、无需新增敏感权限的计时候选：`ACTIVITY_STOPPED` 明确定义为 Activity 在界面中变得不可见；Android 同时说明 PiP 进入时 Activity 会 `onPause()` 但应继续播放，完全不可见时才 `onStop()`。项目 `minSdk 29`，可以直接观察 `ACTIVITY_STOPPED`；`UsageStats.getTotalTimeVisible()` 也可作为对照数据。[UsageEvents.Event](https://developer.android.com/reference/android/app/usage/UsageEvents.Event) [Picture-in-picture](https://developer.android.com/develop/ui/views/picture-in-picture) [UsageStats](https://developer.android.com/reference/android/app/usage/UsageStats)
- 该候选只能证明/计量“目标 App 仍可见”，不能判断视频究竟处于播放还是暂停。它与现有“整个官方 YouTube App 的可见使用”口径更接近，但若产品要求精确到媒体播放状态，则仍需独立数据源和产品决定，不能把可见时间写成播放时间。
- 若系统使用事件无法稳定区分 PiP 与真正离开，再考虑用户明确启用的 AccessibilityService：`AccessibilityWindowInfo.isInPictureInPictureMode()` 可识别交互窗口是否处于画中画，但需要额外窗口访问能力、明确告知/同意以及 Google Play 辅助功能合规评估。[AccessibilityWindowInfo](https://developer.android.com/reference/android/view/accessibility/AccessibilityWindowInfo) [Google Play policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en)
- 即使能识别画中画，普通消费级第三方 App 仍没有已证实的可靠方式永久关闭 YouTube 画中画并禁止用户重新播放；通知监听/媒体会话暂停最多是软性候选，不能预先当作满足第二项要求。
- 当前阻止页的音频焦点只能让已有播放淡出/静音；Android 明确允许另一个 App 再次请求并抢占音频焦点，用户实测也已证明画中画播放键可恢复。因此不能靠反复争抢音频焦点宣称“不可重播”。[Audio focus](https://developer.android.com/media/optimize/audio-focus)
- 不扩权的另一候选不是“监管画中画”，而是把关闭官方 YouTube 的系统画中画能力设为保护生效前提。Android 的画中画由目标 App 自己声明/自动进入，WorkToWatch 不能替目标 App 关闭；普通应用也没有稳定公开接口读取并修改另一 App 的 PiP AppOp。可以引导用户进入 YouTube 应用详情或由画中画控制中的设置入口手动关闭，再真机验证归零时是否彻底离开 YouTube。若采用，这意味着产品明确“不支持保留 YouTube 画中画”，而不是声称已能管理它。
- 2026-09-16 小米 14 Pro 首次分叉实测：Roy 手动关闭官方 YouTube 的系统画中画后，在余额为零的同一场景中，阻止页出现后没有残留画中画，视频和声音均停止。该结果通过“首次进入时彻底替代并停播”，支持把“关闭 PiP”作为不扩权前置条件继续验证；尚未覆盖最近任务重进、桌面图标重进、重新开启 PiP、服务重启或跨机型，不能升级为完整可用结论。
- 2026-09-16 同机继续实测：保持 YouTube 系统画中画关闭且余额为零，从最近任务卡片重新进入 YouTube 后，阻止页会再次立即出现，仍无画面或声音。该路径通过；桌面图标重进、服务重启和跨机型仍未覆盖。
- 2026-09-16 22:49 ADB 自动样本（设备解锁）：确认 YouTube `PICTURE_IN_PICTURE: deny`、普通余额 0、夜间放松关闭、保护前台服务运行。通过 Launcher Intent 启动 YouTube 后约 1 秒，`BlockerActivity` 成为唯一前台/焦点窗口；YouTube 任务保持 fullscreen 但不可见，其窗口 `mHasSurface=false`，无 `mode=pinned`，YouTube 媒体会话为 `STOPPED`。阻止计数 15→16，日志记录 `BLOCK_SHOWN`、`PAGE_REQUESTED`、`PAGE_VISIBLE`，截图目视为 00:00 阻止页且无 PiP。该样本通过桌面启动等价路径的结构、视觉和媒体状态检查。
- 随后的三次重复及 YouTube 进程重启样本期间，设备于 22:49:46 自动熄屏并进入锁屏/Dozing；这批结果混入屏幕不可交互条件，全部不计入通过或失败。当前 ADB 只能唤醒到锁屏，须 Roy 解锁后再继续重复与进程重启回归。
- 2026-09-16 解锁后重测：保持 `PICTURE_IN_PICTURE: deny`、余额 0、夜间放松关闭和保护服务运行，连续三次 Launcher Intent 启动及一次 `am force-stop` YouTube 后重启，阻止计数从 17 逐次增至 21，每次均未出现 `mode=pinned`。自动解析焦点/YouTube Surface 的脚本字段为空，因此不把这四次写成新的视觉或 Surface 证据；结合此前已通过的焦点、Surface、媒体、截图样本，可确认“关闭 PiP 后，多次进入及 YouTube 进程重启不会绕过零余额阻止”在本机成立。
- iOS 的 `DeviceActivityEvent` 计量口径为目标 App/网站位于前台屏幕的时间，Apple 没有保证第三方 YouTube 画中画会继续被计量；`ManagedSettings` 的 Shield 更强，但也没有公开保证会关闭已存在画中画或阻止重播。iOS 因而同样未通过可行性判定，须真机 Spike 复核。[DeviceActivityEvent](https://developer.apple.com/documentation/deviceactivity/deviceactivityevent) [ShieldSettings](https://developer.apple.com/documentation/managedsettings/shieldsettings)

## 当前停点与恢复前提

研究分叉已经结束：PiP 开启时两项核心要求均失败；关闭 PiP 后，本机零余额阻止的多入口与进程重启样本通过。当前不再为 V1 采集 PiP 播放计时或实现 `ACTIVITY_STOPPED` / 无障碍路线，而是把关闭 PiP 做成 WorkToWatch 的必需设置并完成正余额端到端验收。

实验准备发现：22:00–08:30 的“夜间放松”额度与普通 `balance_ms` 分开保存，首页显示两者合计；当前开发工具“清空测试余额”只清零普通余额，没有清空 `night.remainingMs()`，但确认框却写成“全部娱乐时间”，属于测试工具行为与文案不一致。当前已安装版要立即进入零余额实验，可先在设置首页关闭“夜间放松”；这只隐藏/停用本夜赠送额度，重新开启会恢复本夜剩余额度。后续如修复，应让测试清空动作同时清零当前夜间额度，或把按钮和结果明确拆分，不能继续声称已清空全部可用时间。

按以下顺序继续：

- [x] Roy 已确认“V1 不支持 PiP、必须关闭官方 YouTube 画中画后使用”，并授权按此边界实施。
- [x] 取消 V1 的 PiP 播放计时、`ACTIVITY_STOPPED` Spike、无障碍和通知读取路线；原始失败证据保留，不再把它们当当前待办。
- [x] 在不改代码的前提下关闭 YouTube“画中画”，余额为零时重新打开：阻止页出现后不再留下画中画，视频与音频均停止。
- [x] 保持 PiP 关闭和余额为零，从最近任务重进 YouTube：阻止页再次立即出现，无画面或声音。
- [x] 保持相同条件，通过与桌面图标相同的 Launcher Intent 重新进入 YouTube：阻止页再次立即出现，无 PiP，YouTube 窗口无可显示 Surface，媒体会话停止。
- [x] 设备解锁后执行三次重复启动及一次 YouTube 进程重启后的启动回归：阻止计数 17→21，四次均无 pinned 任务；解析失败的焦点/Surface 字段未计为证据。
- [x] 修复开发工具“清空测试余额”只清攒得余额、不清当前夜间赠送的问题；实现须同次持久化并在失败时一起回滚。
- [x] WorkToWatch 权限页加入直达 YouTube PiP 设置、用户确认和未确认时的保护/入口门槛；WorkToScroll 不受影响。
- [x] 覆盖安装到小米 14 Pro：设置入口准确打开官方 YouTube PiP 页且开关为关闭，确认后保护服务恢复；既有账本、使用情况访问和悬浮窗授权保留。开发工具把普通余额 0 与隐藏的夜间剩余 `2633718 ms` 同次清为 0。
- [x] 加入 5 分钟测试余额后，通过 Launcher Intent 打开 YouTube：约 5 分钟内余额从 `300000` 连续扣到 0，累计扣减 `900000→1200000`，阻止计数 `22→23`；归零后 `BlockerActivity` 为 top resumed，YouTube task 不可见且为 fullscreen，无 pinned/PiP，媒体会话为 PAUSED/STOPPED。该样本验证整个官方 YouTube App 的全屏前台用时，不等于真实视频播放与停声验收。
- [ ] 如恢复 iOS，对同一场景执行前台 60 秒 → 画中画并切换其他 App 60 秒 → 阈值触发 → Shield/重播验证；不要用 Android 结论替代。

## 验收、停点与决策

通过不等于能显示阻止页，而须在同一测试中同时证明：画中画按已确认的产品口径确实减少额度；额度归零后画中画的视频和音频停止；用户点击播放键不能形成可持续播放；正常离开目标 App 不会被误扣。每项均需真机记录。建议首轮以 60 秒场景、三次重复、切换边界误差不超过 2 秒作为诊断门槛；这只是实验标准草案，不是已确认的对外承诺。

任一平台无法在不改变消费级产品安装/分发模式的前提下满足上述全部要求，应明确记录“该平台不适合承诺此核心功能”，而不是以提示、一次性暂停或前台监测替代。届时由 Roy 决定终止产品、收窄承诺，或批准新的产品形态研究。

当前 Android 产品决定：V1 不承诺监管 PiP，而把“关闭官方 YouTube 系统画中画”设为保护生效的强制前置条件。按此边界，小米 14 Pro 上的零余额阻止已覆盖首次进入、最近任务、桌面等价启动、多次重复和 YouTube 进程重启；一笔 5 分钟测试余额也已完成 YouTube App 前台“扣减到零→自动阻止”，核心阻止路线在本机有条件成立。该正余额样本未播放真实视频，真实停声仍须补验，且单机结果不代表跨厂商发布可靠性。若以后要求用户保留 PiP，则当前普通消费级路线不成立，必须重新立项而不能沿用本结论。

## 关联资料

- [WorkToWatch 产品实施](overseas-target-apps.md)
- [iPhone 核心原型开发准备](../todo/ios-prototype.md)
- [Android 分目标娱乐应用监控准备](../todo/target-app-selection.md)
- [产品要求](../../docs/SPEC.md)
