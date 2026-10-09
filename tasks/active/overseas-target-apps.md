# WorkToWatch 产品实施

状态：**当前产品实施轨道（2026-09-16 已开始开发）**。与 Google Play 流程与合规准备并行；现有 WorkToScroll 小红书产品保留，另行开发独立品牌 WorkToWatch，首发只支持官方 YouTube Android App。当前 v53 基线已形成双产品构建，永久应用 ID 确认为 `com.roy.worktowatch`；静态构建与隔离验证通过，并已在小米 14 Pro 完成独立安装、必要授权、保护服务启动和零余额 YouTube 阻止首验，正数余额扣减及其余可靠性矩阵仍待验收。创建日期：2026-09-09。

入口：[任务索引](../README.md)。维护人：实际启动本任务的 agent；进入实施前移入 `active`，并在关键决策、验证或阻塞时维护本文件。

## 目标与范围

为面向 Google Play 的独立 WorkToWatch 产品形成可实施、可验收的产品定义，并复用当前 WorkToScroll 已开发的通用计时、奖励、摄像头、账本、保护与阻止能力。WorkToWatch 面向“无意识或过量使用 YouTube”的单点问题；用户侧不宣传为 WorkToScroll 系列，也不展示通用目标选择。

本任务不是“把包名替换一下”的实施授权。它的可交付物是：WorkToWatch 目标用户与承诺、首发市场/语言建议、官方 YouTube Android App 适配范围、品牌与商店输入、体验及可靠性验收清单。YouTube 在目标国家的可用性、标准包标识、前台使用事件、悬浮窗遮挡表现、音频和返回路径均需核实。不得借此扩展为读取页面内容、观看/搜索历史、完整应用列表、账户信息或新增后台摄像头。

## 已确认产品路线（2026-09-15）

- [x] 保留 WorkToScroll 小红书产品，不覆盖其代码目标、应用身份、数据、文案或已有验证边界。
- [x] WorkToWatch 是独立产品与独立用户品牌；共享底层代码不要求用户看见系列关系。
- [x] 首发只支持整个官方 YouTube Android App，不承诺仅识别 Shorts，也不展示 TikTok、Instagram 或通用 App 选择。
- [x] 继续采用“有效工作/学习获得 Watch Time、按 YouTube 前台实际使用扣减、耗尽后显示本产品阻止页”的自律辅助边界。
- [x] WorkToWatch 使用独立 Google Play 商品、应用身份和数据沙箱；不迁移或共享 WorkToScroll 余额。
- [x] 对外宣传集中于 YouTube 失控使用，不使用“管理你最爱刷的软件”等泛化主张。
- [x] `WorkToWatch` 为当前命名方向，不使用冒号副标题或 YouTube 商标作为产品名；正式采用前仍须完成名称、包名、域名及商标可用性核查。
- [x] TikTok、Instagram 不属于本产品首发或隐藏路线；真正启动时另行决定独立品牌和商店形态。

## 启动前要确认

- [ ] 确定首发国家/地区；Google Play 已是首发分发方向，但账号、注册、付款和提交仍由流程轨道单独推进。
- [x] 确认 YouTube 为唯一首发支持对象；TikTok、Instagram 不默认进入本产品。
- [x] 确认 WorkToWatch 采用独立应用身份、商店商品和数据，不与 WorkToScroll 共用安装包或余额。
- [x] 确认产品承诺仍是“按前台使用扣减、耗尽后显示本应用阻止页”的自律辅助工具，不承诺系统级封禁、绕过防护或跨所有 Android 设备可靠运行。
- [ ] 确认首发语言组合及英文文案；此前方案同意英文优先，但具体语言地区与正式文案尚未定稿。

## 实施前核查与验收

- [ ] 核心人体识别的多场景/多机型稳定性、误暂停及无人误恢复仍待量化验证。[侧后方识别探索已按Roy要求结项](../completed/side-rear-presence-exploration.md)：本轮真实640×480未显示收益，保留320×240/0.50；短时样本不代表该项通过。此处只接收未完验收边界，不自动重开实验或授权改码。
- [ ] 核实官方 YouTube Android App 在首发地区的安装、启动、前台/后台事件与标准包标识；不将网络资料或单一机型结果当作跨地区事实。
- [ ] 在至少一种原生 Android 与两种主要厂商系统上，分别验收有余额扣减、余额耗尽、全屏阻止、返回/桌面、锁屏/恢复、音频与分屏/多窗口边界。
- [ ] 明确产品所需权限、数据边界、首次同意/拒绝体验与用户可理解的状态反馈；流程轨道据此完成隐私、Data safety 与审核说明。
- [ ] 提供与实际功能一致的 WorkToWatch 文案、截图需求和验收场景；不得沿用“小红书”或中国大陆版已验证范围的陈述，也不得使用 YouTube 商标作为本产品品牌或图标。
- [ ] 与当前摄像头、奖励、后台保护耐久验收分开记录，并以它们作为产品公开可用性的独立证据；国际目标 App 适配不能替代这些核心可靠性验收。

