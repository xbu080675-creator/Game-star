# Game Star Box Development Changelog

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
