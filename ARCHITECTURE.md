# WorkToScroll / WorkToWatch 实际结构

本文只记录当前代码已经形成的模块、数据流和技术边界，帮助维护者定位改动影响。产品规则与体验承诺以 [SPEC](docs/SPEC.md) 为准；方案、候选和取舍见 [DESIGN](docs/DESIGN.md)；当前验收状态与设备证据见[任务索引](tasks/README.md)；设备排障和脚本使用见 [notes](notes.md)。

## 产品变体与隔离边界

单个原生 Java `:app` 模块通过 `workToScroll` 与 `workToWatch` product flavor 构建两个独立安装包。

| 边界 | 实现 |
| --- | --- |
| 安装身份与数据 | 每个变体有独立 application ID，因此 Android 沙箱、SharedPreferences 和私有文件互不读取或迁移。 |
| 产品配置 | 目标应用、产品名称、目标应用提示和必要运行前提由变体的 `BuildConfig` 提供；共用代码不得硬编码某一个产品。 |
| 产品资源 | `app/src/workToScroll` 与 `app/src/workToWatch` 保存产品专属字符串和资源覆盖；其余逻辑留在 `main`。 |
| 内部代码位置 | 两个产品共用 `com.roy.worktoscroll` namespace；它不是应用身份或数据边界。 |

构建配置及 APK 层隔离检查见 [app/build.gradle](app/build.gradle)。具体产品范围、品牌与发布状态不在本文维护。

## 模块划分与所有权

| 模块 | 主要组成 | 职责与不得承担的事 |
| --- | --- | --- |
| 产品外壳与界面 | `MainActivity`、`CatUi`、`BlockerView`、`BlockerActivity` | 展示首页、设置、工作入口及共享阻止页；不作为账本或保护健康的唯一事实来源。 |
| 保护与目标应用处理 | `ProtectionService`、`Permissions`、`ProtectionPolicy`、`ProtectionRecovery`、`ProtectionUiState` | 在用户意愿和必要前提均满足时结算目标应用用量并呈现阻止界面；不启动摄像头，也不保证被强停或厂商冻结期间继续运行。 |
| 账本与时间规则 | `LedgerStore`、`BalanceEngine`、`DailyReset`、`RelaxWindow`、`WorkProgress` | 维护娱乐余额、使用累计、有效工作和到期/额度状态；相关变更通过同一私有快照提交，避免局部写入造成金额与进度口径分裂。 |
| 工作监测 | `WorkActivity`、`CameraRecovery`、`MonitoringReturnTicket`、`PersonObservation`、`WorkClock` | 在前台、解锁且用户明确开启时完成本机相机分析、在场判断和有效时间累计；相机意愿不跨冷启动恢复。 |
| 反馈与诊断 | `ExhaustionFeedback`、`DetectionCue`、`CountingResumeCue`、`CuePlayer`、`PauseFlash`、`WorkDebugLog` | 将已确认的业务或监测状态转为界面/声光反馈，并输出受限的本机诊断；反馈不改变账本，诊断不参与奖励。 |

## 关键数据流

### 娱乐保护

```text
系统使用事件
  → ProtectionService
  → BalanceEngine / LedgerStore 结算当前变体的目标应用用量
  → 零余额时先显示普通悬浮层
  → BlockerActivity 确认可见后接手呈现并让目标退到后台
```

`ExhaustionFeedback` 只接受可验证的新耗尽事件，并在进程内一次消费，避免服务、浮层和全屏页重复演出同一次耗尽。普通悬浮层与全屏阻止页共用呈现组件，防止两个路径形成不同的业务或视觉状态。

### 工作奖励

```text
前置 CameraX 图像
  → 本机 MediaPipe 人体观察
  → PersonObservation / WorkClock 形成有效时间增量
  → WorkProgress 计算本轮进度与奖励差额
  → LedgerStore 同次写入历史工作、进度与娱乐余额
```

图像只在内存中用于当前分析，不进入账本、日志或上传路径。检测声音与暂停反馈读取观察/计时状态，但不能绕过 `WorkClock` 直接增加有效时间或奖励。

### 生命周期与恢复

```text
用户保存的保护意愿 + 必要前提
  → MainActivity / 系统恢复入口请求 ProtectionService
  → 服务重读账本和系统状态后继续或暴露异常

前台工作页的临时失焦
  → CameraRecovery 保留同进程监测意愿
  → 满足前台、解锁、授权和模型就绪条件后才恢复分析
```

`MonitoringReturnTicket` 只在同一进程内提供一次性的导航交接，不会在后台、冷启动或明确结束后重开工作页或相机。系统恢复同样不会自动开启摄像头。

## 持久化、恢复与诊断边界

- 业务账本保存在各产品自己的 `ledger` 私有偏好中；UI 偏好、保护恢复状态和诊断状态独立保存，不用 UI 显示替代账本或服务健康。
- 每日到期、夜间额度和工作奖励是账本的不同状态，但由结算路径协调，避免同一使用区间被重复扣减或奖励。
- `camera-status.json` 只保留最新诊断快照；调试构建可维护有大小上限的工作会话事件日志。它们不保存图像、页面正文或完整浏览历史，也不作为当前监测仍在运行的单独证明。
- 工作监测的相机状态、保护服务存活和实际阻止效果分别取证；任一状态不能代替另一项的真机验收。

## 权限、隐私与平台约束

- 保护机制依赖使用情况访问、普通悬浮窗和前台服务；工作监测仅在显式开始后的前台使用摄像头。
- 当前实现不包含无障碍服务、麦克风采集、网络传输、连续图像/录像保存或完整浏览历史存储；离线模型随应用包提供。
- 普通悬浮层可受目标应用、系统和厂商策略影响。进程冻结、系统回收、强停、音频、锁屏和多窗口等体验结果必须单独验证，不能由构建、服务存在或账本记录推断。
- 产品变体的 APK 隔离检查只验证身份与配置边界，不能替代权限、运行行为或用户体验验收。

## 资料入口

- [产品要求与用户体验](docs/SPEC.md)
- [技术方案、候选路线与未决项](docs/DESIGN.md)
- [当前任务、验收证据与下一步](tasks/README.md)
- [调试经验与设备运行注意事项](notes.md)
