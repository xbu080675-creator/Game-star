# Privileged Self-Update Adapter

## 职责

本模块仅负责 **Game Star Box / 竞界自身 OTA 的特权安装阶段**。它不负责下载、版本发现、APK 来源判断，也不提供通用 shell 能力。

## 输入

仅接受已经由普通 OTA 层完成验证的候选 APK：

- packageName 固定为 `com.xbu.esportscenter`；
- 完整 APK 文件；
- APK 实际字节数；
- 64 位 SHA-256；
- 调用必须来自 `AppUpdateManager` 的已验证更新流程。

## 输出

- Shizuku 连接/授权/安装阶段状态；
- 安装提交成功；
- 明确错误码；
- 在 Shizuku 不可用或失败时请求普通系统安装器兜底。

## 权限需求

- Shizuku/Sui Binder 可用；
- 用户首次明确授予本应用 Shizuku 权限；
- 非 Root Shizuku 后端以 ADB shell UID 运行 UserService。

普通 UI、游戏库、赛事、HUD 均不得依赖该权限启动。

## 允许操作白名单

当前唯一允许操作：

`installVerifiedSelfUpdate(Game Star Box APK)`

UserService 内唯一允许的系统变更是通过固定 `/system/bin/pm install -r <staged-apk>` 模板覆盖安装已经验证的竞界 APK。

## 明确禁止的操作

- 任意 APK 安装；
- 卸载应用；
- 清数据；
- 停用/冻结应用；
- 修改系统设置；
- 授予其他权限；
- 读取其他应用私有数据；
- 通用 `exec(command)` / `runShell(String)`；
- `sh -c` 动态命令；
- 从 WebView、网络、文件或剪贴板接收 shell 命令。

## 信任边界

```text
WebView/UI
   ↓ 无路径、无 shell 字符串
AppUpdateManager
   ↓ 已完成来源/SHA/包名/versionCode/签名校验
ShizukuSelfUpdateAdapter
   ↓ 固定 AIDL
PrivilegedInstallerService (shell/root UID)
   ↓ 固定 pm install -r
Android Package Manager
```

WebView 永远不能直接持有 privileged binder。

## 威胁模型

重点防止：

- 非竞界 APK 被误送入静默安装；
- 网络返回值直接控制 shell；
- APK 在普通层校验后、特权写入时发生字节变化；
- Binder 中途死亡导致半事务残留；
- 特权接口逐渐膨胀为通用 shell；
- 安装失败后破坏当前可用版本。

为降低 TOCTOU 风险，APK 从普通进程以固定大小 Binder chunk 传入 UserService，UserService 在 `/data/local/tmp` 重新暂存并再次计算 SHA-256；哈希和长度完全一致后才执行安装。

## 状态机

```text
IDLE
 → CONNECTING
 → PERMISSION (仅首次/权限丢失)
 → BINDING
 → STAGING
 → VERIFYING
 → COMMITTING
 → SUCCESS

任意阶段异常 → FALLBACK / FAILED
```

## 错误码

主要错误码：

- `GSB-PRIV-SHIZUKU-UNAVAILABLE`
- `GSB-PRIV-SHIZUKU-PERMISSION-DENIED`
- `GSB-PRIV-SHIZUKU-BINDER-DEAD`
- `GSB-PRIV-INSTALL-CANDIDATE-INVALID`
- `GSB-PRIV-INSTALL-PACKAGE-DENIED`
- `GSB-PRIV-INSTALL-VERIFY-SIZE-MISMATCH`
- `GSB-PRIV-INSTALL-VERIFY-HASH-MISMATCH`
- `GSB-PRIV-INSTALL-COMMIT-TIMEOUT`
- `GSB-PRIV-INSTALL-COMMIT-FAILED`

## 日志规范

稳定前缀：

- `[GSB-PRIV]`：Shizuku/Binder 生命周期；
- `[GSB-INSTALL]`：安装事务。

禁止日志输出 keystore、密码、Token、私钥和完整敏感路径内容。候选哈希只记录前缀。

## 超时与重试

- Shizuku Binder 初次等待：约 1.6 秒；
- `pm install` 提交最长 120 秒；
- 不自动无限重试；
- Shizuku 失败后仅进入普通系统安装器兜底，不循环申请权限。

## 回退方案

Shizuku 未安装、未运行、未授权、版本过旧、Binder 死亡或特权事务失败时，回到 Android 原生 Package Installer。所有普通 OTA 安全校验仍然有效；回退不允许绕过校验。

## 测试矩阵

Preview 发布前至少覆盖：

- Shizuku 正常 + 已授权；
- 首次授权；
- 用户拒绝授权；
- Shizuku 未运行；
- Binder 安装中死亡；
- 错误包名；
- 错误 SHA-256；
- 错误文件长度；
- versionCode 相同/更低（由普通 OTA 层阻断）；
- 签名不一致（由普通 OTA 层阻断）；
- `pm install` 失败；
- 非竞界 APK 无法进入特权入口；
- Shizuku 异常不影响普通功能启动。

CI 另有静态 Privileged Surface Guard，禁止 AIDL/Adapter 出现通用 shell 执行入口。

## 已知 ROM / Android 兼容性

- 目标 Android 26+；
- Shizuku 官方 v13.1.5 API；
- Android 11+ 可使用无线调试启动 Shizuku；
- 厂商 ROM 对 shell Package Manager 权限可能存在差异，失败时必须回退系统安装器；
- 当前优先验证红魔 9 Pro+ 实机行为。

## 变更记录

- 2026-09-14：建立首版 Shizuku 自更新适配器；权限范围仅限竞界自身静默覆盖更新。
