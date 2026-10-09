# TikTok 营销视频关键帧

状态：奖励到账转场 v2 已获 Roy 认可并收入素材库；工作监测中人物静帧 v2 和监控页 HTML 视觉提案 v3 待审阅，阻止页仍为独立草稿（2026-09-27）。入口：[任务索引](../README.md)。

## 目标、范围与授权

为面向 TikTok 的推广短视频制作可读性更强的界面特写关键帧。已按 Roy 指定制作 YouTube 阻止页并加入“15分钟换5分钟”；随后依据[奖励页参考图](../../Resources/WorkToWatch_推广视频素材/worktowatch-tiktok-reward-reference.png)制作红色工作计时到绿色奖励到账的 3.6 秒转场。此处是 WorkToWatch 视频画面草稿，不改变 WorkToScroll 或 WorkToWatch 的实际页面、默认兑换档位、产品要求或已验收范围。

## 步骤

- [x] 检查用户提供的[参考图](../../docs/design/worktowatch-tiktok-blocker-reference.png)与 WorkToWatch 的 Android 产品边界。
- [x] 生成[阻止页关键帧 v1](../../docs/design/worktowatch-tiktok-blocker-15-for-5-v1.png)：保留原图核心信息，加入准确文字“15分钟换5分钟”，将 iPhone 外观改为通用 Android 手机外观。
- [x] 制作奖励转场 v1（[原 HTML](../../docs/design/worktowatch-tiktok-reward-transition-v1.html)／[原 MP4](../../docs/design/worktowatch-tiktok-reward-transition-v1.mp4)）；原节奏已由 Roy 指出不合适，不再作为当前审阅稿。
- [x] 按 Roy 指定重排为 T0=14:59、T1=15:00、T2=奖励到账，三个时间点各隔一秒；T2 立即切绿并弹出，撒花、彩带和绿色扩散环从切换点开始。[当前 HTML](../../Resources/WorkToWatch_推广视频素材/worktowatch-tiktok-reward-transition.html)／[v2 MP4](../../Resources/WorkToWatch_推广视频素材/worktowatch-tiktok-reward-transition-v2.mp4)。
- [x] Roy 认可奖励转场 v2；将成片、源稿、关键静帧和参考图收入[推广视频素材库](../../Resources/WorkToWatch_推广视频素材/README.md)，并更新导出脚本与链接。
- [x] 按 Roy 选定的两张人物照片制作[工作监测中 14:59 宣传静帧 v1](../../Resources/WorkToWatch_推广视频素材/worktowatch-tiktok-monitoring-14-59-draft-v1.png)；保留简洁计时、在场状态和暂停／结束工作两个操作，人物相似度及最终采用待审阅。
- [x] Roy 确认 v1 人物正确、要求改用室内环境；据新参考图制作[工作监测中 14:59 宣传静帧 v2](../../Resources/WorkToWatch_推广视频素材/worktowatch-tiktok-monitoring-14-59-draft-v2.png)，保留人物和界面，镜头内换为暖色书桌环境。室内环境与最终采用待审阅。
- [x] 以 v2 静帧为视觉参考制作[监控页 HTML 视觉提案 v3](../../Resources/WorkToWatch_推广视频素材/worktowatch-monitoring-interface-review-v3.html)，再按 Roy 意见改为全屏人物、半透明计时卡与按钮；示例时间不构成固定产品规则。此稿只供审阅与视频画面探索，未修改 Android 页面。
- [ ] 阻止页草稿及后续镜头的整体视频可读性随总片审阅。

## 证据与局限

阻止页 v1 已目视核对：YouTube、Watch Time、00:00、暂无观看时间、先工作，再观看以及新增的“15分钟换5分钟”均可读；由内置 imagegen 编辑。工作监测中静帧由内置 imagegen 以用户人物照片为参考生成；Roy 确认 v1 人物正确，v2 按新参考图替换了镜头内环境。目视核对 v2 的人物、暖色室内布景、14:59/15:00、规则文字及“暂停／结束工作”按钮；v2 采用仍待确认。监控页 v3 用独立 HTML 裁取 v2 的人物区域并重新绘制界面，已用 Chromium 渲染 1080×1920 竖屏画面、检查人物裁切、文本层级与控制区，页面无脚本错误；未验证真实摄像头或 Android 交互。奖励转场由自包含 HTML/CSS/JS 绘制，经 Playwright 逐帧截图、ffmpeg 编码；v2 输出 H.264 1080×1920、30fps、3.6 秒、无音轨。已目视核对[T0 红色 14:59](../../Resources/WorkToWatch_推广视频素材/worktowatch-tiktok-reward-blocked-v2.png)、[T1 红色 15:00](../../Resources/WorkToWatch_推广视频素材/worktowatch-tiktok-reward-threshold-v2.png)、[T2 绿色到账](../../Resources/WorkToWatch_推广视频素材/worktowatch-tiktok-reward-arrival-v2.png)、[撒花彩带](../../Resources/WorkToWatch_推广视频素材/worktowatch-tiktok-reward-impact-v2.png)和[稳定绿色画面](../../Resources/WorkToWatch_推广视频素材/worktowatch-tiktok-reward-reward-v2.png)。参考图的“本轮有效时间 20:00”已按此镜头 15 分钟门槛改为 15:00，并以 Android 外框替换 iPhone 外观。画面均为宣传视频草稿，不是实际应用截图；正式对外使用前应核对发布时的档位与产品能力。

## 当前停点与下一步

奖励转场 v2 已作为视频素材保存，未宣称实际应用页面或公开发布已完成。工作监测中人物静帧 v2 与监控页 HTML 视觉提案 v3 待 Roy 审阅；阻止页仍为独立草稿，整体视频待后续镜头接续。奖励转场 HTML 可用浏览器打开、播放或拖动进度；[导出脚本](../../tools/export-worktowatch-reward-transition.cjs)可复现 v2 MP4 与静帧。监控页 v3 暂不进入 Android 产品实现。

关联：[WorkToWatch 产品要求](../../docs/SPEC.md#worktowatch) · [WorkToWatch 产品实施](overseas-target-apps.md)。
