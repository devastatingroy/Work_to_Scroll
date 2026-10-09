# iPhone 核心原型开发准备

状态：暂缓；等待恢复该方向与开发条件。迁移日期：2026-09-08。

入口：[任务索引](../README.md)。维护人：接续本任务的 agent；关键步骤、阻塞、验证或完成时更新本文件，状态迁移同步索引。

2026-09-08 已切回 Android，本任务不代表当前优先级。先前“新会话默认 iPhone”的历史指令已被取代。恢复后先核对实际 Mac、Xcode、iPhone、账号/签名与首期范围，不沿用旧版本表作为当前事实。

依据：[iPhone 设计草案](../../docs/design/ios-design-draft.md)。该草案提供方案，本文件维护条件核查与停点。没有新增 iOS 工程或真机结果。

## 已有记录与证据

## 画中画核心能力复核（2026-09-16，未通过可行性判定）

- 触发原因：WorkToWatch 的 Android 真机发现 YouTube 进入画中画后不再扣减额度；额度耗尽时虽出现阻止界面，画中画窗口仍可由用户重新播放。该问题使“实际观看等额扣减”和“归零后不能继续观看”两项核心要求都未获证明。
- Apple 的 [`DeviceActivityEvent`](https://developer.apple.com/documentation/deviceactivity/deviceactivityevent) 将计量定义为所选 App 或网站处于前台屏幕的累计时间。官方资料没有说明第三方 YouTube 画中画是否仍计入；由于画中画常在另一 App 位于前台时继续显示，不能据此假定会等额扣减，必须用真机阈值实验核验。
- [`ManagedSettings.ShieldSettings`](https://developer.apple.com/documentation/managedsettings/shieldsettings) 可以对用户选择的 App 设置系统 Shield，强于 Android 普通使用时长读取；但 Apple 官方资料同样没有保证它会关闭或暂停已存在的第三方画中画窗口，或阻止用户在该窗口再次点播放。因此不能把 Shield 当作第二项要求已经成立的证据。
- 结论边界：iOS 目前只能列为“有更强系统限制候选、但画中画核心能力未证实”的路线，不能作为 WorkToWatch 已可行的替代平台，也不据此修改 SPEC、开始编码或对外承诺。
- 若恢复本任务，首个 Spike 只验证这两项：① YouTube 前台 60 秒后进入画中画并在另一 App 前台停留 60 秒，阈值是否如实触发；② 阈值在画中画期间触发后，Shield 是否使画中画视频/音频停止且无法通过播放键恢复。任一项失败，即停止将 iOS 作为此核心承诺的产品路线。
- 前置条件仍不变：需先实际启用 Mac mini、连接 iPhone、完成 Xcode 真机调试，并配置 Family Controls 能力；其分发权限还需 Apple 审核，见 [Requesting the Family Controls entitlement](https://developer.apple.com/documentation/familycontrols/requesting-the-family-controls-entitlement)。

## 暂停任务：iPhone 核心原型开发准备

## 本次接续：开发条件核查（2026-09-07）

- [x] 已按 Roy 本轮要求先检查条件；当前属于实施前核查，尚未进入编码。已读取 iPhone 草案，并核对项目规范、现有设计及仓库文件。
- [x] 当前会话运行于 Windows；仓库无 iOS 工程，当前 PATH 未找到 xcodebuild 或 swift。此结果只说明本机现状，不能据此判断 Roy 是否另有可用 Mac。
- [x] 重新核对 Apple 官方 [Xcode 系统要求](https://developer.apple.com/xcode/system-requirements)、[Family Controls 开发配置](https://developer.apple.com/documentation/xcode/configuring-family-controls)和[分发授权](https://developer.apple.com/documentation/familycontrols/requesting-the-family-controls-entitlement)：需要匹配的 Mac/macOS/Xcode 与目标 iPhone；Family Controls 开发能力通过 Apple Developer Program 配置，主应用和相关扩展均需有效签名。开发期配置与分发审核是两道不同条件，TestFlight/App Store 的分发授权未获批不等于无法先做开发签名真机调试。
- [x] Roy 最新补充：已有一台刚购买、尚未拆封的 Mac mini，之前“没有 Mac”的信息已更正；具体芯片、内存、存储及 macOS 尚待确认。测试手机为 iPhone 14 Pro / iOS 26.5（用户提供，尚未连接核验）；没有付费 Apple Developer Program 会员。
- [x] Roy 已确认先安装到自己的 iPhone 调试，并采用首期“授权 → 选择小红书 → 零余额限制 → 加入测试额度 → 实际使用扣减 → 耗尽重锁”；通过后再加入摄像头、电脑联动。确认实施顺序不等于确认草案全部技术细节、误差指标或兼容范围。
- [ ] 优先启用现有 Mac mini：连接显示器及键鼠，完成首次设置，核对芯片/macOS/存储，安装匹配 iOS 26.5 的稳定版 Xcode 与 iOS 平台组件，再连接 iPhone 并配置开发调试。Apple 当前系统表中 Xcode 26.6 搭载 iOS 26.5 SDK，支持 macOS Tahoe 26.2 至 26.x；以实际安装时核查为准。
- [ ] 配置 Apple Developer Program 会员及 Family Controls 开发签名；尚未授权购买硬件、租用服务或付费注册。暂不以 TestFlight 为首期安装前提。
- 当前停点：首期范围已确认，已有待开机配置的 Mac mini；尚未实际连接 Mac、编译或安装 iOS App。下一步等待机器型号及系统信息，指导环境安装；环境验收以 Xcode 识别 iPhone、简单 App 编译安装启动，再核验本产品 Family Controls 主应用和扩展签名为准。

## 上一会话保存停点（历史；以本次接续为准）

- Roy 要求保存相关文件与项目进度，将另开会话继续苹果版开发；本会话到此暂停。保存要求不等于已经确认草案中的所有范围与方案取舍。
- 接续时先读本节和 [iPhone 设计草案](../../docs/design/ios-design-draft.md)，再按需对照 [SPEC](../../docs/SPEC.md) 和 [DESIGN](../../docs/DESIGN.md)。iPhone 仍处于设计阶段，没有新增 iOS 工程、编译结果、安装包或真机验收。
- 下一步先确认可用 iPhone 型号/iOS、Mac、Apple Developer Program 账号及首期实施范围；上一会话的异步问题尚未收到答案，新会话不能假定已具备环境或等待旧问题自动回传。
- 推荐首期为“授权 → 选择小红书 → 零余额限制 → 加入测试额度 → 实际使用扣减 → 耗尽重锁”，范围确认后再实施。优先证明分段消费与追加奖励不会重送额度；摄像头和电脑联动的后续顺序仍是提案。
- 正式 SPEC/DESIGN/ARCHITECTURE 本轮未修改；草案里已标出旧 DESIGN 中需要按最新 Apple 文档修正的返回入口，以及尚未解决的余额与统计边界。不要把旧技术判断或新候选方案直接视为已验证事实。
- Android 授权调查、竞品截图和既有原型停点均已保存在本项目，下方保留恢复现场；新会话默认接续 iPhone，不重复 Android 授权实验或自动开始 Android 五分钟测试。

## 当前任务：iPhone 版本方案（2026-09-07）

- [x] Roy 接受当前 Android 授权门槛暂时难以避免，明确要求下一任务转向苹果版本，先设计方案。
- [x] 对照现有 SPEC/DESIGN，核查 Apple 官方个人授权、系统限制页、实际使用阈值、数据访问边界及开发/分发资格。
- [x] 形成 [iPhone 设计草案](../../docs/design/ios-design-draft.md)，仅为待确认提案，未开始编码或修改正式产品规则。
- [x] Roy 已确认第一阶段先验证手机限制与消费，之后再加入摄像头和电脑联动。
- [x] 已收到设备及账号信息，见本次接续；信息确认不代表环境已具备。
- [x] 已将确认的首期范围同步 DESIGN；草案中的其他取舍仍待验证或确认。
- [ ] 环境与会员具备后进入实施；原生拦截、消费结算和分发均未做 iPhone 真机验证。
- 关键新证据：iOS 26.5 起存在官方 `openParentalControlsApp`；旧 DESIGN §4.3 的返回方式需按版本修正。普通授权下的使用报告不能直接作为主应用实时账本；iOS 26.4 新增直接数据访问在客户安装场景受 EU 条件限制，不能作为中国用户通用依赖。完整余额结算仍需技术验证，不承诺秒级精确。
- Android 调查和实现恢复现场保留如下；本轮不操作 Android 手机、不启动待完成的五分钟测试。
