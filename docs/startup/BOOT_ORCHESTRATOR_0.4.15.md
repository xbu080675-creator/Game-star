# Boot Orchestrator 0.4.15

## 目标

建立平台无关的启动语义状态机，让后续 GAME STAR BOX 启动动画能够由真实后端启动进度驱动，而不是继续依赖固定 4.02 秒时间轴。

## 当前阶段

0.4.15 只落地 Core 侧 Boot Orchestrator，不修改已经在 0.4.14 实机验收通过的启动视觉。

阶段：

```text
CORE_INIT
CAPABILITY_REGISTRY_READY
REDMAGIC_PROBE
SESSION_RUNTIME_READY
WEBVIEW_READY
BOOT_COMPLETE
```

其中 `WEBVIEW_READY` 尚未在 0.4.15 接入 Activity/WebView 生命周期，因此本版只验证后端语义进度与日志，不宣称动画已经动态变速。

## 进度映射

- CORE_INIT: 15%
- CAPABILITY_REGISTRY_READY: 30%
- REDMAGIC_PROBE: 50%
- SESSION_RUNTIME_READY: 65%
- WEBVIEW_READY: 88%
- BOOT_COMPLETE: 100%

进度只表达启动必要阶段，不包含 OTA 网络检查、Laner 网络数据、多设备发现等非阻塞任务。

## 安全边界

`core/boot` 必须保持纯 Java：

- 禁止 Android API；
- 禁止 Shizuku；
- 禁止 Settings；
- 禁止 shell / ProcessBuilder / Runtime.exec；
- 禁止厂商 Binder/sysfs 句柄进入 Core。

CI 新增 Boot Orchestrator Guard 强制检查该边界。

## 日志

稳定前缀：`[GSB-BOOT]`

日志字段：

```text
phase=<BootPhase> progress=<0..100> blockingReady=<true|false>
```

## 下一步

0.4.16 将把 `WEBVIEW_READY` 与 Native/WebView 生命周期接通，并把 BootSnapshot 作为只读、类型化数据暴露给现有启动动画。动画只允许根据语义进度调整速度/等待段，不得重新引入 Android 系统 Splash 视觉，也不得让非阻塞网络任务拖住进入主界面。
