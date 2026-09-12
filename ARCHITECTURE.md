# WorkToScroll 实际结构

当前采用的 Android 路线为普通悬浮窗，由 Roy 在对照两版后明确选择；原版的无障碍结构保存在提交 `8a98c84` 和标签 `checkpoint/gate1-before-overlay-20260907`。完整产品要求见 docs/SPEC.md。本文件描述已实现机制，不把路线选择当作全部真机验收通过。

## 模块与数据流

单个原生 Java app 模块，摄像头阶段接入 AndroidX Activity 1.10.1、CameraX 1.4.2 与 MediaPipe Tasks Vision 1.0.0 / bundled EfficientDet-Lite0 int8，取代此前 ML Kit 人脸模型。应用 ID `com.roy.worktoscroll.overlaytest`，Java namespace 保留 `com.roy.worktoscroll`；沿用新版私有余额，和旧无障碍原版沙箱隔离。

| 文件 | 职责 |
|---|---|
| MainActivity.java | 猫总首页，以及用 page 参数区分的设置/权限详情/开发工具页面实例；保留授权、保护启停与测试动作，前台时按已保存开关恢复服务 |
| ProtectionUiState.java | 纯展示状态优先级：停止意愿、必要授权缺失、运行中断、服务异常、后台受限、运行中；不写账本或启动服务 |
| CatUi.java | 原生页面宽度/系统边距、纸色与黑色样式、控件与图片解码；blocker 创建共享原生 BlockerView，不写入业务状态 |
| BlockerView.java | 猫总无道具形象、独立零余额/说明/返回区；1550ms 共用镜头/字效，退出和移除时取消；遵循系统关闭动画 |
| ExhaustionFeedback.java | 进程内一次性消费耗尽事件；核对扣费前后数值、短时有效令牌与一次领取，不写账本 |
| Permissions.java | 查询使用情况访问和 Settings.canDrawOverlays，不查询或申请无障碍 |
| ProtectionService.java | 普通 Service，使用 specialUse 前台服务运行监测；通知提供回首页和停止操作；显示 TYPE_APPLICATION_OVERLAY |
| BlockerActivity.java | 非导出的不透明全屏阻止页；独立任务、不进最近任务、离开不保留历史；真正让目标退后台，返回工具/桌面及系统返回不重新露出目标 |
| BalanceEngine.java | 原版复用的纯余额算法，按使用区间扣减，仅显式奖励能增加余额 |
| LedgerStore.java | 实验版同进程单例与 SharedPreferences 快照，原版私有数据不读取、不迁移 |
| WorkActivity.java | 前置预览、离线人体检测、显式开始/暂停/继续/结束、试验性预览解绑、声音开关、在场与奖励展示；帧仅在内存存在 |
| PreviewTrial.java | 当前工作页进程内的预览关闭状态和至少10秒展示门槛；自动恢复保持关闭，用户主动暂停后重新开始则恢复校准预览 |
| CameraRecovery.java | 内存中的监测意愿、临时中断、采集健康度与有限重试；WorkActivity 的 ViewModel 仅跨同进程配置重建保留，不从账本恢复开相机意愿 |
| MonitoringReturnTicket.java | 进程级、一次性、90 秒的外部应用返回交接；只允许根首页回到工作页，不持久化且不启动相机 |
| PersonObservation.java | 无图像的人体候选/可靠数量及分数，转为 WorkClock 的可靠单人、弱单人、无人或不确定信号 |
| DetectionCue.java / CuePlayer.java / UnrecordedReminder.java | 原始检测变化与连续未记录分钟提醒；随包语音不排队，过期/暂停停止，不改变计时 |
| WorkClock.java | 单调时钟和新鲜帧约束下的在场容错计时；可靠确认后的弱单人信号最多保留 120 秒，不使用系统日期计算奖励 |
| DailyReset.java | 本地 06:00 截止点、到期判断、时区重排及清零提示状态的纯计算 |
| WorkProgress.java | 不可变的历史工作、本轮 10/15/20 分钟门槛/分钟奖励、中断期限与恢复计算 |
| WorkDebugLog.java / WorkDebugMetrics.java | 仅调试版启用的工作会话事件日志与纯计算口径；记录本次有效总量、中断、三分钟轮次重置和结束摘要，不参与账本或奖励计算 |
| ProtectionPolicy.java | 默认开启/已保存停止的加载规则，按当前时区计算四小时及09:00—21:00提醒边界，可独立测试 |
| ProtectionRecovery.java | 开机/本包更新后的保护恢复请求、时间/时区变化后的提醒重排、单次非精确提醒及取消；不启动摄像头 |

