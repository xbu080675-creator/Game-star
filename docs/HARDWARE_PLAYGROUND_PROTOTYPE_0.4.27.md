# Game Star Box 0.4.27 — Hardware Playground Prototype

## 目标

本轮将首次启动从“硬件自检演出”推进到“可玩的硬件启动关卡”。核心产品原则：**硬件能力本身就是玩法，而不是功能列表或自动动画。**

参考 PlayStation / Astro Bot 的方法是“先让用户把手里的硬件玩一遍”，但不复制 Sony 的角色、Logo、设备外观、音效或资源。

## 首次启动关卡

交互顺序：

1. **Shoulder Contact**：REDMAGIC 实体左/右肩键分别轻触与长按，真实 SAR 输入驱动传感模组演出和物理马达反馈；
2. **Motion**：Android 原生 `SensorManager` 优先读取 `TYPE_GAME_ROTATION_VECTOR`，回退 `TYPE_ROTATION_VECTOR`；用户倾斜真实手机，让内部探针走到左右两个极限；若设备/ROM 无可用传感器，才允许拖动机身作为有限兜底；
3. **Touch Glass**：用户在真实触摸屏上划动，擦除黑色玻璃遮罩，露出内部 PCB / SoC / PMIC / UFS；
4. **Haptic Materials**：用户依次触发 `DETENT / RATCHET / LATCH` 三种物理触觉样本，同一颗手机马达输出三种不同波形；这一段没有“低频声音冒充震动”的路径；
5. **Thermal Model**：用户绕转子拖动，让可视化冷却风扇模型转起来；
6. **Final Grip**：最后才提示同时长按 L+R，进入既有 `ARMED -> IgnitionGate -> IGNITING`；
7. **Ignition**：只有硬件序列与后台 `CORE_RUNTIME / GAME_CATALOG / MAIN_SURFACE` 同时 READY，才关闭剖视、点亮屏幕并显示 `GAME STAR BOX`。

## 真硬件与模型边界

本原型真实使用：

- REDMAGIC 肩键 SAR F7/F8 输入；
- Android 原生姿态/旋转传感器；
- 手机真实触摸屏；
- Android `Vibrator/VibratorManager` 物理马达；
- 隐藏主 WebView 的真实首屏 Provisioning。

本原型**没有**声称控制 REDMAGIC 真风扇。界面明确标注 `THERMAL FAN MODEL`，当前仅为热管理玩法模型。真实风扇控制必须等厂商接口被验证后，通过独立 typed / reversible Adapter 接入，不允许猜 Settings、sysfs 或 shell 写法。

## Native Motion Adapter

文件：

`app/src/main/java/com/xbu/esportscenter/platform/android/HardwarePlaygroundMotionAdapter.java`

职责：

- 只读取 Android `SensorManager`；
- 优先 `TYPE_GAME_ROTATION_VECTOR`，回退 `TYPE_ROTATION_VECTOR`；
- 将传感器矩阵转换为语义 `pitchDegrees / rollDegrees`；
- 约 28 ms 最小派发间隔；
- Activity pause / finish / destroy 时停止注册；
- 不引用 Shizuku、REDMAGIC Settings、WebView 或 Core。

稳定日志：`[GSB-PLAYGROUND]`。

错误码：

- `GSB-PLAYGROUND-MOTION-UNAVAILABLE`
- `GSB-PLAYGROUND-MOTION-REGISTER-FAILED`
- `GSB-PLAYGROUND-MOTION-DECODE-FAILED`

## Physical Haptic Material Adapter

文件：

`app/src/main/java/com/xbu/esportscenter/platform/android/HardwarePlaygroundHapticAdapter.java`

职责：

- Android S+ 使用 `VibratorManager.getDefaultVibrator()`；
- Android T+ 使用 `VibrationAttributes.USAGE_HARDWARE_FEEDBACK`；
- `DETENT`：短促机械止挡；
- `RATCHET`：多段轻脉冲模拟齿轮齿感；
- `LATCH`：更重的分段锁止波形；
- 无任何音频输出；Bluetooth / 耳机不参与触觉链。

