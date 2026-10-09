# 放松时间窗（夜间 + 午间）

状态：v53 `0.5.31-night-relax-preview` 已保数据覆盖安装；构建/lint/单测通过，**等待真机跨夜验收（2026-09-16）**。新增的午间窗（11:00–13:00 / 30 分钟）已完成代码与单测，同样**未经真机验收**。入口：[任务索引](../README.md)。

## 目标与授权

Roy 要求小红书版新增：每晚一段时间让监控没那么紧，又不至于无限刷塌方。经独立评估后确认采用**限额方案（方案 A）**：默认每晚本地时间 22:00–08:30 送固定放松额度（默认 45 分钟），窗口内先扣额度、用完回落到正常余额规则（有余额继续扣、余额空了才拦），08:30 额度作废并自动恢复 22:00 前的保护状态。不改动保护开关本身，保留既有账本、奖励、摄像头与后台保护行为。

2026-09-18 Roy 追加要求：中午 11:00–13:00 再送 30 分钟。已确认沿用同一规则——**未用完的部分 13:00 作废、次日 11:00 重给**，且**默认开启**。实现为同一时间窗记录的第二个实例，不复制结算逻辑。

方案取舍与理由见对话记录：不采用“定时改开关 + 记忆旧状态”，改为纯时间窗覆盖层——因为它只“少拦”、不依赖精确闹钟，天然规避小米冻结期无法可靠恢复的问题；“恢复 10 点前设置”因保护开关未被改写而自动成立。

## 步骤

- [x] 独立评估可行性与产品风险，确认限额方案 A（额度优先、耗尽回落）。
- [x] 新增纯 record `RelaxWindow`（跨零点时间窗、每晚只 refill、重启保剩余、时区处理）。初版名为 `NightRelax`，因午间窗复用同一记录而改名。
- [x] `BalanceEngine` 增加 relax budget：先扣额度、溢出扣余额、额度不计入 consumedMs。
- [x] `ProtectionService.settleUntil` 按窗口边界分段结算；拦截判定按窗口内/外分流。
- [x] `LedgerStore` 持久化夜间与午间两组字段、`refreshWindows`/`giftWindow`/`setWindowRemaining`/`nextWindowEdge`/`giftLabel` 统一路由，`setNightEnabled`/`setNoonEnabled` 分别开关。
- [x] `MainActivity` 设置页娱乐规则加“夜间放松”“午间放松”两个开关与说明，激活中的那个额外显示当前剩余分钟；首页按生效窗口显示赠送来源与结束时间。
- [x] 单测：`RelaxWindowTest`（跨零点、windowStartKey 稳定、每晚只 refill、重开强制刷新、clamp、越界钳制、午间窗边界与次日重给）+ `BalanceEngineTest` 追加额度优先/溢出/清除三例 + `EntertainmentRulesTest` 追加午间路由、边界分段、开关持久化四例。
- [x] 同步 SPEC、README、ARCHITECTURE。
- [ ] 真机跨夜验收：22:00 进窗放行、45 分钟耗尽后按余额回落、余额空落拦截、08:30 出窗恢复；小米冻结/锁屏/重启期间的边界行为。
- [ ] 真机午间验收：11:00 进窗放行、30 分钟耗尽后回落、13:00 出窗恢复且未用完作废、与 06:00 每日清零互不干扰；午间与夜间窗口切换处不误拦/漏拦。
- [x] 覆盖安装账本核对：v52→v53普通`adb install -r`成功；余额、消费、历史工作、保护开关、每日清零、比例等业务字段保留，`cursor_ms`仅按服务启动时的当前时间自然前移。
- [ ] 午间字段为新增 key，需在真机覆盖安装时确认夜间额度未被重置、午间额度默认给满。

## 证据与边界

- `:app:testDebugUnitTest` 160项全过；`:app:assembleDebug` 出包；`:app:lintDebug` 0 错误（保留23项既有warning）。v53 APK SHA-256 为`AD0C55D036B293C0BD2399D33DFE0CFD18A2A3454A88C16F5D787DB874C9E701`，已从v52保数据覆盖安装；安装前APK和账本分别备份为`app/build/device-before-v53-v52.apk`与`app/build/before-v53-ledger.xml`。构建与安装不等于真机验收——窗口边界的实际拦/放、跨夜与冻结行为必须按真实时间点在手机上验证。
- 改动文件：新增 `RelaxWindow.java`、`RelaxWindowTest.java`（删除 `NightRelax.java`/`NightRelaxTest.java`）；修改 `BalanceEngine.java`、`LedgerStore.java`、`ProtectionService.java`、`MainActivity.java`、`BalanceEngineTest.java`、`EntertainmentRulesTest.java`。未触碰基线分支上其他未提交的 doc/task 改动。
- 分支：`feature/night-relax-quota`。
- 索引：已补入[任务索引](../README.md)的 active 表。文件名沿用 `night-relax-quota.md` 以保持与分支 `feature/night-relax-quota` 和既有链接一致。

## 当前停点与下一步

代码与文档已就绪并提交到 `feature/night-relax-quota`。下一步在日常真实使用中完成上述未勾选的真机场景，记录是否存在窗口边界误拦/漏拦、额度刷新时机偏差或与每日清零的交互问题。逻辑单测不等于自然跨夜验收。

关联：[娱乐规则](entertainment-rules.md) · [摄像头奖励](camera-rewards.md) · [后台保护](background-protection.md) · [产品要求](../../docs/SPEC.md)。
