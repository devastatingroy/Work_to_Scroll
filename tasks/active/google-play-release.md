# Google Play 首发（WorkToWatch）流程与合规准备

状态：**当前流程与合规轨道（2026-09-10）**。Google Play 为首发第一顺位；国内小米/华为等商店降为候选路线（见[国内软件商店候选准备](../todo/release-readiness.md)、[小米商店资质与备案候选准备](../todo/xiaomi-publishing-credentials.md)）。本任务只处理平台流程和合规，**尚未**开始账号注册、付款或审核提交；产品代码已由独立的 WorkToWatch 产品实施轨道启动。创建日期：2026-09-10。

入口：[任务索引](../README.md) · [产品要求 SPEC](../../docs/SPEC.md) · [WorkToWatch 产品准备](overseas-target-apps.md)。维护人：接续本任务的 agent；关键决策/验证/阻塞/状态变化时同步更新本文件与索引。

## 背景与方向变化

- 2026-09-10 Roy 明确：首发从小米/国内商店转为**主攻 Google Play**，小米/华为国内商店往后放。
- Google Play 面向的是海外用户，与现有 WorkToScroll 小红书产品并行而不是替代；2026-09-15 Roy 已确认新产品采用独立品牌 **WorkToWatch**，不以可见系列关系绑定两个产品。
- **关键产品变更（Roy 明确）：WorkToWatch 首发只支持整个官方 YouTube Android App。** 当前代码已形成与 WorkToScroll 隔离的 WorkToWatch 构建和 YouTube 目标配置；目标应用事件、界面/素材/文案和真机验证继续走 SPEC/DESIGN 与适配验收，**不在本任务内直接改代码**。现由[WorkToWatch 产品实施](overseas-target-apps.md)作为独立产品轨道接续。

## 目标、范围与授权边界

- 本任务限于 **Google Play 的开发者账号、测试轨道、政策声明、隐私资料、商店资料和审核提交流程**。
- 不含：目标用户/目标 App 决策、YouTube 适配、界面/文案设计、产品可靠性验收、Android 代码改动或 SPEC/DESIGN 产品决策；这些统一由[WorkToWatch 产品准备](overseas-target-apps.md)维护。
- 也不含：$25 开发者账号注册费付款（**付款是红线，须 Roy 本人操作**）、创建/实名账号、正式签名包制作和提交审核。

## 待 Roy 决策

- [ ] 账号主体：个人 Google 账号 vs 组织账号（组织需 D-U-N-S 编号，流程更重）。
- [ ] 目标上架国家/地区与语言（影响商店文案、隐私政策、内容分级）。
- [x] WorkToWatch 独立产品、YouTube 单点首发、与 WorkToScroll 品牌/商品/应用身份/数据隔离的方向已写回 SPEC/DESIGN。
- [ ] WorkToWatch 正式包名与版本策略；已确认必须独立，具体值仍待名称/包名可用性核查（现测试包 `com.roy.worktoscroll.overlaytest` / 仅 debug，不可申报）。

## 步骤与验证边界

- [x] 核实 Google Play Console 当前门槛：新建个人开发者账号须完成至少 12 名测试者连续 14 天加入的 closed test，才可申请生产权限；组织账号需要 D-U-N-S 编号，可能需预留最长 30 天的取得时间。两条路线均需身份、联系方式与付款资料核验，注册费付款仍由 Roy 本人决定和操作。
- [x] 核实当前工程的提交前差距：`targetSdk 36` 满足 2026-08-31 起的新应用要求；但正式包仍是 debug 身份（`com.roy.worktoscroll.overlaytest`、versionCode 27），不能提交。当前没有 AccessibilityService，故无障碍声明不是已知阻塞；实际使用 `PACKAGE_USAGE_STATS`、悬浮窗、相机、通知、开机恢复和 `specialUse` 前台服务，需以最终正式包和实际流程准备审核说明、显著告知与复现视频。
- [x] 核实隐私与商店材料前置：所有发布在 closed/open/production track 的应用均须完成 Data safety 表和可公开访问的隐私政策 URL；政策、店铺资料和实际权限/SDK 数据处理必须一致。
- [ ] 梳理 Google Play 特有合规：Data Safety（数据安全）表、前台服务/摄像头用途声明与演示视频、内容分级问卷、权限逐项说明、targetSdk（现 36，旧记录已满足，需复核）。
- [ ] 以已确定的产品行为为输入，完成 Data safety、权限用途、前台服务用途、内容分级和商店审核复现资料；不在本任务自行定义产品行为。
- [ ] WorkToWatch 隐私政策（英文/目标语言）、对外联系方式和商店资料；产品内首次同意/拒绝流程由产品轨道定义并验收。
- [ ] 正式包名与签名证书、可追溯版本策略，摆脱 `.overlaytest`/debug 身份。
- [ ] 可靠性验收（摄像头中断恢复、后台保护、发热）仍是公开发布前置，与国内版共用证据，见关联任务。

