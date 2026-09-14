# Backend Phase 1 — Core Runtime / REDMAGIC Entry Read Path

## 职责

本阶段只建立后端骨架与红魔入口状态的**只读**探测链路，不修改任何系统设置，不接管竞技键，不新增 Shizuku 权限面。

## 模块边界

```text
GameStarBoxApplication
├─ Core
│  ├─ CapabilityRegistry
│  └─ GameSessionManager
└─ Platform Adapter
   └─ RedMagicEntryMonitor (read-only)
```

Core 不引用 Android Context、Settings、Shizuku、shell、厂商 Binder 或 sysfs。

## 输入

REDMAGIC/Nubia 系统可能公开的 Global Settings：

- `gcs_need_kill_game_launcher`
- `nubia_game_scene`
- `nubia_game_mode`

## 输出

语义能力：`platform.redmagic.entry.read`

能力状态使用 Core 的 `CapabilityAvailability`：

- `AVAILABLE`
- `UNAVAILABLE`
- `DENIED`
- `DEAD`
- `UNSUPPORTED`
- `UNKNOWN`

## 当前行为

应用进程启动时初始化后端运行时；`RedMagicEntryMonitor` 注册 ContentObserver 并读取上述三个状态。状态变化只写结构化日志并刷新 Capability Registry。

当前版本**不会**：

- 写入任意 Settings；
- 调用 Shizuku；
- 执行 shell；
- 修改红魔性能模式；
- 启动/停止红魔游戏空间；
- 自动拉起任意游戏或其他应用；
- 接管竞技键。

## 安全边界

本阶段不扩大 `docs/ENGINEERING_CONSTITUTION.md` 中现有特权白名单。Shizuku 仍仅用于 Game Star Box 自更新。

若下一阶段需要通过 Shizuku/ADB/系统设置写入来接管红魔能力，必须先单独评审 capability、修改工程宪法与白名单、定义回退与实机测试矩阵，再写实现。

## 日志

- Core：`[GSB-CORE]`
- REDMAGIC Adapter：`[GSB-RM]`

稳定错误/状态码示例：

- `GSB-RM-ENTRY-READ-AVAILABLE`
- `GSB-RM-ENTRY-NON-REDMAGIC`
- `GSB-RM-ENTRY-SETTINGS-NOT-EXPOSED`
- `GSB-RM-ENTRY-READ-DENIED`
- `GSB-RM-ENTRY-MONITOR-START-FAILED`
- `GSB-CORE-SESSION-INVALID-TRANSITION`

## Game Session 状态机

当前 Core 定义：

`IDLE -> PREPARING -> LAUNCHING -> RUNNING -> SUSPENDED -> RUNNING -> ENDED -> IDLE`

当前版本仅建立状态机，不自动启动游戏。

## 0.4.11 实机测试重点

1. 竞界可以正常启动，前端视觉/启动音效/OTA 不应发生回归。
2. 红魔 9 Pro+ 上打开竞界后切换竞技键，`[GSB-RM]` 日志应出现新的 snapshot。
3. 若 ROM 暴露相应 Settings，Capability 应进入 `AVAILABLE`。
4. 不应出现新的 Shizuku 授权请求、ADB 操作或系统设置写入。
5. OTA 仍必须保持原固定签名与安全校验链。

## 后续阶段

下一步在这条只读链稳定后，再进入 REDMAGIC Entry 接管设计。任何写能力继续遵循“类型化 capability、最小权限、Fail Closed、可恢复”的工程宪法。