UsageEvents → 应用前后台/锁屏事件 → BalanceEngine → 实验版本机账本 → 零余额且小红书在前台时先显示普通悬浮窗，再启动BlockerActivity → 新页获得实际窗口焦点后移除浮层及释放音频焦点，目标进入后台。

CameraX 前置图像 → MediaPipe person 检测 → PersonObservation 可靠单人/弱单人/无人/不确定 → WorkClock 有效毫秒增量 → WorkProgress 奖励差额 → LedgerStore 同一次快照写入工作累计与娱乐余额 → 既有消费路径。恰好一个可靠人体即为可靠在场，额外低置信度候选不否决它；两个及以上可靠人体保持不确定。弱单人只在已经可靠确认在场后进入有 120 秒上限的保留，不单独建立在场，也不能无限续时。声音沿原始观察另走 DetectionCue → CuePlayer，不绕过 WorkClock 的确认和宽限。

人体模型在分析线程加载并串行推理，未就绪不允许开始；停止后的旧代次结果不进入账本。人物结果不保存框或图像，只每秒原子覆盖 app 私有 files/camera-status.json 的最新人数/分数/状态/音频指标；[摄像头读取脚本](scripts/camera-health-check.ps1)核对 PID、手机单调时钟和结果年龄后判定读数有效，离开工作页后旧快照不代表监测仍在运行。ui_preferences 保存 presence_sound_enabled（默认开启），不迁移账本；固定使用 DEFAULT_FRONT_CAMERA，不读取临时版本的镜头偏好。UnrecordedReminder 对每次仍有监测意愿的无效记录在满 60 秒后最多请求一次“目前未有效记录”；检测不确定、无新鲜帧、相机自动失败和临时失焦共用该规则。不可安全播放时保留一次资格，回到可交互工作页且仍无有效记录才请求；主动暂停/结束、恢复有效记录或关闭声音清除本次资格。

调试构建额外在 app 私有目录维护 `work-session-events.jsonl` 和原子覆盖的 `work-session-latest.json`。用户开始工作时以历史 `totalValidMs` 记精确基线，随后记录工作开始、相机状态、计时状态变化、中断开始/恢复、三分钟轮次重置、约每分钟检查点和结束摘要；计时状态同时带无人、弱候选或多候选等非图像分类，窗口焦点/锁屏/交互性变化记录独立触发原因，声音另记录请求、开始、完成、取消和错误。“本次有效时间”始终取当前历史总量减本次基线，因此不会因 `sessionValidMs` 重置而丢失。事件日志超过 512 KiB 后只保留一份轮转旧文件；不记录图像、页面正文或完整浏览历史，也不参与账本保存成败与奖励发放。电脑端只读入口为 [工作会话调试脚本](scripts/work-session-debug.ps1)。

## 独立人体检测实验

[experiments/side-rear-presence](experiments/side-rear-presence/README.md) 为独立 Gradle 工程和独立应用 ID，不加入上述主应用模块或奖励数据流。CameraX 采集 → MediaPipe Tasks Vision 1.0.0 / EfficientDet-Lite0 int8 全画面 person 检测 → PresenceGate 连续状态 → 每秒覆盖的私有 status.json；电脑通过只读脚本同时核对进程和单调时间后读取人数、分数及状态。画面仅在内存存在，不写图像；没有网络权限。当前仅用于侧后方机位初筛，尚无工作区域校准、身份识别、工作计时或奖励。构建和状态单元测试不代表已完成真机识别，实际进度见[探索任务](tasks/active/side-rear-presence-exploration.md)。

