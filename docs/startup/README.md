# Game Star Box Startup Identity

## 职责

保证从 Android Launcher 点击竞界到 WebView `GAME STAR BOX` 启动动画接管之间，不暴露 Android/ROM 默认白色启动页、默认应用图标或其他非 Game Star Box 视觉元素。

## 0.4.13 范围

本版本只处理 **System Splash / Starting Window → WebView Boot** 的视觉连续性，不改动已经验收的 WebView 启动动画时间轴和 `fairy_boot.ogg` 音画同步逻辑。

启动链：

```text
Launcher
  ↓
Theme.EsportsCenter.Starting
  ↓ black + Game Star Box core identity
Android 12+ System Splash / Android 8-11 Starting Window
  ↓
WebView first frame
  ↓
existing GAME STAR BOX boot animation
  ↓
main UI
```

## 视觉身份

Starting Window 使用与现有启动动画一致的基础语言：

- 纯黑背景；
- 圆环；
- 中心星形核心；
- 分离菱形；
- 蓝色能量弧。

不得显示 Android 默认图标、ROM 默认浅色背景或中文“竞界”字样。

## 平台处理

### Android 12+

通过 `values-v31/styles.xml` 显式设置：

- `android:windowSplashScreenBackground = #000000`
- `android:windowSplashScreenAnimatedIcon = @drawable/gsb_boot_core`

### Android 8-11

通过 `Theme.EsportsCenter.Starting` 的 `android:windowBackground` 使用 `@drawable/gsb_boot_preview`，避免默认 Light Theme starting window 闪白。

## 安全边界

该改动仅涉及 Android 资源和 Activity 启动主题：

- 不新增权限；
- 不调用 Shizuku；
- 不执行 shell；
- 不读写系统 Settings；
- 不改变 OTA 安全链；
- 不改变 REDMAGIC Adapter 能力边界。

## 测试矩阵

0.4.13 Preview 实机重点：

1. 冷启动：不得再出现白色 Android/default splash。
2. 热启动：不得出现白闪或默认图标。
3. OTA 更新后自动重启：启动链仍只出现 Game Star Box 视觉。
4. System Splash 到 WebView 动画之间不得出现明显亮度跳变。
5. 原 `fairy_boot.ogg` 音画同步、触觉节点和主界面加载不得回归。
6. 启动失败时仍应保留现有 Native fatal/recovery 路径。

## 下一阶段：Boot Orchestrator

0.4.13 只统一视觉入口。下一阶段将把 WebView 动画从固定 4.02 秒时间轴升级为真实 Runtime 驱动：Core、Capability Registry、REDMAGIC Probe、Session Runtime、WebView Ready 等阶段共同产生语义化启动进度；动画保留最短品牌节奏，并在慢任务时进入可拉伸等待段。

原则：只有“进入主界面所必需”的后端任务能够阻塞启动，OTA 网络检查、Laner 网络数据、多设备发现等均不得阻塞 Boot Complete。
