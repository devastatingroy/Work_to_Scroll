# 摄像头计时与每轮奖励验收

状态：实现；等待真机验收。迁移日期：2026-09-08。

入口：[任务索引](../README.md)。维护人：接续本任务的 agent；关键步骤、阻塞、验证或完成时更新本文件，状态迁移同步索引。

恢复时先核对实际安装版本与账本；当前已安装 0.4.1 前置人体观察和声音版，余额及历史保留已核对，摄像头接入详见[侧后方任务](side-rear-presence-exploration.md)。下方 0.3.1 记录为旧阶段迁移证据；真实时长闭环仍未完成。

2026-09-08 接续：主 App 实现已提交 aade20a，Roy 试用后反馈“测试感觉不错”；这是当前机位的初步体验反馈，不代表下列奖励、中断、消费与异常场景逐项通过。

## 剩余验收

- [ ] 当前规则下真实工作满 10 分钟得 5 分钟、后续每分钟奖励、短暂停续接及 3 分钟重置，确认不重复到账。
- [ ] 物理离开、低头、多人及误判反馈；锁屏、返回、旋转和中断后的手动恢复。
- [ ] 电话全屏/悬浮接听、听筒熄屏；微信文字/快捷回复、语音全屏/浮窗及视频抢占相机，分别记录焦点、相机、有效时间与恢复。需 Roy 配合，不自行拨号或发消息。
- [ ] 联合[后台保护验收](background-protection.md)验证真实奖励 → 连续/分段消费 → 耗尽再拦截。

[发热测试](../todo/camera-thermal-test.md)单列待执行；[猫总 UI](cat-ui.md)负责引导、设置与反馈画面；[早期摄像头证据](../completed/android-camera-history.md)只作历史来源。

## 已有记录与证据

## 当前实施：每轮奖励门槛与中断重置（2026-09-08）

- [x] Roy 确认：每轮先满 10 分钟得 5 分钟，其后每分钟得 30 秒；连续离开/暂停达到 3 分钟重置本轮进度及分钟奖励资格，短暂停续接。保留已到账奖励与历史工作；暂停显示剩余重置时间。
- [x] 分离历史累计与本轮奖励，持久化单调中断起点/检查点/启动编号；短暂停续接，长暂停及重启清除本轮资格。规则 v3 升级清除旧轮进度，保留已有余额、历史有效工作、已到账奖励和历史补齐；不补发、不追回。
- [x] 工作页改为本轮有效工作/本轮已获得；暂停显示向上取整的剩余时间和重置说明；当前规则首次说明更新至 cat_intro_seen_v2。SPEC、DESIGN、独立设计稿、README、ARCHITECTURE 已同步并检查引用。
- [x] 0.3.1-round-rules / versionCode 11 构建成功，55 项测试全部通过（含23项 WorkProgress/时钟组合测试）；lint 0 错误、7 警告；签名验证通过，权限清单与猫总版完全相同，保护及消费核心文件未改。覆盖 599999/600000 ms 首门槛、179999/180000 ms 暂停边界、长暂停后的资格重置、重复恢复、重启、延迟 tick、旧规则迁移和余额不倒扣。
- [x] APK：app/build/outputs/apk/debug/app-debug.apk；SHA-256：F2EB7212565D9FC557F037544D7ED987EF20DE7F4101E6BBA912186A9D505EE7。保留先前猫总 APK 于 app/build/baseline-0.3.0-before-round-rules.apk。
- [x] 手机35c2f60f重连，安装前读回0.3.0/versionCode10，CAMERA无running；已备份设备APK至app/build/device-baseline-0.3.0-before-round-rules.apk、账本至app/build/ledger-before-round-rules.xml。安装前balance_ms=270000、consumed_ms=1080000、work_total_ms=2742217、work_session_ms=580209、work_reward_ms=270000、work_upgrade_credit_ms=180000、enabled=true、规则版本2。
- [x] 0.3.1普通覆盖安装Success，首页冷启动Status:ok（518ms）；未添加/清空余额、未自动开始摄像头、未截图工作页。
- [x] 再次重连后读回versionCode11/0.3.1-round-rules、规则版本3、本轮work_session_ms=0；balance_ms仍270000、consumed_ms仍1080000、work_total_ms仍2742217、work_upgrade_credit_ms仍180000、enabled仍true。安装后账本保留于app/build/ledger-after-round-rules.xml；相机无running。
- [x] 仅在MainActivity且相机未运行的前后双重校验下检查首页截图：可用娱乐4分30秒、已工作45分钟、保护运行中、两个入口和设置均完整可见，无裁切。截图app/build/round-rules-home-device.png，不含相机预览。
- [ ] 首次说明/设置检查未完成：uiautomator未生成节点文件，随后手机自动锁屏；没有截图锁屏或工作页。真实10分钟发奖、3分钟重置倒计时及误判体验待用户正常工作时验收。
- 0.3.1 当时停点（历史）：安装、规则迁移、余额/历史保留和非相机首页显示已核实。当前版本及接续状态见本文件开头；实际10分钟奖励与3分钟重置仍待真人使用验证，既有后台耐久问题不因升级自动算通过。