实验版 0.1.1 的声音单独沿原始人体检测结果进入 DetectionCue：首次明确结果或有人/零候选切换 → CuePlayer 播放随包 WAV，重复状态不播、旧声音不排队；不等待 PresenceGate 的确认/宽限。低分不确定不宣告离开，过期/暂停停止播放。该分支不修改主应用奖励或声音规则；播放器状态并入原有诊断快照，实际听觉验收仍由 Roy 完成。

## 猫总界面

- AndroidManifest 的应用图标引用 drawable-nodpi/app_icon_hourglass.png，直接使用已确认的 C 标准沙漏修正版；原 drawable/ic_launcher.xml 继续供后台运行与提醒通知使用，避免彩色整块位图进入通知小图标路径。
- 首页只展示实际娱乐余额、累计有效工作和两个主要操作；有余额时“准玩”，零余额禁用本应用内小红书入口，外部应用拦截仍由原服务处理。首页保留真实保护状态，点击进入设置；权限和测试按钮从首页移入二级页面，保护动作沿用既有逻辑。
- MainActivity 通过显式 Intent 的 page 值打开 home/settings/permissions/tools 页面实例；每页在 onPause 移除刷新，在 onResume 沿用原有恢复调用。原生返回逐级退出，账本、应用 ID、签名与权限不变。
- settings 保护卡读取 ProtectionUiState，必要授权摘要独立呈现；permissions 从系统重读 usage/overlay/notification/backgroundRestricted，不新增配置成功标记。小米后台未报告受限时仍显示待核对。停止确认只在用户确认且 ledger.enabled 仍为 true 时执行原停用逻辑；恢复请求先检查原开启意愿及必要授权，保留既有 startForegroundService 路径，不凭请求成功宣称服务健康。运行诊断放入 tools。
- ui_preferences/cat_intro_seen_v3 仅记录当前每轮规则说明已展示，升级后说明一次；引导自身不写 ledger 或清零余额，每日规则初始化由账本负责。设置可重新打开使用及隐私说明。
- drawable-nodpi 中首页使用 cat_home_waiting / cat_home_approved 两张 A 方案图片：MainActivity 创建首页时通过 CatUi 以 inSampleSize=2 各解码一次，页面持有两个 Drawable，500ms 刷新只在 balanceMs > 0 状态变化时切换引用，与文案、娱乐按钮使用同一条件。阻止页共用 CatUi.blocker / BlockerView 与 cat_time_up.webp 无道具半身图，以 inSampleSize=2 解码并 FIT_CENTER 显示；旧 cat_blocker_expired 不再作为阻止页素材。旧 cat_home 保留为历史素材。工作页的盯场、持续离开、到账三图以 inSampleSize=4 按资源缓存，500ms 的状态刷新不重新解码图片。
- WorkActivity 仅在 capturing 且 WorkClock.AWAY 时显示“人呢？”；其他暂停/不确定状态说明真实原因。奖励提交后 2.8 秒内切换盖章图，AWAY 显示优先；pauseCamera 清除奖励表现。猫图与奖励反馈自身不触发声音或推理；人体检测声音由前述 DetectionCue / CuePlayer 独立处理，无网络调用。
- 页面用原生 ScrollView/LinearLayout 并限制内容最大宽度 480dp，处理系统栏和刘海边距；小屏、大字体及横屏允许滚动。工作页预览为 210×130dp，关闭时显示占位文字。打开说明因焦点变化暂停相机，关闭后按保留的监测意愿自动恢复。

## 耗尽反馈的触发与去重

