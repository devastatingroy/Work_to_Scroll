# Android 敏感授权替代调查

> 归档状态：本次资料与同机调查已完成；候选替代路线未获实施授权，不代表跨机型验证。历史事实按 2026-09-07 的记录理解。 入口：[任务索引](../README.md)。

日期：2026-09-07。类别：设计探索。状态：资料与代码调查完成，建议待 Roy 决定；不是新产品要求或已验证替代实现。

## 结论与适用条件

在“普通个人手机、普通第三方安装、不要求 root/调试/设备管理、继续使用已安装的小红书、按实际前台使用扣余额、零余额自动阻止继续使用”这些条件同时成立时，本轮没有找到可去掉敏感授权且等价实现的受支持 Android 路线。这是官方接口与权限边界支持的工程判断，不是对所有厂商私有能力的绝对不存在证明。

如果只要求保留“先工作再娱乐”的行为循环，可以通过自愿计时、受控网页等降低跨应用权限需求，但会改变已确定体验。VPN、桌面和工作资料也有各自代价，不能直接当作现有实现的免权限替换。

## 本轮重新核实的项目事实

- 当前主目录实际采用使用情况访问 + 普通悬浮窗 + 前台服务，已经没有无障碍组件。
- `ProtectionService.java:168` 使用 `UsageStatsManager.queryEvents` 得到使用事件；第 238 行使用 `TYPE_APPLICATION_OVERLAY` 显示阻止页。
- Manifest 声明使用情况、悬浮窗、前台服务及通知权限；当前没有 INTERNET、相机或麦克风权限。未来摄像头及同步阶段不能套用本原型的无联网声明。
- 已直接查看用户原图 [使用情况警告](hyperos-usage-access-warning.png)。它明确将使用情况访问称为高度敏感权限，警告个人隐私及财产安全，并要求确认承担后果。当前问题不只是无障碍。
- 原图只能证明此次 Xiaomi/HyperOS 警告；不能据此推断其他 Android 手机都显示相同措辞，或确认它与商店安装/侧载的因果关系。
- 没有操作手机、安装竞品、改授权或运行功能测试；此前原版和新版真机结果仍以 TASK 记录的边界为准。

## 核心机制为什么仍需要授权

“观察其他应用”与“覆盖/暂停其他应用”是两个独立能力。

