# 应用显示名称改为 WorkToScroll

状态：完成；名称修改已实施。迁移日期：2026-09-08。

入口：[任务索引](../README.md)。维护人：接续本任务的 agent；关键步骤、阻塞、验证或完成时更新本文件，状态迁移同步索引。

范围仅为名称修改与安装核对。原记录中桌面标签刷新的目视限制保留；当前整体界面验收由[猫总 UI](../active/cat-ui.md)接续。

## 已有记录与证据

## 当前实现：显示名称改为 WorkToScroll（2026-09-08）

- [x] Roy 明确要求应用名改为 WorkToScroll。仅调整当前应用标签、首页自动引用、保护通知和返回按钮；应用 ID、签名、数据及权限不变，旧版仍停用。
- [x] 已同步 SPEC 名称决定、DESIGN/README/ARCHITECTURE 当前称谓；保留下方尚未确认的桃子首页设计，不据此实现新视觉方案。
- [x] 0.2.4-camera-test / versionCode 8 构建、38项测试和lint通过（0错误7警告），签名通过；APK标签为WorkToScroll，真机普通覆盖安装Success，已读回versionCode8。安装前后balance_ms均209970、consumed_ms均870030，工作累计与补齐金额不变；enabled=true且保护前台服务运行，旧版仍disabled。未截取摄像头画面；首页文字抓取未取得匹配节点，因此不声称已目视确认桌面标签刷新。
- APK SHA-256：`B0302C49D4D68FA4777264368F2E273B72B5C678A7CC9A2E99AAAE14DE8FD978`。名称修改已完成，下方首页设计仍待Roy确认；没有改变功能规则或清除数据。