## 实施启动（2026-09-16）

- [x] Roy 已明确授权开始开发 WorkToWatch。
- [x] 以当前 v53 `0.5.31-night-relax-preview` 为代码基线建立独立分支 `codex/worktowatch-android`；原 WorkToScroll 分支和安装数据不覆盖。
- [x] 重新执行 160 项单元测试与 lint：测试 160/160 通过，lint 0 错误、23 个既有警告；Debug 构建通过。
- [x] Roy 确认永久应用 ID 为 `com.roy.worktowatch`。公开 Google Play 精确 ID 检索无结果，直接商品页返回 404，但这只能说明未发现公开商品，不能证明后台未被占用，最终仍以 Play Console 创建商品时的校验为准。
- [x] 建立 WorkToScroll / WorkToWatch 双产品构建变体及独立 source set；WorkToScroll 保持 `com.roy.worktoscroll.overlaytest`、v53 与既有数据升级路径，WorkToWatch 使用独立 `com.roy.worktowatch`、versionCode 1 / `0.1.0-dev`。
- [x] 把目标包、产品名、目标应用名、入口文案和 Manifest 查询从共用逻辑中分离；WorkToWatch 只配置官方 YouTube Android App `com.google.android.youtube`，WorkToScroll 继续只配置 `com.xingin.xhs`。
- [x] 增加两种构建各自的产品身份单测和 `verifyDebugProductIsolation` APK 检查。两边各 161 项单测通过，lint 均为 0 错误、23 个既有警告；两个 Debug APK 与各自 AndroidTest APK 构建通过，应用 ID、产品名、BuildConfig 目标、内部 action 前缀和 Manifest 查询隔离检查通过。
- [x] 在小米 14 Pro / HyperOS 3.0 独立安装 WorkToWatch Debug 构建，核对官方 YouTube 包、独立应用 ID/数据目录、必要授权、保护服务、零余额启动阻止及返回桌面。
- [x] WorkToWatch 独立 launcher drawable 已按 Roy 2026-09-16 最新指定更换为 1254×1254 不透明白底 PNG（深绿沙漏、蓝色上半沙、红色播放键），源文件与 flavor 资源 SHA-256 均为 `01B02304405261CBA54899676D4FDB75E8C7B9C5A494248C25A9F81837E5DADD`；WorkToScroll 图标资源不变。
- [x] 一笔 5 分钟测试余额已完成 YouTube App 前台扣减与耗尽自动阻止；未播放真实视频，不能替代停声验收。
- [ ] 继续验证锁屏恢复、真实视频音频、分屏/多窗口和长期后台边界；单机条件链路不能替代完整矩阵。
- [x] 2026-09-16 晚间定位到 WorkToWatch 的“零余额可看”现象：夜间赠送额度仍剩约 45 分钟，因而 YouTube 被正常放行；服务、必要授权和账本余额均正常。Roy 确认保留赠送，首页已改为主数字显示“当前可用娱乐”，并写明“攒得 + 夜间赠送（至结束时间）”；目标入口与猫总状态也按同一合计口径处理。小米覆盖安装保留独立账本与授权，真机首页显示 44 分 49 秒（攒得 0、夜间赠送 44 分 49 秒、至 08:30），从首页点击“去 YouTube”已实际进入官方 YouTube。该回归只验证赠送显示/入口，不替代正数扣减、耗尽转换、画中画、音频或长期后台验收。

## 首轮真机证据（2026-09-16）

