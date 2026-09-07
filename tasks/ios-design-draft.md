# iPhone 版本设计草案

状态：待 Roy 确认。2026-09-07 设计探索；未编码、未做 iPhone 真机验证。

依据：[产品要求](../docs/SPEC.md)、[已有方案](../docs/DESIGN.md)。本文件用于本轮评审，确认后将长期方案归并至 DESIGN，不建立第二套正式规范。当前进度见 [TASK](../TASK.md)。

## 1. 建议与范围

建议采用 Swift / SwiftUI 原生应用，使用 Apple Screen Time 的个人授权、系统限制页和使用阈值回调。保留“有效工作 10 分钟获得 5 分钟娱乐、仅实际使用扣减、耗尽重新限制”的产品目标。

分阶段推进：先验证 iPhone 上的授权、限制和消费；通过后接入有效工作与摄像头，再接电脑浏览器联动。这是实施顺序提案，不代表 Roy 已授权扩大当前原型范围。

iOS 提供与产品需求匹配的公开接口，但不等于已经证明体验更好、计时足够可靠或应用能够获批分发。Android 同机竞品警告已确认，调查见 [记录](android-permission-research.md)，无需为了本轮方案重复该实验。

## 2. 用户实际体验

| 时刻 | 建议体验 | 边界 |
|---|---|---|
| 首次打开 | 简短解释工作换娱乐，用户主动开始设置 | 未完成授权前不能阻止用户打开现有小红书；引导动画不算真实拦截体验 |
| 开启限制 | 请求屏幕使用时间个人授权，再通过 Apple 选择器选择小红书 | 不要求家庭组或家长代授权；系统弹窗文字不可由我们改写 |
| 零余额打开小红书 | 显示系统托管的限制页，解释需要先获得娱乐时间 | 可配置图标、标题、说明、按钮等；不能当普通任意布局页面设计 |
| 从限制页去工作 | iOS 26.5 及以上使用官方打开本应用入口；旧版提示手动返回本应用 | 这是新发现的版本差异，不使用非公开跳转技巧 |
| 开始有效工作 | 展示工作计时和下一次奖励进度，首次使用摄像头时才申请相机权限 | 首期限制实验用明确标注的测试时间，不伪装已完成工作识别 |
| 获得奖励 | 每满 10 分钟有效工作记录一笔 5 分钟奖励，工作会话可以继续 | 工作中已有余额时是否仍允许手机娱乐，现有 DESIGN 尚未确定，不擅自选择 |
| 使用奖励 | 允许打开原生小红书，离开它或锁屏不应继续扣娱乐时间 | 精确程度取决于系统统计与回调验证；不使用普通倒计时冒充实际使用 |
| 时间耗尽 | 系统回调触发重新限制，并保存已结算状态 | 需验证主应用被划掉、重启等情况下的行为 |
| 授权撤销 | 首页明确显示限制不可用并给恢复入口 | 个人授权可撤销，也不阻止卸载；不设计为无法退出的管控工具 |

系统授权至少涉及屏幕使用时间；完整工作功能另需相机授权。声音/震动与应用内状态先满足提醒需求，不把通知权限作为首期限制生效的前提。不申请麦克风，不保存或上传连续摄像头视频。

## 3. 最小技术结构

建议在当前仓库中新增独立 `ios/` 原生工程，与现有 Android 模块并存，不迁移或重构 Android。

| 组成 | 责任 |
|---|---|
| 主应用 | 授权、目标选择、余额与工作页面、后续摄像头和电脑会话 |
| Device Activity Monitor 扩展 | 处理系统使用阈值和监测区间事件、重新施加限制 |
| Shield Configuration 扩展 | 配置系统限制页的可定制内容 |
| Shield Action 扩展 | 处理限制页按钮，新版系统返回本应用 |
| 共享本地存储 | 目标令牌、奖励记录、消费周期、处理过的事件及异常状态 |

FamilyControls 负责 `.individual` 授权和 `FamilyActivityPicker`；ManagedSettings 负责限制；DeviceActivity 负责目标应用在屏幕最前方的累计使用事件。[S1][S3]

目标选择保存 Apple 的不透明令牌，不假定可以直接读取或硬编码小红书的 bundle ID。主应用和扩展共享的账本必须支持跨进程事务；不能把 Android 单进程偏好存储原样搬过来。系统限制存储使用本应用明确命名的 store，恢复/停止时只处理本应用拥有的规则。

