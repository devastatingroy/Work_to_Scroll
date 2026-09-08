# 应用图标采用与备选归档

状态：完成；2026-09-08 已采用标准沙漏修正版并安装 0.3.3，其他方案归档为备选。锁屏导致未完成的桌面目视检查已转交[猫总界面验收](../active/cat-ui.md)。

入口：[任务索引](../README.md)。维护人：接续本任务的 agent。

## 目标与授权边界

Roy 最初要求先调研与设计；后续明确确认「可以。先用这个。别的可以存档，作为备选方案」。现授权采用标准沙漏修正版、替换桌面应用图标及保留其他候选。仅调整图标和版本标识，不改变首页角色、通知图标、权限或业务规则。当前主任务不因图标任务改变。

## 步骤

- [x] 起始图标核对：`app/src/main/res/drawable/ic_launcher.xml`，深绿 #236B55 底、白色线描时钟。
- [x] 核对当前角色方向：[猫总界面设计](../../docs/design/interface-and-roles.md)，C 已采用，其他角色仍待探索。
- [x] 查阅 Opal 官方品牌资料与 App Store 公开图像样本，整理 Forest、ScreenZen、Focus Friend 的方向。ScreenZen 存在圆环与叠石样本，保留版本差异。
- [x] 使用内置 imagegen，基于当前 cat_home.png 生成三候选对照板：A 珊瑚橙立体猫总、B 黄色扁平猫总、C 紫色时间兑换券。已目视检查生成结果。
- [x] Roy 指定 C 风格，要求再来两个；确认的是本轮探索方向，不是最终图标或替换授权。
- [x] 生成 C2「播放键＋时间环」与 C3「工作完成＋娱乐票券」，保留紫底、暖白和简洁几何风格。两张均使用内置 imagegen 单独生成，已目视检查。
- [x] Roy 指出原 C 沙漏扭曲，修正后确认「先用这个」，正式采用 C 标准沙漏修正版；Manifest 引用本地 app_icon_hourglass.png，旧 ic_launcher 继续供通知使用。
- [x] 0.3.3-hourglass-icon / versionCode13 构建与 lint 通过（0 错误、8 警告，含新增 IconLauncherShape）；签名与权限和 0.3.2 相同，Java 源文件哈希与本轮开始时一致。纯图标/版本修改，本轮未重复运行业务单测。
- [x] 设备35c2f60f普通覆盖安装Success，包管理器读回0.3.3/13，首页冷启动Status:ok。余额0、消费1350000ms、工作累计2742217ms、奖励270000ms、历史补齐180000ms、enabled=true、奖励规则3均保留。
- [x] 其他候选在原路径保留并标记备选，DESIGN/README 提供入口；不创建应用内切换功能。
- [x] 桌面目视检查因锁屏未通过，已转交[猫总界面验收](../active/cat-ui.md)，该任务归档不代表全部启动器或圆角裁切通过。

## 证据与局限

当前图标已从 Manifest 追到实际资源；尚未核验手机启动器的主题处理。设计判断不代表首次识别或传播效果已验证。

- [参考与方案展示页](../../docs/design/app-icon-exploration.html)：同类实际图像引用、当前图标按资源复现、三方案及取舍。竞品图像通过官方公开 URL 联网加载，不重新绘制成竞品原图。
- [三方案草图](../../docs/design/app-icon-candidates-v1.png)：已保存本项目，原始生成图片保留。下排是生成式小图示意，不是精确缩放或启动器测试。
- 首轮曾建议 A；Roy 后续选择 C 并确认标准沙漏修正版。A/B、原 C 与 C2/C3 仅作备选，旧建议不覆盖当前选择。
- 官方网页文本和搜索图像样本可读；浏览器自动化两次超时，未完成展示页在浏览器中的目视验收。已按项目约定用系统默认外部浏览器打开，竞品远程图像加载情况待用户查看。

