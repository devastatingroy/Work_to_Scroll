# 任务索引

2026-09-16 当前操作：[WorkToWatch 产品实施](active/overseas-target-apps.md)已获 Roy 授权开始开发。以当前 v53 `0.5.31-night-relax-preview` 为基线建立 `codex/worktowatch-android` 分支；160 项单元测试重新执行并全部通过，lint 0 错误、23 个既有警告，Debug 构建通过。当前停在首次代码落地前的永久应用 ID 确认，推荐 `com.roy.worktowatch`；确认后先建立不覆盖 WorkToScroll 身份和数据的双产品构建与隔离检查，再进行 YouTube 适配和真机闭环。原[摄像头监控发热对照测试](active/camera-thermal-test.md)现场保持不变，后续从其任务文件继续。

这里是唯一任务入口。恢复流程：AGENTS → 本索引 → 用户指定或当前主任务 → 按需读取正式文档与代码。当前主线为 Google Play 首发，明确分为两条独立轨道：[WorkToWatch 产品实施](active/overseas-target-apps.md)负责独立 YouTube 产品的定义、开发、适配和可靠性验收；[Google Play 流程与合规准备](active/google-play-release.md)负责账号、测试轨道、隐私和商店审核材料。2026-09-15 已确认保留现有 WorkToScroll 小红书产品，另做独立品牌 WorkToWatch；首发只支持整个官方 YouTube Android App，底层可复用但应用身份、数据和平台内容隔离；2026-09-16 已授权开始开发。两条轨道相互依赖，但任何一条的完成都不代表另一条完成；国内软件商店仅保留为候选路线。此前[设置页重新设计](active/settings-redesign.md)，0.5.7 已安装小米“锁定最近任务”三步图解，待解锁核对真机排版；“待核对”状态映射仍是独立未决问题。[每日清零与兑换比例](active/entertainment-rules.md)此前 0.5.0 已覆盖安装、79 项测试通过，余额和历史保留；等待真实新增档位奖励与自然跨夜验收。此前[猫总监工界面验收](active/cat-ui.md)，0.4.4 已安装“猫总批假”双状态图（零余额“工作优先”桌牌／有余额“准了”红章）并提交 `7ce99bb`，下一步是解锁手机核对实机显示、零余额画面及余额双向切换。[摄像头监控发热对照测试](active/camera-thermal-test.md)短测三轮已完成、支持低负载版本明显减轻发热，长期耐久与量化识别表现仍未验收。[侧后方在场观察](completed/side-rear-presence-exploration.md)已结项并保留历史证据；[零余额阻止页音频处理](active/audio-blocking.md)保留验收入口。用户明确指定其他任务时以用户要求为准，不能由目录排序推定授权。

2026-09-13 结项：[侧后方人体识别探索](completed/side-rear-presence-exploration.md)已按Roy要求结束。本次真实640×480未显示识别收益，保持默认320×240/0.50；证据完整保留，不再等待补采或下一轮实验。未完的识别可靠性、计时及发热验收已交由现有对应任务保留，本轮未commit，既有分支/stash/未提交改动保留。

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
| [状态变化的戏剧化 UI 反馈设计](active/state-feedback-design.md) | 猫总耗尽单幕 0.5.4 已覆盖安装、103 项单测通过；零余额静态页已核对，接续动效与消费验收 |
| [设置页重新设计](active/settings-redesign.md) | 0.5.10 已安装；允许自启动后的正常重启已实测恢复小红书封禁，待核对新增引导的排版/返回与省电“待核对”映射 |
| [每日清零与兑换比例](active/entertainment-rules.md) | 已安装；接续真实 15/20 分钟奖励、自然跨夜清零及后台到期验证 |
| [摄像头监控发热对照测试](active/camera-thermal-test.md) | v52第七轮预览持续开启、有效37分07秒端点为−1.8°C；高起温及条件不明，严格同条件耐久仍待验 |
| [零余额阻止页音频处理](active/audio-blocking.md) | 处理阻止后继续出声，保留共享音频焦点的确认与真机验收边界 |
| [摄像头计时与每轮奖励验收](active/camera-rewards.md) | 当前源码为v46；v49–v52诊断已结项，继续验收日常计时、奖励和中断恢复 |
| [猫总监工界面验收](active/cat-ui.md) | 0.5.2 已安装阻止页 A 到期假条图，待解锁验收；接续首页双状态、引导、返回路径及工作反馈验收 |
| [WorkToWatch 产品准备](active/overseas-target-apps.md) | **当前产品轨道**：独立 YouTube 产品路线已确认；首发单点、品牌/应用身份/数据隔离，尚未获准改码 |
| [Google Play 流程与合规准备](active/google-play-release.md) | **当前流程与合规轨道**：账号、测试轨道、Data safety、隐私与商店审核材料；未注册/未付款/未提交 |
| [后台冻结与消费拦截耐久验收](active/background-protection.md) | 0.5.6 已实测单任务、单最近任务计数及目标 STOPPED；0.5.7 加入锁定图解并保留修复，待完成最新版本视觉、停声和退出回归 |
| [默认保护与提醒验收](active/default-protection.md) | 已通过一次允许自启动后的正常重启拦截；接续系统回收、手动停止和四小时实际提醒 |

## todo

| 任务 | 目标与依赖 |
| --- | --- |
| [监控时的手机摆放提醒](todo/camera-placement-reminder.md) | 提醒避免笔记本电脑等物体遮挡，建议手机放在侧面、侧后方或背后，距离不超过 0.6 米；已记录，尚未实施 |
| [Android 多目标娱乐应用监控准备](todo/target-app-selection.md) | 为小红书、抖音、红果等已支持 App 的单选/多选监控准备范围、边界和逐项真机验收；不改变当前固定小红书原型 |
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

## 正式资料与设计入口

[产品要求](../docs/SPEC.md) · [方案总入口](../docs/DESIGN.md) · [实际结构](../ARCHITECTURE.md) · [调试经验](../notes.md) · [界面与角色设计](../docs/design/interface-and-roles.md) · [iPhone 设计草案](../docs/design/ios-design-draft.md)

界面参考图随设计稿放在 docs/design，权限警告截图随 completed 中的调查记录保存；从所属文档进入，不为附件另建重复索引。