- ProtectionService.settleUntil 在原用量结算前后只读比较：余额由正变零，且新增 consumedMs 恰等于原余额，才创建 ExhaustionFeedback；不改变 BalanceEngine、计时或每日清零算法。
- 事件为随机令牌，最长 5 秒内有效，不落盘。每日清零、重新获得余额、观测重建、权限/保存错误和服务销毁会清除；用户导航取消未呈现事件。服务重建后来源不明的零余额显示中性事实，不补演历史耗尽。
- 浮层立即显示同一静态画面并沿用现有音频焦点处理；它不播放动画。请求全屏页的 Intent 带令牌，BlockerActivity 实际获焦后向服务领取一次，再撤浮层并播放。动画不延迟拦截、页面启动或停声路径；Activity 保存状态恢复、不带令牌或旧令牌只呈现静态。onNewIntent 同样核对令牌；onPause/onDestroy 和 View 移除时取消动画。
- 角色、标题与底线分别做属性动画，余额和按钮不参与缩放。系统禁用动画时直接静态展示；不新增声音、震动、权限或联网。
- androidTest 的 FeedbackInstrumentation 只用于已有零余额且未工作时的原生样片验证；模拟的是进程内事件，禁止修改真实余额/消费/累计工作。它不证明真实消费到拦截的完整闭环。

## 摄像头与奖励边界

