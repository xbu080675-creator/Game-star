# REDMAGIC Shoulder Calibration Privileged Adapter

## 职责

在 Game Star Box 首次启动肩键校准或用户明确发起的重新校准期间：

1. 临时激活 REDMAGIC/Nubia 肩键 SAR 场景；
2. 只读底层 SAR 输入；
3. 转换成 `LEFT/RIGHT + DOWN/UP` 语义事件；
4. 校准结束或离开前台时恢复进入前的 REDMAGIC Scene 原值。

本模块不负责游戏内肩键映射，不提供屏幕点击注入，不拉起完整 Game Space，不常驻监控设备输入。

## 输入

普通进程只能请求类型化动作：

- 激活当前校准会话所需的固定 REDMAGIC Scene；
- 恢复进入校准前的 REDMAGIC Scene；
- 启动当前校准会话的肩键读取；
- 停止当前校准会话的肩键读取。

调用方不能传入 shell 命令、Settings key/value、namespace、设备路径、keyCode、包名或任意自由字符串参数。

## 输出

- `LEFT DOWN/UP`；
- `RIGHT DOWN/UP`；
- 有限状态码，例如 Scene 激活失败、SAR 未发现、Shizuku 不可用、Reader 启动失败。

任何其他原始输入都不得跨越 Binder 边界。

## 权限需求

- Shizuku/Sui Binder 可用；
- 用户授予 Game Star Box Shizuku 权限；
- UserService 以 shell/root identity 运行，从而可以执行固定 REDMAGIC Scene 设置和读取受信任 SAR `/dev/input/eventN`。

不会给竞界普通 App 进程永久授予 `WRITE_SECURE_SETTINGS`。

已授权过同一包名时，Shizuku 不会每次重复弹窗；首次未授权时才请求一次。

## 允许操作白名单

### Scene 激活

固定允许 `/system/bin/settings` 对 `global` namespace 的三项键执行 `get/put/delete`：

- `nubia_game_scene`
- `nubia_game_mode`
- `cc_game_mis_operate`

校准开始前保存原值；校准期间固定写入：

- `nubia_game_scene=1`
- `nubia_game_mode=1`
- `cc_game_mis_operate=0`

退出时恢复原值。原值不存在则删除对应键。

不允许设置 `virtual_game_key`。

### SAR Reader

固定允许：

1. `/system/bin/getevent -pl`
2. `/system/bin/getevent -ql <validated REDMAGIC SAR event node>`

`<validated REDMAGIC SAR event node>` 必须由 Privileged Service 自己发现，格式严格为 `/dev/input/eventN`，且设备名必须包含 `nubia_tgk_aw_sar`。

仅解析：

- `KEY_F7` / `0x41` → LEFT；
- `KEY_F8` / `0x42` → RIGHT；
- DOWN / UP。

## 明确禁止

- 通用 `exec(command)` / `runShell(String)`；
- `sh -c`；
- UI/WebView/网络指定 Settings 参数或 `/dev/input` 路径；
- 写除三项白名单外的任何 Global/Secure/System Settings；
- 设置 `virtual_game_key`；
- 永久授予 `WRITE_SECURE_SETTINGS`；
- 读取并向上层转发其他按键或触摸事件；
- `sendevent`；
- `input keyevent` / `input motionevent`；
- uinput / 虚拟手柄；
- 屏幕点击注入；
- 写 sysfs；
- 游戏运行期间长期常驻读取。

## 信任边界

```text
BootMainActivity foreground
   ↓ typed lifecycle request
RedMagicShoulderPrivilegedAdapter
   ↓ Shizuku Binder
RedMagicShoulderReaderService
   ├─ capture original 3 Global Settings
   ├─ temporary REDMAGIC scene activation
   └─ trusted nubia_tgk_aw_sar getevent reader
              ↓ F7/F8 only
IRedMagicShoulderCallback
   ↓ LEFT/RIGHT DOWN/UP only
ShoulderBootInputHub (Core)
   ↓
ShoulderBootStateMachine
```

WebView 不持有 privileged binder，也不能选择 Settings key/value、设备或命令。

