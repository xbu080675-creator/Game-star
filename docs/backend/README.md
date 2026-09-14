# Backend Phase 1 — Core Runtime / REDMAGIC Entry

## 职责

当前阶段建立后端骨架与红魔入口链路。0.4.11 完成只读状态探测；0.4.12 在不新增特权面的前提下，将真实竞技键开关边沿转换为 Core 内部的语义化 `GameEntryRequest`。

## 模块边界

```text
GameStarBoxApplication
├─ Core
│  ├─ CapabilityRegistry
│  ├─ GameSessionManager
│  └─ GameEntryCoordinator
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

当前语义能力：

- `platform.redmagic.entry.read`
- `platform.redmagic.entry.edge`

能力状态使用 Core 的 `CapabilityAvailability`：

- `AVAILABLE`
- `UNAVAILABLE`
- `DENIED`
- `DEAD`
- `UNSUPPORTED`
- `UNKNOWN`

## 0.4.12 Entry 语义边沿

红魔当前 ROM 约定：`gcs_need_kill_game_launcher = 0` 表示竞技键/游戏空间开关处于开启状态。

0.4.12 不把“当前值为 0”直接视作一次新的用户动作，而是先建立基线，仅在观察到真实的 `非 0 -> 0` 转换时发出：

```text
GameEntryRequest {
  source = REDMAGIC_COMPETITIVE_SWITCH
  sequence = monotonic
  createdAtElapsedRealtimeMs = ...
}
```

这样可以避免“应用启动时竞技键原本就开着”被误判成一次新的接管请求。

## 当前行为

应用进程启动时初始化后端运行时；`RedMagicEntryMonitor` 注册 ContentObserver 并读取三个红魔状态。状态变化刷新 Capability Registry。

当检测到真实竞技键开启边沿时，Platform Adapter 只向 Core 提交语义化入口请求，由 `GameEntryCoordinator` 统一分发并使用 `[GSB-ENTRY]` 日志记录。

当前版本**不会**：

- 写入任意 Settings；
- 调用 Shizuku；
- 执行 shell；
- 修改红魔性能模式；
- 启动/停止红魔游戏空间；
- 自动拉起任意游戏或其他应用；
- 从后台强制拉起竞界 Activity；
- 修改红魔游戏列表；
- 接管系统级竞技键行为。

## 安全边界

本阶段不扩大 `docs/ENGINEERING_CONSTITUTION.md` 中现有特权白名单。Shizuku 仍仅用于 Game Star Box 自更新。

若下一阶段需要通过 Shizuku/ADB/系统设置写入来接管红魔能力，必须先单独评审 capability、修改工程宪法与白名单、定义回退与实机测试矩阵，再写实现。

## 日志

- Core：`[GSB-CORE]`
- REDMAGIC Adapter：`[GSB-RM]`
- Entry：`[GSB-ENTRY]`

稳定错误/状态码示例：

- `GSB-RM-ENTRY-READ-AVAILABLE`
- `GSB-RM-ENTRY-NON-REDMAGIC`
- `GSB-RM-ENTRY-SETTINGS-NOT-EXPOSED`
- `GSB-RM-ENTRY-READ-DENIED`
- `GSB-RM-ENTRY-MONITOR-START-FAILED`
- `GSB-RM-ENTRY-EDGE-AVAILABLE`
- `GSB-RM-ENTRY-EDGE-SWITCH-NOT-EXPOSED`
- `GSB-RM-ENTRY-EDGE-MONITOR-UNAVAILABLE`
- `GSB-CORE-SESSION-INVALID-TRANSITION`

## Game Session 状态机

当前 Core 定义：

`IDLE -> PREPARING -> LAUNCHING -> RUNNING -> SUSPENDED -> RUNNING -> ENDED -> IDLE`

当前版本仅建立状态机，不自动启动游戏。

## 0.4.12 实机测试重点

1. 竞界正常启动，前端视觉、启动音效、菜单音乐、OTA 不回归。
2. 竞技键关闭时启动竞界，再把竞技键切到开启：应产生一次 `REDMAGIC_COMPETITIVE_SWITCH` Entry Request。
3. 竞技键已经开启时再启动竞界：只建立 baseline，不应伪造新的 Entry Request。
4. 连续关闭/开启竞技键：每个真实 `非 0 -> 0` 边沿只产生一次请求。
5. 不应出现新的 Shizuku 授权请求、ADB 操作或 Settings 写入。
6. 红魔原游戏空间行为在本版本不被主动修改。

## 后续阶段

下一步在 Entry 语义事件稳定后，进入 REDMAGIC Entry 接管 capability 的安全评审与实现。若需要写系统状态，继续遵循“类型化 capability、最小权限、Fail Closed、可恢复”，并与现有 OTA Privileged Adapter 隔离。
