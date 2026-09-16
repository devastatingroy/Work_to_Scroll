# 夜间放松额度

状态：v53 `0.5.31-night-relax-preview` 已保数据覆盖安装；构建/lint/160项单测通过，等待真机跨夜验收（2026-09-16）。入口：[任务索引](../README.md)。

## 目标与授权

Roy 要求小红书版新增：每晚一段时间让监控没那么紧，又不至于无限刷塌方。经独立评估后确认采用**限额方案（方案 A）**：默认每晚本地时间 22:00–08:30 送固定放松额度（默认 45 分钟），窗口内先扣额度、用完回落到正常余额规则（有余额继续扣、余额空了才拦），08:30 额度作废并自动恢复 22:00 前的保护状态。不改动保护开关本身，保留既有账本、奖励、摄像头与后台保护行为。

方案取舍与理由见对话记录：不采用“定时改开关 + 记忆旧状态”，改为纯时间窗覆盖层——因为它只“少拦”、不依赖精确闹钟，天然规避小米冻结期无法可靠恢复的问题；“恢复 10 点前设置”因保护开关未被改写而自动成立。

## 步骤

- [x] 独立评估可行性与产品风险，确认限额方案 A（额度优先、耗尽回落）。
- [x] 新增纯 record `NightRelax`（跨零点时间窗、每晚只 refill、重启保剩余、时区处理）。
- [x] `BalanceEngine` 增加 relax budget：先扣额度、溢出扣余额、额度不计入 consumedMs。
- [x] `ProtectionService.settleUntil` 按窗口边界分段结算；拦截判定按窗口内/外分流。
- [x] `LedgerStore` 持久化七个夜间字段、refreshNight/setNightRemaining/setNightEnabled/nightDescription。
- [x] `MainActivity` 设置页娱乐规则加“夜间放松”开关与说明，激活显示今晚剩余分钟。
- [x] 单测：`NightRelaxTest`（跨零点、windowStartKey 稳定、每晚只 refill、重开强制刷新、clamp、越界钳制）+ `BalanceEngineTest` 追加额度优先/溢出/清除三例。
- [x] 同步 SPEC、README、ARCHITECTURE。
- [ ] 真机跨夜验收：22:00 进窗放行、45 分钟耗尽后按余额回落、余额空落拦截、08:30 出窗恢复；小米冻结/锁屏/重启期间的边界行为。
- [x] 覆盖安装账本核对：v52→v53普通`adb install -r`成功；余额、消费、历史工作、保护开关、每日清零、比例等业务字段保留，`cursor_ms`仅按服务启动时的当前时间自然前移。

## 证据与边界

- `:app:testDebugUnitTest` 160项全过；`:app:assembleDebug` 出包；`:app:lintDebug` 0 错误（保留23项既有warning）。v53 APK SHA-256 为`AD0C55D036B293C0BD2399D33DFE0CFD18A2A3454A88C16F5D787DB874C9E701`，已从v52保数据覆盖安装；安装前APK和账本分别备份为`app/build/device-before-v53-v52.apk`与`app/build/before-v53-ledger.xml`。构建与安装不等于真机验收——窗口边界的实际拦/放、跨夜与冻结行为必须按真实时间点在手机上验证。
- 改动文件：新增 `NightRelax.java`、`NightRelaxTest.java`；修改 `BalanceEngine.java`、`LedgerStore.java`、`ProtectionService.java`、`MainActivity.java`、`BalanceEngineTest.java`。未触碰基线分支上其他未提交的 doc/task 改动。
- 分支：`feature/night-relax-quota`。

## 当前停点与下一步

代码与文档已就绪并提交到 `feature/night-relax-quota`。下一步在日常真实使用中完成上述未勾选的真机场景，记录是否存在窗口边界误拦/漏拦、额度刷新时机偏差或与每日清零的交互问题。逻辑单测不等于自然跨夜验收。

关联：[娱乐规则](entertainment-rules.md) · [摄像头奖励](camera-rewards.md) · [后台保护](background-protection.md) · [产品要求](../../docs/SPEC.md)。
