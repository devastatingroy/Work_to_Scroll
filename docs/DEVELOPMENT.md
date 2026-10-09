# 开发与本机验证

本文面向维护此代码仓库的开发者，记录双产品构建与静态隔离入口；具体真机验证步骤、设备状态和停点以[任务索引](../tasks/README.md)及对应 active 任务为准。

## 前提

配置本地 `local.properties`，并使用 JDK 17、SDK 36 和 Build Tools 36.1.0。

## 构建与静态检查

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:verifyDebugProductIsolation
```

第一个命令构建并测试两个产品变体；第二个命令检查 APK 的应用 ID、目标包、产品名和 Manifest 查询隔离。

| 产品 | Debug APK |
| --- | --- |
| WorkToScroll | `app/build/outputs/apk/workToScroll/debug/app-workToScroll-debug.apk` |
| WorkToWatch | `app/build/outputs/apk/workToWatch/debug/app-workToWatch-debug.apk` |

为兼容既有本机脚本，`assembleDebug` 也会把最新 WorkToScroll APK 同步到 `app/build/outputs/apk/debug/app-debug.apk`。

## 边界

构建、单元测试、lint、安装和 APK 隔离检查都不能替代真机行为验收。设备调试脚本、其产品参数和安全前提以脚本帮助、[调试经验](../notes.md)和对应任务文件为准；不要从本页推断某台设备已通过保护、音频、相机或后台可靠性验证。
