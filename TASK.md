# Task：iPhone 版本设计探索

## 新会话接续入口（2026-09-07 保存停点）

- Roy 要求保存相关文件与项目进度，将另开会话继续苹果版开发；本会话到此暂停。保存要求不等于已经确认草案中的所有范围与方案取舍。
- 接续时先读本节和 [iPhone 设计草案](tasks/ios-design-draft.md)，再按需对照 [SPEC](docs/SPEC.md) 和 [DESIGN](docs/DESIGN.md)。iPhone 仍处于设计阶段，没有新增 iOS 工程、编译结果、安装包或真机验收。
- 下一步先确认可用 iPhone 型号/iOS、Mac、Apple Developer Program 账号及首期实施范围；上一会话的异步问题尚未收到答案，新会话不能假定已具备环境或等待旧问题自动回传。
- 推荐首期为“授权 → 选择小红书 → 零余额限制 → 加入测试额度 → 实际使用扣减 → 耗尽重锁”，范围确认后再实施。优先证明分段消费与追加奖励不会重送额度；摄像头和电脑联动的后续顺序仍是提案。
- 正式 SPEC/DESIGN/ARCHITECTURE 本轮未修改；草案里已标出旧 DESIGN 中需要按最新 Apple 文档修正的返回入口，以及尚未解决的余额与统计边界。不要把旧技术判断或新候选方案直接视为已验证事实。
- Android 授权调查、竞品截图和既有原型停点均已保存在本项目，下方保留恢复现场；新会话默认接续 iPhone，不重复 Android 授权实验或自动开始 Android 五分钟测试。

## 当前任务：iPhone 版本方案（2026-09-07）

- [x] Roy 接受当前 Android 授权门槛暂时难以避免，明确要求下一任务转向苹果版本，先设计方案。
- [x] 对照现有 SPEC/DESIGN，核查 Apple 官方个人授权、系统限制页、实际使用阈值、数据访问边界及开发/分发资格。
- [x] 形成 [iPhone 设计草案](tasks/ios-design-draft.md)，仅为待确认提案，未开始编码或修改正式产品规则。
- [ ] Roy 确认第一阶段范围；已询问先验证手机限制与消费，还是完整 MVP 设计后分阶段实施。
- [ ] 确认可用 iPhone 型号/iOS、Mac 与 Apple Developer Program 账号；已发出问题，尚待回复。
- [ ] 确认方案后再更新正式 DESIGN 并进入实施；原生拦截、消费结算和分发均未做 iPhone 真机验证。
- 关键新证据：iOS 26.5 起存在官方 `openParentalControlsApp`；旧 DESIGN §4.3 的返回方式需按版本修正。普通授权下的使用报告不能直接作为主应用实时账本；iOS 26.4 新增直接数据访问在客户安装场景受 EU 条件限制，不能作为中国用户通用依赖。完整余额结算仍需技术验证，不承诺秒级精确。
- Android 调查和实现恢复现场保留如下；本轮不操作 Android 手机、不启动待完成的五分钟测试。

## Android 调查结论与恢复现场

### 当前新增调查：减少 Android 敏感授权（设计探索，2026-09-07）