- 设备：小米 14 Pro（`23116PN5BC` / `shennong`），HyperOS 3.0；官方 YouTube 为 `com.google.android.youtube`，版本 `21.21.87`。
- 安装：`com.roy.worktowatch`、versionCode 1、`0.1.0-dev`，数据目录 `/data/user/0/com.roy.worktowatch`；原 WorkToScroll 仍为 `com.roy.worktoscroll.overlaytest`、v53、独立数据目录。安装前后原账本的余额 0、累计工作 `52532063 ms`、阻止计数 199 均未改变，只有正常监测游标前进。
- 小米增强防护：经文件管理器安装的新包被标记为未知来源受限，`ACCESS_RESTRICTED_SETTINGS=ignore`，导致“使用情况访问”开关受限；旧 WorkToScroll 的安装来源为 `com.android.shell`，没有该附加限制。经 Roy 要求协助解决后，只把 WorkToWatch 的 `ACCESS_RESTRICTED_SETTINGS` 改为 `allow`，再由 Roy 在系统页亲自开启“使用情况访问”；悬浮窗同样由 Roy 手动授权。最终 `GET_USAGE_STATS=allow`、`SYSTEM_ALERT_WINDOW=allow`。未授予截图中被小米拒绝的“获取应用列表”，本产品合并 Manifest 也未请求 `QUERY_ALL_PACKAGES`。
- 运行：`ProtectionService` 以前台服务运行；WorkToWatch 初始账本为零，不继承 WorkToScroll。零余额启动 YouTube 后，前台切至 `com.roy.worktowatch/com.roy.worktoscroll.BlockerActivity`，阻止计数从 0 增至 1，随后回到桌面，YouTube 不再保持前台。
- 图标：2026-09-16 已按 Roy 最新图片更换为白底深绿沙漏、蓝色上半沙和红色播放键，重新构建与产品隔离检查通过；覆盖安装后在 HyperOS 桌面目视确认图形完整、无裁切，与紫底 WorkToScroll 清楚区分。
- 边界：本轮证明单台小米设备上的独立安装、权限、官方 YouTube 识别、零余额阻止、回桌面路径、5 分钟 App 前台扣减—耗尽自动阻止及桌面图标裁切；未证明真实视频停声、锁屏/恢复、分屏/多窗口、其他厂商或长期后台可靠性。

## 当前停点

产品路线、双产品构建、APK 隔离、首台小米真机零余额阻止及一笔 5 分钟测试余额的前台扣减—耗尽自动阻止已经落地；该正余额样本停留在 YouTube App 前台，不能替代真实视频播放与停声验收。下一步继续锁屏/恢复、真实视频音频、分屏/多窗口，再补主要厂商和长期后台样本。首发国家/语言、正式品牌文案和 Google Play 流程继续分别推进，不把单机条件链路表述为可公开发布。

## 画中画边界（2026-09-16，已确认）

后续研究、权限边界、验收与停点已独立归档为当前子任务[画中画软件监控可行性研究](picture-in-picture-monitoring.md)；本节保留本产品的原始现象和已知实现边界。

- Roy 在小米真机报告：YouTube 进入画中画后仍可见且继续播放，但 WorkToWatch 余额不再扣减；这不是已通过的正余额验收，原真机记录已明确排除分屏/多窗口边界。
- 余额归零时，阻止页会替代 YouTube 全屏界面，但 YouTube 自动缩成右上角画中画；视频当下暂停，单击后仍有完整媒体控制。现有阻止只改变全屏/音频状态，没有移除系统画中画或封住后续播放入口。
- Roy 已确认 WorkToWatch V1 不支持 PiP，必须先关闭官方 YouTube 的系统画中画；不再研究保留 PiP 时的计时和强行监管，也不新增 AccessibilityService、通知读取或设备管理权限。
- 关闭 PiP 后，小米 14 Pro 的首次进入、最近任务、桌面等价启动、三次重复和 YouTube 进程重启均未绕过零余额阻止；这是单机有条件成立证据，不代表跨厂商可靠性。
- 产品实现提供直达系统 PiP 设置和明确用户确认；普通应用无法可靠自动读取该开关，因此不会把确认状态包装为自动检测，也不能防止用户从系统设置重新开启后从外部进入 YouTube。
- 正余额 App 前台“扣减到零→自动阻止”已经通过；下一步继续真实视频停声、锁屏/恢复、分屏、长期后台与跨机型矩阵。

## 关联资料

- [Google Play 流程与合规准备](../active/google-play-release.md)
- [WorkToWatch 产品要求](../../docs/SPEC.md#worktowatch)
- [当前固定小红书边界](../../docs/DESIGN.md#gate-1android-奖励消费与再拦截基础原型)
- [后台保护耐久验收](../active/background-protection.md)
- [摄像头计时与每轮奖励验收](../active/camera-rewards.md)
