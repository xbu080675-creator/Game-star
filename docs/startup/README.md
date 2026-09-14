# Game Star Box Startup Identity

## 职责

保证从 Android Launcher 点击竞界到 WebView `GAME STAR BOX` 启动动画接管之间，不暴露 Android/ROM 默认白色启动页、默认应用图标或其他非 Game Star Box 视觉元素。

## 0.4.14 修正

0.4.13 的平台 Splash 自己绘制了一套“圆环 + 星形核心 + 分离菱形 + 蓝色能量弧”，实机观感像一个独立 loading 标志，反而把 System Splash 变成了新的视觉主体。这违反了“平台层退到看不见、正式启动动画拥有全部品牌运动”的目标。

0.4.14 改为：**平台启动层只输出与正式动画第 0 帧一致的纯黑场，所有可见 Game Star Box 元素仍由既有 WebView 动画生成。** Android 12+ 的强制 Splash 图标使用透明 drawable，持续时间设为 0；Android 8-11 Starting Window 也只使用纯黑背景。

启动链：

```text
Launcher
  ↓
Theme.EsportsCenter.Starting
  ↓ exact WebView frame 0: pure black
Android System Splash / Starting Window
  ↓
existing GAME STAR BOX boot animation starts normally
  ↓
main UI
```

这不是删除 Android 必需的启动阶段，而是让该阶段在视觉上与正式动画第 0 帧完全重合，因此用户不应看到第二套 Logo、loading 环、Android 图标或亮色闪屏。

## 视觉身份规则

- 平台 System Splash / Starting Window：只允许纯黑场，不主动绘制品牌 Logo。
- 品牌图形、圆环、核心、菱形、能量弧、`GAME STAR BOX` 字样：只允许由正式 WebView 启动动画绘制。
- 不得显示 Android 默认图标、ROM 默认浅色背景或中文“竞界”字样。
- 不得为了“填满等待时间”额外制造独立 loading 动画。

## 平台处理

### Android 12+

通过 `values-v31/styles.xml` 显式设置：

- `android:windowSplashScreenBackground = #000000`
- `android:windowSplashScreenAnimatedIcon = @drawable/gsb_boot_transparent`
- `android:windowSplashScreenAnimationDuration = 0`

系统层仍存在，但视觉只剩黑场。

### Android 8-11

`Theme.EsportsCenter.Starting` 的 `android:windowBackground` 直接使用 `#000000`，不再使用独立 boot preview drawable。

## 安全边界

该改动仅涉及 Android 资源和 Activity 启动主题：

- 不新增权限；
- 不调用 Shizuku；
- 不执行 shell；
- 不读写系统 Settings；
- 不改变 OTA 安全链；
- 不改变 REDMAGIC Adapter 能力边界。

## 0.4.14 实机测试矩阵

1. 冷启动：不得出现白色 Android/default splash。
2. 冷启动：不得出现 0.4.13 那种独立小圆环/loading 标志。
3. 热启动：不得出现白闪、默认图标或额外品牌页。
4. OTA 更新后自动重启：应直接黑场并进入原 GAME STAR BOX 动画。
5. 黑场 → WebView 动画之间不得出现明显亮度、位置或尺寸跳变。
6. 原 `fairy_boot.ogg` 音画同步、触觉节点和主界面加载不得回归。
7. 启动失败时仍应保留现有 Native fatal/recovery 路径。

## 下一阶段：Boot Orchestrator

0.4.14 只纠正平台视觉层，不改动已经验收的 4.02 秒 WebView 时间轴。下一阶段才把启动动画升级为真实 Runtime 驱动：Core、Capability Registry、REDMAGIC Probe、Session Runtime、WebView Ready 等阶段共同产生语义化启动进度；动画保留最短品牌节奏，并在慢任务时进入可拉伸等待段。

原则：只有“进入主界面所必需”的后端任务能够阻塞启动，OTA 网络检查、Laner 网络数据、多设备发现等均不得阻塞 Boot Complete。
