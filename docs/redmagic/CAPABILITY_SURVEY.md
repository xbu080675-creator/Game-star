# REDMAGIC Capability Survey

## 目标

为 Game Star Box 的 REDMAGIC Adapter 建立第一批可验证能力地图。Core 只认识语义能力；本文件记录平台侧证据，不把“发现了厂商组件/节点”误当成“已经可以安全控制”。

## P0 语义能力

基础四类：

- `game.network.boost`
- `game.performance.boost`
- `game.memory.cleanup`
- `game.interruption.shield`

Game Tools / Quick Menu 候选：

- `overlay.fps_monitor`
- `overlay.crosshair`
- `device.fan_control`
- `device.charge_separation`
- `input.shoulder_mapping`
- `capture.screen_record`

## 当前只读证据

`RedMagicCapabilityProbe` 仅收集以下证据：

- `cn.nubia.gamelauncher` 是否存在；
- `cn.nubia.gameassist` 是否存在；
- `gcs_need_kill_game_launcher` / `nubia_game_scene` / `nubia_game_mode` 是否至少有一项可读；
- `/sys/kernel/fan/fan_enable` 是否存在；
- `/sys/kernel/fan/fan_speed_level` 是否存在。

这些证据只说明 REDMAGIC 游戏能力栈或风扇控制路径“可能存在”。在完成调用方式、权限要求、失败恢复和机型验证之前，所有对应语义控制能力保持 `UNKNOWN`，不得标记为 `AVAILABLE`。

## 安全边界

本阶段：

- 不写 Settings；
- 不调用 Shizuku；
- 不执行 shell；
- 不写 sysfs；
- 不启动/注入红魔游戏空间组件；
- 不复用 OTA Privileged Adapter。

后续每一个真实控制能力都必须单独确认：调用目标、参数范围、权限、回滚动作、超时、ROM 兼容性、失败降级和实机证据。

## 下一步

优先调查顺序：

1. 性能增强：确认 `nubia_game_mode` / Game Assist 是否存在稳定原生控制接口；
2. 通知屏蔽：优先寻找红魔公开或稳定系统接口，避免自己做粗暴全局 DND；
3. 内存清理：确认厂商是否提供游戏前清理动作，禁止通用 kill-all；
4. 网络加速：确认是否依赖独立服务/VPN/QoS；
5. Game Tools：风扇、充电分离、肩键、FPS HUD、准星、录屏逐项审计。

只有已验证的控制路径才能从 `UNKNOWN` 升为 `AVAILABLE`。