- 0.4.2 降负载试用曾以640×480请求[15,15] FPS。0.5.9发热工程试验版把Preview与ImageAnalysis目标共同降为320×240、共同请求本机前置支持的[10,10] FPS，并把推理间隔从500ms延长到1000ms。每次主动开始后Preview至少真实绑定并展示10秒，倒计时结束才允许手动解绑；解绑后只降低当前工作页窗口亮度至5%，主动暂停、结束或重新校准时恢复系统默认亮度。自动恢复沿用进程内关闭状态，主动暂停后重新开始恢复预览。低负载绑定失败仍回退原配置。配置请求不等于实际帧率或降温证明，须以真机状态核对。
- camera-status.json 增加 capture_policy、preview_resolution、preview_bound、analysis_width/height、preview_mode、analysis_interval_ms、screen_dimmed、preview_close_remaining_ms；尺寸来自绑定后的 ResolutionInfo 和实际分析帧，preview_bound 才表示 Preview 用例是否仍绑定，mode 只是 PreviewView 请求模式。读取脚本兼容旧版缺少这些字段的快照。帧率结合只读 media.camera 的当前请求/输出状态核对，不能从推理次数反推。
- [断线测温脚本](scripts/camera-thermal-record.sh)在 ADB shell 中独立运行：armed模式支持600、1200、1800或2400秒，等待拔线及新鲜监测后读取BatteryService同源数据。2400秒工程测试只记录第0、10、20、30、40分钟共5组，并在第20与第40分钟写明确CHECKPOINT；其他既有时长仍按每60秒记录。本地/data/local/tmp保存温度（0.1°C）、电量、充电状态和相机数值诊断，无图像。启动等待最多20分钟；检查点只能证明采样时状态，不能证明两个检查点之间完全无中断。smoke模式仅作5秒日志验证，不算温升测试。
- 工作页首次由用户主动启动，使用 KEEP_SCREEN_ON；不修改系统自动锁屏设置。暂停、结束、离开前台时解绑相机并释放亮屏标记。临时失焦/后台/锁屏保留内存监测意愿；onResume 刷新、窗口焦点及模型就绪经过同一个 maybeResumeCamera，只有前台可交互、解锁、有权限且用户仍希望监测时恢复。同进程旋转由 RecoveryModel 保留 CameraRecovery。外部应用短暂切换后，进程级 MonitoringReturnTicket 可在 90 秒内被根 MainActivity 一次性消费并转回工作页；它仅交接导航，WorkActivity 仍调用同一恢复门槛。主动暂停、工作页返回或结束清除凭证；进程重启、强停、划掉最近任务和冷启动默认停止。
- CameraRecovery 区分 IDLE/WAITING/STARTING/MONITORING/FAILED；每次启动或失去新鲜结果 8 秒重试，最多连续 3 次失败，间隔 1/2 秒，连续可用前台的一轮恢复总时限为 30 秒。连续新鲜分析结果 5 秒才清除失败预算，单帧不能无限重置；真实失焦取消本次尝试并在返回时重新给启动时间，保留已发生失败数，不修改账本的首次中断时间。模型准备超过 15 秒、权限缺失、不可恢复相机错误、检测或保存失败直接进入失败出口。无人/多人结果不触发相机重启。
- WorkActivity 同时观察 CameraX CameraState 与结果新鲜度；释放时先使采集代次失效并移除相机观察者，再解绑。恢复成功仍由 WorkClock 新鲜结果及在场确认驱动计时，绑定成功和 MONITORING 仅表示采集状态，不证明在场。诊断增加 recovery_state、recovery_failures 和 monitoring_wanted，不保存图像。
- 恢复失败在工作页持续显示原因及继续入口。CuePlayer 的两段在场提示独立于未记录语音；UnrecordedReminder 在每次非主动中断满 60 秒后才请求第三段“目前未有效记录”语音，整次中断最多一次。三种语音遵循同一开关和媒体音量，提示语音在通话、其他媒体或音量为零时抑制，不排队、不调音量；失焦/暂停释放提示音及焦点。原在场语音也在通话音频模式下抑制。实际可听及系统来电行为仍需真机确认。
- 当前发热工程试验参数：320×240目标分析尺寸，每1000ms最多一次推理，KEEP_ONLY_LATEST丢弃积压；实际分辨率由CameraX与设备协商。监测期间相机持续采集，分析限频不等于硬件每秒只采一帧或间歇关闭相机；每帧处理后关闭MPImage / ImageProxy并回收Bitmap，没有录像编码或图像落盘路径。恰好一个可靠人体作为在场线索，额外弱候选不否决该信号，连续约1秒确认后计时；两个及以上可靠人体保持不确定。零人体候选允许3秒宽限，超过后暂停。已经在场后仅有一个低分候选时最多保留120秒，达到上限后暂停并要求新的可靠确认；没有可靠人体的多个弱候选、未先确认的低分候选或其他不确定结果立即暂停。无身份识别，不证明工作行为，不含手机移动/另一部手机检测。
- 采集到结果超过 1.5 秒、相邻结果间隔超过 1.5 秒、锁屏、失去焦点或 Activity 暂停均不补计未知区间。仅接收当前采集代次的新结果，旧异步回调无法跨暂停或新会话发放时间；确认窗口不追补。页面定时器只刷新和检查失联，不生成有效时间。
- totalValidMs 保存历史有效工作；sessionValidMs 为当前轮有效进度，当前轮不足 600000 ms 时应得为零，达到后按 `floor(sessionValidMs / 60000) * 30000` 计算。每次观察仅加入本轮前后应得差额；sessionRewardMs 是本次显式开启期间累计成功奖励，只增不减，重置轮次时保留，用于同快照钱包差额计算。界面“本轮已获得”来自本轮应得值，不把以前轮次奖励算到当前轮。
- 连续暂停 180000 ms 时仅重置 sessionValidMs（门槛及余量随之重置），保留 totalValidMs、sessionRewardMs 和余额。finish 后 begin 清除本次计数；inactive→active 不产生奖励差额。奖励与工作检查点、已有消费字段一次 commit，成功后再发布内存结果；负奖励差额拒绝，失败暂停相机。
- work_interrupted_elapsed_ms 保存首次中断起点（-1 为计时中），work_checkpoint_elapsed_ms 保存最近工作状态检查点，work_checkpoint_boot_count 保存启动编号。暂停重复调用不能推迟起点，返回确认在场后才清除；后台不需常驻倒计时，回到工作页/继续/新帧前按实际单调时间检查期限。WorkClock.expire 返回最后画面新鲜度失效时间，expireWeakHold 返回弱单人保留的固定截止点，延迟 UI tick 不会重新赠送三分钟或越过弱信号上限。
- 进程重建时，未保存暂停的活跃检查点以最后观察时间起算中断；已有暂停则沿用原起点。设备启动编号变化、单调时间倒退或旧记录无时间基准时清除本轮资格，不补计未知时间。系统墙钟改动不参与三分钟判断。
- work_reward_rule_version=3 迁移只清除旧规则当前轮进度，保留实际余额、历史工作、已计入的历史补齐和当前开启期间奖励；不新发补差或追回。规则版本与数据同次提交，失败不发布内存结果；迁移前构造的候选不得覆盖迁移后状态。仅同一实验版沙箱迁移。
- 工作页有效时间最高 62sp（长时间按可用宽度缩放），奖励信息为辅；每分钟提交成功后轻提示一次，跨本轮所选门槛时长的倍数边界时用本轮里程碑替代。约 2.2 秒后 0.6 秒淡出，暂停取消、重开不重播；不增加声音、震动或弹窗。不变的文字不重复设置，页面刷新不等于发奖。
- 该方案避免正常重启/重复回调重复发奖；不是数据库交易日志或对磁盘故障、文件篡改的全面保证。消费、工作页面和保存都在主线程串行运行；摄像头分析在独立执行器进行，结果回主线程。

