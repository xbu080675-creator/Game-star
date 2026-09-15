# Game Star Box 工程宪法补充条款：REDMAGIC 肩键校准能力（0.4.27）

> 状态：强制执行，作为 `docs/ENGINEERING_CONSTITUTION.md` 的窄范围补充条款。
> 生效范围：0.4.27 肩键首次校准实验及后续沿用该能力的版本。
> 冲突规则：本文件仅覆盖原工程宪法中“Shizuku 仅用于竞界自身 OTA”以及“REDMAGIC Settings 全部只读”这两项窄限制；其余安全、Core/Adapter、日志、错误码、Fail Closed、CI 与留档规则全部继续有效。

## 1. 允许能力

除既有 `ota.self_update.install` 外，新增一个独立评审的肩键校准 capability：

`input.redmagic.shoulder.calibrate`

它由两个不可拆散的子能力组成：

- `scene.activate_temporary`：只在校准生命周期内临时激活 REDMAGIC 肩键 SAR；
- `sar.read_f7_f8`：只读受信任 REDMAGIC/Nubia SAR 节点的 F7/F8。

用途仅限首次启动肩键适配和用户明确触发的重新校准。该能力不是通用游戏模式开关、全局输入监控器或按键映射器。

## 2. 允许操作白名单

### 2.1 临时 REDMAGIC Scene

Shizuku UserService 只允许使用固定 `/system/bin/settings`，并且只能操作 `global` namespace 的三项固定键：

1. `nubia_game_scene`
2. `nubia_game_mode`
3. `cc_game_mis_operate`

校准开始前必须先读取并保存三项原值；只有原值为空或安全整数时才允许继续。校准期间固定写入：

- `nubia_game_scene=1`
- `nubia_game_mode=1`
- `cc_game_mis_operate=0`

校准完成、Activity pause/stop/destroy 或显式停止时必须恢复进入前的原值；原值不存在则执行固定 `delete global <key>`。

调用方不能提供 Settings key、namespace、value、operation 或命令字符串。

### 2.2 SAR Reader

Privileged Adapter 仅允许：

1. 固定 `/system/bin/getevent -pl` 枚举 Linux input 设备；
2. 只接受设备节点格式 `/dev/input/eventN`；
3. 设备名称必须包含经公开取证确认的 REDMAGIC/Nubia 肩键 SAR 标识 `nubia_tgk_aw_sar`；
4. 对已经通过验证的节点，以固定 `/system/bin/getevent -ql <validated-node>` 进行只读监听；
5. 只解析 `EV_KEY` 中的 `KEY_F7` / scan code `0x41` 和 `KEY_F8` / scan code `0x42`；
6. 只向普通进程输出 `LEFT DOWN/UP`、`RIGHT DOWN/UP` 和有限状态码。

所有命令与参数来源均由 Native/Privileged Adapter 内部生成。WebView、网络、文件、剪贴板和用户文本输入不得提供路径、命令、键值或 Settings 参数。

## 3. 明确禁止

该 capability 严禁：

- 写除上述三项之外的任何 `Settings.Global/Secure/System`；
- 设置 `virtual_game_key`；
- 拉起、模拟或劫持完整 REDMAGIC Game Space；
- 给竞界永久授予 `WRITE_SECURE_SETTINGS`；
- `sendevent`、`input keyevent`、`input motionevent` 或任何输入注入；
- uinput / 虚拟手柄创建；
- 把肩键转换成屏幕点击；
- 读取或转发非 F7/F8 的键盘、触摸、音量键、电源键或其他原始输入；
- 接受任意 `/dev/input/*` 路径；
- 通用 `exec(command)`、`runShell(String)`、动态 `sh -c`；
- 在首次校准/显式重新校准以外长期运行；
- 与 OTA Privileged Installer 共用 AIDL 或合并成万能 UserService。

## 4. 生命周期

```text
DISABLED
  ↓ 首次校准 / 显式重新校准
CONNECTING_SHIZUKU
  ↓
PERMISSION / BINDING
  ↓
CAPTURE_ORIGINAL_SCENE
  ↓
ACTIVATE_TEMP_SCENE
  ↓
DETECTING_SAR
  ↓
READING_F7_F8
  ↓ 校准完成 / Activity 离开 / Binder 断开 / 显式停止
STOP_READER
  ↓
RESTORE_ORIGINAL_SCENE
  ↓
UNBIND
  ↓
DISABLED
```