暂不加入报表扩展、账号、云钱包或跨手机余额同步。若首页确实需要系统使用报告，再按真实用途增加报表扩展；不利用报表扩展把受保护的使用数据搬到主应用。[S6]

## 4. 最需要先证明的环节：消费账本

官方支持“累计实际使用达到阈值时通知扩展”，但这不直接提供一个供主应用随时读取的精确剩余秒数。[S3][S6]

第一轮固定额度实验采用以下顺序：

1. 保持限制，持久化一次测试额度与唯一消费周期标识。
2. 注册对应目标的使用事件，显式排除开始前的历史活动；监测区间长度符合系统约束。
3. 注册成功并复核共享状态后才解除限制；失败显示未能开启，不制造有额度却无监测的静默放行。
4. 达到额度阈值时，扩展核验周期标识，执行可重复恢复的重新限制与结算；旧事件、重复事件不得重复扣款。
5. 主应用恢复时核对授权、目标令牌、账本和系统监测配置；不能每次启动都清空监测、重送原额度。

注册方法返回之前扩展就可能收到回调，主应用与扩展必须围绕相同周期串行更新，避免先收到“已耗尽”再被主应用解除限制的竞态。[S5]

监测区间最短 15 分钟，和“奖励允许使用 5 分钟”的阈值是两个概念，不能把 5 分钟奖励直接写成 5 分钟监测区间。[S8] `includesPastActivity` 自 iOS 17.4 可用；建议先以 17.4 及以上为候选下限，最终支持范围等待目标设备信息及验证。[S4]

固定 5 分钟能够耗尽重锁，只证明最小技术闭环；以下问题仍是完整产品的前置条件：

- **分段与中途余额**：使用 2 分钟后离开，之后应仍能消费约 3 分钟；重新打开本应用不能变回 5 分钟。首页不能把未收到最终阈值的 5 分钟显示为精确剩余 5 分钟。
- **继续赚取奖励**：已有未消费完的额度，再赚 5 分钟，必须保留此前实际消费。重新注册事件是否保留统计基准需实测；不能简单把旧额度与新额度相加后从零计时。
- **跨监测区间**：午夜、时区变化、区间结束、重启不能无条件清零余额或重送额度。余额结转要求与系统统计区间不是同一规则。
- **系统回调延迟或异常**：记录提前触发、延迟、未触发；不能用现实时间流逝推断用户一直在刷，不能据此补扣。

完整账本优先研究稳定统计基准与累积阈值；如果需中间阈值记录进度，要单独验证回调数量限制、精度及追加额度时的基准连续性。当前尚未证明这足以满足全部规则，不能在实现前标注为已解决。若最终只能得到粗粒度余额，先展示实测误差与可选体验，请 Roy 决定；不静默改成“放行后连续倒计时”。

iOS 26.4 新增的直接使用数据访问不是中国用户通用解法：Apple 规定客户安装要同时满足 EU 设备所在地及 Apple Account 地区条件；开发签名下可测试不等于用户分发可用。本方案不依赖该能力。[S7]

## 5. 工作、摄像头和电脑的后续接入

消费环节通过后，沿用 SPEC 的有效工作规则：单调时钟累计 Valid Work Time，每个工作会话/奖励节点只入账一次，累计达到 10 分钟发 5 分钟，不自动结束会话。

摄像头采用 AVFoundation + Vision 做端侧可观察状态判断，优先验证人在/离开/机位移动；识别不出时不能宣称用户玩手机或不认真。按既有 iPhone 前台体验设计，退后台、锁屏或摄像头中断暂停有效工作累计，不能默认后台持续拍摄。[S10] 实测手机摆放、亮屏耗电、发热、误暂停和提醒干扰；识别可靠性不足不包装成完整 MVP。

电脑延续既有 Chrome/Edge 域名规则方案：开启联动时先等电脑确认规则生效，再开始有效计时；失联暂停新增有效时间，结束或租约到期恢复。此处只继承已确认的协议行为，尚未证明现有仓库已实现。是否默认启用电脑保护、工作期间手机已有额度是否仍锁定，仍待产品确认。

## 6. 验证顺序、通过条件和环境

