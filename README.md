# WorkToEarn

当前交付范围：Android Gate 1 自用功能原型。用人工测试余额验证小红书的“零余额拦截 → 前台使用扣减 → 耗尽再拦截”。摄像头、工作奖励、PC 扩展和同步尚未接入。

当前版本为 `0.1.2-gate1`（versionCode 3），已完成 Xiaomi 14 Pro 上的核心技术验证，待用户体验验收；不是完整 MVP 或长期可靠性承诺。具体证据、APK 指纹及未验证项目见 [TASK.md](TASK.md)。

应用显示名称已按用户确认改为 WorkToEarn；原项目目录及应用 ID `com.roy.worktoscroll` 保持不变，以便覆盖更新并保留已有余额、授权和厂商配置。

## 项目入口

- [TASK.md](TASK.md)：当前进度、真机证据、待完成测试与停点。
- [docs/SPEC.md](docs/SPEC.md)：完整产品的需求边界。
- [docs/DESIGN.md](docs/DESIGN.md)：当前 Android 验证范围及保留的 iOS、PC 候选方案，不是已实现功能清单。
- [ARCHITECTURE.md](ARCHITECTURE.md)：当前 Android 原型的实际结构、计时与数据边界。

## 构建

需要 JDK 17、Android SDK API 36、Build Tools 36.1.0。使用 Android Studio 打开项目根目录，或配置本机 `local.properties` 的 `sdk.dir` 后运行：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

工具版本由工程固定为 Gradle 8.11.1 / Android Gradle Plugin 8.10.1。APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

如果终端已设置 `HTTPS_PROXY`，但 Gradle 下载失败，Java 不会自动沿用该环境变量；可在当前命令传入代理参数，不要把含账号密码的代理写入工程：

```powershell
$proxyUri = [Uri]$env:HTTPS_PROXY
$networkArgs = @("-Dhttps.proxyHost=$($proxyUri.Host)", "-Dhttps.proxyPort=$($proxyUri.Port)",
    "-Dhttp.proxyHost=$($proxyUri.Host)", "-Dhttp.proxyPort=$($proxyUri.Port)")
.\gradlew.bat @networkArgs :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

## 手机测试

1. 安装测试 APK，打开 WorkToEarn。
2. 在应用内分别进入“使用情况访问”和“无障碍保护服务”，由使用者在系统页面授权。
3. 本次 Xiaomi 14 Pro 测试需将本应用的省电策略设为“无限制”；此前曾出现服务已绑定但进程被冻结的情况。这是本机的验证条件，不保证其他机型或长期运行同样有效。
4. 回到应用，确认两项均已授权且服务已连接，再点“开启保护”。
5. 零余额时打开小红书，检查是否显示阻止界面，并确认可以回到桌面或本应用。
6. 点击“加入 5 分钟（测试）”，测试连续使用、退出后再进入、锁屏后恢复。该按钮每次增加 5 分钟，不是把余额设为 5 分钟；需要重新实验时先通过确认框清空测试余额。
7. 验证余额耗尽后重新出现阻止界面。测试结束点“停止保护”，避免零余额持续影响正常使用。

系统的侧载安装或受限设置提示需要在手机上确认；本应用不会代替使用者授予系统权限。自用测试只支持标准版小红书 `com.xingin.xhs`，分身、多窗口和画中画尚未验证。

Xiaomi 14 Pro 真机复测显示，强行停止本应用再冷启动后，余额完整保留，但无障碍服务会被移出启用列表且不会自动恢复。本次为避免留下无保护状态，测试后通过 ADB 恢复了测试前配置；这不等于用户已经体验并接受真实手动恢复流程。实际遇到此情况时，通过首页“2 · 授权无障碍保护服务”进入系统设置，重新开启 WorkToEarn 服务，再检查首页状态。普通退出、系统回收和强行停止应分别验证。

## 真机测试辅助

`scripts/gate1-device-check.ps1` 在已经授权、开启保护且通过界面加入恰好 5 分钟测试余额后，执行约 6 分钟的分段消费观察：前台 2 分钟、桌面停留、继续使用到耗尽。脚本仅打开小红书与桌面，读取本应用账本，不滚动或操作小红书内容。启动前必须解锁，期间避免同时手动操作。测试临时把自动锁屏延长到 10 分钟，并在结束时恢复原值；如果进程被强杀或设备断线，按脚本开头输出的原值恢复 `screen_off_timeout`（本次设备原值为 120000 ms）。

```powershell
.\scripts\gate1-device-check.ps1 -Serial '<adb devices 中的设备号>'
```

该脚本只覆盖分段消费，不自动保存日志/截图，也不验证返回按钮、独立连续消费、权限撤销或进程重启。PASS 只说明脚本检查项通过，仍需人工核对画面。恢复原短超时后手机可能立即锁屏，不能把黑屏当作拦截证据；必要时解锁后重新打开小红书核对，不必为此重跑整段实验。无论正常结束还是中断，都独立读回锁屏超时确认已恢复。

本轮日志、截图与测试报告位于被 Git 忽略的 `app/build/`，不是 App 自动采集的数据，也不会随源码提交；关键结果已摘要到 TASK.md。不要把构建目录中的临时文件当作永久归档。

测试无障碍服务时不要运行普通 `uiautomator dump`：默认 UiAutomation 会临时抑制无障碍服务，导致“检查工具自己让保护停止”。本次已观察到该现象，工具退出后服务恢复。改用屏幕截图、ADB 状态及本应用账本；若以后使用 instrumentation，应明确采用 `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`。依据：[Android UiAutomation 文档](https://developer.android.com/reference/android/app/UiAutomation)。

脚本被外部中断时，PowerShell 的 finally 不一定执行到完成；必须另行检查 `adb shell settings get system screen_off_timeout` 并恢复原值。后台问题诊断还要区分“服务已绑定”和“进程真的在运行”：本机曾出现 AOSP 报告 `isFrozen=false`，但主线程 `wchan=do_freezer_trap`、adbd 报告 cgroup.freeze=1 的情况。不要仅凭授权和服务存在宣称保护有效，也不要直接修改冻结控制节点；先验证厂商公开提供的后台/省电设置。

本 APK 没有网络权限，不读取或保存页面正文、窗口内容、输入内容或截图。使用记录仅用于本机即时计时，不保存完整应用历史。

## 验证边界

构建、账本单元测试和静态检查不等同于真机 Gate 1 通过。具体已完成项目和实测结果以 TASK.md 为准。初始 500 ms 检查周期与 750 ms 事件缓冲属于实验参数，不是计时精度或拦截延迟承诺。