Shizuku Binder 初始未送达时允许有限重试，不得无限循环。用户拒绝授权后不得反复弹窗。

## 5. 降级策略

- Shizuku 未安装、未启动、未授权或 SAR 未识别：不得阻塞竞界普通功能；首次校准页保留本地触摸恢复入口。
- 临时 Scene 激活失败：不得继续扩大权限或写其他 REDMAGIC 设置。
- 原值不可安全解析：Fail Closed，不修改该设备的 REDMAGIC Scene。
- 不得因为 Reader 失败而启用更宽泛的 `/dev/input` 全局监听。
- Binder 在恢复前死亡时记录错误；不得尝试使用新的宽权限路径补救。REDMAGIC 自身在 Activity 场景切换时仍会重置游戏场景状态。

## 6. 日志与错误码

稳定日志前缀：`[GSB-SHOULDER]`。

允许记录 capability 生命周期、是否发现受信任 SAR、LEFT/RIGHT 语义事件、Scene 激活/恢复结果和错误码。禁止记录其他原始输入内容。

新增/保留错误码：

- `GSB-SHOULDER-SHIZUKU-UNAVAILABLE`
- `GSB-SHOULDER-SHIZUKU-DENIED`
- `GSB-SHOULDER-SCENE-ACTIVATE-FAILED`
- `GSB-SHOULDER-SCENE-RESTORE-FAILED`
- `GSB-SHOULDER-SAR-NOT-FOUND`
- `GSB-SHOULDER-READER-START-FAILED`
- `GSB-SHOULDER-BINDER-DEAD`

## 7. 测试硬门槛

进入 Preview 前必须验证：

- 不进入 REDMAGIC Game Space 也能在 9 Pro+ 校准页激活肩键 SAR；
- 左肩产生 LEFT，右肩产生 RIGHT，点按与长按都有完整 DOWN/UP；
- 校准前后 `nubia_game_scene`、`nubia_game_mode`、`cc_game_mis_operate` 原值可逆恢复；
- `virtual_game_key` 在整个流程中不被写入；
- 非 F7/F8 输入绝不进入 Core；
- 非 `nubia_tgk_aw_sar` 节点被拒绝；
- 无 Shizuku 时触摸兜底仍可完成启动；
- 已授权 Shizuku 时不得无意义重复弹授权窗；
- 首次未授权时只请求一次；
- Binder 死亡时应用不崩溃；
- 校准完成或 Activity 切后台后 reader 不残留；
- 不发生 input/sendevent/uinput 注入；
- OTA 自更新特权链保持原有安全边界。

## 8. 证据来源与实机反馈

第一轮 REDMAGIC 9 Pro+：肩键灯效响应，但竞界没有收到标准 L1/R1 KeyEvent，确认需要 SAR Reader。

第二轮 REDMAGIC 9 Pro+：Shizuku 未出现新授权提示，竞界内肩键仍未工作；只有先进入红魔游戏中心把肩键功能打开后才可使用。这个结果确认 Reader 之前缺少“激活 REDMAGIC 游戏场景/SAR”的前置步骤。

公开工程 RedTrigger（MIT，Lucas Zampieri, 2026）记录：`nubia_game_scene=1` 激活 SAR，`nubia_game_mode=1` 为最小游戏模式标志，`cc_game_mis_operate=0` 关闭重复滑动阻挡；同时明确不设置 `virtual_game_key`，避免拉起完整 Game Space。Game Star Box 仅吸收这些平台事实，独立实现固定、可逆的 typed adapter，不引入其 uinput/输入注入能力，也不永久授予 WRITE_SECURE_SETTINGS。

## 9. 决策

从本补充条款起，Game Star Box 的 Shizuku 白名单为两个彼此隔离的 capability：

1. `ota.self_update.install`：竞界自身已验证 APK 的静默覆盖安装；
2. `input.redmagic.shoulder.calibrate`：校准期间临时激活固定 REDMAGIC Scene + 只读受信任 SAR F7/F8，并在退出时恢复原值。

除此之外仍默认禁止所有新的 Shizuku/shell 能力；任何新增 Settings key、命令、设备范围或输入注入都必须再次修改工程宪法并留档。