## 生命周期

```text
DISABLED
 → CONNECTING_SHIZUKU
 → PERMISSION / BINDING
 → CAPTURE_ORIGINAL_SCENE
 → ACTIVATE_TEMP_SCENE
 → DETECTING_SAR
 → READING_F7_F8
 → STOP_READER
 → RESTORE_ORIGINAL_SCENE
 → UNBIND
 → DISABLED
```

Activity 离开前台、校准完成、显式停止均必须进入 STOP/RESTORE。

Shizuku Binder 初始未送达时只做有限次数短间隔重试；不无限轮询。

## 错误码

- `GSB-SHOULDER-SHIZUKU-UNAVAILABLE`
- `GSB-SHOULDER-SHIZUKU-UNSUPPORTED`
- `GSB-SHOULDER-SHIZUKU-DENIED`
- `GSB-SHOULDER-SHIZUKU-CONNECT-FAILED`
- `GSB-SHOULDER-SCENE-ACTIVATE-FAILED`
- `GSB-SHOULDER-SCENE-RESTORE-FAILED`
- `GSB-SHOULDER-SAR-NOT-FOUND`
- `GSB-SHOULDER-READER-DETECT-FAILED`
- `GSB-SHOULDER-READER-START-FAILED`
- `GSB-SHOULDER-BINDER-DEAD`

## 日志规范

稳定前缀：`[GSB-SHOULDER]`。

只记录 capability 生命周期、Scene 激活/恢复结果、受信任节点数量、LEFT/RIGHT 语义事件和错误码；不记录其他原始输入。

## 回退方案

无法取得 Shizuku 权限、无法临时激活 REDMAGIC Scene、无法发现 SAR 或 Reader 异常时：

- 不扩大 Settings 白名单；
- 不扩大到全 `/dev/input`；
- 不拉起完整 Game Space；
- 保留 `shoulder-boot.html` 顶部本地触摸 L/R 入口完成启动流程；
- 普通主界面不依赖该 capability。

## 测试矩阵

至少覆盖：

- REDMAGIC 9 Pro+ 不进入 Game Space 也能让肩键在校准页可用；
- 左/右实体肩键分别产生 LEFT/RIGHT；
- 点按 DOWN/UP 完整；
- 长按持续状态正确；
- L+R 同按可驱动点火；
- 三项 REDMAGIC Global Settings 在校准结束后恢复原值；
- `virtual_game_key` 未被写入；
- 非 F7/F8 被忽略；
- 不匹配的 input device 被拒绝；
- Shizuku 未运行；
- 首次授权；
- 已授权无重复弹窗；
- 用户拒绝授权；
- Binder 读取中死亡；
- Activity 切后台 Reader 立即退出并尝试恢复 Scene；
- 校准完成 Reader 立即退出并恢复 Scene；
- 触摸兜底不依赖 Shizuku；
- OTA Privileged Installer 无回归。

## 已知 ROM / Android 兼容性

- 当前主要验证设备：REDMAGIC 9 Pro+；
- 标准 Android Gamepad L1/L2/R1/R2 与 ROM 直接暴露的 F7/F8 仍优先由普通 KeyEvent Adapter 处理；
- 第一轮实机确认：灯效响应但标准 KeyEvent 未到达竞界，需要 SAR Reader；
- 第二轮实机确认：未进入红魔游戏中心开启肩键时，SAR 仍不可用，说明必须先激活 REDMAGIC game scene；
- 其他 REDMAGIC 机型必须重新验证 Settings 行为、设备名和 F7/F8 证据，不能直接宣称兼容。

## 变更记录

- 2026-09-15：根据 REDMAGIC 9 Pro+ “必须先进游戏中心开启肩键”实机结果，加入三项固定、可逆的 REDMAGIC Global Settings 临时激活；不设置 `virtual_game_key`，不永久授予 WRITE_SECURE_SETTINGS。
- 2026-09-15：根据 “灯亮但无 KeyEvent” 结果建立 SAR Reader；参考 RedTrigger MIT 工程对 Nubia SAR / F7 / F8 的公开取证，未引入其 uinput/输入注入能力。
