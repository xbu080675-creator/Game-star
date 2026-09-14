package com.xbu.esportscenter;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MainActivity extends Activity {

    private FrameLayout root;
    private WebView webView;
    private AppUpdateManager updateManager;

    private FrameLayout updateOverlay;
    private TextView updateEyebrow;
    private TextView updateTitle;
    private TextView updateStatus;
    private TextView updateMeta;
    private TextView updatePercent;
    private TextView updateDetail;
    private TextView updateSafety;
    private ProgressBar updateProgress;
    private Button updateLater;
    private Button updateAction;
    private boolean updatePermissionFlowPending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            configureWindow();
            root = new FrameLayout(this);
            root.setBackgroundColor(Color.rgb(2, 7, 11));
            setContentView(root);

            updateManager = new AppUpdateManager(this);
            updateManager.setListener(this::onUpdateState);

            createWebView(false);
            root.post(this::hideSystemBars);
        } catch (Throwable t) {
            showFatal("STARTUP", t);
        }
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }
    }

    private void createWebView(boolean safeMode) {
        if (webView != null) {
            root.removeView(webView);
            webView.destroy();
        }

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(2, 7, 11));

        if (safeMode) {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.addJavascriptInterface(new NativeBridge(), "GSBNative");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(
                        "(function(){if(document.getElementById('gsb-native-update-js'))return;" +
                        "var s=document.createElement('script');s.id='gsb-native-update-js';" +
                        "s.src='native-update.js';document.head.appendChild(s);})();",
                        null
                );
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (updateManager != null) updateManager.checkForUpdates(false);
                }, 4800L);
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                final boolean didCrash = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && detail.didCrash();
                runOnUiThread(() -> {
                    try {
                        createWebView(true);
                    } catch (Throwable t) {
                        showFatal(didCrash ? "WEBVIEW_RENDERER_CRASH" : "WEBVIEW_RENDERER_KILLED", t);
                    }
                });
                return true;
            }
        });

        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        webView.loadUrl("file:///android_asset/index.html");
    }

    private class NativeBridge {
        @JavascriptInterface
        public void haptic(String cue) {
            runOnUiThread(() -> emitHaptic(cue));
        }

        @JavascriptInterface
        public boolean canHaptic() {
            return canUseRefinedHaptics();
        }

        @JavascriptInterface
        public void checkUpdate() {
            if (updateManager != null) updateManager.checkForUpdates(true);
        }

        @JavascriptInterface
        public void downloadUpdate() {
            if (updateManager != null) updateManager.downloadAndInstall();
        }

        @JavascriptInterface
        public String updateState() {
            return updateManager == null ? "{}" : updateManager.getStateJson();
        }
    }

    private void onUpdateState(AppUpdateManager.State state) {
        if (webView != null) {
            String json = state.toJson().toString();
            webView.evaluateJavascript(
                    "window.onGSBUpdateState&&window.onGSBUpdateState(" + json + ");",
                    null
            );
        }

        if (state.requiresInstallPermission) {
            updatePermissionFlowPending = true;
        }

        boolean shouldShow = state.available
                || state.downloading
                || state.requiresInstallPermission
                || state.error != null;

        if (!shouldShow) {
            hideUpdateOverlay(false);
            return;
        }

        ensureUpdateOverlay();
        showUpdateOverlay();

        updateEyebrow.setText(state.error == null
                ? "GAME STAR BOX // SYSTEM UPDATE"
                : "GAME STAR BOX // UPDATE RECOVERY");

        if (!state.latestVersionName.isEmpty()) {
            updateTitle.setText("版本 " + state.latestVersionName);
        } else {
            updateTitle.setText("系统更新");
        }

        updateStatus.setText(state.status);
        updateStatus.setTextColor(state.error == null
                ? Color.rgb(207, 229, 242)
                : Color.rgb(255, 140, 147));

        StringBuilder meta = new StringBuilder();
        meta.append("当前 ").append(BuildConfig.VERSION_NAME);
        if (!state.latestVersionName.isEmpty()) {
            meta.append("  →  ").append(state.latestVersionName);
        }
        if (!state.sourceLabel.isEmpty()) {
            meta.append("   ·   ").append(state.sourceLabel);
        }
        if (state.totalBytes > 0) {
            meta.append("   ·   ").append(formatBytes(state.totalBytes));
        }
        updateMeta.setText(meta.toString());

        int progress = Math.max(0, Math.min(100, state.progressPercent));
        updateProgress.setProgress(progress);
        updatePercent.setText(progress + "%");
        updateProgress.setVisibility((state.downloading || progress > 0) ? View.VISIBLE : View.INVISIBLE);
        updatePercent.setVisibility((state.downloading || progress > 0) ? View.VISIBLE : View.INVISIBLE);

        StringBuilder detail = new StringBuilder();
        if (state.downloading && state.totalBytes > 0) {
            detail.append("已接收 ")
                    .append(formatBytes(state.downloadedBytes))
                    .append(" / ")
                    .append(formatBytes(state.totalBytes));
        }
        if (!state.changelog.isEmpty()) {
            if (detail.length() > 0) detail.append("\n\n");
            detail.append("更新内容\n").append(state.changelog);
        }
        if (state.error != null) {
            if (detail.length() > 0) detail.append("\n\n");
            detail.append("错误\n").append(state.error);
        }
        updateDetail.setText(detail.toString());

        updateSafety.setText(
                "安装前强制校验   SHA-256  ·  包名  ·  versionCode  ·  固定签名\n" +
                "更新包仅接受 Game Star Box 官方 Preview Release"
        );

        if (state.downloading) {
            updateLater.setText("后台进行");
            updateLater.setEnabled(true);
            updateAction.setEnabled(false);
            updateAction.setText(progress > 0 ? "下载中  " + progress + "%" : "准备更新…");
        } else if (state.requiresInstallPermission) {
            updateLater.setText("取消");
            updateLater.setEnabled(true);
            updateAction.setEnabled(true);
            updateAction.setText("允许安装并继续");
        } else if (state.error != null) {
            updateLater.setText("关闭");
            updateLater.setEnabled(true);
            updateAction.setEnabled(state.available);
            updateAction.setText(state.available ? "重新尝试" : "关闭");
        } else if (progress >= 100) {
            updateLater.setText("隐藏");
            updateLater.setEnabled(true);
            updateAction.setEnabled(false);
            boolean privilegedStage = state.status.contains("Shizuku")
                    || state.status.contains("安全安装服务")
                    || state.status.contains("正在应用")
                    || state.status.contains("静默提交")
                    || state.status.contains("更新已提交");
            updateAction.setText(privilegedStage ? "正在应用更新" : "等待系统安装确认");
        } else {
            updateLater.setText("稍后");
            updateLater.setEnabled(true);
            updateAction.setEnabled(true);
            updateAction.setText("下载并安装");
        }
    }

    private void ensureUpdateOverlay() {
        if (updateOverlay != null) return;

        updateOverlay = new FrameLayout(this);
        updateOverlay.setBackgroundColor(Color.argb(224, 2, 7, 11));
        updateOverlay.setClickable(true);
        updateOverlay.setFocusable(true);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(28), dp(24), dp(28), dp(22));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(10, 22, 31));
        bg.setStroke(dp(1), Color.rgb(70, 101, 121));
        bg.setCornerRadius(dp(12));
        panel.setBackground(bg);
        panel.setElevation(dp(18));

        updateEyebrow = new TextView(this);
        updateEyebrow.setTextColor(Color.rgb(107, 172, 207));
        updateEyebrow.setTextSize(10f);
        updateEyebrow.setLetterSpacing(0.16f);
        updateEyebrow.setTypeface(null, android.graphics.Typeface.BOLD);

        updateTitle = new TextView(this);
        updateTitle.setTextColor(Color.WHITE);
        updateTitle.setTextSize(34f);
        updateTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        updateTitle.setPadding(0, dp(6), 0, 0);

        updateStatus = new TextView(this);
        updateStatus.setTextColor(Color.rgb(207, 229, 242));
        updateStatus.setTextSize(14f);
        updateStatus.setPadding(0, dp(7), 0, 0);

        updateMeta = new TextView(this);
        updateMeta.setTextColor(Color.rgb(117, 143, 160));
        updateMeta.setTextSize(11f);
        updateMeta.setPadding(0, dp(8), 0, 0);

        LinearLayout progressRow = new LinearLayout(this);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        progressRow.setPadding(0, dp(18), 0, 0);

        updateProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        updateProgress.setMax(100);
        updateProgress.setProgress(0);
        updateProgress.setIndeterminate(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            updateProgress.getProgressDrawable().setTint(Color.rgb(119, 224, 255));
        }

        updatePercent = new TextView(this);
        updatePercent.setText("0%");
        updatePercent.setTextColor(Color.rgb(215, 242, 250));
        updatePercent.setTextSize(12f);
        updatePercent.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        updatePercent.setTypeface(null, android.graphics.Typeface.BOLD);

        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                0, dp(5), 1f
        );
        LinearLayout.LayoutParams percentLp = new LinearLayout.LayoutParams(
                dp(58), dp(28)
        );
        percentLp.setMargins(dp(14), 0, 0, 0);
        progressRow.addView(updateProgress, progressLp);
        progressRow.addView(updatePercent, percentLp);

        updateDetail = new TextView(this);
        updateDetail.setTextColor(Color.rgb(157, 176, 188));
        updateDetail.setTextSize(12f);
        updateDetail.setLineSpacing(dp(2), 1.08f);
        updateDetail.setPadding(0, dp(14), 0, 0);

        updateSafety = new TextView(this);
        updateSafety.setTextColor(Color.rgb(114, 171, 151));
        updateSafety.setTextSize(10f);
        updateSafety.setLineSpacing(dp(1), 1.05f);
        updateSafety.setPadding(0, dp(14), 0, dp(18));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        updateLater = new Button(this);
        updateLater.setText("稍后");
        updateLater.setTextColor(Color.rgb(194, 209, 218));
        updateLater.setTextSize(12f);
        updateLater.setAllCaps(false);
        updateLater.setMinWidth(0);
        updateLater.setMinHeight(0);
        GradientDrawable laterBg = new GradientDrawable();
        laterBg.setColor(Color.rgb(17, 34, 45));
        laterBg.setStroke(dp(1), Color.rgb(55, 78, 93));
        laterBg.setCornerRadius(dp(6));
        updateLater.setBackground(laterBg);
        updateLater.setOnClickListener(v -> {
            emitHaptic("tick");
            hideUpdateOverlay(true);
        });

        updateAction = new Button(this);
        updateAction.setText("下载并安装");
        updateAction.setTextColor(Color.rgb(5, 16, 22));
        updateAction.setTextSize(12f);
        updateAction.setTypeface(null, android.graphics.Typeface.BOLD);
        updateAction.setAllCaps(false);
        updateAction.setMinWidth(0);
        updateAction.setMinHeight(0);
        GradientDrawable actionBg = new GradientDrawable();
        actionBg.setColor(Color.rgb(225, 246, 255));
        actionBg.setCornerRadius(dp(6));
        updateAction.setBackground(actionBg);
        updateAction.setOnClickListener(v -> {
            emitHaptic("click");
            if (updateManager != null) updateManager.downloadAndInstall();
        });

        LinearLayout.LayoutParams laterLp = new LinearLayout.LayoutParams(dp(112), dp(42));
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(dp(190), dp(42));
        actionLp.setMargins(dp(10), 0, 0, 0);
        actions.addView(updateLater, laterLp);
        actions.addView(updateAction, actionLp);

        panel.addView(updateEyebrow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        panel.addView(updateTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        panel.addView(updateStatus, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        panel.addView(updateMeta, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        panel.addView(progressRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        panel.addView(updateDetail, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        panel.addView(updateSafety, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        panel.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int panelWidth = Math.min(dp(760), Math.max(dp(520), screenWidth - dp(92)));
        int panelHeight = Math.min(dp(500), Math.max(dp(360), screenHeight - dp(70)));

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                panelWidth, panelHeight, Gravity.CENTER
        );
        updateOverlay.addView(panel, panelLp);
        root.addView(updateOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        updateOverlay.setVisibility(View.GONE);
    }

    private void showUpdateOverlay() {
        if (updateOverlay == null) return;
        if (updateOverlay.getVisibility() == View.VISIBLE) return;
        updateOverlay.setAlpha(0f);
        updateOverlay.setVisibility(View.VISIBLE);
        updateOverlay.animate().alpha(1f).setDuration(170L).start();
    }

    private void hideUpdateOverlay(boolean animated) {
        if (updateOverlay == null || updateOverlay.getVisibility() != View.VISIBLE) return;
        if (!animated) {
            updateOverlay.animate().cancel();
            updateOverlay.setAlpha(1f);
            updateOverlay.setVisibility(View.GONE);
            return;
        }
        updateOverlay.animate()
                .alpha(0f)
                .setDuration(140L)
                .withEndAction(() -> {
                    if (updateOverlay != null) {
                        updateOverlay.setVisibility(View.GONE);
                        updateOverlay.setAlpha(1f);
                    }
                })
                .start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1024d / 1024d);
        }
        if (bytes >= 1024L) {
            return String.format(java.util.Locale.ROOT, "%.0f KB", bytes / 1024d);
        }
        return bytes + " B";
    }

    private boolean isSystemHapticsEnabled() {
        try {
            return Settings.System.getInt(
                    getContentResolver(),
                    Settings.System.HAPTIC_FEEDBACK_ENABLED,
                    1
            ) == 1;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private Vibrator getRefinedVibrator() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null;
        try {
            VibratorManager manager = getSystemService(VibratorManager.class);
            if (manager == null) return null;
            Vibrator vibrator = manager.getDefaultVibrator();
            if (vibrator == null || !vibrator.hasVibrator()) return null;
            if (!vibrator.hasAmplitudeControl()) return null;
            return vibrator;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean canUseRefinedHaptics() {
        return isSystemHapticsEnabled() && getRefinedVibrator() != null;
    }

    private void emitHaptic(String cue) {
        if (!isSystemHapticsEnabled()) return;

        Vibrator vibrator = getRefinedVibrator();
        if (vibrator == null) return;

        try {
            final int effectId;
            if ("tick".equals(cue)) {
                effectId = VibrationEffect.EFFECT_TICK;
            } else if ("click".equals(cue) || "test".equals(cue)) {
                effectId = VibrationEffect.EFFECT_CLICK;
            } else {
                return;
            }
            vibrator.vibrate(VibrationEffect.createPredefined(effectId));
        } catch (Throwable ignored) {
        }
    }

    private void hideSystemBars() {
        try {
            Window window = getWindow();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false);
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    );
                }
            } else {
                window.getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
            }
        } catch (Throwable ignored) {
        }
    }

    private void showFatal(String stage, Throwable t) {
        try {
            if (root == null) {
                root = new FrameLayout(this);
                root.setBackgroundColor(Color.rgb(2, 7, 11));
                setContentView(root);
            } else {
                root.removeAllViews();
            }

            TextView text = new TextView(this);
            text.setTextColor(Color.WHITE);
            text.setTextSize(16f);
            text.setGravity(Gravity.CENTER);
            text.setPadding(48, 48, 48, 48);
            text.setText(
                    "电竞中心启动失败\n\n" +
                    "Stage: " + stage + "\n" +
                    "Error: " + t.getClass().getSimpleName() + "\n" +
                    String.valueOf(t.getMessage())
            );
            root.addView(text, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        if (webView != null) webView.onResume();

        if (updatePermissionFlowPending) {
            boolean allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                    || getPackageManager().canRequestPackageInstalls();
            updatePermissionFlowPending = false;
            if (allowed && updateManager != null) {
                new Handler(Looper.getMainLooper()).postDelayed(
                        updateManager::resumePendingSystemInstall,
                        180L
                );
            }
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (updateManager != null) {
            updateManager.shutdown();
            updateManager = null;
        }
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (updateOverlay != null && updateOverlay.getVisibility() == View.VISIBLE) {
            hideUpdateOverlay(true);
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
