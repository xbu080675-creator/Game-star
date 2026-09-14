# Game Star Box 工程宪法 v2（特权能力版）

> 状态：强制执行
> 适用范围：Game Star Box / 竞界全部代码、CI、OTA、Shizuku、ADB/shell、未来 Root/System Adapter。
> 优先级：安全 > 稳定 > 可恢复 > 正确性 > 性能 > 体验 > 开发速度。

## 0. 为什么升级规则

当应用只拥有普通 Android 权限时，多数错误只会影响自身；一旦引入 ADB / Shizuku / shell 级能力，错误可能影响包管理、系统设置、其他应用或设备可用性。因此特权能力必须被视为独立安全域，不能按普通功能开发。

本宪法在原 Core / Adapter 边界、禁止跨层、Core 无平台 API、固定日志与错误码、开发留档等规则之上增加特权能力硬约束。

## 1. 特权边界

1. Core **不得**直接调用 Shizuku、ADB、shell、PackageInstaller、Root API。
2. 所有特权行为必须位于独立 `Privileged Adapter` 中，通过稳定接口暴露给上层。
3. UI 只能请求“语义化动作”，例如 `installSelfUpdate(candidate)`，不得传入 shell 命令字符串。
4. 禁止提供通用 `exec(command)`、`runShell(String)`、任意脚本执行、任意 Intent/URI 转发等万能入口。
5. 禁止把 WebView、JS、远程配置、网络响应直接连接到 shell 参数。
6. 新增任何特权能力都必须单独评审，不能因为“已经有 Shizuku”就顺手复用权限。

## 2. 最小权限原则

1. 当前 Shizuku 特权用途默认仅允许 **Game Star Box 自更新**。
2. 允许静默安装的包名固定为：`com.xbu.esportscenter`。
3. 非本包 APK 必须拒绝走特权安装通道，并回退系统正常安装流程或直接拒绝。
4. 不得默认获得或实现：任意应用安装、卸载、停用、清数据、授权权限、改系统设置、读其他应用私有数据等能力。
5. 未来若确需增加上述能力，必须作为新的 capability 独立设计、独立开关、独立测试、独立留档。

## 3. 自更新安全门

特权安装前必须全部通过，任一失败立即停止（Fail Closed）：

1. OTA manifest 来源符合官方规则。
2. APK SHA-256 与 manifest 完全一致。
3. APK packageName 必须为 `com.xbu.esportscenter`。
4. candidate `versionCode` 必须大于当前版本；禁止降级覆盖，除非进入明确的人工恢复模式。
5. APK 签名证书必须与固定 Game Star Box 签名策略匹配，并与当前安装版本保持可升级关系。
6. APK 必须能够被 Android PackageManager 正常解析。
7. 下载文件必须位于应用控制的确定路径，不接受外部任意文件路径作为静默安装输入。

以上校验必须在调用 Shizuku / shell 之前完成；shell 不是校验器，只是最后执行器。

## 4. Shell 命令规则

1. 优先使用类型化 API / PackageInstaller Session；只有缺失能力时才允许调用固定 shell 子命令。
2. shell 命令必须由代码内固定模板构造，参数逐项验证，禁止拼接用户输入。
3. 禁止使用 `sh -c` 承载可变文本。
4. 禁止执行从网络、WebView、文件、剪贴板读取出的命令。
5. 禁止通配符、命令替换、管道、重定向、`&&` / `||` 等复合 shell 语法，除非该能力经过单独安全评审并有必要性证明。
6. 所有特权命令必须有明确超时、退出码处理和异常映射；不得无限等待。
7. 特权进程结束后不得残留长期 shell 会话。

## 5. Shizuku 生命周期与降级

必须把以下情况视为正常运行状态而不是异常边角：

- Shizuku 未安装；
- Shizuku 未启动；
- 用户未授权；
- 授权被撤销；
- Binder/服务在操作中死亡；
- Android 版本或厂商 ROM 行为不兼容。

处理原则：

1. 能力探测必须显式返回 `AVAILABLE / UNAVAILABLE / DENIED / DEAD / UNSUPPORTED` 等状态。
2. 权限失效时不得崩溃，不得循环申请，不得反复拉起系统页面。
3. 自更新优先 Shizuku；不可用时安全回退系统 Package Installer。
4. 任何降级路径都不能绕过 SHA-256、包名、版本和签名检查。
5. Shizuku 可用与否不得影响主界面、游戏库、赛事、HUD 等普通功能启动。

## 6. 安装事务与恢复

1. 安装流程必须实现明确状态机：`IDLE -> VERIFIED -> PREPARING -> WRITING -> COMMITTING -> SUCCESS/FAILED`。
2. 状态转换必须单向、可追踪；禁止 UI 自行猜测安装状态。
3. 使用 PackageInstaller Session 时，失败必须主动 abandon 未完成 session。
4. APK 临时文件使用 `.part` / candidate 机制，完整校验后才进入安装阶段。
5. 应用重启后必须能识别并清理过期安装残留，不得积累垃圾 session 或临时 APK。
6. 更新失败必须保证旧版本仍可启动；不允许先破坏当前版本再尝试修复。
7. 禁止自动无限重试。所有重试都必须有次数上限和退避。

