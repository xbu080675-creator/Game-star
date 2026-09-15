# First Boot Provisioning / Hardware Awakening

## 职责

把 Game Star Box 首次启动拆成两条并行时间轴：

- 前台 `Hardware Awakening`：用户通过左右肩键逐步唤醒控制模块并完成 L+R 点火手势；
- 后台 `First Boot Provisioning`：Core Runtime、游戏库缓存、隐藏主界面首屏同时准备。

`IgnitionGate` 仅在两条时间轴都完成后放行 `ARMED -> IGNITING`。

## 输入

Core 只接受语义状态：

- `FirstBootProvisioningCoordinator.Phase.CORE_RUNTIME`
- `FirstBootProvisioningCoordinator.Phase.GAME_CATALOG`
- `FirstBootProvisioningCoordinator.Phase.MAIN_SURFACE`
- Hardware Awakening 的 `ARMED` 状态

Core 不接受 Android API、WebView、PackageManager、Shizuku、REDMAGIC Settings、网络或文件路径。

## 输出

- Provisioning Snapshot：`completed / overallProgress / blockingReady`
- Ignition Gate Snapshot：`hardwareReady / provisioningReady / open`
- 当且仅当 Gate open 时，平台层允许调用 `ShoulderBootStateMachine.releaseIgnition()`。

## 状态机

```text
Hardware Awakening:
LEFT_TAP -> LEFT_HOLD -> RIGHT_TAP -> RIGHT_HOLD -> BOTH_HOLD -> ARMED
                                                               |
Provisioning: CORE_RUNTIME + GAME_CATALOG + MAIN_SURFACE -------+
                                                               v
                                                           IGNITING
                                                               |
                                                           COMPLETE
```

`ARMED` 是用户已经完成实体硬件仪式但系统仍在完成最后同步的短暂状态。它不是 Loading Page，也不能自行跳入 `IGNITING`。

## 平台实现

`BootMainActivity` 在 Activity 创建后立即：

1. 创建游戏目录并在单独后台线程调用 `catalogJson()` 预热缓存；
2. 隐藏主 WebView，但允许其继续加载；
3. 提前挂接 Capability / Session listener；
4. 暴露窄 `GSBProvisioning` bridge；
5. `native-update.js` 在肩键演出期间提前加载 `gesture-runtime.js`、`game-library.js`、`session-runtime.js`、`quick-menu-runtime.js`；
6. 首屏完成两帧渲染后回报 `MAIN_SURFACE`；
7. 菜单音乐仍延迟到真实 boot gate 打开后加载。

## 安全边界

本模块不新增任何特权能力。它不能：

- 写 REDMAGIC / Android Settings；
- 调用 Shizuku、shell、ADB；
- 执行任意包扫描范围扩张；
- 注入输入；
- 因 Provisioning 失败而扩大权限面。

肩键 SAR 激活/读取继续由独立 REDMAGIC Privileged Adapter 管理，本模块只消费语义结果。

## 性能规则

- 游戏目录预热不得在 UI Thread 执行；
- 主 WebView 在硬件唤醒层下保持 `INVISIBLE` 而不是停止加载；
- 游戏图标仍沿用 `InstalledGameCatalog` 现有策略：只为自动识别游戏生成首屏图标，不为所有 launcher 项同步栅格化；
- 点火动画开始前 `CORE_RUNTIME / GAME_CATALOG / MAIN_SURFACE` 必须全部 READY；
- 网络、OTA、赛事数据等非首屏工作不得进入 Blocking Gate。

## 日志

稳定前缀：`[GSB-BOOT]`。

关键日志：

- `first-boot game catalog ready ms=...`
- `hidden main surface hydrated`
- `hardware awakening armed; waiting for first surface readiness`
- `ignition released: hardwareReady=true provisioningReady=true`

## 错误码

- `GSB-BOOT-GAME-CATALOG-PREWARM-FAILED`

本版本不为本地首屏脚本增加宽泛自动重试错误码；本地资产失败应在 CI / WebView 日志中暴露并修复，而不是通过网络或 shell 兜底。

## 测试门槛

- 首次启动肩键演出期间游戏目录必须在后台线程完成缓存；
- 隐藏主 WebView 必须在点火前加载游戏库、Session、Quick Menu；
- `MAIN_SURFACE` 只能在首屏脚本加载且完成至少两帧渲染后标记；
- L+R 达标只进入 `ARMED`，Provisioning 未 READY 时不得进入 `IGNITING`；
- Provisioning 已 READY 时，ARMED 应立即释放点火，不出现传统 Loading；
- 点火完成后主界面第一次可见时游戏卡片/Quick Menu/Session 状态已经存在；
- 菜单音乐不得在肩键演出期间提前播放；
- 第二次启动仍可走 FAST hardware wake，但必须遵守相同 Gate；
- Activity pause/stop 不得把未完成的按压误判成硬件完成。

## 变更记录

- 2026-09-15：根据 REDMAGIC 9 Pro+ 实机肩键链路已打通后的体验反馈，将首次启动从“肩键校准页”升级为“Hardware Awakening + First Boot Provisioning + Ignition Gate”。