| 阶段 | 要证明什么 | 通过后才能开展 |
|---|---|---|
| 0 环境与资格 | 可用 Mac/Xcode、真实 iPhone、开发账号和签名配置；确认主应用及各扩展分发资格申请 | 真机开发；分发获批前不承诺 TestFlight 可安装 |
| 1 授权与零余额 | 完整录下中文授权链，Roy 评估接受程度；从桌面/搜索/外部链接进入所选小红书均受限制；限制页返回可用 | 奖励消费测试 |
| 2 固定额度 | 测试加入 5 分钟，连续用满后重锁；2 分钟使用 + 离开/锁屏 5 分钟 + 再用约 3 分钟后重锁 | 完整账本与余额体验 |
| 3 账本与可靠性 | 部分消费后再赚奖励、重开主应用、划掉主应用、重启、午夜、修改时间/时区、撤销权限、改变选择、旧回调与重复回调；观察同账号其他设备是否影响统计 | 有效工作与摄像头接入 |
| 4 工作体验 | 真实工作发奖励、有效时间残余、离开暂停、恢复、相机中断、耗电发热可接受 | 电脑联动与完整 MVP 验收 |
| 5 完整闭环 | 电脑启用确认/失联/结束恢复，实际工作赚取与娱乐消费连续使用；独立验证分发安装 | 对外产品承诺 |

每次记录真实目标应用使用秒数、系统阈值回调时刻和实际重锁时刻。初步建议把单次 5 分钟消费重锁误差不超过 10 秒作为实验目标，至少重复 5 次，再由 Roy 对实测体验确定验收容差；这是提案，不是 Apple 保证。授权失效时不得显示保护正常；连续使用明显超额仍未限制、重开后额度复原、历史使用导致提前锁定均属未通过。

编译和模拟器检查不能替代上述真机验收。当前电脑为 Windows；是否存在可用 Mac、iPhone/iOS 版本、Apple Developer Program 账号均已询问，待回复。Mac 与 Xcode 必须按目标 SDK 匹配，不在未确认硬件前建议购买。[S11]

Apple 将开发期配置与分发资格区分；TestFlight/App Store 使用 Family Controls 需要申请分发授权，主应用和使用 Screen Time 的每个扩展都要覆盖。实施启动时即检查并准备申请材料，不等完整功能完成后才发现缺资格；本轮不代提交申请。[S9]

待 Roy 确认：第一阶段是先做限制/消费实验，还是先细化整个 MVP 再分阶段实施；候选系统范围待设备信息确定。摄像头与电脑功能只在相应范围被确认后实施。

## 官方依据（2026-09-07 核查）

- S1：[WWDC22：个人授权与 Screen Time 三框架](https://developer.apple.com/videos/play/wwdc2022/110336/)。个人模式 iOS 16 起；授权可撤销，不阻止卸载。
- S2：[ShieldActionResponse.openParentalControlsApp](https://developer.apple.com/documentation/managedsettings/shieldactionresponse/openparentalcontrolsapp)。官方文档元数据标明 iOS 26.5 起；旧 DESIGN §4.3 待确认后修正。
- S3：[DeviceActivityEvent](https://developer.apple.com/documentation/deviceactivity/deviceactivityevent)。活动是目标在屏幕最前方的累计时间。
- S4：[includesPastActivity](https://developer.apple.com/documentation/deviceactivity/deviceactivityevent/includespastactivity)。官方元数据标明 iOS 17.4 起；需区分注册前统计和当前区间。
- S5：[startMonitoring](https://developer.apple.com/documentation/deviceactivity/deviceactivitycenter/startmonitoring(_:during:events:))。同名覆盖、错误和立即回调语义。
- S6：[DeviceActivityReport](https://developer.apple.com/documentation/deviceactivity/deviceactivityreport)。报告扩展的隐私沙箱和禁止向外搬移敏感数据。
- S7：[approvedWithDataAccess](https://developer.apple.com/documentation/familycontrols/authorizationstatus/approvedwithdataaccess)。EU 客户安装限制、开发与分发区别。
- S8：[intervalTooShort](https://developer.apple.com/documentation/deviceactivity/deviceactivitycenter/monitoringerror/intervaltooshort)。监测区间至少 15 分钟。
- S9：[配置 Family Controls](https://developer.apple.com/documentation/xcode/configuring-family-controls)、[请求分发授权](https://developer.apple.com/documentation/familycontrols/requesting-the-family-controls-entitlement)。
- S10：[摄像头后台中断原因](https://developer.apple.com/documentation/avfoundation/avcapturesession/interruptionreason/videodevicenotavailableinbackground)。
- S11：[Xcode 系统要求](https://developer.apple.com/xcode/system-requirements)。

网页正文由 Apple 官方文档及其公开 documentation JSON 核实；资料支持 API 边界，不替代本产品当前版本的实机结果。
