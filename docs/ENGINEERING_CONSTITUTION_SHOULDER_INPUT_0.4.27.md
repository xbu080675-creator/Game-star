# Game Star Box 工程宪法补充条款：REDMAGIC 肩键只读输入（0.4.27）

> 状态：强制执行，作为 `docs/ENGINEERING_CONSTITUTION.md` 的窄范围补充条款。
> 生效范围：0.4.27 肩键首次校准实验及后续沿用该能力的版本。
> 冲突规则：本文件仅覆盖原工程宪法中“Shizuku 仅用于竞界自身 OTA”这一单一限制；其余安全、Core/Adapter、日志、错误码、Fail Closed、CI 与留档规则全部继续有效。

## 1. 新增允许能力

新增第二个、独立评审的 Shizuku capability：

`input.redmagic.shoulder.read`

用途仅限：

- 首次启动肩键适配；
- 用户明确触发的重新校准；
- 为 Game Star Box 启动状态机产生 `LEFT/RIGHT + DOWN/UP` 语义输入。

该能力不是通用输入监控器，也不是按键映射器。

## 2. 允许操作白名单

Privileged Adapter 仅允许：

1. 以固定 `/system/bin/getevent -pl` 枚举 Linux input 设备；
2. 只接受设备节点格式 `/dev/input/eventN`；
3. 设备名称必须匹配 REDMAGIC/Nubia 肩键 SAR 证据：`nubia_tgk_aw_sar`、`sar0` 或 `sar1`；
4. 对已经通过上述验证的节点，以固定 `/system/bin/getevent -ql <validated-node>` 进行只读监听；
5. 只解析 `EV_KEY` 中的 `KEY_F7` / scan code `0x41` 和 `KEY_F8` / scan code `0x42`；
6. 只向普通进程输出：`LEFT DOWN/UP`、`RIGHT DOWN/UP` 和有限状态码。

所有命令与参数来源均由 Native/Privileged Adapter 内部生成。WebView、网络、文件、剪贴板和用户文本输入不得提供设备路径、命令或键值。

## 3. 明确禁止

该 capability 严禁：

- 写 `Settings.Global/Secure/System`；
- 修改 `nubia_game_scene`、`nubia_game_mode` 或其他 REDMAGIC 设置；
- `sendevent`、`input keyevent`、`input motionevent` 或任何输入注入；
- uinput / 虚拟手柄创建；
- 把肩键转换成屏幕点击；
- 读取或转发非 F7/F8 的键盘、触摸、音量键、电源键或其他原始输入；
- 接受任意 `/dev/input/*` 路径；
- 通用 `exec(command)`、`runShell(String)`、动态 `sh -c`；
- 在首次校准/显式重新校准以外长期运行；
- 与 OTA Privileged Installer 共用 AIDL 或把两个能力合并成万能 UserService。

## 4. 生命周期

```text
DISABLED
  ↓ 首次校准 / 显式重新校准
CONNECTING_SHIZUKU
  ↓
PERMISSION / BINDING
  ↓
DETECTING_SAR
  ↓
READING_F7_F8
  ↓ 校准完成 / Activity 离开 / Binder 死亡 / 超时
STOPPING
  ↓
DISABLED
```

校准完成后必须立即停止 reader、销毁子进程并解绑 UserService。Activity 退出、应用被销毁、Shizuku Binder 死亡时同样必须停止。

## 5. 降级策略

- Shizuku 未安装、未启动、未授权或设备节点未识别：不得阻塞竞界普通功能；首次校准页保留本地触摸恢复入口。
- 不得为了让肩键工作自动修改 REDMAGIC Settings。
- 不得因为原始肩键读取失败而启用更宽泛的 `/dev/input` 全局监听。
- 任何未知状态 Fail Closed：不读取、不注入、不写系统设置。

## 6. 日志与错误码

稳定日志前缀：`[GSB-SHOULDER]`。

允许记录：

- Shizuku 生命周期状态；
- 是否发现受信任 SAR 节点；
- LEFT/RIGHT DOWN/UP 语义事件；
- 错误码与耗时。

禁止记录其他原始输入内容。

建议错误码：

- `GSB-SHOULDER-SHIZUKU-UNAVAILABLE`
- `GSB-SHOULDER-SHIZUKU-DENIED`
- `GSB-SHOULDER-SAR-NOT-FOUND`
- `GSB-SHOULDER-SAR-DEVICE-REJECTED`
- `GSB-SHOULDER-READER-START-FAILED`
- `GSB-SHOULDER-BINDER-DEAD`

## 7. 测试硬门槛

进入 Preview 前必须验证：

- 红魔 9 Pro+ 左肩产生 LEFT 事件、右肩产生 RIGHT 事件；
- 点按和长按均能得到完整 DOWN/UP；
- 非 F7/F8 输入绝不进入 Core；
- 非 REDMAGIC SAR 节点被拒绝；
- 无 Shizuku 时触摸兜底仍可完成启动；
- 用户拒绝授权时不循环弹窗；
- Binder 死亡时 reader 停止且应用不崩溃；
- 校准完成后 `/system/bin/getevent` reader 不残留；
- Activity 切后台/销毁后不残留 reader；
- 不发生 Settings 写入、input/sendevent/uinput 注入；
- OTA 自更新特权链保持原有安全边界。

## 8. 证据来源与实现约束

0.4.27 红魔 9 Pro+ 实机反馈：肩键灯效有响应，但竞界没有收到标准 `L1/R1` KeyEvent。

公开工程 RedTrigger（MIT，Lucas Zampieri, 2026）记录了 Nubia/REDMAGIC 肩键 SAR 的底层证据：左肩为 `KEY_F7`，右肩为 `KEY_F8`，相关设备名包含 `nubia_tgk_aw_sar`；Nubia 游戏服务会截获原始事件并用于触摸映射。Game Star Box 仅将这些事实作为平台取证依据，独立实现窄范围只读 Reader，不引入其 uinput/输入注入能力。

## 9. 决策

从本补充条款起，Game Star Box 的 Shizuku 白名单为两个彼此隔离的 capability：

1. `ota.self_update.install`：竞界自身已验证 APK 的静默覆盖安装；
2. `input.redmagic.shoulder.read`：首次/显式重新校准期间只读 REDMAGIC SAR F7/F8。

除此之外仍默认禁止所有新的 Shizuku/shell 能力，新增能力必须再次修改工程宪法并留档。