- [x] 核对当前实现、SPEC 核心规则与用户原始警告截图：本轮重点是使用情况访问；不能把已移除无障碍当作问题解决。
- [x] 对照 Android 官方接口与 AOSP 权限/角色源码、系统自带限制、VPN、桌面/容器/设备管理及分发渠道，调查保持核心功能的替代路线。
- [x] 汇总可行条件、体验代价、证据边界和最小验证建议，见 [调查记录](tasks/android-permission-research.md)。
- 当前调查结论：在普通个人设备/普通安装、保留现有小红书、按实际用时扣减与零余额自动限制的条件下，未找到免敏感授权的等价公开路线；VPN、桌面、自有网页与自愿模式会改变体验，设备管理/调试会引入新的门槛。系统数字健康能力不代表普通 App 可申请同等资格。
- 当前停点（2026-09-07 晚间）：已完成一个同机正规商店竞品的直接对照。小米商店安装的“不做手机控”6.0.9.7 在本机 Xiaomi 14 Pro / Android 16 / HyperOS OS3.0.307.0.WNBCNXM 上，撤销后重开使用情况访问也显示同样的红色危险、隐私/财产安全与自行承担后果警告；Roy 明确确认该次对应“不做手机控”。截图及跨机型资料见 [调查记录](tasks/android-permission-research.md) 末节。不能再预设“商店安装就可消除该警告”；但不能推及所有应用或机型。三星/Pixel/vivo 仅取得官方资料线索，无其他真机验收。未确认替代产品路线。
- 本轮仅调查；不改应用代码、正式 SPEC/DESIGN 或手机权限，不启动既有五分钟测试。下方保留上一实现任务的恢复现场。
- 同机调查校正：已通过小米商店安装“不做手机控”6.0.9.7（com.pl.getaway.getaway，安装来源 com.xiaomi.market）。Roy 随后明确更正：刚才亲手开启和确认的是 WorkToEarn 的权限，不能作为竞品授权证据。后续须从名称明确的系统应用列表重新定位；单独读取到权限 allow 也不能证明对应警告是否出现。Agent 未代授或撤销权限，未启动竞品保护。
- 后续已纠正归属：从独立“应用使用情况数据”列表定位“不做手机控”，Roy 手动复测并明确确认 21:34 警告属于竞品，见 `tasks/hyperos-competitor-usage-warning.png`。此前误操作记录仍无效，不与本次有效样本混用。竞品仍保留安装，用户手动调整过权限；Agent 未撤销或代授权限，未替用户点击风险确认，未测试竞品拦截，不将手机最终权限/运行状态假设为原始值。

**2026-09-07 最新决定与当前任务**：Roy 已选择普通悬浮窗新版，主分支已合入；原版检查点、记录和独立 APK 保留。Roy 亲自看到 0.1.1 引导后要求去掉“应用列表入口”，避免两个入口显乱。0.1.2-overlay-test 已删除该按钮及对应说明，弹窗只保留“进入本应用设置”和“取消”；仅在应用详情启动异常时自动回退标准列表，其他厂商继续使用标准入口。构建及 lint 通过，更新安装成功，冷启动正常。真机读取首页确认已授权按钮存在，但随后手机重新锁屏，尚未取得修改后弹窗画面。未改变权限、保护开关或余额，未执行五分钟及长期可靠性检查。

2026-09-07，Roy 已亲自完成使用情况访问和普通悬浮窗授权，此前读取两项 appops 均为 allow。Roy 再次对照明确反馈新版更好：原版两项授权都吓人，新版只有使用情况访问仍明显吓人，关键语句为“财产安全将会受到威胁”；第二项需在大量应用列表里找工具的操作不便已实现入口优化，等待新引导真机检查。零余额阻止页与返回测试版通过本机技术检查，回桌面复测有成功画面但首次采样不一致，暂不升级为稳定通过。上次测试结束已停止实验保护，余额为 0；本轮未重新读取手机状态或进入五分钟消费测试。

上一实现任务停点：Roy 回复“ok”，并要求同步支撑文件；单入口简化已完成并获确认，相关代码提交为 `ed0298d`。README、DESIGN 与 ARCHITECTURE 已与实现一致。当时仅整理文档，不操作手机或开始后续测试；尚无修改后弹窗的独立真机画面，五分钟消费及后续可靠性验证继续保留待办。

Roy 的选型依据：新版的授权警告体验相对更好，但共同依赖的使用情况访问仍有明显劝退风险。两张用户提供的原图保存在主项目 `tasks/hyperos-usage-access-warning.png` 与 `tasks/hyperos-accessibility-warning.png`；原图均显示“危险”、隐私/财产安全警告及自行承担后果确认。本轮仅更新入口代码与安装包，没有撤销或代授任何系统权限。

## 隔离与备份