## 可配置奖励与每日到期

- WorkProgress 保存 `roundMinutes` 与 `selectedMinutes`，默认均为 10。短暂停只改变中断状态，保留本轮比例；结束后新开、三分钟重置或无法证明连续性的重启采用所选比例。门槛后奖励为每分钟 `300000 / roundMinutes` 毫秒。历史累计奖励只用于差额入账，不因余额到期回退。
- LedgerStore 快照新增 `work_round_minutes`、`work_selected_minutes`、`daily_reset_enabled/next_ms/zone/last_ms/notice`。旧版本缺少字段时保留规则 v3 的既有进度、余额与历史，初始化默认开启的下一本地 06:00；不重复执行旧规则迁移，不重置后续用户选择。
- DailyReset 负责纯日期策略；LedgerStore.refreshDailyReset 负责到期时先请求服务结算可核实的截止点前用量，再把清零余额与新截止点同次保存，成功后发布。失败恢复消费游标、消费累计及余额，不先发新奖励。连续工作只清钱包，已记账奖励保持，避免再次补发。
- ProtectionService 在到期截止点拆开用量结算；账本发奖先补做到期结算再入新奖励。主页面展示、娱乐入口、设置切换、保存及恢复广播均检查到期。无精确闹钟或新增权限；后台冻结、强停、停止保护时不承诺准点执行，恢复前不能把旧余额当新余额使用。
- 首页使用清零开关与截止点显示下一次时间；一次性清零提示与账本持久化，消费统计不含到期删除。工作页与共用阻止页读取实际比例；新规则引导使用 UI 偏好 v3。

## 运行与恢复边界

- 悬浮窗授权入口：Xiaomi 设备先显示经本机核对的“权限管理 → 其他权限 → 显示悬浮窗”引导，通过 ACTION_APPLICATION_DETAILS_SETTINGS + package URI 打开本应用详情；引导弹窗只显示进入本应用设置与取消按钮。应用详情无法启动（不存在或无权访问）时回退到列表。其他厂商直接沿用标准入口；不依赖小米私有组件名，不代授权限。首页依据实际 canDrawOverlays 状态更新按钮标签。

