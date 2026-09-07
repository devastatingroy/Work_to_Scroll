# Task：当前 Android 悬浮窗原型

## 当前结论与停点

**2026-09-07 最新决定与当前任务**：Roy 明确选择普通悬浮窗新版作为当前 Android 路线，并要求优化悬浮窗授权入口；这不等于认定 Android 只有两种实现，也不等于剩余可靠性检查已通过。0.1.1-overlay-test 已实现 Xiaomi“本应用设置 + 已验证路径说明”，保留标准应用列表入口作为备选；其他厂商保持标准入口。构建及 lint 通过，已更新安装，手机报告 versionCode=2；实际新引导页面导航验证因手机锁屏尚未完成。所选新版已通过合并提交 `99db3d6` 合入主分支，主目录构建成功且 APK 哈希与已安装版本一致；原版保留检查点、记录和独立 APK；不改应用 ID、不迁移数据。

2026-09-07，Roy 已亲自完成使用情况访问和普通悬浮窗授权，此前读取两项 appops 均为 allow。Roy 再次对照明确反馈新版更好：原版两项授权都吓人，新版只有使用情况访问仍明显吓人，关键语句为“财产安全将会受到威胁”；第二项需在大量应用列表里找工具的操作不便已实现入口优化，等待新引导真机检查。零余额阻止页与返回测试版通过本机技术检查，回桌面复测有成功画面但首次采样不一致，暂不升级为稳定通过。上次测试结束已停止实验保护，余额为 0；本轮未重新读取手机状态或进入五分钟消费测试。

当前采用方向已确定，授权信任风险与入口优化继续记录；五分钟消费及后续可靠性验证仍未执行，本轮只处理入口优化，不把选择路线作为测试通过。

Roy 的选型依据：新版的授权警告体验相对更好，但共同依赖的使用情况访问仍有明显劝退风险。两张用户提供的原图保存在主项目 `tasks/hyperos-usage-access-warning.png` 与 `tasks/hyperos-accessibility-warning.png`；原图均显示“危险”、隐私/财产安全警告及自行承担后果确认。本轮仅更新入口代码与安装包，没有撤销或代授任何系统权限。

## 隔离与备份

- 主目录：`D:/Software_Projects/Work_to_Scroll`，分支 `master`，采用新版代码；原版应用 `com.roy.worktoscroll` 仍在手机上独立保留。
- 实验目录：`D:/Software_Projects/Work_to_Scroll-overlay`，分支 `experiment/no-accessibility`，应用 `com.roy.worktoscroll.overlaytest`，名称 `WorkToEarn 悬浮窗测试`。
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

## 第一阶段：待真机与 Roy 验收

- [x] Roy 确认后重试安装成功，冷启动 `Status: ok`，首页展示独立名称、零余额、未开启保护和两项权限入口；截图 `app/build/wte-overlay-start.png`。
- [~] Roy 已亲自授权并再次对照，明确新版警告体验更好；使用情况访问的“财产安全将会受到威胁”仍明显劝退，悬浮窗列表入口难找，尚不算授权体验整体验收通过。
- [~] 零余额打开小红书已显示实验版阻止页，再次打开后 10 秒采样仍显示，前两次进入对应 blocks=2，未见反复新增浮层；截图 `app/build/wte-overlay-block.png`、`wte-overlay-stable.png`。这不是连续录像或完整触摸防穿透验收。
- [~] 返回测试版成功，topResumedActivity 与 `wte-overlay-return.png` 一致。回桌面首次点击后的 Activity 采样仍为小红书，复测截图 `wte-overlay-home.png` 为桌面；首次未同时保存画面，不能确定原因，保留可靠性待核对。
- [x] 点击停止保护后首页显示未开启，账本 enabled=false，服务列表为空，浮层消失；运行通知未获用户授权，本次不验证通知抽屉显示/停止按钮。截图 `app/build/wte-overlay-stopped.png`。

### 授权入口优化