1. Android `queryEvents` 旧接口及 API 35 新查询接口都明确要求 `PACKAGE_USAGE_STATS`；只查自身的 `queryEventsForSelf` 才免此授权，但只能知道 WorkToEarn 自己，不能知道小红书。按包过滤查询不等于系统授予一个更窄、更温和的新权限。[官方接口](https://developer.android.com/reference/android/app/usage/UsageStatsManager)
2. 普通跨应用浮层要求 `SYSTEM_ALERT_WINDOW`；仅移除用量权限后，浮层本身不能告诉我们底下是谁。[官方窗口文档](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY)
3. 旧 `getRunningTasks` 对普通第三方应用只返回受限子集，不能替代跨应用前台监测。[官方接口](https://developer.android.com/reference/android/app/ActivityManager#getRunningTasks(int))
4. 系统数字健康拥有普通应用不具备的资格：本轮读取的 AOSP 主线中 `SUSPEND_APPS` 为 signature/role/verifier，`OBSERVE_APP_USAGE` 为 signature/privileged/role；`SYSTEM_WELLBEING` 角色为 systemOnly、static、不可见。该源码解释系统能力边界，不证明每台 OEM 系统与主线实现一致。[权限定义](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/AndroidManifest.xml)、[角色定义](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/main/PermissionController/res/xml/roles.xml)

## 替代路线逐项判断

| 路线 | 能得到什么 | 相对当前核心体验的缺口或代价 | 判断 |
|---|---|---|---|
| 只用无障碍，去掉使用情况访问 | 可以探索用窗口事件识别、计时并覆盖 | 重新引入用户已经见过的另一项危险警告；实际计时须重做验证 | 换了敏感授权，未解决信任目标 |
| 保留悬浮窗，去掉使用情况访问 | 仍能画遮挡页 | 无法据此可靠知道小红书何时开始/结束使用；全局遮挡还会影响其他 App | 不等价 |
| 本地 VPN / DNS 断网 | 按应用或域名限制联网 | 缓存内容仍可看；流量不等于前台用时；需要 VPN 信任确认，同一用户/资料只能有一个活动 VPN | 只能作为改规则候选 |
| 自定义桌面 / 快捷入口 | 在自己控制的启动入口检查余额 | 通知、最近任务、外部链接等普通入口不受其单独控制；离开桌面后缺乏完整计时与重新限制能力 | 入口干预，不等价于自动限制 |
| 系统数字健康 / 专注模式 / 应用锁 | 系统自己能实施限制 | 未找到普通 App 可用的通用接口，能把我们的奖励钱包自动写入其限额；手动设置不构成自动闭环 | 厂商合作或人工方案才可继续探索 |
| 普通设备管理员 | 部分设备管理能力 | 不因启用普通管理员就获得 setPackagesSuspended 的 owner 资格 | 不是轻量替代 |
| Device Owner / Profile Owner / 工作资料 | 有资格时可系统暂停受管理应用 | 引入设备/资料管理设置；个人设备工作资料通常管理独立空间内的应用，不直接等于控制已有个人小红书；按实际用时扣减仍需验证 | 技术方向存在，未证明体验更轻或免敏感授权 |
| Shizuku / ADB / root | 借更高权限访问系统能力 | 调试/提权、额外工具及维护成本；Shizuku 官方说明无 root 启动在重启后需再操作 | 不建议作为面向普通用户的减门槛方案 |
| 在本 App 内提供娱乐网页 | 自己页面的计时和遮挡可由自身完成 | 用户要改用网页；小红书网页兼容、登录、跳转未验证；外部原生 App 不受控制 | 确实能减权限，但改变消费场所 |
| 把原生小红书装进容器或嵌入本 App | 理论上尝试控制托管环境 | Android 标准跨应用嵌入需要目标 App 允许；不等于任意应用可以无条件托管。非标准虚拟化另有兼容与数据边界，未验证 | 不能当作现成低成本方案 |
| 自愿计时 / 提醒 / 屏幕固定 | 工作奖励循环、主动防分心 | 不保证娱乐实际用时、任意正常入口的限制及耗尽自动重锁；屏幕固定可以由用户退出 | 可以作为另一种产品体验，需确认 |
| 系统预装或 OEM 合作 | 可能获得专用/系统能力 | 需要合作资格、厂商适配；普通上架不等于获得系统特权 | 长期条件路线，非当前可执行替代 |

上表中缓存仍可浏览、流量与前台时间不等价、桌面不能覆盖其他启动路径等，是由机制推导的结论，不冒充本轮小红书真机实验。

路线依据：

- [VPN 官方指南](https://developer.android.com/develop/connectivity/vpn)：首用连接确认、按应用配置、单用户/资料的活动 VPN 限制。照片原图状态栏出现 VPN 标记，但不能据旧图断言当前正在运行哪项 VPN；共存冲突应纳入真实需求核对。
- [设备暂停接口](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setPackagesSuspended(android.content.ComponentName,%20java.lang.String[],%20boolean))、[工作资料边界](https://developer.android.com/work/managed-profiles)：owner/受委托资格与资料隔离。不能笼统声称所有工作资料都需要恢复出厂，也不能承诺在现有个人安装上无缝生效。
- [Shizuku 官方用户手册](https://shizuku.rikka.app/guide/setup/)：无线调试启动与重启后的维护要求。
- [WebView](https://developer.android.com/develop/ui/views/layout/webapps/webview)、[跨应用嵌入](https://developer.android.com/develop/ui/views/layout/activity-embedding)：受控网页和原生应用托管不是同一能力。
- [屏幕固定与 Lock Task](https://developer.android.google.cn/work/dpc/dedicated-devices/lock-task-mode?hl=en)：普通屏幕固定与受管理设备的锁定模式不同。
- [AppBlock 官方权限说明](https://appblock.app/help/android/personal-data/)也把使用情况访问列为识别当前应用的能力；这是竞品公开机制说明，不是本轮实测竞品或证明所有竞品都如此。

## “换发行方式就没有红色警告”还不能成立

用户原图显示的是使用情况权限的风险告知，不能直接解释成我们的 APK 被判定为恶意应用。小米历史官方白皮书已介绍对此类权限使用全屏风险提醒，但历史资料不等于当前 HyperOS 3 的完整触发规则。[MIUI 12 安全与隐私白皮书](https://trust.mi.com/pdf/MIUI_Security_and_Privacy_White_Paper_CN_April_2020.pdf)

安装来源导致的 Android “受限设置”是另一个机制；Google 官方举例说明侧载应用的无障碍可能需要额外开放受限设置。[Google 说明](https://blog.google/products-and-platforms/platforms/android/how-android-helps-you-stay-safe-from-mobile-fraud-apps/)

本轮未找到可核实的当前官方保证：发布 release 包、签名、备案或上架小米/Google Play 就会取消这张使用情况警告。正式发行可能增加来源可信度，但不能承诺改变系统权限文案，更不能以它作为选型前提。无联网或只在本地结算可以缩小真实数据风险，不会自动缩小系统授予的能力范围。

## 建议与最小验证顺序（未批准实施）

1. 若完整自动限制不可妥协：保留当前代码作为基线，先验证真实用户对权限的接受程度；不再为了替换权限而立即重写。当前路线的跨机型可靠性仍未验收。
2. 先做“同一 HyperOS 上官方商店竞品的首次用量授权”和“其他目标机型上相同用途授权”的对照，记录系统/应用版本、安装渠道、完整文案和步骤。不同应用的渠道对照只能提供线索；因果确认仍需相同 App 的可比安装条件或厂商答复。
3. 用户体验候选（经 Roy 追问修正）：没有必要授权，刚安装时无法拦截用户直接打开小红书，也无法兑现赠送五分钟后自动重锁；提前展示工作计时与奖励数字，只能体验部分功能，不等于先体验完整核心价值。摄像头验证还需要相机权限。延后授权只能作为了解产品的流程候选，不能当作解决授权劝退的已成立方案；未经授权时必须清楚显示尚未保护，且不能改变 SPEC 后直接实施。
4. 小规模观察：邀请 5—8 名目标用户，先看实际用途再完成真实授权；记录授权到达/完成数、拒绝理由、首次完整循环完成数，并观察下一次是否继续用。该样本用于发现问题，不用于宣称统计显著提升。若多数人理解用途后仍拒绝，停止把强制自动限制当作所有用户必经入口的假设，重新讨论产品边界。
5. 若“不出现此类敏感授权”成为硬要求：候选只能是缩小到自有网页/自愿循环，或争取系统合作等条件变化；需要 Roy 决定保留哪些体验，再改正式文档。

剩余未知：HyperOS 3 当前警告豁免/渠道规则、其他目标机型实际提示、竞品完整流程、Profile Owner 方案的实际计时与个人应用迁移成本、替代方案用户接受度。本轮没有将这些未知项升级为结论。

## 同机及跨机型对照进展（2026-09-07 晚间）

- Roy 已明确优先验证正规商店竞品与其他机型的警告差异。手机本轮重新读取为 Xiaomi 14 Pro / Android 16 / HyperOS OS3.0.307.0.WNBCNXM。
- 从手机自带小米商店搜索并安装“不做手机控”，设备确认包名 `com.pl.getaway.getaway`、版本 `6.0.9.7`、versionCode `600000907`、targetSdk `33`、安装来源 `com.xiaomi.market`、安装时间 21:18:44。[商店公开页面](https://app.mi.com/details?id=com.pl.getaway.getaway)显示的版本为 6.0.9.6，以实际安装包为本次证据。未从第三方下载站或 ADB 安装竞品。
- Roy 明确更正早先亲手确认的是 WorkToEarn 权限，因此早先操作不作为竞品是否弹警告的证据；读取到 allow 也不等于看到了授权过程。
- Roy 发来的 21:32 长截图是“不做手机控”的其他权限列表，包含电话、剪贴板、悬浮窗等，不是独立的“应用使用情况数据”页面。使用标准 `android.settings.USAGE_ACCESS_SETTINGS` 可以进入独立列表；已在列表中看到名称明确的“不做手机控”。[列表证据](hyperos-competitor-usage-list.png)
- 21:34 左右从列表选中该行后，Roy 手动关闭、再开启“不做手机控”的使用情况开关，捕捉到“使用情况访问权限”的红色危险页，包含财产安全警告和确认倒计时。Roy 随后明确回复“是，这次确定是不做手机控”。因此该画面归属已经用户确认，可作为本机正规商店竞品的直接对照证据；Agent 未点击警告确认。[竞品警告画面](hyperos-competitor-usage-warning.png)
- **本机对照结论**：小米商店安装的“不做手机控”6.0.9.7，与 WorkToEarn 测试版一样出现使用情况访问的“危险”、隐私/财产安全、自行承担后果等核心警告。因此不能把“正式商店安装就会取消这张警告”作为产品假设；这不证明所有商店 App/系统版本都无例外。该次属于撤销后重新授权，不冒充首次安装后的完整授权录像；未测试竞品完整拦截功能。

其他机型的资料证据：

- [AdGuard 官方 One UI 7 教程](https://kb.adguard.info/kb/adguard-for-android/solving-problems/extending-restricted-settings/)给出三星“Usage data access”截图，显示能力说明和应用开关；已查看[原图](https://cdn.adtidy.org/content/kb/ad_blocker/android/solving_problems/problems-caused-by-extending-restricted-settings/usage_data.jpg)。该画面没有小米式红色危险页，但静态图不能证明整个授权过程没有后续警告；AdGuard 也不是本轮安装的同类限制工具，且未验证其截图安装来源。
- [Niagara Launcher 官方教程](https://help.niagaralauncher.app/article/181-usage-breaker)于 2026-04-02 更新，分别列出 Pixel/原生 Android 和三星的用量授权路径。只能支持入口存在，不证明点击后无警告；其产品是提醒方案，不能混同自动拦截。
- [vivo 官方说明](https://www.vivo.com.cn/service/questions/all?categoryId=170&questionId=1836)明确描述 Android 15 对非 vivo 商店安装应用的敏感权限访问限制，包括使用情况访问、悬浮窗等。支持“安装渠道会影响一类额外受限提示”，不证明小米当前红色页受同一豁免条件控制，也不证明 vivo 商店应用没有任何权限风险告知。
- [FocuzPass 官方说明](https://focuzpass.com/accessibility.html)提到 Xiaomi/POCO/Redmi 的危险提示，但该页讨论无障碍，不能冒充使用情况权限的同项对照。

当前没有三星、Pixel、vivo 等其他真机；跨机型结论限于以上官方资料及其明确边界。研究记录不改变 SPEC/DESIGN，不将延后授权升级为已确定方案。
