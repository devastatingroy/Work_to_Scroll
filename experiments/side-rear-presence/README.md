# 人体识别实验（全画面初筛）

独立 Android debug 应用，唯一任务入口：[侧后方探索](../../tasks/active/side-rear-presence-exploration.md)。这里只维护实验运行方式与实际边界，不复制任务进度。

- 包名 `com.roy.worktoscroll.presenceexperiment`；显示名“人体识别实验”。不覆盖原应用，不包含保护服务、真实/模拟账本、奖励、联网、录像或图片保存功能。
- CameraX 1.4.2 后置相机，分析线程每约 500 ms 处理一次最新帧；ImageProxy.toBitmap 处理格式，按帧方向旋转后交给 MediaPipe Tasks Vision 1.0.0（CPU，IMAGE 同步模式）。模型未加载完成不能启动。
- EfficientDet-Lite0 int8；模型来源为 Google 官方 Object Detector 模型入口，下载 URL：`https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/latest/efficientdet_lite0.tflite`。下载于 2026-09-08，随本实验固定资产，SHA-256 `0720BF247BD76E6594EA28FA9C6F7C5242BE774818997DBBEFFC4DA460C723BB`。SDK 固定版本，模型通过哈希固定；不随运行联网下载。发布或商用前仍须核对各模型/SDK许可，当前仅为私用验证。
- 只保留 `person` 标签；输出门槛 0.20，可靠候选门槛 0.50。低分候选不会作为“无人”证据；分数不是校准过的可靠概率。多人立即不确定，连续约 1 秒确认，消失约 3 秒宽限，结果过期 1.5 秒即失效。状态与人数分别读取，不把 GRACE 当作当前成功检测到人。
- **全画面筛选，尚无固定工作区域、身份识别、移动检测、连续工作计时或奖励。** 不冒充完整探索方案验收。
- 0.1.1 增加 Roy 指定的状态声音：零人体候选播放“短滴音＋暂时离开”，至少一个可靠人体播放“欢迎归来”；首次明确结果或状态切换时立即播一次，不等待 PresenceGate 宽限。同状态不复读，低分不确定时不宣告离开，过期/暂停/切出停止声音；新提示替换旧提示，不排队。声音反映原始检测变化，可能暴露检测抖动，不等于判断实际离席或身份。
- 两段 WAV 随包离线播放，不依赖手机 TTS 服务。由 generate-cues.ps1 使用本机 Microsoft Huihui Desktop 合成固定短语，离开声前拼入 240 ms 的 880 Hz 短音；资源分别约 2.10 秒、1.85 秒。使用媒体音量，不修改系统音量、不主动抢占其他应用音频焦点；耳机/蓝牙路由或静音可能影响可听性，安装后先由 Roy 试听确认。
- 画面只在内存存在；手机可显示本地预览，Agent 不截图或读取画面。原始人体框不写文件。应用私有 `files/status.json` 每秒原子覆盖一份最新诊断状态（人数/分数/处理时长/相机状态/时间戳），不保存连续历史。读取脚本输出短测采样，会出现在本任务工具记录中。
- 只手动授权相机；启动应用不启动检测。切出、锁屏或失焦停止，返回不自动恢复。状态文件可能在进程退出后留下最后读数，因此读取脚本同时核对 PID 和手机单调时钟，过期结果不可用于判断人在场。
- status.json 同时提供 audio_last_cue/audio_status/audio_events/media_volume 供 Agent 排查；事件数是播放请求数，completed 是播放器完成回调，均不能替代 Roy 确认听到了声音。试听与检测分开，点击“试听离开声/试听归来声”会先停止摄像头，不把试听冒充检测结果。

## 构建与读取

在项目根目录使用原有 Gradle wrapper，但以本实验为独立构建目录：

```powershell
.\gradlew.bat -p experiments\side-rear-presence :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

生成 APK：本目录 `app/build/outputs/apk/debug/app-debug.apk`。先检查合并 manifest/实际权限及独立包名，再安装。首次启动后先逐个试听两种声音，再由 Roy 点击“允许使用相机”并确认系统相机授权；每一步等待回复，保持未运行状态直到开始检测环节。

用户完成准备并明确回复后，Agent 才按单步指令启动后置检测：

```powershell
& 'D:\AndroidSDK\platform-tools\adb.exe' -s 35c2f60f shell am start -W -f 0x20000000 -n com.roy.worktoscroll.presenceexperiment/com.roy.presenceexperiment.MainActivity --es command start --es lens back
.\experiments\side-rear-presence\read-status.ps1 -Samples 10
```

停止当前一段：同一启动命令改成 `--es command stop`，不发送 lens。这些显式命令只用于用户授权的测试；不使用它们在用户未准备好时启动相机。

声音单项试听可用同一显式命令传 `--es command test-away` 或 `--es command test-return`；每次只执行一个，等待 Roy 反馈。试听会停止检测，相机不启动。也可由 Roy 使用屏幕上对应按钮。生成资源：在项目根执行 `.\experiments\side-rear-presence\generate-cues.ps1`，要求本机存在 Microsoft Huihui Desktop；生成固定文案不使用麦克风或网络。

## 单步引导约定

每次只要求 Roy 做一个动作并等待回复；Agent 随后读取约 10—15 秒状态，报告“多少个新结果检测到人、分数范围、是否稳定/不确定”，再给下一个动作。先通路检查，再侧后方摆放，再自然坐姿，再离开/返回。每段记录机位由 Roy 描述；USB 阶段不推断断线发热。

运行停止/迟延时，读数 `live=false` 的结论是“没有有效读数”，不是“无人”。`live=true` 仍需要看 reliable/people，不能把进程存在当作模型成功识别人。