- 0.1.0 的按钮使用标准 ACTION_MANAGE_OVERLAY_PERMISSION 且带 package URI；[Android 11 官方变更](https://developer.android.com/about/versions/11/privacy/permissions) 说明该入口进入顶层列表并忽略 package，符合 Roy 的体验，不能承诺标准接口直接到单应用开关。
- 本机只读导航发现替代路径：ACTION_APPLICATION_DETAILS_SETTINGS + 本应用 package → 本应用“应用信息” → “权限管理” → “其他权限” → “显示悬浮窗”。最后页面确实显示本应用权限项且已允许，未点击改变开关。截图 `app/build/wte-overlay-appdetails.png`、`wte-overlay-permissiondetails.png`、`wte-overlay-otherpermissions.png`。
- 0.1.1 已按用户要求实施：Xiaomi 上先显示路径说明，再直接进入本应用详情；提供“应用列表入口”，启动异常时回退至标准列表。已授权时第二项改显示“悬浮窗已授权 · 查看设置”。其他厂商沿用标准入口，不推广为全机型直达方案。
- [ ] 新引导弹窗与按钮跳转的真机检查：版本已安装，但 2026-09-07 本轮检查时屏幕处于锁屏/休眠，未得到新页面画面；不能用此前手动打开系统设置的截图替代。

## 第二阶段：第一阶段通过后再执行

- [ ] 连续与分段 5 分钟扣减、锁屏暂停、耗尽再拦截；记录误差、实际显示延迟和漏拦。
- [ ] 长时间后台运行、进程终止、强停后重新打开恢复；单独记录系统行为，不能混为自动恢复通过。
- [ ] 耗电与厂商省电限制；是否需要用户额外设置须真机确认。

## 安装包与验证证据

- 当前 APK：`app/build/outputs/apk/debug/app-debug.apk`，`0.1.1-overlay-test`，versionCode `2`；主目录与独立 worktree 同一路径均已构建，文件哈希一致。
- 当前 SHA-256：`38B173B96C82757EA5F9FE680F738C3C605736FFAE28EBF2644735211D22F1C9`。
- 0.1.1 的 assembleDebug 成功，lint 0 errors / 4 warnings；只改权限入口与版本，不改计时/服务逻辑，没有为本次低风险引导改动新增或重跑余额单元测试。
- `assembleDebug` 成功；`testDebugUnitTest` 9 项，0 失败/错误；`lintDebug` 0 errors / 4 warnings（既有备份兼容性提示与文本国际化提示）。
- APK v2 签名验证通过；aapt 确认独立包名、5 项声明权限；合并 manifest 无无障碍组件、无 INTERNET 权限，服务不导出。
- 测试脚本通过 PowerShell 语法解析；本轮未运行五分钟设备脚本。
- 首次安装曾返回 `INSTALL_FAILED_USER_RESTRICTED`；Roy 确认继续后重试返回 `Success`。随后启动首页成功，初始使用情况访问为 default、普通悬浮窗为 ignore，首页也显示未授权；未授予任何系统权限，未开启保护或加入测试余额。

证据边界：单元测试只验证复用的余额算法；编译、manifest 和签名检查不验证前台服务运行、覆盖效果或恢复体验。浮层 FLAG_NOT_FOCUSABLE 旨在避免焦点暂停造成显隐循环，仍需真机验证。新权限也敏感，不能预先承诺更易接受。

## 入口与来源

- [README](README.md)：安装、两阶段操作与恢复原版方法。
- [ARCHITECTURE](ARCHITECTURE.md)：此分支实际代码、数据流与生命周期边界。
- [DESIGN](docs/DESIGN.md)：用户已选择的当前方案，原 MVP 设计保留作背景。
- 原版研究结论及官方来源：[原版记录](tasks/gate1-accessibility-baseline.md) “无障碍替代路线调查”；既有 `tasks/accessibility-recovery.md` 仅属于原版研究，不能当作此版验证结果。
