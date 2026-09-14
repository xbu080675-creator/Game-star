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
import android.widget.TextView;

public class MainActivity extends Activity {

    private FrameLayout root;
    private WebView webView;
    private AppUpdateManager updateManager;
    private FrameLayout updateOverlay;
    private TextView updateTitle;
    private TextView updateStatus;
    private TextView updateDetail;
    private Button updateAction;

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

        if (state.available || state.downloading || state.requiresInstallPermission || state.error != null) {
            ensureUpdateOverlay();
            updateOverlay.setVisibility(View.VISIBLE);
            updateTitle.setText(state.downloading ? "正在更新" : "系统更新");
            updateStatus.setText(state.status);

            StringBuilder detail = new StringBuilder();
            if (!state.latestVersionName.isEmpty()) {
                detail.append("GAME STAR BOX ").append(state.latestVersionName)
                        .append("  ·  code ").append(state.latestVersionCode);
            }
            if (!state.sourceLabel.isEmpty()) detail.append("\n通道：").append(state.sourceLabel);
            if (state.downloading && state.totalBytes > 0) {
                detail.append("\n进度：").append(state.progressPercent).append("%  ·  ")
                        .append(formatBytes(state.downloadedBytes)).append(" / ")
                        .append(formatBytes(state.totalBytes));
            }
            if (!state.changelog.isEmpty()) detail.append("\n\n").append(state.changelog);
            if (state.error != null) detail.append("\n\n错误：").append(state.error);
            updateDetail.setText(detail.toString());

            if (state.downloading) {
                updateAction.setEnabled(false);
                updateAction.setText("下载中 " + state.progressPercent + "%");
            } else if (state.requiresInstallPermission) {
                updateAction.setEnabled(true);
                updateAction.setText("允许安装更新");
            } else {
                updateAction.setEnabled(true);
                updateAction.setText("下载并更新");
            }
        } else if (updateOverlay != null) {
            updateOverlay.setVisibility(View.GONE);
        }
    }

    private void ensureUpdateOverlay() {
        if (updateOverlay != null) return;

        updateOverlay = new FrameLayout(this);
        updateOverlay.setBackgroundColor(Color.argb(196, 0, 4, 8));
        updateOverlay.setClickable(true);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(20), dp(22), dp(18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(12, 25, 34));
        bg.setStroke(dp(1), Color.rgb(69, 94, 111));
        bg.setCornerRadius(dp(10));
        panel.setBackground(bg);

        updateTitle = new TextView(this);
        updateTitle.setTextColor(Color.WHITE);
        updateTitle.setTextSize(20f);
        updateTitle.setTypeface(null, android.graphics.Typeface.BOLD);

        updateStatus = new TextView(this);
        updateStatus.setTextColor(Color.rgb(166, 206, 228));
        updateStatus.setTextSize(13f);
        updateStatus.setPadding(0, dp(8), 0, 0);

        updateDetail = new TextView(this);
        updateDetail.setTextColor(Color.rgb(151, 168, 179));
        updateDetail.setTextSize(12f);
        updateDetail.setLineSpacing(0f, 1.15f);
        updateDetail.setPadding(0, dp(12), 0, dp(16));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);

        Button later = new Button(this);
        later.setText("稍后");
        later.setTextColor(Color.rgb(197, 211, 220));
        later.setTextSize(12f);
        later.setAllCaps(false);
        later.setBackgroundColor(Color.TRANSPARENT);
        later.setOnClickListener(v -> updateOverlay.setVisibility(View.GONE));

        updateAction = new Button(this);
        updateAction.setText("下载并更新");
        updateAction.setTextColor(Color.rgb(5, 16, 22));
        updateAction.setTextSize(12f);
        updateAction.setAllCaps(false);
        GradientDrawable actionBg = new GradientDrawable();
        actionBg.setColor(Color.rgb(225, 246, 255));
        actionBg.setCornerRadius(dp(5));
        updateAction.setBackground(actionBg);
        updateAction.setOnClickListener(v -> {
            if (updateManager != null) updateManager.downloadAndInstall();
        });

        LinearLayout.LayoutParams laterLp = new LinearLayout.LayoutParams(dp(90), dp(42));
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(dp(150), dp(42));
        actionLp.setMargins(dp(10), 0, 0, 0);
        actions.addView(later, laterLp);
        actions.addView(updateAction, actionLp);

        panel.addView(updateTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        panel.addView(updateStatus, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        panel.addView(updateDetail, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));
        panel.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                dp(560), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        );
        updateOverlay.addView(panel, panelLp);
        root.addView(updateOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        updateOverlay.setVisibility(View.GONE);
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
            updateOverlay.setVisibility(View.GONE);
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
