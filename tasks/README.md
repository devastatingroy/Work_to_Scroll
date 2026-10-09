# 任务索引

这里是唯一任务入口。恢复中断的多步骤任务时：AGENTS → 本索引 → 用户指定或唯一当前主任务 → 只读该任务文件 → 按需读取正式文档与代码；不遍历其他任务或历史。若只有并行轨道且用户未指定任务，不擅自选择。详细进度、版本、设备证据和停点只在对应任务文件维护。

## 当前主线

Google Play 首发分为两条相互独立的轨道：[WorkToWatch 产品实施](active/overseas-target-apps.md)负责独立 YouTube 产品的定义、开发、适配和可靠性验收；[Google Play 流程与合规准备](active/google-play-release.md)负责账号、测试轨道、隐私和商店审核材料。任一轨道完成均不代表另一轨道完成。用户明确指定其他任务时，以用户要求为准。

## 目录与状态规则

| 目录 | 职责 | 状态迁移 |
| --- | --- | --- |
| active | 正在推进，或等待反馈、依赖、真机验证 | 实际开始后从 todo 移入；阻塞但仍在推进的任务留在此处并写明阻塞 |
| todo | 尚未启动或明确暂缓 | 恢复时核对范围与授权，再开始；候选不因入列自动获准实施 |
| completed | 已结束的任务与历史证据 | 写明完成、取消或被替代；未完项转交接续任务并链接，归档不等于验证通过 |

每个任务只有一个维护位置；改变状态时移动原文件，不复制出第二套进度。同步索引及所有入链、出链，附件随所属任务移动或保留稳定资料位置。历史文档的复选框与当时判断可保留，但必须标明适用时点与接续入口。

索引只维护当前主任务、链接、一句话目标和必要依赖；详细进度、版本、待回复问题、结果均放在对应任务中。正常恢复不遍历 todo/completed；需要依赖或原始证据时再沿链接读取。

## 任务文件约定

- 一项可独立接续、验收的多步骤工作一个 Markdown 文件，使用稳定、描述性的英文短横线文件名；不为单轮小修改强制建任务。
- 内容至少包括：标题与状态、目标/范围与授权边界、步骤复选框、验证证据/局限、当前停点、下一步及关联资料。任务完成时补充结果和未完事项去向。历史归档保留原格式并加状态说明，无需机械重写。
- 执行该任务的 agent 负责维护；开始、关键步骤完成、证据推翻旧状态、阻塞/暂停、关键验证及结束时更新。迁移文件前读取当前内容，保留其他会话的新改动。
- 产品要求回写 SPEC，长期方案回写 DESIGN 或相关设计稿，实际代码结构变化才更新 ARCHITECTURE，可复用排障经验进入 notes；任务中保留来源链接，不形成第二套正式规范。
- 任务的步骤完成不等于问题已解决；真机、视觉、触摸、听觉等验收必须有对应证据。不能以构建或模拟测试代替用户体验验收。

## active

| 任务 | 目标与依赖 |
| --- | --- |
| [状态变化的戏剧化 UI 反馈设计](active/state-feedback-design.md) | 接续耗尽反馈的动效与真实消费验收 |
| [设置页重新设计](active/settings-redesign.md) | 接续引导、返回和后台状态的真机体验核对 |
| [每日清零与兑换比例](active/entertainment-rules.md) | 验收真实新增档位奖励、自然跨夜清零和后台到期 |
| [放松时间窗（夜间 + 午间）](active/night-relax-quota.md) | 验收夜间与午间赠送的进出窗、额度耗尽回落、未用完作废与每日清零互不干扰 |
| [摄像头监控发热对照测试](active/camera-thermal-test.md) | 完成严格同条件耐久与识别体验验证 |
| [零余额阻止页音频处理](active/audio-blocking.md) | 验收阻止后的停声与返回边界 |
| [摄像头计时与每轮奖励验收](active/camera-rewards.md) | 验收日常计时、奖励和中断恢复 |
| [猫总监工界面验收](active/cat-ui.md) | 验收首页、引导、返回路径和工作反馈 |
| [WorkToWatch 产品实施](active/overseas-target-apps.md) | 独立 YouTube 产品的实现、适配和可靠性验收 |
| [TikTok 营销视频关键帧](active/marketing-video-keyframes.md) | 制作并审阅 WorkToWatch 阻止页、到账画面的宣传视频关键帧 |
| [画中画软件监控可行性研究](active/picture-in-picture-monitoring.md) | 保留 PiP 前提、真实视频和跨机型边界的研究入口 |
| [Google Play 流程与合规准备](active/google-play-release.md) | 推进账号、测试轨道、隐私和商店审核材料 |
| [后台冻结与消费拦截耐久验收](active/background-protection.md) | 验证后台保护、停声和退出可靠性 |
| [默认保护与提醒验收](active/default-protection.md) | 验证系统回收、手动停止和提醒行为 |

