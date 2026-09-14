# 0.4.27 Realistic Hardware Awakening Prototype

## 目的

本原型用于替换早期偏抽象的 Hardware Awakening 演出。参考 PlayStation / Astro Bot 的核心方法——“硬件测试本身就是演出”——但不复制 Sony 的角色、Logo、设备外观或资源。

本轮目标不是继续堆粒子、能量球、HUD，而是把首次启动表现成一台真实游戏手机正在做硬件上电与自检。

## 演出原则

1. 手机本体必须有可感知的金属中框、玻璃层、厚度、边缘反射与实体肩键行程；
2. 左右肩键测试时镜头靠近对应侧，而不是在屏幕中央画抽象进度条；
3. 测试阶段允许短暂使用半透明剖视，但内部必须表现为具体硬件：肩键传感模块、PCB、SoC、PMIC、风扇、0815 主马达；
4. 长按进度直接映射到对应肩键传感器内部状态，不使用旧版 `bus/core/jet` 能量条语言；
5. L+R 阶段才让主马达和热管理模块加入演出，体现“左右控制链完成后整机上电”；
6. Ignition 时外壳重新闭合，真实屏幕从黑态点亮，最后才出现 `GAME STAR BOX / SYSTEM ONLINE`；
7. 音频只负责机械/电子提示，最低频率受限，不承担触觉；触觉继续由独立 `HardwareAwakeningMotorDriver` 和 Android `Vibrator/VibratorManager` 输出。

## 状态映射

Core 状态机不变：

`LEFT_TAP -> LEFT_HOLD -> RIGHT_TAP -> RIGHT_HOLD -> BOTH_HOLD -> ARMED -> IGNITING -> COMPLETE`

视觉映射：

- `LEFT_TAP`：完整机身，等待实体左肩第一次输入；
- `LEFT_HOLD`：镜头向左侧偏转，机身进入左侧剖视，显示左肩 capacitive sensor；
- `RIGHT_TAP`：镜头回中，左侧模块已锁定；
- `RIGHT_HOLD`：镜头向右侧偏转，显示右肩 capacitive sensor；
- `BOTH_HOLD`：镜头回中，PCB / SoC / PMIC / 风扇 / 0815 主马达同时可见；
- `ARMED`：硬件已验证，继续等待 `FirstBootProvisioningCoordinator`；
- `IGNITING`：剖视关闭，外壳恢复完整，风扇高速、物理主马达波形、屏幕点亮；
- `COMPLETE`：主界面接管。

## 仍保持的架构边界

本轮只改演出层与一次性体验迁移：

- 不修改 `ShoulderBootStateMachine`；
- 不修改 `IgnitionGate`；
- 不修改 `FirstBootProvisioningCoordinator`；
- 不扩大 Shizuku 权限；
- 不扩大 REDMAGIC Settings 白名单；
- 不修改 SAR F7/F8 Reader；
- 不使用输入注入、uinput、虚拟手柄；
- 不修改 OTA 权限面；
- 不回退物理马达驱动。

## 一次性体验迁移

新增：

`hardware_realistic_visual_migration_0427_v4`

覆盖安装旧 0.4.27 测试包后，会仅一次把 `shoulder_calibrated_v1` 重置为 false，以便无需清数据即可完整体验真实硬件剖视版本。完成一次后恢复正常 FAST 启动。

## CI 硬门槛

临时 workflow `Build Realistic Awakening Prototype` 已完成后删除。

成功构建提交：

`bd3f93d80f1014f72276b4f798a297167589bab0`

通过项：

- Android SDK / JDK17 / Gradle 8.9；
- fixed DEV signing；
- realistic hardware awakening guard；
- 必须存在 0815 motor / capacitive sensor / fan / PCB 可视结构；
- 必须存在 v4 migration；
- 必须保留 `VibratorManager` / `USAGE_HARDWARE_FEEDBACK`；
- 禁止旧 `core/bus/jet` 抽象演出重新出现；
- 禁止旧 sub-bass fake-haptic 路径重新出现；
- release compile；
- APK stage / artifact upload。

Artifact ZIP digest：

`sha256:21ad2847066b255e28000601273c1bacba269ba9c909c5c596d96f05f1f36b92`

APK SHA-256：

`d760980822060a617c99ab9583e7c06cd7e3304dc8418f52a26d35c093bcac3a`

## REDMAGIC 9 Pro+ 实机验收

本轮只评估演出是否建立“真实设备正在被唤醒”的感觉：

1. 覆盖安装无需清数据，应完整运行一次 v4 演出；
2. 左肩第一次输入后手机镜头应明显转向左侧并显示真实传感模组；
3. 左长按时 shoulder sensor 内部条变化必须与真实按压进度同步；
4. 右肩同理；
5. L+R 时内部结构必须可读为 PCB / fan / 0815 motor，而不是抽象能量 UI；
6. 物理马达仍必须来自机身，蓝牙耳机不得承载所谓“震动”；
7. 最终点火应体现“外壳闭合 -> 屏幕真正通电 -> GAME STAR BOX”，而不是普通 Logo 转场；
8. 如果演出不够真实，只继续修改 presentation layer，不允许借机改动 Core / privileged boundary。
