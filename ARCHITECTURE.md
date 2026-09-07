# WorkToEarn 悬浮窗实验版实际结构

当前采用的 Android 路线为普通悬浮窗，由 Roy 在对照两版后明确选择；原版的无障碍结构保存在提交 `8a98c84` 和标签 `checkpoint/gate1-before-overlay-20260907`。完整产品要求见 docs/SPEC.md。本文件描述已实现机制，不把路线选择当作全部真机验收通过。

## 模块与数据流

单个原生 Java app 模块，无第三方运行时依赖。应用 ID `com.roy.worktoscroll.overlaytest`，Java namespace 保留 `com.roy.worktoscroll`；独立应用沙箱隔离余额与权限。

| 文件 | 职责 |
|---|---|
| MainActivity.java | 使用情况/悬浮窗/通知授权入口、保护启停、测试余额、状态展示；回到可见首页时按已保存开关尝试恢复服务 |
| Permissions.java | 查询使用情况访问和 Settings.canDrawOverlays，不查询或申请无障碍 |
| ProtectionService.java | 普通 Service，使用 specialUse 前台服务运行监测；通知提供回首页和停止操作；显示 TYPE_APPLICATION_OVERLAY |
| BalanceEngine.java | 原版复用的纯余额算法，按使用区间扣减，仅显式奖励能增加余额 |
| LedgerStore.java | 实验版同进程单例与 SharedPreferences 快照，原版私有数据不读取、不迁移 |

UsageEvents → 应用前后台/锁屏事件 → BalanceEngine → 实验版本机账本 → 零余额且小红书在前台时显示普通悬浮窗。

## 运行与恢复边界

- 悬浮窗授权入口：Xiaomi 设备先显示经本机核对的“权限管理 → 其他权限 → 显示悬浮窗”引导，通过 ACTION_APPLICATION_DETAILS_SETTINGS + package URI 打开本应用详情；引导弹窗只显示进入本应用设置与取消按钮。应用详情无法启动（不存在或无权访问）时回退到列表。其他厂商直接沿用标准入口；不依赖小米私有组件名，不代授权限。首页依据实际 canDrawOverlays 状态更新按钮标签。

- 从用户可见的 MainActivity 调用 startForegroundService；Android 14+ 指定 specialUse 类型。通知使用低重要度频道与不可变 PendingIntent，服务不导出。
- 服务只在保护开启且两项必要权限均有效时开始循环。通知权限不是启动服务的前置条件，首页显示通知是否允许。
- 500 ms 轮询、750 ms 事件结算缓冲沿用原版；没有无障碍快速窗口回调，参数不构成实测延迟上界。
- 普通全屏浮层使用 FLAG_NOT_FOCUSABLE，并接收浮层范围内触摸；意图是避免浮层夺取焦点生成目标 Activity 暂停事件，导致拦截反复显隐。焦点/触摸行为仍待真机验证。
- 返回测试版/桌面使用普通 Intent，不调用无障碍全局动作。导航短暂抑制过期事件的重拦截；仍需真实手势、按钮和后台启动兼容性验证。
- START_NOT_STICKY；没有开机自动启动。服务终止时保留 enabled，回到可见首页且权限仍有效时尝试恢复。首次开始监测重建观察起点，中断期间不补扣。停止保护则保存 enabled=false，移除浮层与通知并停止服务。
- 运行中权限失效暂停结算并移除浮层，推进观察起点；服务仍在时恢复权限后可继续。整体进程冻结或终止不能由自身通知证明仍在保护。
- 快照、计时改时处理和未知区间保护沿用原版；未增加交易日志或通用存储故障兜底。

## 隐私与平台约束

声明 PACKAGE_USAGE_STATS、SYSTEM_ALERT_WINDOW、FOREGROUND_SERVICE、FOREGROUND_SERVICE_SPECIAL_USE、POST_NOTIFICATIONS。不声明无障碍服务、联网、摄像头、麦克风或存储权限，关闭应用备份。只声明可查询小红书，使用事件中其他包名仅用于判断目标离开，不保留完整应用历史或读取页面内容。

普通浮层在系统关键窗口之下；目标应用可拒绝覆盖，系统可调整可见性。浮层添加成功、账本记录计数不等于用户看到有效阻止页。省电、系统回收、锁屏恢复、延迟、音频及不同设备的行为必须分别验证。Google Play 对 specialUse 的审核不由构建通过替代。

验证结果、已安装版本与停点统一见 [TASK.md](TASK.md)。