- 主目录：`D:/Software_Projects/Work_to_Scroll`，分支 `master`，采用新版代码；原版应用 `com.roy.worktoscroll` 仍在手机上独立保留。
- 实验目录：`D:/Software_Projects/Work_to_Scroll-overlay`，分支 `experiment/no-accessibility`，保留早期实验与构建证据；后续修改以主目录 `master` 为准，不默认两处代码或安装包同步。当前新版应用 ID 为 `com.roy.worktoscroll.overlaytest`，名称 `WorkToEarn 悬浮窗测试`。
- 起点提交：`8a98c84`，标签 `checkpoint/gate1-before-overlay-20260907`。包含原版代码及当时未提交的 README、TASK、恢复研究记录。合入前的原版记录完整保存在主项目 `tasks/gate1-accessibility-baseline.md`。
- 独立源码备份：`D:/Software_Projects/Work_to_Scroll-backup-20260907/gate1-before-overlay.bundle`，已通过 git bundle verify。
- 原版安装包备份：同目录 `WorkToEarn-0.1.2-gate1.apk`；SHA-256 `FFF088C3758EB6EA1E7DA766EA16C0479F954934FCDDFC9466865EFEB4A68004`，与原版记录一致。
- worktree 共享 Git 对象库，不是独立灾备；bundle 是额外源码副本。手机私有余额与系统授权不在 bundle 中，原应用保留不动；被 Git 忽略的历史构建日志仍在原目录，不包含于 bundle。
- 安装前只读原版账本：`enabled=false`、余额 `0`、累计扣减 `1620912 ms`、阻止计数 `22`。本轮没有改变原版开关或余额；该快照不保证此后状态不变。

## 已完成

- [x] 原版检查点、标签、独立备份与实验 worktree。
- [x] 独立应用 ID 和名称，不覆盖或迁移原版数据。
- [x] 移除无障碍组件及配置；使用普通 Service / specialUse、运行通知和停止入口。
- [x] 使用情况与普通悬浮窗授权入口；通知授权单独、可选。
- [x] 复用 UsageEvents 与余额算法；普通浮层、正常 Intent 返回路径。
- [x] 停止保护移除服务、通知和浮层；重新打开首页按已有开关尝试恢复，中断期间不补扣；不自动开机启动。
- [x] 既有分段测试脚本改读实验版账本，增加原版保护关闭检查与第一阶段接受开关。
- [x] 更新 README、ARCHITECTURE、DESIGN 的实验范围；SPEC 未改。
- [x] APK 构建、9 项单元测试、lint、签名、包 ID 和组件/权限检查。
- [x] 采用新版路线并合入主分支；Xiaomi 悬浮窗引导简化为单设置入口，0.1.2 已安装，Roy 确认并要求同步支撑文件。

## 第一阶段：待真机与 Roy 验收

- [x] Roy 确认后重试安装成功，冷启动 `Status: ok`，首页展示独立名称、零余额、未开启保护和两项权限入口；截图 `app/build/wte-overlay-start.png`。
- [~] Roy 已亲自授权并再次对照，选择新版；使用情况访问的“财产安全将会受到威胁”仍明显劝退。此前悬浮窗列表难找的问题已通过本应用设置入口优化，单入口简化已获确认；这不代表全部第一阶段检查通过。
- [~] 零余额打开小红书已显示实验版阻止页，再次打开后 10 秒采样仍显示，前两次进入对应 blocks=2，未见反复新增浮层；截图 `app/build/wte-overlay-block.png`、`wte-overlay-stable.png`。这不是连续录像或完整触摸防穿透验收。
- [~] 返回测试版成功，topResumedActivity 与 `wte-overlay-return.png` 一致。回桌面首次点击后的 Activity 采样仍为小红书，复测截图 `wte-overlay-home.png` 为桌面；首次未同时保存画面，不能确定原因，保留可靠性待核对。
- [x] 点击停止保护后首页显示未开启，账本 enabled=false，服务列表为空，浮层消失；运行通知未获用户授权，本次不验证通知抽屉显示/停止按钮。截图 `app/build/wte-overlay-stopped.png`。

### 授权入口优化