## 7. Fail Closed 与 Kill Switch

1. 特权能力出现未知状态时默认“不执行”，而不是“试试看”。
2. 任何签名、包名、版本、解析、权限状态异常都必须阻断特权安装。
3. 必须保留本地特权安装总开关，关闭后强制走系统安装器。
4. 若检测到连续特权安装异常，应自动熔断本次会话的 Shizuku 安装能力。
5. 熔断不得影响应用普通功能。

## 8. WebView 安全边界

当前 UI 运行在本地 WebView，因此：

1. `JavascriptInterface` 不得暴露通用 shell、文件系统或 PackageManager 能力。
2. JS 只能调用有限、无歧义、无自由文本命令参数的 Native Bridge 方法。
3. Native 层必须再次验证所有来自 JS 的请求；不得信任前端已经校验。
4. 特权操作关键参数应由 Native/Core 生成或读取，不由 JS 提供。
5. 任何未来远程网页内容都不得拥有现有特权 Bridge；远程内容与特权 Bridge 必须物理隔离。

## 9. 日志与隐私

1. 特权模块使用稳定日志前缀，例如 `[GSB-PRIV]`、`[GSB-INSTALL]`。
2. 日志记录：动作类型、阶段、结果、错误码、耗时、版本号、candidate 哈希前缀。
3. 禁止记录 keystore、密码、Secret、完整 Token、私钥、用户敏感路径内容。
4. shell 命令如包含敏感参数，日志中必须使用结构化动作名而不是原始命令全文。
5. 所有失败必须映射为模块/阶段/平台可本地化错误码。

建议错误码形态：

`GSB-PRIV-INSTALL-VERIFY-SIGNATURE-MISMATCH`

`GSB-PRIV-SHIZUKU-BINDER-DEAD`

`GSB-PRIV-INSTALL-COMMIT-TIMEOUT`

## 10. 测试硬门槛

特权能力未通过以下测试不得进入 Preview：

- 正常自更新；
- Shizuku 不存在；
- Shizuku 未授权；
- 操作中权限撤销；
- Binder 中途死亡；
- APK 下载中断与断点续传；
- APK SHA-256 错误；
- 包名伪造/错误；
- 签名不匹配；
- versionCode 相同或更低；
- APK 损坏无法解析；
- 存储空间不足；
- PackageInstaller commit 失败；
- APP 在安装流程中被杀死后重新启动；
- 非 Game Star Box APK 尝试进入特权安装通道；
- 特权模块异常时普通 UI/赛事/游戏功能仍可使用。

至少要有自动化负向测试覆盖“拒绝危险输入”，不能只测试成功路径。

## 11. CI / 发布门槛

1. Preview 继续使用固定签名，CI 必须验证实际 APK 证书指纹。
2. 发布前必须完成构建、签名校验、OTA manifest 生成和关键安全测试。
3. APK 先发布，manifest 最后切换，避免 manifest 指向不存在的包。
4. 任何特权模块代码变更必须在 `DEV_CHANGELOG.md` 留档。
5. 特权能力变更必须说明：新增能力、权限范围、威胁模型、失败模式、回退策略、测试结果。
6. 不允许为了赶进度绕过安全测试；安全门失败即禁止发布。

## 12. 模块文档强制项

任何 Privileged Adapter 的 README 必须包含：

- 职责；
- 输入；
- 输出；
- 权限需求；
- 允许操作白名单；
- 明确禁止的操作；
- 信任边界；
- 威胁模型；
- 状态机；
- 错误码；
- 日志规范；
- 超时与重试；
- 回退方案；
- 测试矩阵；
- 已知 ROM/Android 兼容性；
- 变更记录。

## 13. 架构目标

推荐边界：

```text
UI / WebView
    ↓ semantic request only
Core / UseCase
    ↓ typed capability interface
InstallService
    ├─ ShizukuInstallAdapter
    ├─ SystemInstallerAdapter
    └─ future: RootInstallAdapter (默认不存在)
```

核心原则：**权限越高，接口越窄；能力越危险，默认越关闭；任何失败都必须可恢复。**

## 14. 当前明确决策

- Shizuku 的首个用途：仅 Game Star Box 自身 OTA 静默覆盖更新。
- 不替换全局 Android 安装器。
- 不影响其他 APP 的正常安装流程。
- 不提供通用静默安装器。
- 不提供通用 shell 控制台。
- 普通功能不得依赖 Shizuku 才能启动。

如后续需求与本节冲突，必须先修改本宪法并留档，再修改代码；不得先写代码后补规则。