来源：[Opal 品牌资料](https://brandkit.opal.so/) · [Forest](https://apps.apple.com/us/app/forest-focus-for-productivity/id866450515) · [ScreenZen](https://apps.apple.com/us/app/screenzen-screen-time-control/id1541027222) · [Focus Friend](https://apps.apple.com/us/app/focus-friend-by-hank-green/id6742278016)。

## 当前停点与下一步

已采用并安装[C 标准沙漏修正版](../../docs/design/app-icon-c-hourglass-v2.png)，该图与打包资源 SHA-256 应相同；原图不覆盖。构建APK SHA-256：887B2A89C725F09E6BDD95F3C269C88967FC78DE8660EA860261C6009218D2E8。备份与账本快照：app/build/baseline-0.3.2-before-icon.apk、ledger-before-icon.xml、ledger-after-icon.xml；应用内金额、版本规则与保护意愿读回一致。设备熄屏截图全黑，唤醒后系统报告keyguard showing=true，不宣称目视验收通过；下一步由[猫总界面验收](../active/cat-ui.md)在解锁后核对真实桌面。

其他备选原文件保留：[首轮 A/B/原 C](../../docs/design/app-icon-candidates-v1.png)、[C2 时间环与播放键](../../docs/design/app-icon-c2-time-play.png)、[C3 完成与娱乐票券](../../docs/design/app-icon-c3-earned-ticket.png)。全部从[已采用图标与备选归档](../../docs/design/app-icon-exploration.html)进入，不自动升级为使用方案。

### 沙漏修正版提示词

工具：内置 imagegen；输入为用户本轮截图，输出保存项目目录，保留旧稿。

```text
Edit the supplied WorkToScroll C icon reference to correct ONLY the distorted hourglass emblem. Deliver one clean square 1024x1024 icon, no comparison board, no letter C, no miniature duplicates, no off-white surrounding canvas. Preserve the reference's saturated royal purple background, warm ivory ticket shape, rounded corners, left and right ticket notches, ticket orientation tilted approximately 13 degrees counterclockwise, overall minimalist flat geometric style. Purple background fills the entire square. The ivory ticket centered with safe breathing room. CRITICAL correction: inside the ticket replace the existing distorted hourglass/play hybrid with an unmistakable STANDARD HOURGLASS silhouette as a purple cutout. Think classic sand timer, NOT a play button, NOT a bow tie, NOT a lightning bolt, NOT an abstract S. It must be perfectly mirror-symmetric left/right and symmetric top/bottom in the ticket's own coordinate frame: two equal trapezoidal chambers, broad flat parallel top and bottom ends, straight inward diagonal sides tapering to one narrow central neck, broad lower chamber flaring out symmetrically. Top and bottom chambers exactly equal, centered on the same axis. Add short flat wider top and bottom cap bars integrated into silhouette to strengthen classic hourglass recognition, but keep bold and extremely simple. Rotate hourglass together with ticket as one rigid graphic so it remains upright relative to ticket. No extra triangles, no pointing-right section, no twisting, no bending, no curving or mismatched lobes. Preserve ivory ticket around all sides of the hourglass; use generous interior negative space and no tiny details. No glass reflections, no grains, no outlines, no shadows, no texture, no text, no watermark. The only conceptual change is replacing the broken hybrid hourglass with a clear conventional hourglass.
```

## C 风格延展提示词

参考图：首轮三方案板，只参考右侧 C；使用内置 imagegen，两次独立调用，原始生成文件保留。

### C2

```text
Use case: logo-brand. Produce one square 1024x1024 mobile app icon concept for WorkToScroll. Reference image is a style reference ONLY: use the RIGHTMOST C icon's saturated vivid royal-purple and warm ivory minimalist geometric style. Ignore A and B cats entirely. NEW VARIANT C2, but absolutely NO text or label in the artwork. Full-bleed purple square, no surrounding board, no outer white margin. Center a single bold warm-ivory symbol merging a countdown/progress ring and a right-pointing play triangle. Outer ring is thick, smoothly rounded, nearly complete with one deliberate small opening around 1–2 o'clock, one short inward radial clock-hand notch integrated with the ring, large generous central purple counterspace enclosing a simple ivory play triangle. Make the symbol optically unified, friendly geometric with softly rounded corners, flat and precise like reference C. Do not add extra checkmarks, badge, ticket, numbers or external arrows. This concept means earned time available for entertainment. Strong recognizable silhouette, warm-ivory #FFF8E5 against royal-purple around #6220C6, at most very subtle purple tonal shading like reference C, no glossy 3D, no shadows, no busy gradients, no tiny details. Center symbol inside central 68 percent for cropping safety. Crisp edges, polished memorable brand icon. No text, watermark, mockup devices, or multiple icons.
```

### C3

```text
Use case: logo-brand. Create one new square 1024x1024 WorkToScroll mobile app icon. Reference image style guide: ONLY rightmost C purple/ivory ticket graphic. Ignore both cats. New C3 variant exploring completed work earning an entertainment pass. No visible labels or text. Full bleed royal-purple square around #6220C6, warm ivory #FFF8E5 bold single ticket silhouette in center, tilted 12 degrees counterclockwise, rounded corners and symmetric ticket notches at left/right. Central ticket occupies about 67 percent of canvas width leaving safe margins. Inside the ticket use a BIG, very clean negative-space geometric emblem: a checkmark merging at its rising right arm into a right-facing play triangle. The check is the dominant recognizable form and the play tip gives forward momentum; integrate them as one simple purple cutout rather than two unrelated pictograms. Balanced elegant silhouette, broad shapes with soft corner radii, recognizable even very small. Crucially different from reference C which has hourglass cutout: this must have a checkmark/play cutout, no hourglass, no circle or clock, no badge, no perforation dots or tiny lines. The feeling is 'work done, permission to play', upbeat and purposeful. Keep reference C's minimal premium visual style with virtually flat color and only very subtle tonal purple variation. No 3D, gloss, drop shadows, texture, outlines, letters, numbers, watermark, comparison layout, or device mockup.
```

## 生成提示词

工具：内置 imagegen；参考图为现有猫总角色身份，输出为设计探索板。

```text
Use case: logo-brand. Create ONE polished design exploration comparison board for the existing WorkToScroll app, landscape 1536x1024. Reference image is ONLY the existing supervisor cat character identity, not an edit target. Three equally sized columns with large rounded-square app icons, each repeated at a much smaller size beneath to judge small-size recognition. Warm off-white presentation background, clean generous spacing, no phone devices. Small crisp headings ONLY 'A', 'B', 'C' above the columns, small overall heading 'WorkToScroll' and 'ICON EXPLORATION' at top. No other words, no captions, no numbers inside icons. A: vivid coral-orange solid background, tight closeup of a stern but endearing black and white tuxedo cat with golden eyes, low round spectacles and tiny red tie, recognize SAME face as reference, stylized soft 3D with controlled detail and strong black-white shapes, head fills 80% of icon, pupils clear, glasses bold but do not obscure the eyes, dry humor not angry, no body or stamp. B: electric yellow solid background, bold flat graphic interpretation of SAME black and white supervisor cat head and round spectacles, very few large shapes, slightly asymmetrical raised brow, sophisticated brand mascot glyph with crisp edges, tiny coral tie optional, no fur texture, no body, not a generic smiling kitten. C: saturated purple solid background, one bold warm ivory entertainment ticket tilted slightly clockwise with a single hourglass-shaped cutout where the LOWER half forms a play triangle; the icon should communicate time earned to play, simple striking geometric symbol, no text no currency no arrows no extra tiny badges. Each icon must be distinct and visually clear at 48px, generous safe margins for future circular cropping, no cloned competitor symbols. Presentation mockup only, no claims of tested performance. Avoid thin complex fur, decorative scene elements, gradients on board, watermark, labels inside icons. Exact clean repetition of each icon as small preview beneath its large version.
```