## todo

| 任务 | 目标与依赖 |
| --- | --- |
| [监控时的手机摆放提醒](todo/camera-placement-reminder.md) | 提醒避免笔记本电脑等物体遮挡，建议手机放在侧面、侧后方或背后，距离不超过 0.6 米；已记录，尚未实施 |
| [Android 多目标娱乐应用监控准备](todo/target-app-selection.md) | 为小红书、抖音、红果等已支持 App 的单选/多选监控准备范围、边界和逐项真机验收；不改变当前固定小红书原型 |
| [WorkToWatch 单产品多入口与多目标能力研究](todo/worktowatch-multi-entry-product-research.md) | 研究一个 Google Play 产品、多单点营销入口及后续多目标能力的可行性与边界；仅记录研究任务，不等于已批准实施 |
| [国内软件商店候选准备](todo/release-readiness.md) | 国内小米/华为等商店改为候选路线；保留既有调研和材料矩阵，不启动账号、资质或备案流程 |
| [小米商店资质与备案候选准备](todo/xiaomi-publishing-credentials.md) | 国内候选的具体资质入口；小米注册未提交，恢复时先重新核对主体与平台规则 |
| [iPhone 核心原型开发准备](todo/ios-prototype.md) | 暂缓方向；恢复时核实 Mac、Xcode、设备、账号与签名 |

## completed

| 任务或历史档案 | 结果范围与接续 |
| --- | --- |
| [侧后方无脸在场检测探索](completed/side-rear-presence-exploration.md) | 已结项：640未显示收益，保持320/0.50；原始证据保留，长期识别/计时/发热验收已转交现有任务 |
| [应用图标采用与备选归档](completed/app-icon-design.md) | 标准沙漏已安装，其他候选备选归档；桌面裁切与辨识交由猫总界面验收 |
| [任务目录与恢复入口迁移](completed/task-organization.md) | 唯一入口、分类与引用迁移已完成 |
| [应用显示名称修改](completed/app-rename.md) | 名称修改已完成；当前整体界面由猫总 UI 任务验收 |
| [早期摄像头与奖励实现](completed/android-camera-history.md) | 旧阶段被接续；剩余验收见 active 摄像头任务与 todo 发热测试 |
| [普通悬浮窗替代与早期验收](completed/android-overlay-history.md) | 旧阶段被当前版本接续；可靠性未完项见后台保护任务 |
| [Android 敏感授权调查](completed/android-permission-research.md) | 调查完成，候选路线及跨机型结论保持原证据边界 |
| [原版无障碍恢复调查](completed/accessibility-recovery.md) | 旧版调查结束，保留未覆盖场景与暂缓加固方向 |
| [原版 Gate 1 历史基线](completed/gate1-accessibility-baseline.md) | 旧路线被替代，历史待验收项不视为通过 |
| [根目录文档职责整理](completed/documentation-information-architecture.md) | README、ARCHITECTURE、AGENTS 与任务索引职责已收束 |
| [正式文档有效内容收束](completed/formal-document-consolidation.md) | DESIGN、SPEC 与 notes 已按当前有效内容和职责收束 |

## 正式资料与设计入口

[产品要求](../docs/SPEC.md) · [方案总入口](../docs/DESIGN.md) · [实际结构](../ARCHITECTURE.md) · [调试经验](../notes.md) · [界面与角色设计](../docs/design/interface-and-roles.md) · [iPhone 设计草案](../docs/design/ios-design-draft.md)

界面参考图随设计稿放在 docs/design，权限警告截图随 completed 中的调查记录保存；从所属文档进入，不为附件另建重复索引。
