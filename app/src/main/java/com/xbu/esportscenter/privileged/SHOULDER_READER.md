# REDMAGIC Shoulder Reader Privileged Adapter

## 职责

在 Game Star Box 首次启动肩键校准或用户明确发起的重新校准期间，只读 REDMAGIC/Nubia 电容肩键的底层 SAR 输入，并转换成 `LEFT/RIGHT + DOWN/UP` 语义事件。

本模块不负责游戏内肩键映射，不提供屏幕点击注入，不常驻监控设备输入。

## 输入

普通进程只能请求两个类型化动作：

- 启动当前校准会话的肩键读取；
- 停止当前校准会话的肩键读取。

调用方不能传入 shell 命令、设备路径、keyCode、包名或任意字符串参数。

## 输出

- `LEFT DOWN/UP`；
- `RIGHT DOWN/UP`；
- 有限状态码，例如 SAR 未发现、Shizuku 不可用、Reader 启动失败。

任何其他原始输入都不得跨越 Binder 边界。

## 权限需求

- Shizuku/Sui Binder 可用；
- 用户授予 Game Star Box Shizuku 权限；
- UserService 以 shell/root identity 运行，从而能够读取 REDMAGIC SAR `/dev/input/eventN`。

Shizuku 不可用时，竞界主界面和其他普通功能仍必须可启动；首次校准保留本地触摸恢复入口。

## 允许操作白名单

固定允许：

1. `/system/bin/getevent -pl`
2. `/system/bin/getevent -ql <validated REDMAGIC SAR event node>`

`<validated REDMAGIC SAR event node>` 必须由 Privileged Service 自己发现，格式严格为 `/dev/input/eventN`，且设备名必须满足 Nubia/REDMAGIC SAR 白名单。

仅解析：

- `KEY_F7` / `0x41` → LEFT；
- `KEY_F8` / `0x42` → RIGHT；
- DOWN / UP。

## 明确禁止

- 通用 `exec(command)` / `runShell(String)`；
- `sh -c`；
- UI/WebView/网络指定 `/dev/input` 路径；
- 读取并向上层转发其他按键或触摸事件；
- `sendevent`；
- `input keyevent` / `input motionevent`；
- uinput / 虚拟手柄；
- 屏幕点击注入；
- 修改 REDMAGIC 或 Android Settings；
- 写 sysfs；
- 游戏运行期间长期常驻读取。

## 信任边界

```text
REDMAGIC SAR hardware
   ↓ Linux EV_KEY F7/F8
RedMagicShoulderReaderService (Shizuku shell/root UID)
   ↓ only LEFT/RIGHT DOWN/UP
IRedMagicShoulderCallback
   ↓ semantic hub
ShoulderBootInputHub (Core)
   ↓
ShoulderBootStateMachine
```

WebView 不持有 privileged binder，也不能选择设备或命令。

## 威胁模型

重点防止：

- 借肩键功能扩张为全局键盘/触摸监听；
- 任意 `/dev/input` 路径被注入；
- Reader 变成输入注入器；
- UserService 在校准结束后残留；
- Shizuku 授权被撤销后循环重连或崩溃；
- 其他传感器误识别成肩键 SAR；
- Binder 传输原始输入内容导致隐私面扩大。

## 状态机

```text
DISABLED
 → CONNECTING_SHIZUKU
 → PERMISSION / BINDING
 → DETECTING_SAR
 → READING_F7_F8
 → STOPPING
 → DISABLED
```

Activity 离开前台、校准完成、Binder 死亡或显式停止均进入 `STOPPING`。

## 错误码

- `GSB-SHOULDER-SHIZUKU-UNAVAILABLE`
- `GSB-SHOULDER-SHIZUKU-UNSUPPORTED`
- `GSB-SHOULDER-SHIZUKU-DENIED`
- `GSB-SHOULDER-SHIZUKU-CONNECT-FAILED`
- `GSB-SHOULDER-SAR-NOT-FOUND`
- `GSB-SHOULDER-READER-DETECT-FAILED`
- `GSB-SHOULDER-READER-START-FAILED`
- `GSB-SHOULDER-BINDER-DEAD`

## 日志规范

稳定前缀：`[GSB-SHOULDER]`。

只记录 capability 生命周期、受信任节点数量、LEFT/RIGHT 语义事件和错误码；不记录其他原始输入。

## 超时与重试

- `getevent -pl` 发现阶段最长约 3 秒；
- Reader 本身只在 Activity 前台的校准生命周期内持续；
- Binder/Reader 失败不无限重试；
- 权限被拒绝后不循环申请；
- Activity pause/stop 时立即销毁 Reader 进程。

## 回退方案

无法取得 Shizuku 权限、无法发现 REDMAGIC SAR、Reader 异常时：

- 不修改任何系统设置；
- 不扩大到全 `/dev/input`；
- 保留 `shoulder-boot.html` 顶部本地触摸 L/R 入口完成启动流程；
- 普通主界面不依赖该 capability。

## 测试矩阵

至少覆盖：

- 红魔 9 Pro+ 左/右实体肩键分别产生 LEFT/RIGHT；
- 点按 DOWN/UP 完整；
- 长按持续状态正确；
- L+R 同按可驱动点火；
- 非 F7/F8 被忽略；
- 不匹配的 input device 被拒绝；
- Shizuku 未运行；
- 首次授权；
- 用户拒绝授权；
- Binder 读取中死亡；
- Activity 切后台 Reader 立即退出；
- 校准完成 Reader 立即退出；
- 触摸兜底不依赖 Shizuku；
- OTA Privileged Installer 无回归。

## 已知 ROM / Android 兼容性

- 当前主要验证设备：REDMAGIC 9 Pro+；
- 标准 Android Gamepad L1/L2/R1/R2 与 ROM 直接暴露的 F7/F8 仍优先由普通 KeyEvent Adapter 处理；
- 当前实机已经确认：肩键灯效响应但标准 KeyEvent 未到达竞界，因此需要 SAR Reader 路径；
- 其他 REDMAGIC 机型必须重新验证设备名和 F7/F8 证据，不能直接宣称兼容。

## 变更记录

- 2026-09-15：根据 REDMAGIC 9 Pro+ 实机“灯亮但无 KeyEvent”结果建立只读 SAR Reader；参考 RedTrigger MIT 工程对 Nubia SAR / F7 / F8 的公开取证，未引入其 uinput/输入注入能力。
