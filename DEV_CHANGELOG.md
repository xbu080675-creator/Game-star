# Game Star Box Development Changelog

## 0.4.10 / versionCode 23

- 修复红魔等厂商 ROM 在关闭“允许 ADB 安装 / USB 安装”时被误报为 `Shizuku 不可用` 的问题。
- PrivilegedInstallerService 现在识别 `INSTALL_FAILED_USER_RESTRICTED`、`install is disabled`、`adb install disabled` 等 Package Manager 输出，并返回独立错误码 `GSB-PRIV-INSTALL-PACKAGE-DENIED-ADB-POLICY`。
- Shizuku Adapter 将该错误直接解释为：Shizuku 已连接且授权正常，但 ROM 禁止 shell/ADB 安装 APK；提示到开发者选项开启允许 ADB 安装/USB 安装后重试。
- 不再把 UserService 返回的明确错误统一包装成 `GSB-PRIV-INSTALL-TRANSACTION-FAILED`，上层可以区分 Shizuku 生命周期问题与 ROM 安装策略问题。
- Shizuku Binder 初次等待窗口由约 1.6 秒放宽到 5 秒，并增加“正在等待 Shizuku Binder”状态，减少瞬时初始化误判。
- 安装成功后由 UserService 通过固定 `am start -S -W -n com.xbu.esportscenter/.MainActivity` 动作重新拉起竞界；命令、包名与 Activity 均不可由 UI/WebView/网络输入控制。
- 自动重启失败不会把已成功安装的更新误判为安装失败；界面会提示手动重新打开竞界。
- 继续保持原有官方来源、SHA-256、包名、versionCode、固定签名校验以及特权接口白名单，不扩大 Shizuku 权限面。

## 0.4.9 / versionCode 22

- 修复启动动画与 `fairy_boot.ogg` 音画不同步：移除原先 350 ms 强制启动视觉时间轴的抢跑路径。
- 启动画面在支持 Web Animations API 的 WebView 上改由启动音频 `currentTime` 作为主时钟，CSS 动画暂停后逐帧对齐音频时间；触觉节点继续跟随同一时间轴。
- 启动音频异常或 1.8 秒内仍未开始播放时，主动停止音频并切入纯视觉兜底，避免“画面先跑、声音随后补上”的错位。
- 原生更新面板改为更紧凑的主机系统卡片：缩小最大宽高、标题字号、内边距、按钮与进度区占用。
- 更新说明区域支持卡片内滚动，长 changelog 不再依赖放大整个更新面板。
- 更新遮罩透明度略降，减少对后台主界面的压迫感；OTA 安全校验与 Shizuku 自更新链路均未放宽。

## 0.4.8 / versionCode 21

- 接入 Shizuku 13.1.5，新增受限 `Privileged Adapter`，首个且唯一特权用途为 Game Star Box 自身 OTA 静默覆盖安装。
- 新增 `IPrivilegedInstaller` 窄 AIDL，仅暴露固定自更新事务，不提供任意 shell、任意 APK 路径或通用命令执行入口。
- 新增 Shizuku UserService：普通 OTA 层完成官方来源、SHA-256、包名、versionCode、固定签名校验后，APK 以固定大小 Binder chunk 传入 shell/root 进程。
- UserService 在 `/data/local/tmp` 使用固定前缀重新暂存 APK，并再次校验文件长度与 SHA-256，通过后才执行固定 `/system/bin/pm install -r <staged-apk>`。
- Shizuku 首次使用会请求一次明确授权；Shizuku 未运行、未授权、版本过旧、Binder/事务失败时安全回退 Android 系统安装器。
- 未使用 Shizuku 路径时才请求“安装未知应用”权限；Shizuku 正常时不再弹 Android 系统安装确认界面。
- 非 `com.xbu.esportscenter` 候选在普通 OTA 层和 Privileged Adapter 双重拒绝；versionCode 必须严格递增。
- 新增 `[GSB-PRIV]` / `[GSB-INSTALL]` 日志前缀、安装超时、残留 staging 清理、事务取消以及 Privileged Surface CI Guard。
- 新增特权模块 README，记录权限白名单、信任边界、威胁模型、错误码、测试矩阵、降级与 ROM 兼容策略。

## 0.4.7 / versionCode 20

- 重做原生 OTA 安装体验，保留 Native 层作为更新关键 UI，避免 WebView 状态异常影响安装流程。
- 更新浮层改为主机系统风：目标版本、当前版本、下载源、包体大小、状态、更新内容与安全校验提示集中呈现。
- 新增原生下载进度条、百分比与已下载字节显示；下载时可隐藏面板并继续后台下载。
- 错误状态提供明确恢复入口；更新流程不因 UI 隐藏而中断。
- 未知来源安装授权采用一次性恢复标记：用户从授权页返回且权限已授予时，仅继续此前由用户发起的更新，不做后台隐式安装。
- 安全提示明确安装前仍执行 SHA-256、包名、versionCode、固定签名校验；本版本不降低任何 OTA 安全门槛。
- 当前最终安装动作仍走 Android 系统安装器；Shizuku 静默自更新将在 Privileged Adapter 与安全状态机完成后另行接入。

## 0.4.6 / versionCode 19

- 主界面加入 CC0 环境音乐 `Spacelife #14`，启动动画结束后低音量淡入并循环播放。
- 系统页增加主界面音乐开关；设置本地持久化。
- 第三方音乐来源、CC0 许可、原始文件信息与 SHA-256 固定校验已归档。
- CI 每次构建重新获取音乐并验证固定哈希和文件大小，来源文件异常时构建失败。

## 2026-09-14 / Privileged Capability Security Policy

- 新增 `docs/ENGINEERING_CONSTITUTION.md`，将 ADB / Shizuku / shell 能力正式纳入强制工程规则。
- 工程优先级调整为：安全 > 稳定 > 可恢复 > 正确性 > 性能 > 体验 > 开发速度。
- 特权能力必须隔离在 Privileged Adapter；Core / WebView 禁止直接调用 shell、Shizuku、ADB 或 PackageInstaller 特权接口。
- 禁止通用 `exec(command)` / 任意 shell 控制台；UI 只允许请求类型化、语义化动作。
- 当前 Shizuku 白名单仅允许 `com.xbu.esportscenter` 自更新；不得成为通用静默安装器，不影响其他 APP 的系统安装流程。
- 静默安装前强制执行官方来源、SHA-256、包名、versionCode、签名、PackageManager 可解析性校验，任一失败 Fail Closed。
- 明确 Shizuku 未安装、未启动、未授权、权限撤销、Binder 死亡等降级路径；失败时回退系统安装器且普通功能继续可用。
- 特权安装引入事务状态机、超时/有限重试、session 清理、熔断与本地 kill switch 要求。
- 新增负向安全测试矩阵和 CI 发布硬门槛；任何特权代码变更必须继续留档。

## 0.4.5 / versionCode 18

- OTA 基础设施接入：GitHub preview Release 为唯一正式版本真源。
- 内置 GitHub 直连 + 六个请求级镜像通道，APK 下载前并发 Range 测速并动态排序。
- 下载支持 `.part` 断点续传与跨通道恢复。
- 安装前执行 SHA-256、包名、versionCode、固定签名证书四重校验。
- 新增 APP 内更新浮层、自动后台检查以及 WebView Native Bridge 更新接口。
- Preview 构建切换到固定 DEV 签名；CI 强制验证证书 SHA-256。
- 发布顺序固定为 APK 先上传、`latest.json` 最后覆盖。
- 启动视觉资产保持冻结，不在本版本修改。