稳定日志：`[GSB-PLAY-HAPTIC]`。

## Presentation / Native 边界

WebView 只请求语义动作：

- `GSBPlayground.startMotion()`
- `GSBPlayground.stopMotion()`
- `GSBPlayground.haptic(sample)`，其中 sample 被 Native 限定为 `0..2`。

Native 只向本地启动页发布语义姿态：

- `window.onGSBMotion(pitch, roll)`
- `window.onGSBMotionUnavailable(code)`

UI 不能传入 Sensor 类型、VibrationEffect 数组、Settings key、设备路径、shell 命令或厂商控制参数。

## 触摸层安全 / 可玩性修复

透明的 touch canvas / fan interaction overlay 默认 `pointer-events:none`，只有对应关卡激活时才接管触摸，避免隐藏层抢掉肩键触控兜底或 Motion 拖动。

## 一次性体验迁移

新增：

`hardware_playground_migration_0427_v5`

覆盖安装旧 0.4.27 测试包时，仅一次把 `shoulder_calibrated_v1` 重置为 false，以便无需清数据即可完整体验 Hardware Playground。完成一次后恢复正常 FAST 启动。

## 已知原型限制

当前 `ShoulderBootStateMachine` 在左右单侧校准完成后已经进入 `BOTH_HOLD`，而 Motion / Touch / Haptic / Thermal Playground 暂时属于 presentation-side sequencing。

因此，如果用户在中间关卡**故意提前同时长按实体 L+R 足够久**，Core 和 `HardwareAwakeningMotorDriver` 理论上可能提前进入 ARMED / ignition-side haptic。正常演出不会在此时提示 L+R，因此普通体验不应触发，但这不是最终结构。

下一阶段硬化目标：新增平台无关 `PlaygroundGate`（或等价 Core 语义状态），要求 `playgroundComplete && shoulderSequenceComplete && provisioningReady` 三者同时成立才允许 `ARMED -> IGNITING`，并让最终物理 ignition haptic 也只由同一个语义 Gate 释放。

不得用 UI flag、延时、忽略 raw input 等方式掩盖这个问题。

## CI 验证

临时 workflow：`Build Hardware Playground Prototype`，成功后已从分支删除。

最终成功构建：

- head：`15d0cb9c1f4b4a02dfb10ca1424809947a7c9c22`
- run：`34903151403`
- artifact id：`10371611370`
- Artifact ZIP digest：`sha256:312812bcf716cf0920bd8e547d15b6c9e9a3e3d1d3ed60b36d8b48a7bebb05b4`
- APK SHA-256：`9b928d88b6d2d0e80f27b041ed50db81fe82fba8c0886aa89bb1aaa54ad01420`

通过：

- native motion adapter guard；
- tactile material adapter guard；
- REDMAGIC physical motor contract retained；
- touch / haptic / thermal playground structure guard；
- v5 migration guard；
- fake-haptic sub-bass negative guard；
- JDK17 / API35 / Gradle 8.9 release compile；
- fixed DEV signing；
- APK staging / artifact upload。

## REDMAGIC 9 Pro+ 实机验收重点

1. 左右实体肩键输入与物理马达仍无回归；
2. Motion 阶段倾斜真实手机时，内部探针方向和延迟是否自然；
3. 触摸擦除是否跟手、是否像“玩设备”而不是教程；
4. `DETENT / RATCHET / LATCH` 三种马达波形是否能明显区分；
5. 热管理模型的手势是否有趣而不过长；
6. 最后 L+R 是否形成足够明显的高潮；
7. 进入主页时首屏是否已经 hydrate 完毕；
8. 若体验方向通过，再把 Playground Completion 正式提升为 Core Gate 后才考虑合入 main。