- 从用户可见的 MainActivity 调用 startForegroundService；Android 14+ 指定 specialUse 类型。通知使用低重要度频道与不可变 PendingIntent，服务不导出。
- 服务只在保护开启且两项必要权限均有效时开始循环。通知权限不是启动服务的前置条件，首页显示通知是否允许。
- 500 ms 轮询、750 ms 事件结算缓冲沿用原版；没有无障碍快速窗口回调，参数不构成实测延迟上界。
- 服务启动、权限恢复、异常或单调时钟心跳相隔超过5秒时，先用最近24小时使用事件重建前台状态，仅保留目标是否在前台，不保存应用历史。恢复段不扣费；750ms事件缓冲内继续接收切换事件，但恢复时刻以前不收费。没有可用历史时不沿用过期的target_active，仍需后续事件确认目标；24小时窗口不能保证识别更早且无后续事件的前台状态。
- 账本保存 observation_gaps 与 last_observation_gap_ms，开发工具展示中断次数和最近时长；包括休眠等停顿，不能直接解释成冻结次数。首页查询系统 isBackgroundRestricted 并提示异常，设置页提供本应用后台设置入口；小米额外提示省电策略。系统未报告受限或当前心跳正常均不代表厂商不会冻结后台。
- 普通全屏浮层使用 FLAG_NOT_FOCUSABLE，并接收浮层范围内触摸；意图是避免浮层夺取焦点生成目标 Activity 暂停事件，导致拦截反复显隐。焦点/触摸行为仍待真机验证。
- 0.3.4新增BlockerActivity；0.3.5每次新浮层首次onDraw后等待250ms再请求启动一次，不在每轮轮询重试。原因是本机addView/post会早于系统认定窗口可见，出现SAW-only路径被拒而visible-window路径放行的差异；等待不能替代真机验收。启动抛错或2秒后浮层仍在时写WTS_BLOCK_PAGE诊断，保留视觉覆盖。只有PAGE_VISIBLE窗口焦点回调才主动撤浮层；正常检测到离开/锁屏等路径仍可撤浮层。全屏页独立于target_active保持，避免目标暂停导致阻止页自动消失；500ms只核对开启意愿、余额和权限，不写账本。停止保护、余额到账或授权失效后结束自己的任务。
- 阻止页使用独立taskAffinity、singleTask、excludeFromRecents与noHistory，不进入工作页任务栈。按钮启动工具/桌面后仅移除阻止页任务；API33+注册系统返回回调，旧系统覆盖onBackPressed，统一回桌面。采用现有悬浮窗授权的后台启动例外，不增权限；厂商仍可能拒绝启动，PAGE_REQUESTED不能替代PAGE_VISIBLE及目标STOPPED实测。诊断脚本分别显示blocker_page_focused/visible与旧浮层可见性。
- 音频焦点作为浮层阶段的辅助：每次新阻止层请求一次USAGE_MEDIA / AUDIOFOCUS_GAIN，不接受延迟获焦；移除浮层时释放，不发播放键、不调系统音量、不循环争抢。Roy已接受可能打断其他后台媒体的取舍，但真机证明0.3.2单靠焦点仍持续出声，0.3.4主路径改为真实页面切换，不能将焦点获准视为成功停声。异常仅写WTS_AUDIO，不撤视觉覆盖或中断结算。[官方焦点说明](https://developer.android.com/media/optimize/audio-focus)描述的是有条件的播放方响应，不是按包强制暂停。
- 返回测试版/桌面使用普通 Intent，不调用无障碍全局动作。导航短暂抑制过期事件的重拦截；仍需真实手势、按钮和后台启动兼容性验证。
- `protection_policy_version=1` 首次迁移时默认 enabled=true（Roy 已明确要求启用新默认），后续 enabled 为持久化的用户意愿；手动停止保存 false，重新进入或升级不得重置。首页展示实际服务心跳、权限及健康度，不以 enabled 代替运行证据。首页移除有效工作余量数字，仅保留可用娱乐余额；内部余量按本轮门槛与中断规则维护。
- 有权限且意愿开启时，前台服务返回 START_STICKY 请求系统恢复；`ProtectionRecovery` 接收 `BOOT_COMPLETED`、本包更新及时区变化。开机时把 `RECEIVED → START_REQUESTED → STARTED` 或 `MANUAL_STOPPED`、`MISSING_PERMISSION`、`START_REJECTED`、`STORAGE_FAILED` 写入独立的 `boot_recovery_status` 本机偏好；`ProtectionService` 只在 `startForeground` 已成功且两项必要授权仍有效后记录 `STARTED`，并捕获服务提升为前台时的拒绝。主应用用 `Settings.Global.BOOT_COUNT` 建立基线，保护仍开启而新启动编号没有恢复记录时才写 `NO_BOOT_RECORD`，它是待排查线索而非“未收到广播”的确定结论。`scripts/protection-health-check.ps1` 同时读取当前启动编号和这份状态，不会打开应用或读取页面内容。首次进入也会尝试启动。未首次打开新策略的安装不在后台自行激活；强停后通常仍需用户重新打开，不宣称持续可靠。所有重启观察区间均不追扣未知历史，摄像头也不因冷启动自动开启。
- 手动停止成功后停止服务、移除浮层并存提醒起点；用 AlarmManager.setAndAllowWhileIdle 的单次非精确 RTC_WAKEUP（不申请精确闹钟权限）排四小时后的白天时段。接收时再次检查 enabled、当前时段和间隔，先提交新起点再通知以抑制重复广播；夜间延迟到次日09:00。开启保护取消待发提醒与现有未保护通知。关闭通知时跳过发送、不循环补发；时间倒退重建起点，时区变更按当地白天重排。提醒独立 prefs，不写入或改动工作/消费金额。
- 运行中权限失效暂停结算并移除浮层，推进观察起点；服务仍在时恢复权限后可继续。整体进程冻结或终止不能由自身通知证明仍在保护。
- 小米本机已出现前台服务仍存在、ActivityManager isFrozen=false、实际cgroup.freeze=1和do_freezer_trap同时成立的情况；greezer有tobg、screen off及LM FZ记录。冻结时本进程的定时器和恢复代码都不能执行，必须先解除相应后台限制；START_STICKY不能解冻尚未死亡的进程。只读诊断使用 scripts/protection-health-check.ps1，对照心跳新鲜度、实际cgroup、目标焦点和阻止窗口，避免打开主应用解冻后丢失故障现场。阻止窗口存在仍不能替代实际触摸/可见性验收。
- 快照、计时改时处理和未知区间保护沿用原版；未增加交易日志或通用存储故障兜底。

## 隐私与平台约束

默认保护阶段新增 RECEIVE_BOOT_COMPLETED 用于系统重启后按已保存意愿恢复；不增加网络、精确闹钟、麦克风或摄像头后台采集权限。

在已有 PACKAGE_USAGE_STATS、SYSTEM_ALERT_WINDOW、FOREGROUND_SERVICE、FOREGROUND_SERVICE_SPECIAL_USE、POST_NOTIFICATIONS 上增加 CAMERA；无无障碍服务、麦克风和存储权限，关闭应用备份。显式移除第三方依赖可能合入的 INTERNET 与 ACCESS_NETWORK_STATE 权限，离线模型随 APK 打包。只声明可查询小红书，使用事件中其他包名仅用于判断目标离开，不保留完整应用历史或读取页面内容。合并 manifest 及 APK 权限必须在构建后检查。

普通浮层在系统关键窗口之下；目标应用可拒绝覆盖，系统可调整可见性。浮层添加成功、账本记录计数不等于用户看到有效阻止页。省电、系统回收、锁屏恢复、延迟、音频及不同设备的行为必须分别验证。Google Play 对 specialUse 的审核不由构建通过替代。

验证结果、已安装版本与停点统一见 [任务索引](tasks/README.md)。小米后台冻结的排查方法、设置修正与证据边界见 [notes.md](notes.md)。