## 已核流程与主要堵点（2026-09-10）

| 优先级 | 堵点 | 当前事实 | 推进条件 |
| --- | --- | --- | --- |
| P0 | 账号路线决定上线日程 | 新个人账号的 production access 有“12 人连续 14 天 closed test”硬门槛；组织账号需 D-U-N-S 和组织资料，D-U-N-S 申请可能最长 30 天。 | Roy 选择个人或组织主体；个人路线先准备合格测试者，组织路线先核验 D-U-N-S 与公开联系方式。 |
| P1 | 审核资料必须反映最终产品 | App 的使用情况、悬浮窗、相机、通知和 special-use 前台服务的用途、数据处理和停止方式仍会随产品轨道的最终定义变化。 | 产品轨道锁定功能与授权后，形成逐项显著告知、隐私政策、Data safety 答案与可复现演示视频。 |
| P1 | 发布身份与可安装包 | 当前 package/applicationId 含 `.overlaytest`，只生成 debug APK。 | 产品轨道完成目标 App 适配后，锁定正式 applicationId、release signing、AAB、升级规则和商店素材。 |

## 建议推进方案（待 Roy 确认产品与账号选择后执行）

1. **流程与合规轨道先准备不依赖产品细节的内容**：选择账号路线，建立联系人与资料清单，准备测试者招募方案、商店资料模板和政策问卷清单；不写未经确认的产品承诺。
2. **等待产品轨道提供冻结输入**：目标市场/语言、首发目标 App、正式 package、权限与数据流、用户同意路径、验收范围。
3. **以冻结输入完成审核包**：逐项完成隐私政策、Data safety、内容分级、权限/前台服务说明、演示视频和内部审核复现说明。
4. **以测试轨道倒排生产**：合格 AAB 后先 internal test；个人账号随即启动 12 人/14 天 closed test；满足条件后申请 production access，再小比例发布并观察政策状态与崩溃指标。产品体验与可靠性证据由产品轨道独立判定。

### 官方依据（2026-09-10 复核）

- [个人新账号测试要求](https://support.google.com/googleplay/android-developer/answer/14151465) · [开发者账号信息与主体要求](https://support.google.com/googleplay/android-developer/answer/13628312) · [账号类型与 D-U-N-S](https://support.google.com/googleplay/android-developer/answer/13634885)
- [目标 API 政策](https://support.google.com/googleplay/android-developer/answer/11926878) · [用户数据与隐私政策](https://support.google.com/googleplay/android-developer/answer/10144311) · [Data safety 表](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Android 前台服务类型](https://developer.android.com/about/versions/14/changes/fgs-types-required)

## 当前停点与下一步

已完成流程和当前工程差距核查，WorkToWatch 的独立产品方向、永久应用 ID 与首轮双产品构建已经落地；尚未启动任何账号注册、付款或商店提交。下一步仍等待 Roy 决定账号主体和首发国家/语言；同时由[WorkToWatch 产品实施](overseas-target-apps.md)完成正式名称可用性、产品文案和真机适配验收。产品可靠性未通过时，本任务不得把流程材料齐备表述为“可公开发布”。

## 关联资料

- 国内候选线：[release-readiness.md](../todo/release-readiness.md) · [xiaomi-publishing-credentials.md](../todo/xiaomi-publishing-credentials.md) · [xiaomi-browser-handoff.md](../todo/xiaomi-browser-handoff.md)
- 产品轨道：[WorkToWatch 产品准备](overseas-target-apps.md)
- 产品要求 [SPEC](../../docs/SPEC.md) · 方案 [DESIGN](../../docs/DESIGN.md)
