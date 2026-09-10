# 每日清零与兑换比例

状态：实施及覆盖安装完成，等待真实奖励和自然跨夜验收（2026-09-09）。入口：[任务索引](../README.md)。

## 目标与授权

Roy 已确认完整修改方案并要求实施、手机已连接。默认每天本地时间 06:00 清零，可在设置关闭；首次升级从下一个 06:00 开始，不立即清旧余额。比例默认 10 换 5，可选 15/20 换 5，首次门槛与后续每分钟奖励同时变化（30/20/15 秒）。比例从下一轮生效，不重算已到账奖励。保持历史、权限、保护及摄像头行为。

## 步骤

- [x] 核对工作区和设备：仅 tasks/README.md 有既有未提交改动，须保留；设备 35c2f60f，0.4.4/versionCode 21，摄像头未运行。
- [x] 实现每轮比例、每日到期结算、设置及动态说明。
- [x] 同步 SPEC、DESIGN、ARCHITECTURE、界面说明及 README。
- [x] 验证奖励门槛、暂停恢复、日期边界、重复处理及迁移。
- [x] 备份设备 APK/账本，覆盖安装并核对余额历史及设置。
- [x] 真机设置/显示验证：三档切换、清零开关、进程重开保留、20 档未开启相机的工作页、首页下一清零时间。
- [ ] 真实 15/20 分钟首次奖励及后续每分钟奖励；自然跨夜清零，前台娱乐中的到期拦截及锁屏/后台恢复。

## 证据与边界

开始时设备 balance_ms=555398、consumed_ms=2804602、work_total_ms=7182644、work_reward_ms=0、enabled=true、规则版本 3。安装前重新取快照作为保留比对基准。不得为了测试清空真实余额或调整手机系统时间。

## 当前停点与下一步

已安装 0.5.0-entertainment-rules/versionCode 22，手机安装包 SHA-256 与构建产物一致：`ac0c41f8ebfb237ded1b4a8830973b7458438d718934cb0a1f32fc328640d9d0`。构建、79 项单元测试、lint（0 错误/9 警告）通过；当前新进程 crash buffer 未见 FATAL EXCEPTION，不代表长期稳定性已验证。

设备最终为 10 换 5、每日清零开启，首次截止点 `1788991200000`，即 2026-09-10 06:00（Asia/Shanghai）。覆盖安装、切换15/20、关闭清零、重开进程、恢复默认及最终核对后，balance_ms=555398（首页向上取整显示9分16秒）、consumed_ms=2804602、work_total_ms=7182644、work_reward_ms=0、work_upgrade_credit_ms=180000、enabled=true 均与安装前一致。未增加或清空测试余额，未修改手机时间，未开启摄像头；原有相机授权使用时长未增加。

本地证据位于 `app/build/entertainment-rules-device/`：before-0.4.4.apk、before/after/final-ledger.xml、before-ui_preferences.xml、selected-15/20.xml、daily-disabled.xml、restarted-20-disabled.xml、installed-guide.png、settings-20.png、work-idle-20.png、final-home.png、verification.json。自动化结果位于 app/build/test-results/testDebugUnitTest，构建日志 entertainment-build.log。界面层级读取曾因不能达到空闲态失败，未把旧 XML 当新界面；后续以当前截图和账本读回交叉核对。

下一步由日常真实使用完成上述未勾选场景，再记录是否存在重置误伤、漏发或跨日拦截问题。系统冻结/强停期间无法执行保护，恢复时先补做过期结算；逻辑模拟不等于自然跨夜或真实在场奖励验收。当前改动保留在工作区，未创建提交。


关联：[摄像头奖励](camera-rewards.md) · [后台保护](background-protection.md) · [产品要求](../../docs/SPEC.md) · [方案](../../docs/DESIGN.md)。