- 0.1.0 的按钮使用标准 ACTION_MANAGE_OVERLAY_PERMISSION 且带 package URI；[Android 11 官方变更](https://developer.android.com/about/versions/11/privacy/permissions) 说明该入口进入顶层列表并忽略 package，符合 Roy 的体验，不能承诺标准接口直接到单应用开关。
- 本机只读导航发现替代路径：ACTION_APPLICATION_DETAILS_SETTINGS + 本应用 package → 本应用“应用信息” → “权限管理” → “其他权限” → “显示悬浮窗”。最后页面确实显示本应用权限项且已允许，未点击改变开关。截图 `app/build/wte-overlay-appdetails.png`、`wte-overlay-permissiondetails.png`、`wte-overlay-otherpermissions.png`。
- 0.1.1 已按用户要求实施：Xiaomi 上先显示路径说明，再直接进入本应用详情；当时提供“应用列表入口”；0.1.2 按 Roy 反馈删除该可见入口及说明，仅保留启动异常时自动回退至标准列表。已授权时第二项改显示“悬浮窗已授权 · 查看设置”。其他厂商沿用标准入口，不推广为全机型直达方案。
- [ ] 0.1.2 单入口弹窗真机检查：安装成功、首页已读取；随后手机重新锁屏，未取得修改后弹窗画面。Roy 已亲自看到此前 0.1.1 弹窗并反馈两个入口显乱；不将此反馈视为 0.1.2 画面验收。

## 第二阶段：第一阶段通过后再执行

- [ ] 连续与分段 5 分钟扣减、锁屏暂停、耗尽再拦截；记录误差、实际显示延迟和漏拦。
- [ ] 长时间后台运行、进程终止、强停后重新打开恢复；单独记录系统行为，不能混为自动恢复通过。
- [ ] 耗电与厂商省电限制；是否需要用户额外设置须真机确认。

## 安装包与验证证据

- 当前 APK：`app/build/outputs/apk/debug/app-debug.apk`，`0.1.2-overlay-test`，versionCode `3`；本次文件位于主目录，已更新安装。
- 当前 SHA-256：`7F948E18A26E577CFD2BD03245CCBDEA87B89F83F1BAEBA4C378E5E2164D7512`。
- 0.1.2 的 assembleDebug 成功，lint 0 errors / 5 warnings（Gradle 更新提示、备份兼容提示及 3 项既有文本国际化提示）；只改权限入口与版本，不改计时/服务逻辑，没有为本次低风险引导改动新增或重跑余额单元测试。
- 初始 0.1.0 历史检查：`assembleDebug` 成功；`testDebugUnitTest` 9 项，0 失败/错误；`lintDebug` 0 errors / 4 warnings（既有备份兼容性提示与文本国际化提示）；不冒充本次 0.1.2 重跑结果。
- 初始 0.1.0 历史检查：APK v2 签名验证通过；aapt 确认独立包名、5 项声明权限；合并 manifest 无无障碍组件、无 INTERNET 权限，服务不导出。
- 测试脚本通过 PowerShell 语法解析；本轮未运行五分钟设备脚本。
- 首次安装曾返回 `INSTALL_FAILED_USER_RESTRICTED`；Roy 确认继续后重试返回 `Success`。随后启动首页成功，初始使用情况访问为 default、普通悬浮窗为 ignore，首页也显示未授权；未授予任何系统权限，未开启保护或加入测试余额。

证据边界：单元测试只验证复用的余额算法；编译、manifest 和签名检查不验证前台服务运行、覆盖效果或恢复体验。浮层 FLAG_NOT_FOCUSABLE 旨在避免焦点暂停造成显隐循环，仍需真机验证。新权限也敏感，不能预先承诺更易接受。

## 入口与来源

- 本文早期 0.1.0 真机截图及手动系统导航截图的 `app/build/` 路径，均相对于 `D:/Software_Projects/Work_to_Scroll-overlay`；当前 0.1.2 安装包与构建报告位于主目录 `app/build/`。这些构建产物未纳入 Git，不作为独立备份。
- [README](README.md)：安装、两阶段操作与恢复原版方法。
- [ARCHITECTURE](ARCHITECTURE.md)：此分支实际代码、数据流与生命周期边界。
- [DESIGN](docs/DESIGN.md)：用户已选择的当前方案，原 MVP 设计保留作背景。
- 原版研究结论及官方来源：[原版记录](tasks/gate1-accessibility-baseline.md) “无障碍替代路线调查”；既有 `tasks/accessibility-recovery.md` 仅属于原版研究，不能当作此版验证结果。
